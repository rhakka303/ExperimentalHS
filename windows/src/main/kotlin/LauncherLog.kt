import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * #94 - a real log file the launcher writes to its own folder
 * (`HypdroidDesktop/log/hypdroiddesktop.log`), for one purpose: giving a
 * user something concrete to hand over when something goes wrong.
 * Success and error events only - deliberately not routine UI activity
 * (no settings-change/screen-navigation noise), per #94's own scope
 * decision. One single running file, appended to across every launch,
 * never rotated or size-capped - simplest version, revisit only if that
 * ever becomes a real problem.
 *
 * Real, live-found naming decision: called `hypdroiddesktop.log`, not
 * the generic `launcher.log` this started as - real, live feedback that
 * a support channel asking for "your logs" risks getting hypseus's own
 * log by mistake if this one's filename isn't distinctive enough to
 * stand on its own, even pasted into chat with no path/folder context
 * around it.
 *
 * A plain top-level function, not tied to any one screen, so every real
 * call site (main()'s own startup/scan, GameCarousel's launch attempts,
 * the gamepad polling thread) can reach it the same way.
 */
private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/**
 * No-op when launcherFolder is null - only meaningful for the packaged
 * app (resolveLauncherFolder()'s own doc comment: null when run via
 * `gradlew run` instead of the built exe), same gate every other
 * launcherFolder-dependent feature in this app already uses.
 */
fun log(launcherFolder: File?, message: String) {
    if (launcherFolder == null) return
    val logDir = File(launcherFolder, "log")
    logDir.mkdirs()
    val timestamp = LocalDateTime.now().format(timestampFormat)
    File(logDir, "hypdroiddesktop.log").appendText("[$timestamp] $message\n")
}

/**
 * A genuinely unexpected exception during startup/scan/launch - not one
 * of the already-handled sealed-result failure cases (ScanResult.
 * NotAHypseusInstall, LaunchResult.HypseusNotFound), which log their own
 * plain message instead. Logs the exception's message and full stack
 * trace so it's captured for a support request instead of vanishing,
 * then rethrows unchanged - this only adds a durable record before
 * whatever already happens next, it doesn't change app behavior.
 */
fun <T> logUnexpectedExceptions(launcherFolder: File?, context: String, block: () -> T): T {
    try {
        return block()
    } catch (e: Exception) {
        log(launcherFolder, "Unexpected error during $context: ${e.stackTraceToString()}")
        throw e
    }
}
