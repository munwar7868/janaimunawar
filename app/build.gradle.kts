plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.munawar.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.munawar.ai"
        minSdk = 29
        targetSdk = 34
        versionCode = 12
        versionName = "1.1.1"
    }

    // Optional release signing: only used when the KEYSTORE_* environment variables exist (GitHub secrets).
    // Without them the release APK is signed with the debug key so it is always installable.
    val hasKeystore = !System.getenv("KEYSTORE_FILE").isNullOrBlank()
    // Fallback: a fixed key bundled in the repo (keystore/munawar-ci.jks) so every CI build has the SAME signature
    // and a new APK installs over the old one without uninstalling. For a private key, add the KEYSTORE_* secrets.
    val bundled = rootProject.file("keystore/munawar-ci.jks")
    signingConfigs {
        create("release") {
            if (hasKeystore) {
                storeFile = file(System.getenv("KEYSTORE_FILE"))
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            } else if (bundled.exists()) {
                storeFile = bundled
                storePassword = "munawar123"
                keyAlias = "munawar"
                keyPassword = "munawar123"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasKeystore || bundled.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
