plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val configuredBaseUrl = (
    System.getenv("VIENNA_BASE_URL")?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("VIENNA_BASE_URL").orNull?.takeIf { it.isNotBlank() }
        // O host pode continuar sendo o backend atual enquanto o domínio público
        // migra; a marca e o identificador do APK são VIENNA.
        ?: "https://vaigo.online"
).trimEnd('/')

val configuredReturnUri = (
    System.getenv("VIENNA_MOBILE_RETURN_URI")?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("VIENNA_MOBILE_RETURN_URI").orNull?.takeIf { it.isNotBlank() }
        ?: "vienna://auth/callback"
)

require(configuredBaseUrl.startsWith("https://")) {
    "VIENNA_BASE_URL precisa usar HTTPS em builds de produção."
}

android {
    namespace = "app.vienna.navigation"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.vienna.navigation"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.0.0"

        buildConfigField("String", "VIENNA_BASE_URL", "\"$configuredBaseUrl\"")
        buildConfigField("String", "MOBILE_RETURN_URI", "\"$configuredReturnUri\"")
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


dependencies {
    implementation("com.google.android.gms:play-services-ads:25.4.0")
}
