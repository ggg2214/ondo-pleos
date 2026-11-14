package io.ondo.pleos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ondo.pleos.domain.CrossValidationEngine
import io.ondo.pleos.domain.HighwayMilepostResolver
import io.ondo.pleos.model.GearState
import io.ondo.pleos.model.HighwayDirection
import io.ondo.pleos.model.HighwayMilepostSnapshot
import io.ondo.pleos.model.IncidentReportPacket
import io.ondo.pleos.model.RearWarningAlert
import io.ondo.pleos.model.VehicleSensorSnapshot
import io.ondo.pleos.model.WarmthImpactSummary
import io.ondo.pleos.sdk.DrivingRestrictionState
import io.ondo.pleos.sdk.EmulatorPleosSdkBundle
import io.ondo.pleos.sdk.OndoRelayClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface OndoStage {
    data object DrivingStandby : OndoStage
    data class SnapshotVoiceListening(val snapshot: HighwayMilepostSnapshot) : OndoStage
    data class DispatchedAndVerified(val packet: IncidentReportPacket) : OndoStage
    data class RearVehicleWarning(val alert: RearWarningAlert) : OndoStage
    data class ArrivalWarmthReport(val summary: WarmthImpactSummary) : OndoStage
}

data class OndoUiState(
    val telemetry: VehicleSensorSnapshot,
    val drivingRestrictions: DrivingRestrictionState = DrivingRestrictionState(),
    val liveMilepostLabel: String = "경부선 하행 342.3km",
    val nearbyConnectedCars: Int = 38,
    val relayDispatched: Boolean = false,
    val stage: OndoStage = OndoStage.DrivingStandby
)

