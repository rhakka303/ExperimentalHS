// #7 - desktop project skeleton, kept deliberately minimal.
// #11 - compose.material3 added: needed for the actual milestone UI
// (a plain game list), not speculative. compose.desktop.currentOs alone
// does not pull it in.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
}

repositories {
    // google() is required here: Compose Desktop 1.8.x pulls in AndroidX
    // lifecycle/annotation artifacts published only to Google's Maven repo,
    // not Maven Central.
    google()
    mavenCentral()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    // #17 scope addition (2026-08-31, visible arrow-button paging) - the
    // basic ArrowBack/ArrowForward icons aren't pulled in by material3
    // alone.
    implementation(compose.materialIconsExtended)
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            // #6 - ships as a folder dropped into an existing hypseus
            // install, not an installer. TargetFormat.Exe below is only
            // used indirectly by createDistributable to assemble that
            // folder; no installer is produced or intended.
            targetFormats(TargetFormat.Exe)
            packageName = "HypdroidDesktop"
            packageVersion = "1.0.0"
        }
    }
}
