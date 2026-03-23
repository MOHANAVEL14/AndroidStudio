plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)

    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.sossmsapp"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        applicationId = "com.example.sossmsapp"
        minSdk = 26
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("androidx.media:media:1.7.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    dependencies {
        val camerax_version = "1.3.0"
        implementation("androidx.camera:camera-core:${camerax_version}")
        implementation("androidx.camera:camera-camera2:${camerax_version}")
        implementation("androidx.camera:camera-lifecycle:${camerax_version}")
        implementation("androidx.lifecycle:lifecycle-service:2.6.2")
        implementation("com.google.code.gson:gson:2.10.1")
        implementation("com.google.android.gms:play-services-maps:18.2.0")
        implementation("com.squareup.retrofit2:retrofit:2.9.0")
        implementation("com.squareup.retrofit2:converter-gson:2.9.0")
        // Import the Firebase BoM (Bill of Materials)
        // This allows you to omit versions for specific Firebase libraries
        implementation(platform("com.google.firebase:firebase-bom:33.1.0"))

        // Add the dependency for Firebase Authentication (Phone OTP)
        implementation("com.google.firebase:firebase-auth-ktx")

        // Add the dependency for Cloud Firestore (Logging alerts)
        implementation("com.google.firebase:firebase-firestore-ktx")
    }
}