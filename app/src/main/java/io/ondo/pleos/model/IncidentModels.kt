package io.ondo.pleos.model

enum class GearState { P, R, N, D }

enum class HighwayDirection(val koreanLabel: String) {
    UPBOUND("상행"),
    DOWNBOUND("하행")
}

/**
 * 원터치 제보 버튼 터치 즉시(t=0) FusedLocation SDK로부터 고정되는 고속도로 정밀 이정 모델.
 * 차량이 시속 100km(28m/s)로 계속 이동하더라도 신고 좌표는 사고 목격 지점으로 고정됩니다.
 */
data class HighwayMilepostSnapshot(
    val highwayName: String,
    val direction: HighwayDirection,
    val kilometerMark: Double,
    val latitude: Double,
    val longitude: Double,
    val bearingDeg: Float,
    val capturedAtEpochMs: Long
) {
    val formattedMilepost: String
        get() = "$highwayName ${direction.koreanLabel} ${"%.1f".format(kilometerMark)}km"
}

/**
 * SpeechToText SDK를 통해 핸즈프리로 발화된 3초 음성에서 추출된 차선 및 사고 유형 모델.
 */
data class ParsedVoiceIncident(
    val rawTranscript: String,
    val laneLabel: String,
    val incidentType: String,
    val highSeverity: Boolean
)

/**
 * Vehicle SDK로부터 수집되는 실시간 차량 상태 스냅샷 (신뢰도 교차 검증 및 급감속 능동 제안용).
 */
data class VehicleSensorSnapshot(
    val speedKmh: Int,
    val decelerationMps2: Float,
    val isHazardLampOn: Boolean,
    val ambientTempCelsius: Float,
    val isWiperActive: Boolean,
    val gear: GearState
)

/**
 * Vehicle SDK 센서 데이터 + 다중 차량 동시 제보 교차 검증 결과.
 */
data class CrossValidationResult(
    val confidenceScore: Int,
    val isVerified: Boolean,
    val corroboratingVehicles: Int,
    val reasons: List<String>
)

/**
 * 한국도로공사(ex-로드) 상황실 및 112/119 관제 표준 전송 패킷 + Pleos 후방 차량 전파 페이로드.
 */
data class IncidentReportPacket(
    val snapshot: HighwayMilepostSnapshot,
    val voiceIncident: ParsedVoiceIncident,
    val sensorSnapshot: VehicleSensorSnapshot,
    val validation: CrossValidationResult
)

/**
 * 사고 지점 후방 2~3km 접근 차량(NaviHelper SDK 경로 일치 차량)이 수신하는 2차 사고 예방 경보.
 */
data class RearWarningAlert(
    val highwayName: String,
    val direction: HighwayDirection,
    val incidentKilometerMark: Double,
    val distanceAheadKm: Double,
    val blockedLaneLabel: String,
    val incidentType: String,
    val recommendedAction: String
)

/**
 * 목적지 도착(P단 전환) 시 표시되는 36.5°C 온도 임팩트 요약 및 리워드 모델.
 */
data class WarmthImpactSummary(
    val bodyTemperatureSymbol: Float = 36.5f,
    val protectedRearVehicles: Int,
    val earnedBlueMembersPoints: Int,
    val expresswaySafetyMileageAdded: Boolean
)
