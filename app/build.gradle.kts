plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

ktlint {
    android.set(true)
}

detekt {
    config.setFrom("$rootDir/config/detekt.yml")
    buildUponDefaultConfig = false
}

android {
    namespace = "com.callagent.gateway"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.callagent.gateway"
        minSdk = 26
        targetSdk = 34
        versionCode = 329
        versionName = "2.8.51"

        // Default SIP peer when SharedPreferences are empty (SignalWire test rig).
        buildConfigField("String", "DEFAULT_SIP_SERVER", "\"loomli-gsm-gateway.dapp.signalwire.com\"")
        buildConfigField("String", "DEFAULT_SIP_USER", "\"gateway\"")
        buildConfigField("int", "DEFAULT_SIP_PORT", "5060")

        // OpenAI Realtime WebSocket-direct transport (alternative to SIP/RTP).
        // When enabled, a bridged GSM call opens wss://api.openai.com/v1/realtime
        // directly, authed by an ephemeral token minted at DEFAULT_REALTIME_TOKEN_URL
        // (the bridge-worker /token endpoint). Empty URL = feature off.
        buildConfigField("boolean", "DEFAULT_REALTIME_ENABLED", "false")
        buildConfigField("String", "DEFAULT_REALTIME_TOKEN_URL", "\"\"")
        buildConfigField("String", "DEFAULT_REALTIME_MODEL", "\"gpt-realtime\"")
        buildConfigField("String", "DEFAULT_REALTIME_VOICE", "\"marin\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Encrypted credentials at rest, backed by Android Keystore.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // WebSocket + HTTP client for the OpenAI Realtime direct transport
    // (ephemeral-token mint + wss://api.openai.com/v1/realtime). JSON via
    // Android's built-in org.json; Base64 via android.util.Base64 — no other deps.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}
