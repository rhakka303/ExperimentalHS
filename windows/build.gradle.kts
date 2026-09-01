// #7 - desktop project skeleton, kept deliberately minimal.
// #11 - compose.material3 added: needed for the actual milestone UI
// (a plain game list), not speculative. compose.desktop.currentOs alone
// does not pull it in.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    // #18 - per-game options persistence needs real JSON, not hand-rolled
    // parsing. kotlinx.serialization is the idiomatic Kotlin choice and
    // needs its own compiler plugin.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21"
}

repositories {
    // google() is required here: Compose Desktop 1.8.x pulls in AndroidX
    // lifecycle/annotation artifacts published only to Google's Maven repo,
    // not Maven Central.
    google()
    mavenCentral()
}

// #68 - LWJGL/GLFW, the real library candidate for gamepad input:
// Compose Desktop/AWT has no native gamepad API at all. Windows-only
// natives classifier since that's this project's only real target -
// no macOS/Linux natives pulled in. BOM keeps every LWJGL module on one
// consistent version without repeating it per artifact.
val lwjglVersion = "3.3.3"
val lwjglNatives = "natives-windows"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    // #17 scope addition (2026-08-31, visible arrow-button paging) - the
    // basic ArrowBack/ArrowForward icons aren't pulled in by material3
    // alone.
    implementation(compose.materialIconsExtended)
    // #18 - per-game options JSON.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // #68 - core LWJGL + the GLFW module specifically (real joystick/
    // gamepad polling lives there), plus the actual native .dll each
    // needs at runtime - the jar alone is just JNI bindings with nothing
    // to bind to.
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    runtimeOnly("org.lwjgl", "lwjgl", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-glfw", classifier = lwjglNatives)

    // #27 - unit tests for pure logic (art resolution, and anything
    // similarly pure going forward). Nothing before this story needed
    // it: prior logic (GameScanner, LaunchArgs, Launcher) touches real
    // hypseus.exe/real installs and was verified against those directly
    // instead.
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
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

            // #68 - createDistributable builds a trimmed custom JVM via
            // jlink, containing only the modules it auto-detects as
            // needed. LWJGL's native memory layer needs sun.misc.Unsafe
            // (jdk.unsupported) - confirmed as a real NoClassDefFoundError
            // on the first real prototype run, since nothing else in this
            // app uses that module for jlink to have detected it.
            modules("jdk.unsupported")
        }
    }
}