class OndoViewModel(
    private val sdkBundle: EmulatorPleosSdkBundle
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OndoUiState(
            telemetry = sdkBundle.telemetryFlow.value,
            drivingRestrictions = sdkBundle.drivingRestrictionMonitor.state.value
        )
    )
    val uiState: StateFlow<OndoUiState> = _uiState.asStateFlow()

    init {
        // 실제 Pleos Vehicle SDK 기어/센서 상태 실시간 수신 동기화
        viewModelScope.launch {
            sdkBundle.telemetryFlow.collect { sensor ->
                val current = _uiState.value
                // 주행 중 실차 기어가 P단으로 변경되면 도착 임팩트 리포트로 자연 전환
                val shouldAutoShowArrival =
                    sensor.gear == GearState.P &&
                        current.telemetry.gear != GearState.P &&
                        current.stage is OndoStage.DispatchedAndVerified
                if (shouldAutoShowArrival) {
                    _uiState.value = current.copy(
                        telemetry = sensor,
                        stage = OndoStage.ArrivalWarmthReport(
                            WarmthImpactSummary(
                                protectedRearVehicles = 38,
                                earnedBlueMembersPoints = 500,
                                expresswaySafetyMileageAdded = true
                            )
                        )
                    )
                } else {
                    _uiState.value = current.copy(telemetry = sensor)
                }
            }
        }

        // AAOS CarUxRestrictions 주행 규제 상태 실시간 반영
        viewModelScope.launch {
            sdkBundle.drivingRestrictionMonitor.state.collect { restriction ->
                _uiState.value = _uiState.value.copy(drivingRestrictions = restriction)
            }
        }
    }

    /**
     * 1단계: 원터치 긴급 제보 버튼(또는 Gleo Action `REPORT_HIGHWAY_INCIDENT`) 호출 즉시
     * t=0 좌표 스냅샷 고정 후 3초 STT(또는 Gleo 전달 파라미터) 처리
     */
    fun onEmergencyOneTouchTriggered(prefilledTranscript: String? = null) {
        val fix = sdkBundle.captureInstantFix()
        val snapshot = HighwayMilepostResolver.resolveSnapshot(
            latitude = fix.latitude,
            longitude = fix.longitude,
            bearingDeg = fix.bearingDeg
        )

        val updatedSensor = VehicleSensorSnapshot(
            speedKmh = 78,
            decelerationMps2 = -3.4f,
            isHazardLampOn = true,
            ambientTempCelsius = 2.5f,
            isWiperActive = false,
            gear = GearState.D
        )
        sdkBundle.updateSimulatedTelemetry(updatedSensor)

        _uiState.value = _uiState.value.copy(
            telemetry = updatedSensor,
            liveMilepostLabel = snapshot.formattedMilepost,
            relayDispatched = false,
            stage = OndoStage.SnapshotVoiceListening(snapshot)
        )

        if (!prefilledTranscript.isNullOrBlank()) {
            viewModelScope.launch {
                delay(400L)
                completeVoiceReport(snapshot, prefilledTranscript)
            }
            return
        }

        sdkBundle.speakGuidance("${snapshot.formattedMilepost} 지점이 고정되었습니다. 상황을 말씀해 주세요.")

        viewModelScope.launch {
            delay(1800L)
            val transcript = sdkBundle.recognizeThreeSecondVoiceReport()
            completeVoiceReport(snapshot, transcript)
        }
    }

    /**
     * 2단계: 음성 키워드 파싱 + Vehicle SDK 센서 교차 검증 후 V2X 중계 서버·도로공사·후방 차량 전파
     */
    private fun completeVoiceReport(snapshot: HighwayMilepostSnapshot, transcript: String) {
        val parsedVoice = HighwayMilepostResolver.parseVoiceTranscript(transcript)
        val sensor = sdkBundle.telemetryFlow.value
        val validation = CrossValidationEngine.evaluate(
            sensor = sensor,
            corroboratingVehicles = 1
        )

        val packet = IncidentReportPacket(
            snapshot = snapshot,
            voiceIncident = parsedVoice,
            sensorSnapshot = sensor,
            validation = validation
        )

        _uiState.value = _uiState.value.copy(
            stage = OndoStage.DispatchedAndVerified(packet)
        )
        sdkBundle.speakGuidance("센서 교차 검증이 완료되어 도로공사 상황실과 후방 차량에 즉시 전파했습니다.")

        // Rust 초저지연 V2X 중계 서버(`relay-server-rs`) 비동기 패킷 송출 (미실행 환경에서도 논블로킹 폴백)
        viewModelScope.launch {
            val sent = OndoRelayClient.dispatchVerifiedIncident(packet)
            if (sent) {
                _uiState.value = _uiState.value.copy(relayDispatched = true)
            }
        }
    }

    /**
     * 3단계 시뮬레이션 / Gleo `CHECK_AHEAD_HAZARDS`: 사고 지점 후방 2.3km 접근 차량 경보
     */
    fun simulateRearVehicleWarning(lookaheadKm: Double? = null) {
        val trailingSensor = VehicleSensorSnapshot(
            speedKmh = 84,
            decelerationMps2 = -2.1f,
            isHazardLampOn = true,
            ambientTempCelsius = 2.5f,
            isWiperActive = false,
            gear = GearState.D
        )
        sdkBundle.updateSimulatedTelemetry(trailingSensor)

        val distanceAhead = (lookaheadKm ?: 2.3).coerceIn(0.2, 3.4)
        val alert = RearWarningAlert(
            highwayName = "경부선",
            direction = HighwayDirection.DOWNBOUND,
            incidentKilometerMark = 342.5,
            distanceAheadKm = distanceAhead,
            blockedLaneLabel = "2차로",
            incidentType = "화물차 전복",
            recommendedAction = "미리 우측 3·4차로로 변경하고 70km/h 이하로 서행하세요"
        )

        if (sdkBundle.isAlertOnActiveRoute(alert)) {
            _uiState.value = _uiState.value.copy(
                telemetry = trailingSensor,
                liveMilepostLabel = "경부선 하행 340.2km",
                stage = OndoStage.RearVehicleWarning(alert)
            )
            sdkBundle.speakGuidance(
                "전방 이점 삼 킬로미터, 이차로에 화물차 전복 사고 제보가 접수되었습니다. 미리 우측 차선으로 이동해 서행하세요."
            )
        }
    }

    /**
     * 4단계: 주행 종료(P단 전환) 시 36.5°C 체온 임팩트 리포트 및 포인트 적립
     */
    fun simulateArrivalParkingSummary() {
        val parkedSensor = VehicleSensorSnapshot(
            speedKmh = 0,
            decelerationMps2 = 0.0f,
            isHazardLampOn = false,
            ambientTempCelsius = 2.5f,
            isWiperActive = false,
            gear = GearState.P
        )
        sdkBundle.updateSimulatedTelemetry(parkedSensor)

        _uiState.value = _uiState.value.copy(
            telemetry = parkedSensor,
            liveMilepostLabel = "목적지 주차장 정차 완료 (Gear P)",
            stage = OndoStage.ArrivalWarmthReport(
                WarmthImpactSummary(
                    protectedRearVehicles = 38,
                    earnedBlueMembersPoints = 500,
                    expresswaySafetyMileageAdded = true
                )
            )
        )
        sdkBundle.speakGuidance("오늘 당신이 켠 삼십육점 오 도 온도가 뒤따르던 서른여덟 대의 차량을 안전하게 지켰습니다.")
    }

    fun resetToDrivingStandby() {
        val cruiseSensor = VehicleSensorSnapshot(
            speedKmh = 102,
            decelerationMps2 = -0.2f,
            isHazardLampOn = false,
            ambientTempCelsius = 2.5f,
            isWiperActive = false,
            gear = GearState.D
        )
        sdkBundle.updateSimulatedTelemetry(cruiseSensor)
        _uiState.value = OndoUiState(
            telemetry = cruiseSensor,
            drivingRestrictions = sdkBundle.drivingRestrictionMonitor.state.value
        )
    }

    fun onSuspendEntering() {
        sdkBundle.onSuspendEntering()
    }

    fun onForegroundResumed() {
        sdkBundle.onForegroundResumed()
    }

    override fun onCleared() {
        sdkBundle.shutdown()
        super.onCleared()
    }
}
