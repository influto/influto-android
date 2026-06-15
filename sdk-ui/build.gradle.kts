// Separate Jetpack Compose UI module so the core `:sdk` keeps no third-party deps beyond
// kotlinx-coroutines (the standard Kotlin async runtime) — Compose stays out of the core.
// Published as a SECOND Maven artifact `to.influ:android-sdk-ui`.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") // version from root (2.2.10) == Kotlin
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "to.influ.sdk.ui"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // vanniktech configures the "release" single-variant publication — do NOT also
    // declare publishing { singleVariant("release") } here.
}

dependencies {
    // `api` so a consumer depending on android-sdk-ui transitively gets the core SDK.
    api(project(":sdk"))

    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // The SDK's network methods are suspend; collected inside Compose effects.
    // -core (not -android): only Dispatchers.IO / launch / delay are used, never Main.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()
    coordinates("to.influ", "android-sdk-ui", "1.0.0")
    pom {
        name.set("InfluTo Android SDK — Compose UI")
        description.set("Pre-built Jetpack Compose referral-code-input UI for the InfluTo Android SDK.")
        url.set("https://influ.to")
        licenses { license { name.set("MIT"); url.set("https://opensource.org/licenses/MIT") } }
        developers { developer { id.set("influto"); name.set("InfluTo"); email.set("support@influ.to") } }
        scm { url.set("https://github.com/influto/influto-android"); connection.set("scm:git:git://github.com/influto/influto-android.git") }
    }
}
