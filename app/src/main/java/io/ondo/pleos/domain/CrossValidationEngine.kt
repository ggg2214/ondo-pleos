package io.ondo.pleos.domain

import io.ondo.pleos.model.CrossValidationResult
import io.ondo.pleos.model.GearState
import io.ondo.pleos.model.VehicleSensorSnapshot

/**
 * 허위·오신고 방지를 위한 Vehicle SDK 센서 데이터 + 다중 차량 제보 교차 검증(Cross-Validation) 엔진.
 */
object CrossValidationEngine {

    private const val VERIFIED_THRESHOLD = 70
    private const val RAPID_DECEL_THRESHOLD_MPS2 = -2.5f

    fun evaluate(
        sensor: VehicleSensorSnapshot,
        corroboratingVehicles: Int
    ): CrossValidationResult {
        var score = 40 // 원터치 명시적 제보 기본 점수
        val reasons = mutableListOf<String>()

        if (sensor.gear == GearState.D && sensor.speedKmh >= 30) {
            score += 10
            reasons += "고속 본선 주행 맥락 확인 (${sensor.speedKmh}km/h)"
        }

        if (sensor.isHazardLampOn) {
            score += 22
            reasons += "Vehicle SDK 비상등(Hazard) 점등 감지"
        }

        if (sensor.decelerationMps2 <= RAPID_DECEL_THRESHOLD_MPS2) {
            score += 18
            reasons += "Vehicle SDK 급감속 패턴 감지 (${"%.1f".format(sensor.decelerationMps2)}m/s²)"
        }

        if (sensor.ambientTempCelsius <= 3.0f) {
            score += 4
            reasons += "저온 결빙 주의 환경 (${"%.1f".format(sensor.ambientTempCelsius)}°C)"
        }

        if (corroboratingVehicles > 0) {
            val multiBonus = (corroboratingVehicles * 15).coerceAtMost(30)
            score += multiBonus
            reasons += "인접 구간 Pleos 차량 동시 제보 교차 확인 (+${corroboratingVehicles}대)"
        }

        val finalScore = score.coerceIn(0, 99)
        return CrossValidationResult(
            confidenceScore = finalScore,
            isVerified = finalScore >= VERIFIED_THRESHOLD,
            corroboratingVehicles = corroboratingVehicles,
            reasons = reasons
        )
    }

    /**
     * 운전자가 먼저 버튼을 누르지 않았더라도 고속 주행 중 급제동 + 비상등 점등이 발생하면
     * 능동형 음성 제보 어시스트 프롬프트를 트리거할지 판별합니다.
     */
    fun shouldSuggestProactiveReport(sensor: VehicleSensorSnapshot): Boolean {
        return sensor.gear == GearState.D &&
            sensor.isHazardLampOn &&
            sensor.decelerationMps2 <= -3.0f
    }
}
