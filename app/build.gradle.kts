import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreB64: String? = System.getenv("KEYSTORE_BASE64")

android {
    namespace = "com.zcc09.crosshaircompanion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zcc09.crosshaircompanion"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (!keystoreB64.isNullOrBlank()) {
            create("release") {
                val ksFile = File(project.layout.buildDirectory.get().asFile, "ci-signing/release.p12")
                ksFile.parentFile?.mkdirs()
                ksFile.writeBytes(Base64.getMimeDecoder().decode(keystoreB64.trim()))
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Stable release signing when CI secrets are present, debug signing otherwise.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.android.material:material:1.12.0")
}
