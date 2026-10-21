plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Google Play build: run with -PplayStore=true (marks the app TV-only and signs with your private upload key)
val playStore = (project.findProperty("playStore") as String?) == "true"
val uploadKeystore: String? = System.getenv("UPLOAD_KEYSTORE_FILE")

android {
    namespace = "org.phoenicx.nova"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.phoenicx.nova"
        minSdk = 24
        targetSdk = 34
        versionCode = 17
        versionName = "1.6.6"
        // Google Play: TV-only. Sideload/Amazon: not required (some Fire TV models don't report it).
        manifestPlaceholders["leanbackRequired"] = if (playStore) "true" else "false"
    }

    signingConfigs {
        create("nova") {
            storeFile = rootProject.file("keystore/nova.jks")
            storePassword = "phoenicxnova"
            keyAlias = "nova"
            keyPassword = "phoenicxnova"
        }
        if (uploadKeystore != null) {
            create("upload") {
                storeFile = file(uploadKeystore)
                storePassword = System.getenv("UPLOAD_STORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (playStore && uploadKeystore != null) signingConfigs.getByName("upload")
                            else signingConfigs.getByName("nova")
        }
        debug {
            signingConfig = signingConfigs.getByName("nova")
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
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    val media3 = "1.9.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    // Same FFmpeg audio decoder the official Jellyfin Android TV app uses (DTS, TrueHD, etc.)
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")
}
