import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val configuredBaseUrl = (
    System.getenv("VAIGO_BASE_URL")?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("VAIGO_BASE_URL").orNull?.takeIf { it.isNotBlank() }
        ?: "https://vaigo.online"
).trimEnd('/')

android {
    namespace = "online.vaigo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "online.vaigo.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "VAIGO_BASE_URL", "\"$configuredBaseUrl\"")
        buildConfigField("String", "MOBILE_RETURN_URI", "\"vaigo://auth/callback\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}
