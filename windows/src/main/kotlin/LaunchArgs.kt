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
 *
 * #108 - gameFolder, when non-null, becomes -homedir instead: that is the
 * directory hypseus needs to directly contain singe/, roms/ and vldp/ (see
 * above), so a game folder laid out that way just works. -datadir stays
 * the install root: in cmdline.cpp it changes hypseus's working directory,
 * and homedir lookups fall back to that directory, so the install's own
 * fonts, pics, sound, bezels and hypinput.ini are still found. hypseus
 * also writes its own logs/, ram/ and screenshots/ under -homedir, so
 * those land in the game folder while it is in use.
 *
 * #115 - with a game folder, -ramdir is passed too (see ramDirFor()).
 */
fun buildLaunchArgs(game: Game, installRoot: File, gameFolder: File? = null): List<String> {
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
            // #111 - a game in a multi-game pack: without this hypseus looks
            // inside the shared zip for <zipname>.singe, which isn't there.
            game.altScript?.let { args += listOf("-usealt", it) }
        }
        GameCategory.SINGE_SCRIPT -> {
            args += "singe"
            args += "vldp"
            args += listOf("-framefile", game.framefilePath, "-script", game.romOrScriptPath)
        }
    }

    val dataDir = installRoot.path
    val homeDir = (gameFolder ?: installRoot).path
    args += listOf("-homedir", "$homeDir/", "-datadir", "$dataDir/")
    gameFolder?.let { args += listOf("-ramdir", ramDirFor(it)) }
    // Same baked-in default as Android, per the owner: SDL_Gamepad
    // enabled. Not configurable in phase 1. -fullscreen used to be
    // hardcoded here too - now a real setting (AppSettings.
    // gameFullscreenEnabled), applied via launchArgumentsFor() alongside
    // every other configurable flag instead of unconditionally here.
    args += "-gamepad"

    return args
}

/**
 * #115 - the -ramdir value used when a game folder is on: the game
 * folder's own ram/. Without it hypseus splits a zipped Singe game's ram
 * files across two places: it creates ram/singe/<game>/ under -homedir (the
 * game folder) but writes the file relative to its working directory (the
 * install, from -datadir), where that folder does not exist. The first
 * launch of any such game then fails with "Error copying zip ramfile".
 * -ramdir makes hypseus use this one absolute location for both.
 *
 * #121 - written with the platform's own separators (backslashes on
 * Windows), NOT forward slashes. Before a Singe script can write a file
 * into ram, hypseus (lua_chkdir in luretro.c) creates each folder of the
 * path in turn, cutting at every '/'. With "X:/games/ram" the first cut is
 * the bare drive "X:", which stat() cannot find and mkdir() cannot make, so
 * the script's write is refused ("File exists") and the game quits. With
 * "X:\games\ram" there is no '/' before the ram folder, so that step never
 * happens. The drive letter still comes from the folder chosen on the Game
 * Folder page; nothing here is hard-coded.
 *
 * No trailing separator (File.path never leaves one). hypseus cuts every
 * switch value at 80 characters, the same limit -homedir already has.
 */
fun ramDirFor(gameFolder: File): String = File(gameFolder, "ram").path
