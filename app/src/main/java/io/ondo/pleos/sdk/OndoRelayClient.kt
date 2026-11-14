package io.ondo.pleos.sdk

import io.ondo.pleos.BuildConfig
import io.ondo.pleos.model.IncidentReportPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 검증 완료된 사고 제보 패킷을 초저지연 Rust V2X 중계 서버(`relay-server-rs`)로 비동기 전송하는 클라이언트.
 *
 * - 기본 엔드포인트: `BuildConfig.PLEOS_RELAY_BASE_URL` (에뮬레이터 호스트 루프백 `http://10.0.2.2:8080`)
 * - 로컬 서버 미구동 시에도 앱 UX가 절대 블로킹되지 않도록 짧은 타임아웃과 안전한 폴백을 보장합니다.
 */
object OndoRelayClient {

    suspend fun dispatchVerifiedIncident(
        packet: IncidentReportPacket,
        baseUrl: String = BuildConfig.PLEOS_RELAY_BASE_URL
    ): Boolean = withContext(Dispatchers.IO) {
        val trimmedBase = baseUrl.trim().trimEnd('/')
        if (trimmedBase.isEmpty()) return@withContext false

        var connection: HttpURLConnection? = null
        try {
            val payload = JSONObject().apply {
                put("highway_name", packet.snapshot.highwayName)
                put("direction", packet.snapshot.direction.name)
                put("kilometer_mark", packet.snapshot.kilometerMark)
                put("latitude", packet.snapshot.latitude)
                put("longitude", packet.snapshot.longitude)
                put("bearing_deg", packet.snapshot.bearingDeg.toDouble())
                put("lane_label", packet.voiceIncident.laneLabel)
                put("incident_type", packet.voiceIncident.incidentType)
                put("raw_transcript", packet.voiceIncident.rawTranscript)
                put(
                    "sensor",
                    JSONObject().apply {
                        put("speed_kmh", packet.sensorSnapshot.speedKmh)
                        put("deceleration_mps2", packet.sensorSnapshot.decelerationMps2.toDouble())
                        put("hazard_lamp_on", packet.sensorSnapshot.isHazardLampOn)
                        put("ambient_temp_c", packet.sensorSnapshot.ambientTempCelsius.toDouble())
                        put("wiper_active", packet.sensorSnapshot.isWiperActive)
                    }
                )
            }.toString()

            val url = URL("$trimmedBase/api/v1/incidents")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 1200
                readTimeout = 1200
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            connection.outputStream.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            code in 200..299
        } catch (_: Throwable) {
            false
        } finally {
            connection?.disconnect()
        }
    }
}
