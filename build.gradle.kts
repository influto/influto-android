// Root build file. Plugin versions declared here, applied in the modules.
plugins {
    id("com.android.library") version "8.7.0" apply false
    id("com.android.application") version "8.7.0" apply false
    // Kotlin 2.2.x: required to read Play Billing 9.0.0's newer Kotlin metadata
    // (billing-ktx pulls kotlin-stdlib 2.2.10; the 2.0 compiler can't read 2.2/2.3 metadata).
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.vanniktech.maven.publish") version "0.30.0" apply false
}
