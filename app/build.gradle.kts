plugins {
    alias(libs.plugins.android.application)
}

// Local-only staging settings. Missing configuration must NEVER fall back to production.
val transferStagingBaseUrl = providers.gradleProperty("transferStagingBaseUrl")
    .orNull?.trim()?.takeIf { it.isNotEmpty() }
    ?: "https://staging-not-configured.invalid/"
require(transferStagingBaseUrl.startsWith("https://") &&
    transferStagingBaseUrl.endsWith("/") &&
    transferStagingBaseUrl != "https://transfer-servis52.ru/" &&
    !transferStagingBaseUrl.contains('"') &&
    !transferStagingBaseUrl.contains('\\')) {
    "Debug API URL must be a separate HTTPS staging gateway, ending with /"
}
val transferStagingAccessToken = providers.gradleProperty("transferStagingAccessToken")
    .orNull?.trim() ?: ""
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