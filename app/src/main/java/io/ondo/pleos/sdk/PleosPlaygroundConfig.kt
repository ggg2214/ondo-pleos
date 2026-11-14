package io.ondo.pleos.sdk

import io.ondo.pleos.BuildConfig

/**
 * Pleos Playground 개발자 콘솔 연동 설정 및 권한 스코프 정의.
 *
 * - 보안 원칙: 실제 콘솔 식별자(Project ID, Client ID, Client Secret, Devbox CRN)는
 *   소스코드에 하드코딩하지 않고 gitignored `local.properties` 또는 환경변수로만 주입합니다.
 * - 에뮬레이터 단독 평가 시에는 기본 로컬 폴백 식별자로 100% 정상 구동됩니다.
 */
data class PleosPlaygroundConfig(
    val projectId: String,
    val clientId: String,
    val clientSecret: String,
    val devboxCrn: String,
    val enabledScopes: Set<String>,
    val isVehicleStateEnabled: Boolean,
    val isNaviHelperEnabled: Boolean
) {
    val isDevboxCrnValid: Boolean
        get() = devboxCrn.startsWith("did:cac:42dot:system:devbox::") && devboxCrn.length > 30

    val hasRequiredSafetyPermissions: Boolean
        get() = isVehicleStateEnabled && isNaviHelperEnabled &&
            enabledScopes.contains(SCOPE_VEHICLE_STATE) &&
            enabledScopes.contains(SCOPE_NAVI_HELPER)

    companion object {
        const val DEFAULT_PROJECT_ID = "pleos-emulator-project"
        const val DEFAULT_CLIENT_ID = "pleos-emulator-client"
        const val DEFAULT_DEVBOX_CRN = "did:cac:42dot:system:devbox::00000000-0000-0000-0000-000000000000"

        const val SCOPE_VEHICLE_STATE = "vehicle.state"
        const val SCOPE_NAVI_HELPER = "navi.helper"

        fun fromBuildConfig(): PleosPlaygroundConfig {
            val scopes = BuildConfig.PLEOS_SDK_SCOPES
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

            return PleosPlaygroundConfig(
                projectId = BuildConfig.PLEOS_PROJECT_ID,
                clientId = BuildConfig.PLEOS_CLIENT_ID,
                clientSecret = BuildConfig.PLEOS_CLIENT_SECRET,
                devboxCrn = BuildConfig.PLEOS_DEVBOX_CRN,
                enabledScopes = scopes,
                isVehicleStateEnabled = BuildConfig.PLEOS_VEHICLE_STATE_ENABLED,
                isNaviHelperEnabled = BuildConfig.PLEOS_NAVI_HELPER_ENABLED
            )
        }
    }
}
