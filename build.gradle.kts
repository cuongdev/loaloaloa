plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // Applied conditionally in :app only when google-services.json is present (see app/build.gradle.kts).
    alias(libs.plugins.google.services) apply false
    // Gradle Play Publisher — uploads the AAB + store listing to a Play track. Applied in :app.
    // Publish tasks only run when ANDROID_PUBLISHER_CREDENTIALS is set; normal builds are unaffected.
    alias(libs.plugins.play.publisher) apply false
}
