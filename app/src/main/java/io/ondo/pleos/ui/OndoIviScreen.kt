package io.ondo.pleos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ondo.pleos.R

/**
 * Pleos Connect 공식 서체 (Asta Sans Variable Font, SIL OFL 1.1)
 * Figma Foundation > Typography 규격: 최소 가독 크기 확보(14sp~28sp, 실제 픽셀 18px 이상)
 */
@OptIn(ExperimentalTextApi::class)
val AstaSansFamily = FontFamily(
    Font(
        resId = R.font.asta_sans_wght,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        resId = R.font.asta_sans_wght,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        resId = R.font.asta_sans_wght,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        resId = R.font.asta_sans_wght,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
    Font(
        resId = R.font.asta_sans_wght,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))
    )
)

/**
 * Pleos_Connect_design_assets_215.fig & Pleos_Connect_display_guide_215.fig 공식 컬러 토큰
 * - Dark Application Window: #1D1E21 / Card: #252525 / Inset: #131417 / Control: #313236
 * - Light Application Window: #F7F8FA / Card: #FFFFFF / Inset: #EDEEF2 / Control: #E0E1E6
 * - Informative Active: #00E074 (Dark) / #02C265 (Light)
 * - Informative Error: #F62E24
 * - Regulation Blue: #0082FF (Dark) / #0064FF (Light)
 */
private data class PleosColorTokens(
    val windowBg: Color,
    val cardBg: Color,
    val insetBg: Color,
    val controlBg: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val informativeActive: Color,
    val informativeError: Color,
    val regulationBlue: Color,
    val ondoWarm: Color
)

private val PleosDarkTokens = PleosColorTokens(
    windowBg = Color(0xFF1D1E21),
    cardBg = Color(0xFF252525),
    insetBg = Color(0xFF131417),
    controlBg = Color(0xFF313236),
    divider = Color(0x24FFFFFF),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFB4BCC8),
    informativeActive = Color(0xFF00E074),
    informativeError = Color(0xFFF62E24),
    regulationBlue = Color(0xFF0082FF),
    ondoWarm = Color(0xFFFF6B2C)
)

private val PleosLightTokens = PleosColorTokens(
    windowBg = Color(0xFFF7F8FA),
    cardBg = Color(0xFFFFFFFF),
    insetBg = Color(0xFFEDEEF2),
    controlBg = Color(0xFFE0E1E6),
    divider = Color(0xFFDDE1EB),
    textPrimary = Color(0xFF131417),
    textSecondary = Color(0xFF4C5563),
    informativeActive = Color(0xFF02C265),
    informativeError = Color(0xFFF62E24),
    regulationBlue = Color(0xFF0064FF),
    ondoWarm = Color(0xFFE85515)
)

/**
 * Pleos Connect Application Window UI
 * - OS System UI(좌측 Driving View 속도계, 상단 Status Bar, 하단 GNB)와 역할을 명확히 분리하여
 *   Application Window 내부에 중복 속도계를 그리지 않고 ON:DO 고유 안전 연대 정보에 집중.
 * - Window Policy 준수: 1:1 분할 세로 창(Portrait, 내비게이션 동시 실행)과 2:0/2:1 가로 전체 창(Landscape) 모두 자동 대응.
 */
@Composable
fun OndoIviScreen(viewModel: OndoViewModel) {
    val state by viewModel.uiState.collectAsState()
    val systemDark = isSystemInDarkTheme()
    var darkModeOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val isDark = darkModeOverride ?: systemDark
    val colors = if (isDark) PleosDarkTokens else PleosLightTokens

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.windowBg)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // Connect-S / Connect-L 1:1 분할(628dp~840dp 세로 비율) vs 2:0 전체(1274dp~1700dp 가로 비율) 판별
        val isPortraitWindow = maxWidth < 760.dp || maxHeight > maxWidth

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PleosPageHeader(
                liveMilepost = state.liveMilepostLabel,
                isDrivingRestricted = state.drivingRestrictions.isRequiresDistractionOptimization,
                isPortraitWindow = isPortraitWindow,
                isDark = isDark,
                onToggleTheme = { darkModeOverride = !isDark },
                colors = colors
            )

            if (isPortraitWindow) {
                // 1:1 Portrait 분할 윈도우 레이아웃 (내비게이션 앱 우측 동시 실행 기본 모드)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        PrimaryStageCard(
                            state = state,
                            isPortrait = true,
                            colors = colors,
                            onOneTouchReport = { viewModel.onEmergencyOneTouchTriggered() }
                        )
                    }
                    V2xSensorFusionPanel(
                        state = state,
                        compact = true,
                        colors = colors
                    )
                }

                ScenarioControlBar(
                    stage = state.stage,
                    isPortrait = true,
                    colors = colors,
                    onStandby = { viewModel.resetToDrivingStandby() },
                    onReport = { viewModel.onEmergencyOneTouchTriggered() },
                    onRearAlert = { viewModel.simulateRearVehicleWarning() },
                    onArrivalReport = { viewModel.simulateArrivalParkingSummary() }
                )
            } else {
                // 2:0 / 2:1 Landscape 확장 윈도우 레이아웃
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.60f)
                            .fillMaxHeight()
                    ) {
                        PrimaryStageCard(
                            state = state,
                            isPortrait = false,
                            colors = colors,
                            onOneTouchReport = { viewModel.onEmergencyOneTouchTriggered() }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(0.40f)
                            .fillMaxHeight()
                    ) {
                        V2xSensorFusionPanel(
                            state = state,
                            compact = false,
                            colors = colors
                        )
                    }
                }

                ScenarioControlBar(
                    stage = state.stage,
                    isPortrait = false,
                    colors = colors,
                    onStandby = { viewModel.resetToDrivingStandby() },
                    onReport = { viewModel.onEmergencyOneTouchTriggered() },
                    onRearAlert = { viewModel.simulateRearVehicleWarning() },
                    onArrivalReport = { viewModel.simulateArrivalParkingSummary() }
                )
            }
        }
    }
}

