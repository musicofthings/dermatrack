import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.dermatrack.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dermatrack.ai"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "AUTODERM_API_KEY",
            "\"${localProperties.getProperty("AUTODERM_API_KEY", "").replace("\"", "\\\"")}\"",
        )
        buildConfigField(
            "String",
            "AUTODERM_API_BASE_URL",
            "\"${localProperties.getProperty("AUTODERM_API_BASE_URL", "https://api.autoderm.ai").replace("\"", "\\\"")}\"",
        )
        buildConfigField(
            "String",
            "AUTODERM_API_PATH",
            "\"${localProperties.getProperty("AUTODERM_API_PATH", "/v1/infer-diseases/v1").replace("\"", "\\\"")}\"",
        )
        buildConfigField(
            "String",
            "AMAZON_BACKEND_BASE_URL",
            "\"${localProperties.getProperty("AMAZON_BACKEND_BASE_URL", "http://10.0.2.2:8000").replace("\"", "\\\"")}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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

    androidResources {
        noCompress += listOf("tflite", "task")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)

    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.mlkit.face.detection)
    implementation(libs.okhttp)
    implementation(libs.google.play.services.auth)
}
