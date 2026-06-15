// Separate Play Billing integration module so the core `:sdk` keeps NO third-party deps
// beyond kotlinx-coroutines. The Google Play Billing library lives ONLY here. Published as
// a third Maven artifact `to.influ:android-sdk-billing`. Mirrors the `:sdk-ui` split.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "to.influ.sdk.billing"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    // vanniktech configures the "release" single-variant publication — do NOT also
    // declare publishing { singleVariant("release") } here.
}

dependencies {
    // `api` so a consumer depending on android-sdk-billing transitively gets the core SDK.
    api(project(":sdk"))

    // Play Billing v9 (matches the sample). `api` — a consumer that uses the self-contained
    // observer needs the BillingClient types on its compile classpath; report(purchase)
    // callers already have it from their own integration.
    api("com.android.billingclient:billing-ktx:9.0.0")

    // The module's callbacks are suspend; -core (not -android): only Dispatchers.IO is used.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.robolectric:robolectric:4.13") // real SharedPreferences on the JVM
    testImplementation("androidx.test:core:1.6.1")
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()
    coordinates("to.influ", "android-sdk-billing", "1.0.0")
    pom {
        name.set("InfluTo Android SDK — Play Billing auto-capture")
        description.set("Opt-in automatic purchase capture + historical back-sync for the InfluTo Android SDK, on Google Play Billing.")
        url.set("https://influ.to")
        licenses { license { name.set("MIT"); url.set("https://opensource.org/licenses/MIT") } }
        developers { developer { id.set("influto"); name.set("InfluTo"); email.set("support@influ.to") } }
        scm { url.set("https://github.com/influto/influto-android"); connection.set("scm:git:git://github.com/influto/influto-android.git") }
    }
}
