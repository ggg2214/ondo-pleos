package io.ondo.pleos

import io.ondo.pleos.domain.CrossValidationEngine
import io.ondo.pleos.domain.HighwayMilepostResolver
import io.ondo.pleos.model.GearState
import io.ondo.pleos.model.HighwayDirection
import io.ondo.pleos.model.VehicleSensorSnapshot
import io.ondo.pleos.sdk.EmulatorPleosSdkBundle
import io.ondo.pleos.sdk.PleosPlaygroundConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OndoDomainLogicTest {

    @Test
    fun `t=0 GPS 스냅샷이 경부선 하행 342_5km 이정으로 정확히 변환된다`() {
        val snapshot = HighwayMilepostResolver.resolveSnapshot(
            latitude = 36.9921,
            longitude = 127.1478,
            bearingDeg = 168.0f
        )
        assertEquals("경부선", snapshot.highwayName)
        assertEquals(HighwayDirection.DOWNBOUND, snapshot.direction)
        assertEquals(342.5, snapshot.kilometerMark, 0.1)
        assertEquals("경부선 하행 342.5km", snapshot.formattedMilepost)
    }

    @Test
    fun `3초 음성 발화에서 차로와 화물차 전복 유형이 추출된다`() {
        val parsed = HighwayMilepostResolver.parseVoiceTranscript("2차로 화물차 전복, 파편 주의")
        assertEquals("2차로", parsed.laneLabel)
        assertEquals("화물차 전복", parsed.incidentType)
        assertTrue(parsed.highSeverity)
    }

    @Test
    fun `비상등 점등과 급감속 및 동시 제보 결합 시 신뢰도 검증을 통과한다`() {
        val sensor = VehicleSensorSnapshot(
            speedKmh = 78,
            decelerationMps2 = -3.4f,
            isHazardLampOn = true,
            ambientTempCelsius = 2.5f,
            isWiperActive = false,
            gear = GearState.D
        )
        val result = CrossValidationEngine.evaluate(sensor = sensor, corroboratingVehicles = 1)
        assertTrue(result.isVerified)
        assertTrue(result.confidenceScore >= 90)
    }

    @Test
    fun `Pleos Playground 설정 바인딩 및 Devbox CRN 권한 스코프가 유효하다`() {
        val config = PleosPlaygroundConfig.fromBuildConfig()
        assertTrue(config.projectId.isNotBlank())
        assertTrue(config.clientId.isNotBlank())
        assertTrue(config.isDevboxCrnValid)
        assertTrue(config.hasRequiredSafetyPermissions)
    }

    @Test
    fun `TTS 선행 묵음 설정값이 공식 가이드 권장 범위 150ms_200ms 내에 위치한다`() {
        val prerollMs = EmulatorPleosSdkBundle.VOICE_GUIDANCE_PREROLL_SILENCE_MS
        assertTrue("Pre-roll silence($prerollMs ms) must be within 150..200ms", prerollMs in 150L..200L)
    }

    @Test
    fun `Gleo Interface assets_schema_json이 공식 App Schema 작성 규칙을 충족한다`() {
        val candidates = listOf(
            File("src/main/assets/schema.json"),
            File("app/src/main/assets/schema.json")
        )
        val schemaFile = candidates.firstOrNull { it.exists() }
        assertTrue("assets/schema.json 파일이 존재해야 합니다.", schemaFile != null && schemaFile.exists())

        val raw = schemaFile!!.readText(Charsets.UTF_8)
        assertTrue(raw.contains("\"packageName\": \"io.ondo.pleos\""))
        assertTrue(raw.contains("\"version\": \"${BuildConfig.VERSION_NAME}\""))
        assertTrue(raw.contains("\"name\": \"REPORT_HIGHWAY_INCIDENT\""))
        assertTrue(raw.contains("\"name\": \"CHECK_AHEAD_HAZARDS\""))

        // 모든 description은 영문이어야 하므로 한글 음절이 포함되지 않아야 함
        val hasKorean = raw.any { ch -> ch in '\uAC00'..'\uD7A3' }
        assertFalse("schema.json 내부에는 한글이 포함되면 안 됩니다(영문 전용 가이드).", hasKorean)
    }
}