@Composable
private fun PleosPageHeader(
    liveMilepost: String,
    isDrivingRestricted: Boolean,
    isPortraitWindow: Boolean,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    colors: PleosColorTokens
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pleos Page Navigation > Title App Icon 규격 (radius 18dp)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.insetBg)
                    .border(1.dp, colors.divider, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .border(3.dp, colors.ondoWarm, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.ondoWarm)
                    )
                }
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ON:DO",
                        fontFamily = AstaSansFamily,
                        color = colors.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "36.5°C",
                        fontFamily = AstaSansFamily,
                        color = colors.ondoWarm,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = liveMilepost,
                    fontFamily = AstaSansFamily,
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = colors.insetBg,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.border(
                    1.dp,
                    if (isDrivingRestricted) colors.informativeActive.copy(alpha = 0.45f) else colors.divider,
                    RoundedCornerShape(14.dp)
                )
            ) {
                Text(
                    text = if (isDrivingRestricted) "주행안전 DO" else "주차 모드",
                    fontFamily = AstaSansFamily,
                    color = if (isDrivingRestricted) colors.informativeActive else colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Surface(
                color = colors.cardBg,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.border(1.dp, colors.divider, RoundedCornerShape(14.dp))
            ) {
                Text(
                    text = if (isPortraitWindow) "1:1 Portrait" else "2:0 Landscape",
                    fontFamily = AstaSansFamily,
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Button(
                onClick = onToggleTheme,
                colors = ButtonDefaults.buttonColors(containerColor = colors.controlBg),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = if (isDark) "야간" else "주간",
                    fontFamily = AstaSansFamily,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PrimaryStageCard(
    state: OndoUiState,
    isPortrait: Boolean,
    colors: PleosColorTokens,
    onOneTouchReport: () -> Unit
) {
    val titleSize = if (isPortrait) 24.sp else 28.sp
    val bodySize = if (isPortrait) 16.sp else 17.sp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.cardBg)
            .border(1.dp, colors.divider, RoundedCornerShape(28.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Pleos 주행 안전 가이드: 문단당 최대 3줄 이내(maxLines = 3), 1~2초 내 직관 인지
        when (val stage = state.stage) {
            is OndoStage.DrivingStandby -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusBadge(
                        label = "전방 고속도로 안전 모니터링",
                        dotColor = colors.informativeActive,
                        colors = colors
                    )
                    Text(
                        text = "도로 위 위험 목격 시 원터치로 즉시 제보하세요",
                        fontFamily = AstaSansFamily,
                        color = colors.textPrimary,
                        fontSize = titleSize,
                        lineHeight = if (isPortrait) 32.sp else 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2
                    )
                    Text(
                        text = "버튼 터치 즉시 현재 이정(km)이 고정되며 3초 핸즈프리 음성 제보로 연결됩니다.",
                        fontFamily = AstaSansFamily,
                        color = colors.textSecondary,
                        fontSize = bodySize,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3
                    )
                }

                // Pleos Buttons > Basic Large 규격 (height 80dp, radius 24dp)
                Button(
                    onClick = onOneTouchReport,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.ondoWarm),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                ) {
                    Text(
                        text = "도로 위 사고 원터치 제보 (t=0 이정 고정)",
                        fontFamily = AstaSansFamily,
                        color = Color.White,
                        fontSize = if (isPortrait) 18.sp else 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            is OndoStage.SnapshotVoiceListening -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusBadge(
                        label = "1단계 · t=0 위치 스냅샷 고정 완료",
                        dotColor = colors.ondoWarm,
                        colors = colors
                    )
                    Text(
                        text = "${stage.snapshot.formattedMilepost} 고정됨",
                        fontFamily = AstaSansFamily,
                        color = colors.textPrimary,
                        fontSize = titleSize,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2
                    )
                    Text(
                        text = "핸즈프리 음성 인식 중입니다. 차로와 상황만 짧게 말씀해 주세요. (예: \"2차로 화물차 전복\")",
                        fontFamily = AstaSansFamily,
                        color = colors.textSecondary,
                        fontSize = bodySize,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3
                    )
                }

                Surface(
                    color = colors.insetBg,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.ondoWarm, RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(colors.ondoWarm)
                        )
                        Text(
                            text = "SpeechToText SDK 수신 중 · 3초 내 자동 종료",
                            fontFamily = AstaSansFamily,
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            is OndoStage.DispatchedAndVerified -> {
                val packet = stage.packet
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusBadge(
                        label = "2단계 · 센서 교차 검증 완료 (${packet.validation.confidenceScore}%)",
                        dotColor = colors.informativeActive,
                        colors = colors
                    )
                    Text(
                        text = "도로공사·119 접수 및 후방 3km 전파 완료",
                        fontFamily = AstaSansFamily,
                        color = colors.textPrimary,
                        fontSize = titleSize,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2
                    )
                    Text(
                        text = "${packet.snapshot.formattedMilepost} · ${packet.voiceIncident.laneLabel} ${packet.voiceIncident.incidentType}",
                        fontFamily = AstaSansFamily,
                        color = colors.informativeActive,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                }

                Surface(
                    color = colors.insetBg,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        packet.validation.reasons.take(3).forEach { reason ->
                            Text(
                                text = "✓ $reason",
                                fontFamily = AstaSansFamily,
                                color = colors.textSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            is OndoStage.RearVehicleWarning -> {
                val alert = stage.alert
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusBadge(
                        label = "2차 사고 예방 긴급 경보 · NaviHelper + TTS",
                        dotColor = colors.informativeError,
                        colors = colors
                    )
                    Text(
                        text = "전방 ${alert.distanceAheadKm}km ${alert.blockedLaneLabel} ${alert.incidentType}",
                        fontFamily = AstaSansFamily,
                        color = colors.textPrimary,
                        fontSize = titleSize,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2
                    )
                    Text(
                        text = alert.recommendedAction,
                        fontFamily = AstaSansFamily,
                        color = colors.ondoWarm,
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3
                    )
                }

                Surface(
                    color = colors.informativeError.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.informativeError, RoundedCornerShape(20.dp))
                ) {
                    Text(
                        text = "주행 경로 일치 확인됨 · 음성 안내(TTS) 자동 송출 중",
                        fontFamily = AstaSansFamily,
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
                    )
                }
            }

            is OndoStage.ArrivalWarmthReport -> {
                val summary = stage.summary
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusBadge(
                        label = "주행 종료(P) · 목적지 주차 정차 · 36.5°C 리포트",
                        dotColor = colors.ondoWarm,
                        colors = colors
                    )
                    Text(
                        text = "당신의 ${summary.bodyTemperatureSymbol}°C 온도가 후방 ${summary.protectedRearVehicles}대를 지켰습니다",
                        fontFamily = AstaSansFamily,
                        color = colors.textPrimary,
                        fontSize = titleSize,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 3
                    )
                    Text(
                        text = "온도 릴레이 반경 3.0 km · 뒤따르던 차량들이 안전하게 차로를 변경하고 감속했습니다.",
                        fontFamily = AstaSansFamily,
                        color = colors.textSecondary,
                        fontSize = bodySize,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2
                    )
                }

                Surface(
                    color = colors.informativeActive.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.informativeActive, RoundedCornerShape(20.dp))
                ) {
                    Text(
                        text = "+${summary.earnedBlueMembersPoints}P 블루멤버스 · 안전 마일리지 적립 완료",
                        fontFamily = AstaSansFamily,
                        color = colors.informativeActive,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    label: String,
    dotColor: Color,
    colors: PleosColorTokens
) {
    Surface(
        color = colors.insetBg,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.border(1.dp, colors.divider, RoundedCornerShape(999.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                fontFamily = AstaSansFamily,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * OS 좌측 Driving View와 중복되는 속도계를 배제하고,
 * ON:DO 고유 V2X 연결 현황 및 Vehicle State SDK 교차검증 시그널만 간결하게 표시
 */
@Composable
private fun V2xSensorFusionPanel(
    state: OndoUiState,
    compact: Boolean,
    colors: PleosColorTokens
) {
    val hazardActive = state.telemetry.isHazardLampOn
    val decelActive = state.telemetry.decelerationMps2 <= -2.0f

    if (compact) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.cardBg)
                .border(1.dp, colors.divider, RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "반경 3km V2X 연결",
                    fontFamily = AstaSansFamily,
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Pleos 차량 ${state.nearbyConnectedCars}대",
                    fontFamily = AstaSansFamily,
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalChip(
                    label = if (hazardActive) "비상등 ON" else "비상등 대기",
                    active = hazardActive,
                    activeColor = colors.informativeError,
                    colors = colors
                )
                SignalChip(
                    label = "외기 ${state.telemetry.ambientTempCelsius}°C",
                    active = state.telemetry.ambientTempCelsius <= 3.0f,
                    activeColor = colors.regulationBlue,
                    colors = colors
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.cardBg)
                .border(1.dp, colors.divider, RoundedCornerShape(28.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "V2X 안전 연대 · 센서 퓨전",
                    fontFamily = AstaSansFamily,
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "반경 3km 연결 ${state.nearbyConnectedCars}대",
                    fontFamily = AstaSansFamily,
                    color = colors.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Vehicle SDK 상태 신호로 허위 제보를 자동 필터링합니다.",
                    fontFamily = AstaSansFamily,
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SensorRowItem(
                    title = "비상경고등 시그널",
                    value = if (hazardActive) "점등 감지됨" else "OFF",
                    highlight = hazardActive,
                    accent = colors.informativeError,
                    colors = colors
                )
                SensorRowItem(
                    title = "종방향 감속도 센서",
                    value = "${state.telemetry.decelerationMps2} m/s²",
                    highlight = decelActive,
                    accent = colors.ondoWarm,
                    colors = colors
                )
                SensorRowItem(
                    title = "노면 결빙 주의 외기온",
                    value = "${state.telemetry.ambientTempCelsius}°C (살얼음 주의)",
                    highlight = state.telemetry.ambientTempCelsius <= 3.0f,
                    accent = colors.regulationBlue,
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun SignalChip(
    label: String,
    active: Boolean,
    activeColor: Color,
    colors: PleosColorTokens
) {
    Surface(
        color = if (active) activeColor.copy(alpha = 0.16f) else colors.insetBg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.border(
            1.dp,
            if (active) activeColor else colors.divider,
            RoundedCornerShape(12.dp)
        )
    ) {
        Text(
            text = label,
            fontFamily = AstaSansFamily,
            color = if (active) activeColor else colors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SensorRowItem(
    title: String,
    value: String,
    highlight: Boolean,
    accent: Color,
    colors: PleosColorTokens
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.insetBg)
            .border(1.dp, if (highlight) accent else colors.divider, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontFamily = AstaSansFamily,
            color = colors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontFamily = AstaSansFamily,
            color = if (highlight) accent else colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ScenarioControlBar(
    stage: OndoStage,
    isPortrait: Boolean,
    colors: PleosColorTokens,
    onStandby: () -> Unit,
    onReport: () -> Unit,
    onRearAlert: () -> Unit,
    onArrivalReport: () -> Unit
) {
    val activeIdx = when (stage) {
        is OndoStage.DrivingStandby -> 0
        is OndoStage.SnapshotVoiceListening, is OndoStage.DispatchedAndVerified -> 1
        is OndoStage.RearVehicleWarning -> 2
        is OndoStage.ArrivalWarmthReport -> 3
    }

    val items = listOf(
        Triple("1. 주행 대기", activeIdx == 0, onStandby),
        Triple("2. 원터치 제보", activeIdx == 1, onReport),
        Triple("3. 후방 경보", activeIdx == 2, onRearAlert),
        Triple("4. P단 리포트", activeIdx == 3, onArrivalReport)
    )

    if (isPortrait) {
        // 1:1 분할 세로 창에서도 버튼 글자가 잘리지 않도록 2x2 Pleos Small Button 그리드 배치
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.subList(0, 2).forEach { (label, selected, action) ->
                    StepButton(
                        label = label,
                        selected = selected,
                        colors = colors,
                        onClick = action,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.subList(2, 4).forEach { (label, selected, action) ->
                    StepButton(
                        label = label,
                        selected = selected,
                        colors = colors,
                        onClick = action,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items.forEach { (label, selected, action) ->
                StepButton(
                    label = label,
                    selected = selected,
                    colors = colors,
                    onClick = action,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StepButton(
    label: String,
    selected: Boolean,
    colors: PleosColorTokens,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pleos Buttons > Basic Small 규격 (height 54~56dp, radius 16dp)
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) colors.ondoWarm else colors.controlBg
        ),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = modifier.height(54.dp)
    ) {
        Text(
            text = label,
            fontFamily = AstaSansFamily,
            color = if (selected) Color.White else colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
