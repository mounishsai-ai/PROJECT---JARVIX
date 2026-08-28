import java.util.Properties
import java.io.FileInputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Safely load local.properties outside of the android block
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
val geminiApiKey = localProperties.getProperty("GEMINI_API_KEY") ?: ""
// Vertex AI (aiplatform.googleapis.com) - separate from the AI Studio key above. This is the
// path the $300 Google Cloud credit actually pays for; the AI Studio key sits on a free tier
// that throttles to 20 requests before returning 429s for minutes at a time. VERTEX_API_KEY is
// a Vertex Express "authorization key" (bound server-side to a service account with
// roles/aiplatform.user), not a plain AI Studio key - plain keys are rejected by this endpoint.
val vertexApiKey = localProperties.getProperty("VERTEX_API_KEY") ?: ""
val vertexProjectId = localProperties.getProperty("VERTEX_PROJECT_ID") ?: ""

android {
    namespace = "com.example.aisecretary"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.aisecretary"
        // Android 8.1. The app genuinely requires it: java.time (API 26),
        // NotificationChannel (26), VibrationEffect (26), and
        // setShowWhenLocked/setTurnScreenOn (27) are all core to how Jarvix works.
        // Declaring 24 previously meant it would crash on launch on Android 7 devices.
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "VERTEX_API_KEY", "\"$vertexApiKey\"")
        buildConfigField("String", "VERTEX_PROJECT_ID", "\"$vertexProjectId\"")
    }

    // Room writes the expected schema here so migrations can be verified against it.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        aidl = false
        buildConfig = true
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Modern Kotlin compilation options (replaces deprecated kotlinOptions)
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Arch Components
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Local tests: jUnit, coroutines, Android runner
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests: jUnit rules and runners
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // Room (using KSP instead of kapt)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Network / API
    // Removed google-genai dependency; using raw REST HTTP call instead
}
