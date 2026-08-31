// #7 - standalone desktop project, sibling to android/. Does not share a
// settings file, a Gradle root, or any dependency with the Android build.
rootProject.name = "HypdroidDesktop"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
