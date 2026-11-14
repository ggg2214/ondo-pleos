package io.ondo.pleos.domain

import io.ondo.pleos.model.HighwayDirection
import io.ondo.pleos.model.HighwayMilepostSnapshot
import io.ondo.pleos.model.ParsedVoiceIncident
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * FusedLocation SDK 위경도·방위각(Bearing)을 고속도로 노선 기점(km 이정)으로 변환하고,
 * SpeechToText SDK 음성 문장에서 차선 및 사고 유형 키워드를 추출하는 도메인 리졸버.
 */
object HighwayMilepostResolver {

    private data class HighwayAnchor(
        val name: String,
        val baseKm: Double,
        val lat: Double,
        val lon: Double
    )

    // 주요 고속도로 기준 기점 샘플 테이블 (실제 상용화 시 한국도로공사 노선 이정 링크 DB 연동)
    private val anchors = listOf(
        HighwayAnchor("경부선", baseKm = 342.5, lat = 36.9921, lon = 127.1478),
        HighwayAnchor("영동선", baseKm = 118.2, lat = 37.2895, lon = 127.6341),
        HighwayAnchor("서해안선", baseKm = 275.0, lat = 36.8910, lon = 126.8015)
    )

    fun resolveSnapshot(
        latitude: Double,
        longitude: Double,
        bearingDeg: Float,
        timestampMs: Long = System.currentTimeMillis()
    ): HighwayMilepostSnapshot {
        val nearest = anchors.minByOrNull { haversineKm(latitude, longitude, it.lat, it.lon) }
            ?: anchors.first()

        val deltaKm = haversineKm(latitude, longitude, nearest.lat, nearest.lon)
        // 남동/남서향(90°~270°)은 하행, 북향 계열은 상행으로 판별
        val direction = if (bearingDeg in 90f..270f) {
            HighwayDirection.DOWNBOUND
        } else {
            HighwayDirection.UPBOUND
        }

        val signedOffset = if (latitude <= nearest.lat) deltaKm else -deltaKm
        val resolvedKm = ((nearest.baseKm + signedOffset) * 10.0).roundToInt() / 10.0

        return HighwayMilepostSnapshot(
            highwayName = nearest.name,
            direction = direction,
            kilometerMark = resolvedKm.coerceAtLeast(0.0),
            latitude = latitude,
            longitude = longitude,
            bearingDeg = bearingDeg,
            capturedAtEpochMs = timestampMs
        )
    }

    /**
     * 운전자의 3초 핸즈프리 STT 발화(예: "2차로 화물차 전복, 파편 주의")에서 차선과 사고 유형을 즉시 파싱.
     */
    fun parseVoiceTranscript(transcript: String): ParsedVoiceIncident {
        val normalized = transcript.trim()

        val laneRegex = Regex("([1-5]|일|이|삼|사|오)\\s*차로|갓길")
        val laneMatch = laneRegex.find(normalized)?.value
        val laneLabel = when {
            laneMatch == null -> "차로 확인 중"
            laneMatch.contains("갓길") -> "갓길"
            laneMatch.startsWith("일") -> "1차로"
            laneMatch.startsWith("이") -> "2차로"
            laneMatch.startsWith("삼") -> "3차로"
            laneMatch.startsWith("사") -> "4차로"
            laneMatch.startsWith("오") -> "5차로"
            else -> laneMatch.replace(" ", "")
        }

        val incidentType = when {
            normalized.contains("전복") && normalized.contains("화물") -> "화물차 전복"
            normalized.contains("전복") -> "차량 전복"
            normalized.contains("화재") || normalized.contains("불") -> "차량 화재"
            normalized.contains("추돌") || normalized.contains("충돌") -> "추돌 사고"
            normalized.contains("낙하물") || normalized.contains("파편") -> "도로 낙하물"
            normalized.contains("역주행") -> "역주행 차량"
            normalized.contains("정지") || normalized.contains("고장") -> "정지·고장 차량"
            else -> "전방 돌발 사고"
        }

        val highSeverity = listOf("전복", "화재", "역주행", "추돌", "사람").any { normalized.contains(it) }

        return ParsedVoiceIncident(
            rawTranscript = normalized,
            laneLabel = laneLabel,
            incidentType = incidentType,
            highSeverity = highSeverity
        )
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
