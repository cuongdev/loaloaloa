plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// The google-services plugin hard-fails if google-services.json is missing. Apply it only when the
// shop has dropped in their Firebase config, so the project builds and runs (relay simply inactive)
// before FCM is set up, and lights up automatically once the file is added. See relay-worker/README.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.loaloaloa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.loaloaloa"
        minSdk = 26
        targetSdk = 35
        // Version is injected by semantic-release in CI (env VERSION_NAME / VERSION_CODE);
        // local builds fall back to a dev version so `./gradlew` works without the env.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Stable debug keystore committed to the repo so every build (local + CI) signs with the
        // SAME key. The published APK is a debug build (see .releaserc.json `assembleDebug`); without
        // a fixed key each CI runner generates a random debug keystore, so Android rejects in-place
        // updates (INSTALL_FAILED_UPDATE_INCOMPATIBLE) and users must uninstall + reinstall, losing
        // their on-device data. A debug keystore is NOT a secret — these are the well-known Android
        // debug credentials (alias androiddebugkey / password "android"), safe to commit.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true // AGP 8 disables BuildConfig by default; LoaLoaLoaApp uses BuildConfig.DEBUG
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")
    sourceSets["androidTest"].kotlin.srcDir("src/androidTest/kotlin")

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.androidx)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    implementation(libs.coroutines.android)
    implementation(libs.timber)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore)
    implementation(libs.kotlinx.serialization.json)

    // QR generation (hub publishes a pairing QR) + camera scanning (spoke joins by scanning it).
    implementation(libs.zxing.android.embedded)

    // FCM: wakes a spoke's process to receive relayed transactions even when dozing/killed.
    // The BoM pins versions; the messaging artifact pulls in FirebaseMessagingService.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.coroutines.play.services)
    // GoogleApiAvailability (FcmTokenProvider) — was transitively via firebase-analytics, now explicit.
    implementation(libs.play.services.base)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.room.runtime)
}
