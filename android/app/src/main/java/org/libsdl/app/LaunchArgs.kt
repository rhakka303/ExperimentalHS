package org.libsdl.app

import java.io.File

/**
 * Builds the CLI-equivalent argv hypseus expects, mirroring the desktop
 * invocations from the README/doc/CmdLine.md - minus argv[0]. SDL's own
 * nativeRunMain() (SDL_android.c) always hardcodes argv[0] to "app_process"
 * itself and appends this array starting at argv[1], so this list must
 * start directly with the game-type token, not a program-name placeholder -
 * getting that wrong shifts every arg by one and makes hypseus's parser
 * read the game-type slot as garbage, which fails parse_game_type() and
 * exits cleanly before anything else runs (caught via a real on-device
 * boot test: this exact bug produced a silent "exited cleanly (1)").
 *
 * For DAPHNE_NATIVE, the first element must be one of hypseus's own hardcoded game
 * shortnames (see src/io/cmdline.cpp's dispatch - "lair", "ace", "cliff",
 * etc.), not an arbitrary folder name - this only works because Daphne
 * romsets are conventionally already named that way, which is also why
 * GameScanner uses the folder/rom name directly as Game.name for this
 * category. For SINGE_ZIPPED/SINGE_SCRIPT, the actual game identity comes
 * entirely from the dynamic -zlua/-script Lua content, so argv[1] is
 * always the fixed literal "singe".
 *
 * "vldp" (argv[2]) selects hypseus's software MPEG laserdisc-player
 * backend - always used here since there's no real LDP hardware.
 *
 * -homedir/-datadir are the PARENT of the picked/scanned game folder, not
 * the picked folder itself. Confirmed via a real Singe game's own unzipped
 * .singe script (every Singe/LaserForge game has this same pattern, not
 * specific to one game): it hardcodes BASEDIR = "singe" and does
 * dofile(BASEDIR .. "/Framework/globals.singe"), i.e. it expects the real
 * homedir to have a "singe" subfolder containing both the game and the
 * shared Framework code - exactly matching hypseus's own upstream doc
 * convention (-framefile <home>/singe/<game>/<game>.txt). The picked folder
 * IS that "singe" subfolder (GameScanner.kt already scans it correctly for
 * game subfolders), so -homedir must be one level up. Passing the picked
 * folder itself as -homedir made "singe/Framework/globals.singe" resolve to
 * a nonexistent doubly-nested path, silently failing dofile() and leaving
 * Lua in a corrupted state that only crashed much later, inside an
 * unrelated-looking luaopen_os/strchr call - diagnosed via a real on-device
 * boot test and a full native crash backtrace, not guessed.
 */
fun buildLaunchArgs(game: Game, homeDir: String): Array<String> {
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

    val trueHomeDir = File(homeDir).parent ?: homeDir
    args += listOf("-homedir", "$trueHomeDir/", "-datadir", "$trueHomeDir/")
    // Baked-in defaults for every launch, per the owner: real fullscreen
    // gameplay, and SDL_Gamepad enabled (this is gamepad-first hardware).
    // No -haptic - rumble tuning is a later, separate decision.
    args += listOf("-fullscreen", "-gamepad")

    return args.toTypedArray()
}
