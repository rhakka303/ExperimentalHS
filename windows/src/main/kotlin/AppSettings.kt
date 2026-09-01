import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * #31 - app-wide settings, stored in `HypdroidDesktop/app_settings.json` -
 * separate from #18's per-game `options.json`, since these apply across
 * every game rather than to one. Same `HypdroidDesktop/`-only ownership
 * rule as #18 (per #6's scope decision).
 *
 * Matches the real Android AppSettingsScreen exactly (confirmed against
 * two live screenshots from a real device): Global Cover Art, Background
 * Art, Default Art, and Preserve Video Aspect Ratio.
 *
 * preserveAspectRatioEnabled: this story only needs the toggle to exist
 * and persist since it lives on the same real screen - the actual
 * `-preserve_aspect_ratio` launch-arg behavior (and its real hypseus-singe
 * 3.0.2+ version risk) is #32's job, not this one's.
 */
@Serializable
data class AppSettings(
    val globalCoverArtEnabled: Boolean = false,
    val globalCoverArtType: CoverArtType = CoverArtType.BOX,
    val backgroundArtEnabled: Boolean = false,
    val defaultArtEnabled: Boolean = false,
    val preserveAspectRatioEnabled: Boolean = false,
)

private val json = Json { ignoreUnknownKeys = true }

private fun appSettingsFile(launcherFolder: File) = File(launcherFolder, "app_settings.json")

/**
 * Never throws on a missing or malformed file, same rule as #18's
 * loadAllOptions - an absent or corrupt app_settings.json just means
 * every setting is at its default, not a crash.
 */
fun loadAppSettings(launcherFolder: File): AppSettings {
    val file = appSettingsFile(launcherFolder)
    if (!file.isFile) return AppSettings()
    return try {
        json.decodeFromString(file.readText())
    } catch (e: SerializationException) {
        AppSettings()
    }
}

fun saveAppSettings(launcherFolder: File, settings: AppSettings) {
    appSettingsFile(launcherFolder).writeText(json.encodeToString(settings))
}
