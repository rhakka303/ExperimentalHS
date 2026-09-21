/**
 * #126 - the launcher's own version, for the About screen.
 *
 * Read from the `jpackage.app-version` setting, not typed into the code:
 * jpackage writes the build's `packageVersion` (build.gradle.kts) into the
 * packaged app's config as -Djpackage.app-version, so bumping that one line
 * and rebuilding is all a release needs. The setting only exists in the
 * packaged app; run from Gradle there is none, and that is reported as a
 * development build rather than as some made-up number.
 */
const val APP_VERSION_PROPERTY = "jpackage.app-version"

fun appVersionText(version: String? = System.getProperty(APP_VERSION_PROPERTY)): String {
    val trimmed = version?.trim().orEmpty()
    return if (trimmed.isEmpty()) "Development build" else "Version $trimmed"
}
