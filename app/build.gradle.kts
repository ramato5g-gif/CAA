plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.gazecaa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.gazecaa"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "0.6"
    }

    // Keystore fisso nel repo: ogni APK compilato ha la stessa firma,
    // quindi gli aggiornamenti si installano sopra senza disinstallare.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures { compose = true }

    // Il modello MediaPipe non va compresso dentro l'APK
    androidResources { noCompress += "task" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // --- CameraX ---
    val cameraxVersion = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")

    // --- MediaPipe Face Landmarker (478 punti, iridi incluse) ---
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // --- Jetpack Compose ---
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")

    // --- AndroidX base ---
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
