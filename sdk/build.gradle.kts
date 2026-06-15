plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "to.influ.sdk"
    compileSdk = 35
    // Pin build-tools to an INSTALLED version. AGP 8.7 defaults to 34.0.0, which isn't
    // present on every machine (e.g. the dev WSL2 SDK has 35.0.0 + 36.0.0 only) → the
    // build fails with "Failed to find Build Tools revision 34.0.0" without this pin.
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
    // NOTE: the vanniktech maven-publish plugin configures the "release" single-variant
    // publication itself (sources + javadoc), so we must NOT also declare
    // `publishing { singleVariant("release") }` here — the double-declaration fails
    // configuration ("Using singleVariant publishing DSL multiple times ... is not allowed").
}

dependencies {
    // kotlinx-coroutines is a real runtime dependency (CoroutineScope / Dispatchers.IO /
    // withContext / scope.launch are used unconditionally). Use -core, not -android: the
    // SDK only touches Dispatchers.IO, never Dispatchers.Main — and a library should depend
    // on -core (the consumer app pulls -android, which Gradle resolves to at runtime).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    // RevenueCat is OPTIONAL and accessed via reflection — compileOnly so it is NEVER
    // shipped to or required by consumers.
    compileOnly("com.revenuecat.purchases:purchases:8.+")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // JVM unit tests don't get Android's bundled org.json — provide a real one so the
    // JsonCodec mapping tests run on the JVM.
    testImplementation("org.json:json:20240303")
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()
    coordinates("to.influ", "android-sdk", "1.0.0")
    pom {
        name.set("InfluTo Android SDK")
        description.set("Influencer attribution + store-direct purchase validation for Android.")
        url.set("https://influ.to")
        licenses { license { name.set("MIT"); url.set("https://opensource.org/licenses/MIT") } }
        developers { developer { id.set("influto"); name.set("InfluTo"); email.set("support@influ.to") } }
        scm { url.set("https://github.com/influto/influto-android"); connection.set("scm:git:git://github.com/influto/influto-android.git") }
    }
}
