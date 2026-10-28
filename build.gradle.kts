plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lowbatt.alarm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lowbatt.alarm"
        minSdk = 28
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }

    val ks = file("keystore.jks")
    if (ks.exists()) {
        signingConfigs {
            create("fixed") {
                storeFile = ks
                storePassword = "android"
                keyAlias = "key"
                keyPassword = "android"
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName(if (ks.exists()) "fixed" else "debug")
        }
        debug {
            if (ks.exists()) signingConfig = signingConfigs.getByName("fixed")
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
