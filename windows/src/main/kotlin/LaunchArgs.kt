import java.io.File

/**
 * #9 - the CLI argv hypseus expects for a given game. The Android
 * `LaunchArgs.kt` is the specification, not something copied from - its
 * comments record why the ordering matters: a wrong first element makes
 * hypseus's parser read the game-type slot as garbage, which fails
 * parse_game_type() and exits cleanly with no error, before anything else
 * runs. That failure mode is silent, which is why this must never guess.
 *
 * For DAPHNE_NATIVE, the first element is one of hypseus's own hardcoded
 * game shortnames (src/io/cmdline.cpp's dispatch), which is exactly what
 * `Game.name` already is for this category, since GameScanner (#8) takes
 * it straight from the ROM folder name. For SINGE_ZIPPED/SINGE_SCRIPT the
 * actual game identity comes from the dynamic -zlua/-script content, so the
 * first element is always the fixed literal "singe".
 *
 * "vldp" selects hypseus's software MPEG laserdisc-player backend, always
 * used here since there is no real LDP hardware.
 *
 * -homedir/-datadir is always the install root resolved in #8 - required
 * to directly contain singe/, roms/ and vldp/ as true immediate children,
 * matching both hypseus's own Daphne ROM lookup (homedir::get_romfile()
 * builds "<homedir>/roms/<name>.zip") and Singe's own hardcoded
 * BASEDIR = "singe" convention. The trailing "/" matches Android's
 * convention exactly rather than using a Windows-style separator: hypseus
 * is cross-platform C++ and accepts forward slashes on Windows the same as
 * every other native app built against the C runtime, and this story's
 * acceptance criterion is an exact match against a real logged command
 * line, not a stylistic choice to improve on.
 */
fun buildLaunchArgs(game: Game, installRoot: File): List<String> {
    val args = mutableListOf<String>()

    when (game.category) {
        GameCategory.DAPHNE_NATIVE -> {
            args += game.name
            args += "vldp"
            args += listOf("-framefile", game.framefilePath)
        }
        GameCategory.SINGE_ZIPPED -> {
            args += "singe"
            args += "vldp"
            args += listOf("-framefile", game.framefilePath, "-zlua", game.romOrScriptPath)
        }
        GameCategory.SINGE_SCRIPT -> {
            args += "singe"
            args += "vldp"
            args += listOf("-framefile", game.framefilePath, "-script", game.romOrScriptPath)
        }
    }

    val homeDir = installRoot.path
    args += listOf("-homedir", "$homeDir/", "-datadir", "$homeDir/")
    // Same baked-in defaults as Android, per the owner: real fullscreen
    // gameplay, SDL_Gamepad enabled. Not configurable in phase 1.
    args += listOf("-fullscreen", "-gamepad")

    return args
}
