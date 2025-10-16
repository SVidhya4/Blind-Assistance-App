plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.lisa"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.lisa"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.constraintlayout.v221)
    implementation("androidx.appcompat:appcompat:1.7.0")

    // OkHttp for MJPEG stream fetching
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.1@aar")
    // Vosk APIs
//    implementation ("net.java.dev.jna:jna:5.13.0@aar")
//    implementation ("com.alphacephei:vosk-android:0.3.47@aar")

//    implementation("org.tensorflow:tensorflow-lite:2.16.1")
//    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
//
//    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")
//    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.16.1")
//    implementation("org.tensorflow:tensorflow-lite-api:2.16.1")
//    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
//    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1")

    implementation("org.json:json:20230227")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("ai.picovoice:picovoice-android:3.0.2")
}