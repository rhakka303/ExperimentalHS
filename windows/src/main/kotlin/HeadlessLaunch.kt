import java.io.File

/**
 * #106 - headless launch mode: `HypdroidDesktop.exe --game <name>` skips
 * the UI entirely, resolves the game, launches hypseus with the same
 * arguments the UI would build, waits for it, and exits with its exit
 * code. Meant for external frontends (Attract-Mode, LaunchBox, RetroBat,
 * RocketLauncher, etc.), which block on the emulator process and treat
 * its exit as the end of the game - so this deliberately does not return
 * until hypseus does.
 *
 * Nothing here touches Compose, AWT, or the gamepad listener; main() only
 * reaches this before any of that would start.
 */

// Distinct nonzero codes for the failure cases only this launcher can
// see. hypseus's own exit code is passed through unchanged on success,
// so these can in principle collide with one of its own - accepted.
// Exact values are provisional until tested against a real frontend.
const val EXIT_GAME_NOT_FOUND = 2
const val EXIT_HYPSEUS_NOT_FOUND = 3
const val EXIT_NOT_A_HYPSEUS_INSTALL = 4
const val EXIT_BAD_ARGUMENTS = 5

/**
 * #120 - the optional `--bezel on|off` argument. Lets an external frontend
 * turn one game's bezel on or off through the launcher's own saved
 * options, instead of editing options.json itself.
 */
sealed interface BezelArgument {
    /** No `--bezel` given: the game's saved options are left exactly as they are. */
    data object NotGiven : BezelArgument
    data object On : BezelArgument
    data object Off : BezelArgument

    /** `--bezel` with nothing after it (given = "") or anything but on/off. */
    data class Invalid(val given: String) : BezelArgument
}

/** `on` or `off`, ignoring case; see [BezelArgument]. */
fun headlessBezelArgument(args: Array<String>): BezelArgument {
    val index = args.indexOf("--bezel")
    if (index < 0) return BezelArgument.NotGiven
    val value = args.getOrNull(index + 1)?.trim() ?: return BezelArgument.Invalid("")
    return when (value.lowercase()) {
        "on" -> BezelArgument.On
        "off" -> BezelArgument.Off
        else -> BezelArgument.Invalid(value)
    }
}

/**
 * The value after `--game`, or null when the flag isn't there at all
 * (normal UI start). `--game` with nothing after it returns "" - that is
 * still a headless request, and reports "no game given" instead of
 * quietly opening the UI under a frontend.
 */
fun headlessGameArgument(args: Array<String>): String? {
    val index = args.indexOf("--game")
    if (index < 0) return null
    return args.getOrNull(index + 1) ?: ""
}

/**
 * Frontends differ in what they hand over: a bare name (Attract-Mode's
 * [name]) or a file path (LaunchBox, RetroBat). Tries, in order, the whole
 * token, the file name, and the file name without its extension, so a
 * game name that itself contains a dot still matches as-is before any
 * extension is stripped. Case-insensitive, since Windows folder names
 * are and frontends sometimes lowercase.
 */
fun findGameByToken(games: List<Game>, token: String): Game? {
    val trimmed = token.trim()
    if (trimmed.isEmpty()) return null
    val file = File(trimmed)
    val candidates = listOf(trimmed, file.name, file.nameWithoutExtension).filter { it.isNotEmpty() }
    for (candidate in candidates) {
        games.firstOrNull { it.name.equals(candidate, ignoreCase = true) }?.let { return it }
    }
    return null
}

/**
 * Runs the whole headless flow and returns the process exit code. No
 * dialogs: failures are written to the launcher log (#94) and reported
 * only through the exit code. Like the UI, a successful launch itself is
 * not logged - hypseus writes its own logs for the session.
 *
 * launch is injectable so tests can run this without starting a real
 * hypseus.exe. Its arguments are the game, the install root, the Game
 * Folder page's chosen folder (null when it is off or unset, #108), and
 * the extra launch arguments.
 */
fun runHeadless(
    gameToken: String,
    installRoot: File?,
    launcherFolder: File?,
    launch: (Game, File, File?, List<String>) -> LaunchResult = realLaunch,
): Int = runHeadless(gameToken, installRoot, launcherFolder, BezelArgument.NotGiven, launch)

private val realLaunch: (Game, File, File?, List<String>) -> LaunchResult = { game, root, gameFolder, extra ->
    launchGame(game, root, extra, discardOutput = true, gameFolder = gameFolder)
}

/**
 * #120 - the same flow with an optional `--bezel`. When it is on or off,
 * the game's bezelEnabled is saved to options.json (keeping every other
 * saved option) after the game is found and before its launch arguments
 * are built, so this very launch uses the new value. An invalid `--bezel`
 * stops before anything else: nothing is looked up, saved or launched. An
 * unknown game saves nothing.
 */
fun runHeadless(
    gameToken: String,
    installRoot: File?,
    launcherFolder: File?,
    bezel: BezelArgument,
    launch: (Game, File, File?, List<String>) -> LaunchResult = realLaunch,
): Int {
    if (bezel is BezelArgument.Invalid) {
        val given = if (bezel.given.isEmpty()) "nothing" else "'${bezel.given}'"
        log(launcherFolder, "Headless launch failed: --bezel needs 'on' or 'off', got $given")
        return EXIT_BAD_ARGUMENTS
    }

    if (installRoot == null) {
        log(launcherFolder, "Headless launch failed: could not determine install root - HypdroidDesktop is not running from inside a hypseus install")
        return EXIT_NOT_A_HYPSEUS_INSTALL
    }

    // Read once, up front: the scan and the launch below must agree on the
    // game folder, and both read the same saved settings the UI does.
    val appSettings = launcherFolder?.let { loadAppSettings(it) } ?: AppSettings()
    val gameFolder = appSettings.activeGameFolder()

    val games = when (val scan = logUnexpectedExceptions(launcherFolder, "scanning for games") { scanGames(installRoot, gameFolder) }) {
        is ScanResult.NotAHypseusInstall -> {
            log(launcherFolder, "Headless launch failed: not a hypseus installation: ${scan.checkedPath}")
            return EXIT_NOT_A_HYPSEUS_INSTALL
        }
        is ScanResult.Found -> scan.games
    }

    val game = findGameByToken(games, gameToken)
    if (game == null) {
        val given = if (gameToken.isBlank()) "no game given" else "no game matching '$gameToken'"
        log(launcherFolder, "Headless launch failed: $given")
        return EXIT_GAME_NOT_FOUND
    }

    val bezelOn = when (bezel) {
        BezelArgument.On -> true
        BezelArgument.Off -> false
        else -> null
    }
    if (bezelOn != null && launcherFolder != null) {
        logUnexpectedExceptions(launcherFolder, "saving the bezel setting for ${game.name}") {
            saveOptions(launcherFolder, game.name, loadOptions(launcherFolder, game.name).copy(bezelEnabled = bezelOn))
        }
        log(launcherFolder, "Headless: bezel ${if (bezelOn) "on" else "off"} saved for ${game.name}")
    }

    val extraArguments = extraLaunchArgsFor(installRoot, launcherFolder, appSettings, game)

    return when (val result = logUnexpectedExceptions(launcherFolder, "launching ${game.name}") { launch(game, installRoot, gameFolder, extraArguments) }) {
        is LaunchResult.HypseusNotFound -> {
            log(launcherFolder, "Launch failed for ${game.name}: hypseus.exe not found at ${result.expectedPath}")
            EXIT_HYPSEUS_NOT_FOUND
        }
        is LaunchResult.Started -> result.process.waitFor()
    }
}
