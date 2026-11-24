plugins {
    id("com.android.application") version "9.3.2"
}

android {
    namespace = "com.example.fakeplayer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.fakeplayer"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
