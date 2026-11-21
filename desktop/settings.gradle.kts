// The desktop app (macOS / Windows) is a separate Gradle build, so the Android TV build at the
// repository root is untouched by it. Build with: ./gradlew -p desktop packageDistributionForCurrentOS
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Some androidx libraries that Compose for desktop needs live only on Google's repository.
        if (System.getenv("IPTV_NO_GOOGLE_MAVEN") == null) google()
    }
}
rootProject.name = "iptv-desktop"
