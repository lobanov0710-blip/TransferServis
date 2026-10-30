import java.net.URI

plugins {
    alias(libs.plugins.android.application)
}

// У Debug свой HTTPS-шлюз. При отсутствии настройки используем
// несуществующий адрес, чтобы тестовый APK не обращался к production.
val transferStagingBaseUrl = providers.gradleProperty("transferStagingBaseUrl")
    .orNull?.trim()?.takeIf { it.isNotEmpty() }
    ?: "https://staging-not-configured.invalid/"
val stagingUri = URI(transferStagingBaseUrl)
val stagingHost = stagingUri.host?.lowercase()
require(
    stagingUri.scheme == "https" &&
    stagingHost != null &&
    stagingHost.contains("staging") &&
    stagingUri.path == "/" &&
    stagingUri.rawQuery == null &&
    stagingUri.rawFragment == null &&
    stagingUri.rawUserInfo == null &&
    stagingHost != "transfer-servis52.ru" &&
    stagingHost != "www.transfer-servis52.ru" &&
    stagingHost != "uber-v3.lobanov0710.workers.dev"
) {
    "Debug API must use a separate HTTPS staging host with a root URL ending in /"
}

// Это только временный токен доступа к staging PHP, НЕ HMAC-секрет Worker.
// Он компилируется в Debug APK, поэтому APK не подлежит распространению.
val transferStagingAccessToken = providers.gradleProperty("transferStagingAccessToken")
    .orNull?.trim() ?: ""

// Opt-in for a disposable, debug-key-signed emulator build against the LIVE API.
// This never changes Debug (staging) or Release; it is absent from normal builds.
// A productionSmoke order is a REAL production order and may notify drivers.
val productionSmokeEnabled = providers.gradleProperty("transferProductionSmoke")
    .orNull?.trim() == "I_ACKNOWLEDGE_PRODUCTION_ORDERS"
require(transferStagingAccessToken.none { it == '"' || it == '\\' || it == '\n' || it == '\r' }) {
    "Invalid staging tester token"
}

android {
    namespace = "ru.transferservis.app"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ru.transferservis.app"

        minSdk = 24
        targetSdk = 37

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    // Generate BuildConfig.DEBUG for debug-only network diagnostics.
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // Отдельный package ID и хранилище: staging APK не заменяет рабочее приложение.
            applicationIdSuffix = ".staging"
            buildConfigField("String", "API_BASE_URL", "\"$transferStagingBaseUrl\"")
            buildConfigField("String", "STAGING_ACCESS_TOKEN", "\"$transferStagingAccessToken\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://transfer-servis52.ru/\"")
            buildConfigField("String", "STAGING_ACCESS_TOKEN", "\"\"")
            optimization {
                enable = true

                packageScope =
                    setOf(
                        "androidx.**",
                        "kotlin.**",
                        "kotlinx.**"
                    )
            }
        }
        // Only materialize this variant on an explicit, human-acknowledged build.
        // It uses the regular debug signing key for emulator installation,
        // NOT the production release keystore and NOT a signed release APK.
        if (productionSmokeEnabled) {
            create("productionSmoke") {
                initWith(getByName("debug"))
                applicationIdSuffix = ".productiontest"
                versionNameSuffix = "-PROD-TEST"
                matchingFallbacks += listOf("debug")
                signingConfig = signingConfigs.getByName("debug")
                buildConfigField("String", "API_BASE_URL", "\\\"https://transfer-servis52.ru/\\\"")
                buildConfigField("String", "STAGING_ACCESS_TOKEN", "\\\"\\\"")
            }
        }
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.activity.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.recyclerview)

    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    implementation(libs.okhttp)

    implementation(libs.gson)

    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)

    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}