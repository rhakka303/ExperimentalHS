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
 * preserveAspectRatioEnabled: originally shown on this same screen; #43
 * moved its UI to a new VideoSettingsScreen, but the field itself stays
 * here unchanged - purely a UI relocation, not a storage change.
 *
 * fullscreenEnabled (#43): used to control the *launcher's own window*
 * (App Full Screen) - #102 removed that feature entirely after three
 * real bugs in the same AWT/Skiko transition code (#43, #45, #101), so
 * this field is no longer read for window-state purposes. Kept, not
 * deleted, matching #87's own "keep the field, drop the behavior"
 * pattern - an existing app_settings.json with this set still loads
 * without error, it just has no effect now.
 *
 * gamepadEnabled (#46): back to a plain launch-arg flag, same category
 * as preserveAspectRatioEnabled - appends -gamepad (confirmed real,
 * doc/CmdLine.md/cmdline.cpp). Lives on the Controls screen, not App
 * Settings, since it's control-input related rather than presentation.
 */
@Serializable
data class AppSettings(
    val globalCoverArtEnabled: Boolean = false,
    val globalCoverArtType: CoverArtType = CoverArtType.BOX,
    val backgroundArtEnabled: Boolean = false,
    val defaultArtEnabled: Boolean = false,
    val preserveAspectRatioEnabled: Boolean = false,
    val fullscreenEnabled: Boolean = false,
    val gamepadEnabled: Boolean = false,
    // Real, live-found gap: -fullscreen (the hypseus launch arg that puts
    // the *game itself* full screen, not this launcher's own window -
    // see fullscreenEnabled above for that) was hardcoded unconditionally
    // in LaunchArgs.kt's buildLaunchArgs() instead of being a real
    // setting. Default true so existing installs see no behavior change
    // now that it's configurable - previously it was always on.
    val gameFullscreenEnabled: Boolean = true,
    // #108 - Game Folder page: read games from a user-chosen folder
    // instead of the install's own singe/vldp/roms. See activeGameFolder()
    // for how the two combine.
    val gameFolderEnabled: Boolean = false,
    val gameFolderPath: String? = null,
)

/**
 * #108 - the folder games are read from, or null for the install's own
 * folders (the default). Enabled with no folder chosen yet also means
 * null, so switching the toggle on never blanks the carousel by itself.
 * A chosen folder that later goes missing deliberately does NOT fall
 * back: a disconnected drive shows up as an empty list, instead of
 * silently swapping in a different set of games.
 */
fun AppSettings.activeGameFolder(): File? =
    if (gameFolderEnabled) gameFolderPath?.takeIf { it.isNotBlank() }?.let { File(it) } else null

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
