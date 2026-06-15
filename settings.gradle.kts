pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "influto-android"
include(":sdk")
include(":sdk-ui")
include(":sdk-billing")
include(":sample")
