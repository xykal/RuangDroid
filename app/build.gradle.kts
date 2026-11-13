plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildPepper: String = System.getenv("HYPERDEX_BUILD_PEPPER")?.takeIf { it.isNotEmpty() }
    ?: "UNOFFICIAL_LOCAL_OR_FORK_BUILD"
val keystorePath: String? = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotEmpty() && file(it).exists() }
val keystorePass: String? = System.getenv("KEYSTORE_PASS")?.takeIf { it.isNotEmpty() }
val keyAliasEnv: String = System.getenv("KEY_ALIAS")?.takeIf { it.isNotEmpty() } ?: "hyperdex_official"

android {
    namespace = "dev.xykal.hyperdex"
    compileSdk = 35
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "dev.xykal.hyperdex"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "4.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DHYPERDEX_BUILD_PEPPER=$buildPepper"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("officialRelease") {
            if (keystorePath != null && keystorePass != null) {
                storeFile = file(keystorePath)
                storeType = "PKCS12"
                storePassword = keystorePass
                keyAlias = keyAliasEnv
                keyPassword = keystorePass
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // R8 Full Obfuscation + Resource Shrinking to harden against APK reverse-engineering
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePath != null && keystorePass != null) {
                signingConfigs.getByName("officialRelease")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
