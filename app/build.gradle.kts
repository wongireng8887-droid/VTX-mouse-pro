plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vtx.mousepro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vtx.mousepro"
        minSdk = 35          // Android 15
        targetSdk = 36       // Android 16
        versionCode = 1
        versionName = "0.1.0"

        // hanya 64-bit
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
