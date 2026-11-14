package io.ondo.pleos

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.ondo.pleos.sdk.EmulatorPleosSdkBundle
import io.ondo.pleos.ui.OndoIviScreen
import io.ondo.pleos.ui.OndoViewModel
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    companion object {
        const val GLEO_ACTION_REPORT_INCIDENT = "REPORT_HIGHWAY_INCIDENT"
        const val GLEO_ACTION_CHECK_HAZARDS = "CHECK_AHEAD_HAZARDS"
    }

    private lateinit var viewModel: OndoViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sdkBundle = EmulatorPleosSdkBundle(applicationContext)
        viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OndoViewModel(sdkBundle) as T
                }
            }
        )[OndoViewModel::class.java]

        handleGleoIntent(intent)

        setContent {
            OndoIviScreen(viewModel = viewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        // Suspend 복귀 시 플래그만 해제하고 오디오를 자동 재개하지 않음 (Suspend Mode Audio Focus Guide 준수)
        viewModel.onForegroundResumed()
    }

    override fun onStop() {
        // 차량 시동 OFF(Suspend) 또는 백그라운드 전환 시 즉시 음성 출력 중단 및 오디오 포커스 반환
        viewModel.onSuspendEntering()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleGleoIntent(intent)
    }

    /**
     * 공식 Gleo Interface (`assets/schema.json`) Action 호출 및 보조 URI 딥링크 처리:
     * 1) `REPORT_HIGHWAY_INCIDENT`: `lane_number`, `incident_summary` 파라미터 파싱 후 즉시 t=0 제보 실행 + Callback 응답 설정
     * 2) `CHECK_AHEAD_HAZARDS`: `lookahead_km` 기준 전방 위험 구간 경보 브리핑 표시 + Callback 응답 설정
     * 3) `ondo://emergency-report`: 개발자 ADB 딥링크 호환 유지
     */
    private fun handleGleoIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            GLEO_ACTION_REPORT_INCIDENT -> {
                val prefilled = extractGleoIncidentTranscript(intent)
                viewModel.onEmergencyOneTouchTriggered(prefilledTranscript = prefilled)
                deliverGleoCallback(
                    sourceIntent = intent,
                    functionName = GLEO_ACTION_REPORT_INCIDENT,
                    status = "SUCCESS",
                    message = "Captured highway kilometer milepost and dispatched incident report."
                )
                return
            }

            GLEO_ACTION_CHECK_HAZARDS -> {
                val lookaheadKm = extractGleoLookaheadKm(intent)
                viewModel.simulateRearVehicleWarning(lookaheadKm = lookaheadKm)
                deliverGleoCallback(
                    sourceIntent = intent,
                    functionName = GLEO_ACTION_CHECK_HAZARDS,
                    status = "SUCCESS",
                    message = "Displayed active secondary-collision hazard warning ahead on route."
                )
                return
            }
        }

        val data = intent.data ?: return
        if (data.scheme == "ondo" && data.host == "emergency-report") {
            viewModel.onEmergencyOneTouchTriggered()
        }
    }

    private fun extractGleoIncidentTranscript(intent: Intent): String? {
        var lane = intent.getStringExtra("lane_number")?.trim().orEmpty()
        var summary = intent.getStringExtra("incident_summary")?.trim().orEmpty()

        val rawParamsJson = intent.getStringExtra("parameters") ?: intent.getStringExtra("arguments")
        if (!rawParamsJson.isNullOrBlank()) {
            try {
                val json = JSONObject(rawParamsJson)
                if (lane.isEmpty()) lane = json.optString("lane_number", "").trim()
                if (summary.isEmpty()) summary = json.optString("incident_summary", "").trim()
            } catch (_: Throwable) {
            }
        }

        if (lane.isEmpty() && summary.isEmpty()) return null

        val laneLabel = when (lane.lowercase()) {
            "1", "2", "3", "4" -> "${lane}차로"
            "shoulder" -> "갓길"
            else -> ""
        }
        return listOf(laneLabel, summary).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
    }

    private fun extractGleoLookaheadKm(intent: Intent): Double? {
        if (intent.hasExtra("lookahead_km")) {
            val dbl = intent.getDoubleExtra("lookahead_km", -1.0)
            if (dbl > 0.0) return dbl
            val str = intent.getStringExtra("lookahead_km")?.toDoubleOrNull()
            if (str != null && str > 0.0) return str
        }
        val rawParamsJson = intent.getStringExtra("parameters") ?: intent.getStringExtra("arguments")
        if (!rawParamsJson.isNullOrBlank()) {
            try {
                val json = JSONObject(rawParamsJson)
                val v = json.optDouble("lookahead_km", -1.0)
                if (v > 0.0) return v
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun deliverGleoCallback(
        sourceIntent: Intent,
        functionName: String,
        status: String,
        message: String
    ) {
        val callbackJson = JSONObject().apply {
            put("name", functionName)
            put("status", status)
            put("message", message)
        }.toString()

        val resultIntent = Intent().apply {
            putExtra("name", functionName)
            putExtra("status", status)
            putExtra("result", callbackJson)
        }
        setResult(Activity.RESULT_OK, resultIntent)

        val callbackAction = sourceIntent.getStringExtra("callbackAction")
            ?: sourceIntent.getStringExtra("callback_action")
        if (!callbackAction.isNullOrBlank()) {
            val broadcast = Intent(callbackAction).apply {
                sourceIntent.getStringExtra("callbackPackage")?.let { setPackage(it) }
                putExtra("name", functionName)
                putExtra("status", status)
                putExtra("result", callbackJson)
            }
            sendBroadcast(broadcast)
        }
    }
}
