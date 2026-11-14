package io.ondo.pleos.sdk

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech as AndroidTextToSpeech
import android.speech.tts.UtteranceProgressListener
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.stt.SpeechToText as PleosSpeechToText
import ai.pleos.playground.stt.constant.ErrorType as SttErrorType
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnServerConnectionListener as SttServerListener
import ai.pleos.playground.stt.listener.ResultListener as SttResultListener
import ai.pleos.playground.tts.TextToSpeech as PleosTextToSpeech
import ai.pleos.playground.tts.constant.ErrorType as TtsErrorType
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener as TtsEventListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener as TtsServerListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import io.ondo.pleos.model.GearState
import io.ondo.pleos.model.RearWarningAlert
import io.ondo.pleos.model.VehicleSensorSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Pleos Playground 공식 5종 SDK(`Vehicle:2.3.2`, `FusedLocation:1.1.3`, `NaviHelper:3.0.5`,
 * `TextToSpeech:3.3.1`, `SpeechToText:3.3.1`) 실제 연동 브리지 및 비-Pleos 기기 호환 폴백 어댑터.
 *
 * 공식 Pleos Connect 개발자 가이드 핵심 준수 사항:
 * 1. 실제 `ai.pleos.playground.*` 5종 SDK 초기화, 리스너 등록/해제(`release()`) 수명주기 준수
 * 2. Audio Focus Development Guide: 음성 안내 전 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` 획득 및 종료 시 반환
 * 3. Suspend Mode Audio Focus Guide: 시동 OFF(Suspend / `onStop`) 진입 시 즉시 발화 중단 및 오디오 포커스 반환, Resume 자동 재생 금지
 * 4. TTS Pre-roll Silence Guide: 차량 앰프 웨이크업 시 첫 음절 잘림 방지를 위해 `180ms`(권장 `150~200ms`) 선행 묵음 적용
 * 5. Driving Restriction Guide: `DrivingRestrictionMonitor`를 통해 `CarUxRestrictions` 상태 실시간 제공
 */
data class RawGpsFix(
    val latitude: Double,
    val longitude: Double,
    val bearingDeg: Float
)

interface FusedLocationSdkGateway {
    fun captureInstantFix(): RawGpsFix
}

interface VehicleSdkGateway {
    val telemetryFlow: StateFlow<VehicleSensorSnapshot>
    fun updateSimulatedTelemetry(snapshot: VehicleSensorSnapshot)
}

interface SpeechToTextSdkGateway {
    suspend fun recognizeThreeSecondVoiceReport(): String
}

interface TextToSpeechSdkGateway {
    fun speakGuidance(message: String)
    fun onSuspendEntering()
    fun shutdown()
}

interface NaviHelperSdkGateway {
    fun isAlertOnActiveRoute(alert: RearWarningAlert): Boolean
}

class EmulatorPleosSdkBundle(
    context: Context,
    val playgroundConfig: PleosPlaygroundConfig = PleosPlaygroundConfig.fromBuildConfig()
) :
    FusedLocationSdkGateway,
    VehicleSdkGateway,
    SpeechToTextSdkGateway,
    TextToSpeechSdkGateway,
    NaviHelperSdkGateway {

    companion object {
        /** Pleos Connect TTS 선행 묵음 가이드 권장 범위(150ms ~ 200ms)의 최적 중간값 */
        const val VOICE_GUIDANCE_PREROLL_SILENCE_MS = 180L

        /** Pleos 실차 플랫폼 식별자 (FAQ - App Runtime Environment 가이드) */
        val PLEOS_VEHICLE_DEVICES = setOf("CONNECT_S", "CONNECT_L", "CONNECT_W")
    }

    private val appContext = context.applicationContext
    private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeSpeechJob: Job? = null

    val drivingRestrictionMonitor = DrivingRestrictionMonitor(appContext)

    val isPleosHardwareDevice: Boolean
        get() = Build.DEVICE in PLEOS_VEHICLE_DEVICES

    private val _telemetryFlow = MutableStateFlow(
        VehicleSensorSnapshot(
            speedKmh = 102,
            decelerationMps2 = -0.2f,
            isHazardLampOn = false,
            ambientTempCelsius = 2.5f,
            isWiperActive = false,
            gear = GearState.D
        )
    )
    override val telemetryFlow: StateFlow<VehicleSensorSnapshot> = _telemetryFlow.asStateFlow()

    // 최신 위치 스냅샷 (기본값: 경부선 하행 342.5km 기점 인근, FusedLocation / NaviHelper 수신 시 실시간 갱신)
    private val latestGpsFix = AtomicReference(
        RawGpsFix(
            latitude = 36.9921,
            longitude = 127.1478,
            bearingDeg = 168.0f
        )
    )
    private val isNaviRoutingActive = AtomicReference(true)
    private val latestSttTranscript = AtomicReference<String?>(null)

    // --- Audio Focus & Suspend 관리 ---
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    @Volatile
    private var hasAudioFocus = false
    @Volatile
    private var isInSuspendMode = false

    private val guidanceAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                stopOngoingSpeechInternal()
                abandonAudioFocus()
            }
        }
    }

    private val audioFocusRequest: AudioFocusRequest = AudioFocusRequest.Builder(
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
    )
        .setAudioAttributes(guidanceAudioAttributes)
        .setOnAudioFocusChangeListener(focusChangeListener)
        .build()

    // --- 공식 Pleos 5대 SDK 인스턴스 ---
    private var pleosVehicle: Vehicle? = null
    private var pleosFusedLocation: FusedLocation? = null
    private var pleosNaviHelper: NaviHelper? = null
    private var pleosTts: PleosTextToSpeech? = null
    private var pleosStt: PleosSpeechToText? = null
    @Volatile
    private var isPleosTtsReady = false
    @Volatile
    private var isPleosSttReady = false

    // 비-Pleos 일반 Android 에뮬레이터용 폴백 TTS
    private var androidFallbackTts: AndroidTextToSpeech? = null

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            val mapped = when (gear) {
                VehicleGear.GEAR_PARK -> GearState.P
                VehicleGear.GEAR_REVERSE -> GearState.R
                VehicleGear.GEAR_NEUTRAL -> GearState.N
                null, VehicleGear.GEAR_UNKNOWN -> _telemetryFlow.value.gear
                else -> GearState.D
            }
            val updated = _telemetryFlow.value.copy(
                gear = mapped,
                speedKmh = if (mapped == GearState.P) 0 else _telemetryFlow.value.speedKmh
            )
            _telemetryFlow.value = updated
            drivingRestrictionMonitor.syncFromTelemetry(isParked = mapped == GearState.P)
        }

        override fun onFailed(e: Exception) {
            // Vehicle Service 미지원 영역 또는 시뮬레이터 환경 시 기존 스냅샷 유지
        }
    }

    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
            if (value != null) {
                _telemetryFlow.value = _telemetryFlow.value.copy(
                    isHazardLampOn = value != TurnSignalValue.OFF || _telemetryFlow.value.isHazardLampOn
                )
            }
        }

        override fun onFailed(e: Exception) {
        }
    }

    private val fusedLocationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location != null && location.latitude != 0.0 && location.longitude != 0.0) {
                latestGpsFix.set(
                    RawGpsFix(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        bearingDeg = location.heading
                    )
                )
            }
        }

        override fun onError(e: Exception) {
        }
    }

    private val naviHelperListener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            if (currentLocationInfo.latitude != 0.0 && currentLocationInfo.longitude != 0.0) {
                latestGpsFix.set(
                    RawGpsFix(
                        latitude = currentLocationInfo.latitude,
                        longitude = currentLocationInfo.longitude,
                        bearingDeg = currentLocationInfo.heading.toFloat()
                    )
                )
            }
        }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            isNaviRoutingActive.set(routeStateInfo.isRouting)
        }
    }

    private val pleosTtsEventListener = object : TtsEventListener {
        override fun onReady() {
            isPleosTtsReady = true
        }

        override fun onStart() {}

        override fun onDone() {
            abandonAudioFocus()
        }

        override fun onStop() {
            abandonAudioFocus()
        }

        override fun onError(error: TtsErrorType) {
            abandonAudioFocus()
        }

        override fun onUpdatedRms(rms: Double) {}
    }

    private val pleosSttResultListener = object : SttResultListener {
        override fun onReady() {
            isPleosSttReady = true
        }

        override fun onStartedRecognition() {}

        override fun onUpdated(text: String, isFinal: Boolean) {
            if (text.isNotBlank()) {
                latestSttTranscript.set(text.trim())
            }
        }

        override fun onUpdatedEpdData(epdOn: Long, epdOff: Long) {}

        override fun onEndedRecognition() {}

        override fun onError(error: SttErrorType) {}

        override fun onUpdatedRms(rms: Double) {}
    }

    init {
        initializeAndroidFallbackTts()
        initializePleosVehicleSdk()
        initializePleosNaviHelperSdk()
        initializePleosFusedLocationSdk()
        initializePleosTtsAndSttSdks()
        drivingRestrictionMonitor.syncFromTelemetry(isParked = false)
    }

    private fun initializeAndroidFallbackTts() {
        try {
            androidFallbackTts = AndroidTextToSpeech(appContext) { status ->
                if (status == AndroidTextToSpeech.SUCCESS) {
                    androidFallbackTts?.language = Locale.KOREAN
                    androidFallbackTts?.setAudioAttributes(guidanceAudioAttributes)
                    androidFallbackTts?.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                if (utteranceId == "ondo_guidance") {
                                    abandonAudioFocus()
                                }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                if (utteranceId == "ondo_guidance") {
                                    abandonAudioFocus()
                                }
                            }
                        }
                    )
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun initializePleosVehicleSdk() {
        if (!playgroundConfig.isVehicleStateEnabled) return
        try {
            val vehicle = Vehicle(appContext)
            vehicle.initialize()
            vehicle.getGear().getCurrentGearSelectionState(
                { gear -> gearListener.onSelected(gear) },
                { _ -> }
            )
            vehicle.getGear().registerGearSelection(gearListener)
            vehicle.getTurnSignal().registerTurnSignalState(turnSignalListener)
            pleosVehicle = vehicle
        } catch (_: Throwable) {
            pleosVehicle = null
        }
    }

    private fun initializePleosNaviHelperSdk() {
        if (!playgroundConfig.isNaviHelperEnabled) return
        try {
            val navi = NaviHelper(appContext)
            navi.initialize(
                {
                    try {
                        navi.addListener(naviHelperListener)
                        navi.getCurrentLocationInfo()
                        navi.getRouteStateInfo()
                    } catch (_: Throwable) {
                    }
                },
                { _ -> }
            )
            pleosNaviHelper = navi
        } catch (_: Throwable) {
            pleosNaviHelper = null
        }
    }

    private fun initializePleosFusedLocationSdk() {
        try {
            val fused = FusedLocation(appContext)
            if (fused.isSupported()) {
                fused.initialize()
                fused.getLastKnownLocation(fusedLocationListener)
                fused.registerFusedLocationCallback(fusedLocationListener)
                pleosFusedLocation = fused
            }
        } catch (_: Throwable) {
            pleosFusedLocation = null
        }
    }

    private fun initializePleosTtsAndSttSdks() {
        val hasServerCredentials =
            playgroundConfig.clientId.isNotBlank() && playgroundConfig.clientSecret.isNotBlank()

        // 1) Pleos TextToSpeech SDK
        try {
            val mode = if (hasServerCredentials) TtsMode.HYBRID else TtsMode.ON_DEVICE
            val tts = PleosTextToSpeech(appContext, mode)
            tts.initialize()
            tts.addEventListener(pleosTtsEventListener)
            tts.setAudioAttributes(guidanceAudioAttributes)
            tts.setLocale(Locale.KOREAN)
            if (hasServerCredentials) {
                tts.registerApp(
                    playgroundConfig.clientId,
                    playgroundConfig.clientSecret,
                    object : TtsServerListener {
                        override fun onConnected() {
                            isPleosTtsReady = true
                        }

                        override fun onFailed(error: TtsErrorType) {}
                    }
                )
            }
            pleosTts = tts
        } catch (_: Throwable) {
            pleosTts = null
        }

        // 2) Pleos SpeechToText SDK
        try {
            val mode = if (hasServerCredentials) SttMode.HYBRID else SttMode.ON_DEVICE
            val stt = PleosSpeechToText(appContext, mode)
            stt.initialize()
            stt.addListener(pleosSttResultListener)
            stt.setLocale(Locale.KOREAN)
            if (hasServerCredentials) {
                stt.registerApp(
                    playgroundConfig.clientId,
                    playgroundConfig.clientSecret,
                    object : SttServerListener {
                        override fun onConnected() {
                            isPleosSttReady = true
                        }

                        override fun onFailed(error: SttErrorType) {}
                    }
                )
            }
            pleosStt = stt
        } catch (_: Throwable) {
            pleosStt = null
        }
    }

    override fun captureInstantFix(): RawGpsFix {
        try {
            pleosFusedLocation?.getLastKnownLocation(fusedLocationListener)
            pleosNaviHelper?.getCurrentLocationInfo()
        } catch (_: Throwable) {
        }
        return latestGpsFix.get()
    }

    override fun updateSimulatedTelemetry(snapshot: VehicleSensorSnapshot) {
        _telemetryFlow.value = snapshot
        drivingRestrictionMonitor.syncFromTelemetry(isParked = snapshot.gear == GearState.P)
    }

    override suspend fun recognizeThreeSecondVoiceReport(): String {
        latestSttTranscript.set(null)
        val stt = pleosStt
        if (stt != null && isPleosSttReady) {
            try {
                stt.request()
                delay(2600L)
                stt.stop()
                val recognized = latestSttTranscript.get()
                if (!recognized.isNullOrBlank()) {
                    return recognized
                }
            } catch (_: Throwable) {
            }
        }
        // Pleos STT 미활성(Coming Soon 콘솔 상태 또는 비-Pleos 에뮬레이터) 시 시연 기본 발화 반환
        return "2차로 화물차 전복, 파편 주의"
    }

    /**
     * 공식 가이드 준수 음성 안내:
     * 1) Suspend 상태에서는 새 발화 및 오디오 포커스 요청 금지
     * 2) `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` 오디오 포커스 선획득
     * 3) `180ms`(권장 `150~200ms`) 선행 묵음(Pre-roll Silence) 적용 후 본 음성 재생
     */
    override fun speakGuidance(message: String) {
        if (isInSuspendMode || message.isBlank()) return
        if (!requestAudioFocus()) return

        activeSpeechJob?.cancel()
        activeSpeechJob = sdkScope.launch {
            val tts = pleosTts
            if (tts != null && isPleosTtsReady) {
                try {
                    tts.stop()
                    delay(VOICE_GUIDANCE_PREROLL_SILENCE_MS)
                    if (!isInSuspendMode && hasAudioFocus) {
                        tts.speak(message, true)
                    }
                    return@launch
                } catch (_: Throwable) {
                }
            }

            // 일반 Android 에뮬레이터 폴백: 180ms 무음 Utterance 큐잉 후 본 안내 큐잉
            val fallback = androidFallbackTts
            if (fallback != null) {
                fallback.playSilentUtterance(
                    VOICE_GUIDANCE_PREROLL_SILENCE_MS,
                    AndroidTextToSpeech.QUEUE_FLUSH,
                    "ondo_preroll"
                )
                fallback.speak(
                    message,
                    AndroidTextToSpeech.QUEUE_ADD,
                    null,
                    "ondo_guidance"
                )
            } else {
                abandonAudioFocus()
            }
        }
    }

    override fun isAlertOnActiveRoute(alert: RearWarningAlert): Boolean {
        val withinAheadCorridor = alert.distanceAheadKm in 0.1..3.5
        return withinAheadCorridor && isNaviRoutingActive.get()
    }

    /**
     * Suspend Mode Audio Focus Guide 준수:
     * 시동 OFF(Suspend / Activity `onStop`) 진입 시 진행 중인 출력을 즉시 중지하고 오디오 포커스를 명시적으로 반환합니다.
     * Resume 시에도 이전 오디오를 자동 재개하지 않습니다.
     */
    override fun onSuspendEntering() {
        isInSuspendMode = true
        stopOngoingSpeechInternal()
        abandonAudioFocus()
    }

    fun onForegroundResumed() {
        // Suspend 복귀 시 플래그만 해제하며, 이전 음성을 자동 재생하지 않음 (가이드 필수 원칙)
        isInSuspendMode = false
    }

    private fun stopOngoingSpeechInternal() {
        activeSpeechJob?.cancel()
        activeSpeechJob = null
        try {
            pleosTts?.stop()
        } catch (_: Throwable) {
        }
        try {
            androidFallbackTts?.stop()
        } catch (_: Throwable) {
        }
    }

    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return true
        val result = manager.requestAudioFocus(audioFocusRequest)
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        try {
            audioManager?.abandonAudioFocusRequest(audioFocusRequest)
        } catch (_: Throwable) {
        }
        hasAudioFocus = false
    }

    override fun shutdown() {
        onSuspendEntering()
        sdkScope.cancel()
        drivingRestrictionMonitor.release()

        try {
            pleosVehicle?.let { v ->
                v.getGear().unregisterGearSelection(gearListener)
                v.getTurnSignal().unregisterTurnSignalState(turnSignalListener)
                v.release()
            }
        } catch (_: Throwable) {
        }

        try {
            pleosFusedLocation?.let { f ->
                f.unregisterFusedLocationCallback(fusedLocationListener)
                f.release()
            }
        } catch (_: Throwable) {
        }

        try {
            pleosNaviHelper?.let { n ->
                n.removeListener(naviHelperListener)
                n.release()
            }
        } catch (_: Throwable) {
        }

        try {
            pleosTts?.let { t ->
                t.removeEventListener(pleosTtsEventListener)
                t.release()
            }
        } catch (_: Throwable) {
        }

        try {
            pleosStt?.let { s ->
                s.removeListener(pleosSttResultListener)
                s.release()
            }
        } catch (_: Throwable) {
        }

        try {
            androidFallbackTts?.shutdown()
        } catch (_: Throwable) {
        }
    }
}
