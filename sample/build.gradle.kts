import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Read the sample's InfluTo API key from the gitignored local.properties so a REAL key is
// never committed. Default is a harmless placeholder → a real key can't ship by accident.
// To test: add `influto.apiKey=it_live_yourkey` to sdks/android/local.properties.
val influtoApiKey: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("influto.apiKey") ?: "it_TEST_KEY"

android {
    namespace = "to.influ.sample"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "to.influ.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "INFLUTO_API_KEY", "\"$influtoApiKey\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":sdk"))
    implementation(project(":sdk-ui")) // pre-built Compose ReferralCodeInput
    implementation(project(":sdk-billing")) // opt-in Play Billing auto-capture
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Google Play Billing (latest, May 2026). billing-ktx adds the suspend extensions.
    implementation("com.android.billingclient:billing-ktx:9.0.0")
}
