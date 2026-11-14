import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun resolvePleosProp(key: String, default: String = ""): String {
    return localProps.getProperty(key)
        ?: System.getenv(key)
        ?: (project.findProperty(key) as? String)
        ?: default
}

android {
    namespace = "io.ondo.pleos"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.ondo.pleos"
        // Pleos Connect 공식 개발 환경 요구사항: Android API 28 이상
        minSdk = 28
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val pleosProjectId = resolvePleosProp("PLEOS_PROJECT_ID", "pleos-emulator-project")
        val pleosClientId = resolvePleosProp("PLEOS_CLIENT_ID", "pleos-emulator-client")
        val pleosClientSecret = resolvePleosProp("PLEOS_CLIENT_SECRET", "")
        val pleosDevboxCrn = resolvePleosProp(
            "PLEOS_DEVBOX_CRN",
            "did:cac:42dot:system:devbox::00000000-0000-0000-0000-000000000000"
        )
        val pleosSdkScopes = resolvePleosProp("PLEOS_SDK_SCOPES", "vehicle.state,navi.helper")
        val pleosRelayBaseUrl = resolvePleosProp("PLEOS_RELAY_BASE_URL", "http://10.0.2.2:8080")

        buildConfigField("String", "PLEOS_PROJECT_ID", "\"$pleosProjectId\"")
        buildConfigField("String", "PLEOS_CLIENT_ID", "\"$pleosClientId\"")
        buildConfigField("String", "PLEOS_CLIENT_SECRET", "\"$pleosClientSecret\"")
        buildConfigField("String", "PLEOS_DEVBOX_CRN", "\"$pleosDevboxCrn\"")
        buildConfigField("String", "PLEOS_SDK_SCOPES", "\"$pleosSdkScopes\"")
        buildConfigField("String", "PLEOS_RELAY_BASE_URL", "\"$pleosRelayBaseUrl\"")
        buildConfigField("boolean", "PLEOS_VEHICLE_STATE_ENABLED", "true")
        buildConfigField("boolean", "PLEOS_NAVI_HELPER_ENABLED", "true")

        manifestPlaceholders["pleosProjectId"] = pleosProjectId
        manifestPlaceholders["pleosClientId"] = pleosClientId
        manifestPlaceholders["pleosDevboxCrn"] = pleosDevboxCrn
    }

    signingConfigs {
        val releaseStorePath = resolvePleosProp("RELEASE_STORE_FILE", "")
        if (releaseStorePath.isNotBlank() && file(releaseStorePath).exists()) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = resolvePleosProp("RELEASE_STORE_PASSWORD", "")
                keyAlias = resolvePleosProp("RELEASE_KEY_ALIAS", "")
                keyPassword = resolvePleosProp("RELEASE_KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Pleos Playground 공식 Nexus Maven 5종 SDK 연동
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
