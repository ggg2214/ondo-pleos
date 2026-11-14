package io.ondo.pleos.sdk

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.reflect.Proxy

/**
 * Pleos Connect 주행 규제 개발 가이드(Driving Restriction Development Guide) 준수 모니터.
 *
 * - AAOS / Pleos Connect 에뮬레이터 및 실차(`FEATURE_AUTOMOTIVE`)에서는
 *   `android.car.Car` -> `CarUxRestrictionsManager`의 `registerListener`를 통해
 *   플랫폼이 내려주는 `isRequiresDistractionOptimization` 및 `activeRestrictions` 비트마스크를 실시간 반영합니다.
 * - 일반 Android 기기/테스트 환경에서는 `android.car` 시스템 라이브러리가 없더라도 예외 없이 동작하며,
 *   차량 기어(P단 외) 및 속도 기반으로 주행 중 UX 제한 상태를 동기화합니다.
 */
data class DrivingRestrictionState(
    val isRequiresDistractionOptimization: Boolean = true,
    val noVideo: Boolean = true,
    val noKeyboard: Boolean = true,
    val limitStringLength: Boolean = true,
    val source: String = "VEHICLE_TELEMETRY_FALLBACK"
)

class DrivingRestrictionMonitor(context: Context) {

    companion object {
        // android.car.drivingstate.CarUxRestrictions 표준 상수
        const val UX_RESTRICTIONS_NO_DIALPAD = 1
        const val UX_RESTRICTIONS_NO_FILTERING = 2
        const val UX_RESTRICTIONS_LIMIT_STRING_LENGTH = 4
        const val UX_RESTRICTIONS_NO_KEYBOARD = 8
        const val UX_RESTRICTIONS_NO_VIDEO = 16
    }

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(DrivingRestrictionState())
    val state: StateFlow<DrivingRestrictionState> = _state.asStateFlow()

    private var carInstance: Any? = null
    private var uxRestrictionsManager: Any? = null
    private var isAaosBound: Boolean = false

    init {
        bindCarUxRestrictionsIfAvailable()
    }

    private fun bindCarUxRestrictionsIfAvailable() {
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            return
        }
        try {
            val carClass = Class.forName("android.car.Car")
            val createCarMethod = carClass.getMethod("createCar", Context::class.java)
            val car = createCarMethod.invoke(null, appContext) ?: return
            carInstance = car

            val serviceNameField = carClass.getField("CAR_UX_RESTRICTION_SERVICE")
            val serviceName = serviceNameField.get(null) as String
            val getCarManagerMethod = carClass.getMethod("getCarManager", String::class.java)
            val manager = getCarManagerMethod.invoke(car, serviceName) ?: return
            uxRestrictionsManager = manager

            val managerClass = manager.javaClass
            val currentMethod = managerClass.getMethod("getCurrentCarUxRestrictions")
            applyCarUxRestrictionsObject(currentMethod.invoke(manager))

            val listenerInterface = Class.forName(
                "android.car.drivingstate.CarUxRestrictionsManager\$OnUxRestrictionsChangedListener"
            )
            val listenerProxy = Proxy.newProxyInstance(
                listenerInterface.classLoader,
                arrayOf(listenerInterface)
            ) { _, method, args ->
                if (method.name == "onUxRestrictionsChanged" && !args.isNullOrEmpty()) {
                    applyCarUxRestrictionsObject(args[0])
                }
                null
            }
            val registerMethod = managerClass.getMethod("registerListener", listenerInterface)
            registerMethod.invoke(manager, listenerProxy)
            isAaosBound = true
        } catch (_: Throwable) {
            isAaosBound = false
        }
    }

    private fun applyCarUxRestrictionsObject(restrictionsObj: Any?) {
        if (restrictionsObj == null) return
        try {
            val cls = restrictionsObj.javaClass
            val requiresDo = cls.getMethod("isRequiresDistractionOptimization")
                .invoke(restrictionsObj) as? Boolean ?: true
            val activeMask = cls.getMethod("getActiveRestrictions")
                .invoke(restrictionsObj) as? Int ?: 0

            _state.value = DrivingRestrictionState(
                isRequiresDistractionOptimization = requiresDo,
                noVideo = requiresDo && (activeMask and UX_RESTRICTIONS_NO_VIDEO) != 0,
                noKeyboard = requiresDo && (activeMask and UX_RESTRICTIONS_NO_KEYBOARD) != 0,
                limitStringLength = requiresDo && (activeMask and UX_RESTRICTIONS_LIMIT_STRING_LENGTH) != 0,
                source = "AAOS_CAR_UX_RESTRICTIONS"
            )
        } catch (_: Throwable) {
            // Keep current state on reflection mismatch
        }
    }

    /**
     * 비-AAOS 환경에서 차량 기어/속도 상태 변화에 따라 주행 규제 상태를 동기화합니다.
     */
    fun syncFromTelemetry(isParked: Boolean) {
        if (isAaosBound) return
        _state.value = DrivingRestrictionState(
            isRequiresDistractionOptimization = !isParked,
            noVideo = !isParked,
            noKeyboard = !isParked,
            limitStringLength = !isParked,
            source = "VEHICLE_TELEMETRY_FALLBACK"
        )
    }

    fun release() {
        try {
            uxRestrictionsManager?.let { manager ->
                manager.javaClass.getMethod("unregisterListener").invoke(manager)
            }
        } catch (_: Throwable) {
        }
        try {
            carInstance?.let { car ->
                car.javaClass.getMethod("disconnect").invoke(car)
            }
        } catch (_: Throwable) {
        }
        uxRestrictionsManager = null
        carInstance = null
    }
}
