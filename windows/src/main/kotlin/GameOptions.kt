import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * #18 - per-game options, stored in `HypdroidDesktop/options.json` (the
 * launcher's own folder, per #6's ownership rule - resolved via
 * resolveLauncherFolder(), not the hypseus install root).
 *
 * `arguments`: custom launch arguments per game. Android's GameOptions.kt
 * (`android/app/src/main/java/org/libsdl/app/`) is the specification for
 * the shape and the storage semantics, not something copied from - each
 * entry can itself be a multi-token string (e.g. "-scalefactor 50"),
 * split on whitespace at launch time (MainActivity.kt ~line 396), which
 * is why launchGame() (#10) does the same split rather than appending
 * each entry as a single verbatim argv token.
 *
 * #19 scope addition, 2026-08-31: bezelEnabled/scorebezelAutofit/
 * overlayBezel/aspectBezelFix. Confirmed against Android's real source
 * (MainActivity.kt ~line 381) that these are all just launch-argument
 * flags under the hood, the same category as `arguments` above, not a
 * separate system - scorebezelAutofit -> "-scorebezel_autofit",
 * overlayBezel -> "-overlaybezel", aspectBezelFix -> "-aspectbezelfix",
 * unconditionally when true. bezelEnabled is the one exception: it
 * resolves an actual per-game bezel file (see bezelLaunchArgs below)
 * rather than being a bare flag, and is a no-op if that file doesn't
 * exist - confirmed true right now for all three real test games in
 * smoke/ (their bezels/ folder has no matching PNGs), same situation
 * Android's own code already handles gracefully.
 *
 * #30 - coverArtOverride: this game's own CoverArtType, or null to use
 * the app default (BOX) - see resolveCoverArtFile() in CoverArt.kt (#27).
 * Unlike bezelEnabled/scorebezelAutofit/etc, this isn't a launch-arg flag
 * at all - it only affects what #28's carousel card renders.
 */
@Serializable
data class GameOptions(
    val arguments: List<String> = emptyList(),
    val bezelEnabled: Boolean = false,
    val scorebezelAutofit: Boolean = false,
    val overlayBezel: Boolean = false,
    val aspectBezelFix: Boolean = false,
    val coverArtOverride: CoverArtType? = null,
)

/**
 * #19 - a per-game bezel PNG at <installRoot>/bezels/<gameName>.png, or
 * no-op if it doesn't exist. Per hypseus's own doc/CmdLine.md: "-bezel
 * <bezel.png> - Specify a png bezel in 'bezels' sub-folder" - the bezels
 * subfolder is the implicit default, so -bezel alone is sufficient here.
 * -bezeldir (confirmed real in io/cmdline.cpp, just undocumented) only
 * matters for pointing at a non-default folder, which doesn't apply -
 * the file already sits at the default location. Android's own
 * bezelLaunchArgs passes -bezeldir unconditionally too, but always at
 * the same default location -bezel alone already resolves to, so it's
 * redundant there as well, not something this needed to copy.
 */
fun bezelLaunchArgs(installRoot: File, gameName: String): List<String> {
    val bezelFile = File(File(installRoot, "bezels"), "$gameName.png")
    if (!bezelFile.isFile) return emptyList()
    return listOf("-bezel", "$gameName.png")
}

private val json = Json { ignoreUnknownKeys = true }

private fun optionsFile(launcherFolder: File) = File(launcherFolder, "options.json")

/**
 * Never throws on a missing or malformed file - an absent or corrupt
 * options.json means every game simply has no saved options yet, not a
 * crash. Matches #18's own acceptance criteria.
 */
fun loadAllOptions(launcherFolder: File): Map<String, GameOptions> {
    val file = optionsFile(launcherFolder)
    if (!file.isFile) return emptyMap()
    return try {
        json.decodeFromString(file.readText())
    } catch (e: SerializationException) {
        emptyMap()
    }
}

fun loadOptions(launcherFolder: File, gameName: String): GameOptions =
    loadAllOptions(launcherFolder)[gameName] ?: GameOptions()

fun saveOptions(launcherFolder: File, gameName: String, options: GameOptions) {
    val all: Map<String, GameOptions> = loadAllOptions(launcherFolder) + (gameName to options)
    optionsFile(launcherFolder).writeText(json.encodeToString(all))
}

/**
 * #19 - the full extra-argument list a game's saved GameOptions produces,
 * in the same order Android's MainActivity.kt builds it: bezel, then the
 * three plain flags, then custom arguments last.
 *
 * #32 - preserveAspectRatioEnabled is an app-level setting (#31), not a
 * per-game one, but it slots into this same list at the same position
 * Android's own launchGame() puts it: after aspectBezelFix, before custom
 * arguments. Defaults to false so this stays source-compatible for any
 * caller that predates #32.
 *
 * #46 - gamepadEnabled is another app-level flag, same category and
 * default-false-for-compatibility reasoning as preserveAspectRatioEnabled.
 * Confirmed real: -gamepad is documented in doc/CmdLine.md ("Enable
 * SDL_Gamepad configuration") and implemented in cmdline.cpp - a plain
 * flag, same as the others here, not a value/ini setting.
 *
 * gameFullscreenEnabled - real, live-found gap: -fullscreen used to be
 * hardcoded unconditionally in LaunchArgs.kt's buildLaunchArgs() instead
 * of living here alongside every other configurable flag. Defaults to
 * true (not false, unlike the others) to match that previous always-on
 * behavior for anyone already using this app - only newly-created
 * AppSettings start from AppSettings' own gameFullscreenEnabled default.
 */
fun launchArgumentsFor(
    installRoot: File,
    options: GameOptions,
    gameName: String,
    preserveAspectRatioEnabled: Boolean = false,
    gamepadEnabled: Boolean = false,
    gameFullscreenEnabled: Boolean = true,
): List<String> {
    val args = mutableListOf<String>()
    if (options.bezelEnabled) args += bezelLaunchArgs(installRoot, gameName)
    if (options.scorebezelAutofit) args += "-scorebezel_autofit"
    if (options.overlayBezel) args += "-overlaybezel"
    if (options.aspectBezelFix) args += "-aspectbezelfix"
    if (preserveAspectRatioEnabled) args += "-preserve_aspect_ratio"
    if (gamepadEnabled) args += "-gamepad"
    if (gameFullscreenEnabled) args += "-fullscreen"
    args += options.arguments
    return args
}
