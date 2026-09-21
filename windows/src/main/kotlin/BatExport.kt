import java.io.File

/**
 * #20 - one `.bat` per scanned game, written into `batch/` at the
 * hypseus install root (not `HypdroidDesktop/` - other frontends need to
 * find these too, matching the convention other hypseus tooling already
 * uses for that folder).
 *
 * **Real, hard correction, confirmed against a 6+ year working `.bat`
 * sample the owner provided**: this must NOT reuse #9's buildLaunchArgs()
 * output as-is. That function bakes in the live launcher's own absolute
 * install path via explicit -homedir/-datadir - correct for the live
 * launcher (resolveInstallRoot() recomputes it fresh from the running
 * exe's own location every launch) but wrong for a *static* file: if the
 * whole install ever moves, absolute paths inside a `.bat` silently break
 * until it's regenerated, while relative paths keep working wherever the
 * folder ends up. The real sample calls `..\hypseus.exe` (relative, one
 * level up from `batch\`) with no -homedir/-datadir at all, relying on
 * hypseus's own default of inferring homedir from wherever hypseus.exe
 * itself sits.
 *
 * **Second correction, confirmed live against a real generated file**:
 * only the hypseus.exe invocation itself needs the `..\` - that part is
 * resolved by cmd.exe against the .bat's own folder (batch/) before
 * hypseus ever starts. -framefile/-zlua/-script are resolved by hypseus
 * itself, against the homedir it just inferred (installRoot, one level
 * up from where the .bat sits) - not against the shell's own working
 * directory. So those paths are relative to installRoot directly
 * (`singe\...`, `vldp\...`), with no `..\` prefix - the very first
 * version of this wrongly gave every one of them the same `..\` prefix
 * as the exe, which pointed one level too high (e.g. `..\singe\...`
 * resolves to a `singe\` folder that sits *next to* the real install,
 * not inside it).
 *
 * #133 - the flags are the ones a live launch sends: both call
 * launchFlagsFor() (Launcher.kt), which turns the game's saved options
 * (bezel, overlay bezel, custom arguments) and the app-level switches (Game
 * Full Screen, Gamepad, Preserve Video Aspect Ratio) into hypseus flags, as
 * they are set when Export is clicked. #20 first added no baseline flags at
 * all, then Game Full Screen was let in once it became a real setting, and
 * Gamepad and Preserve Video Aspect Ratio were left out by mistake, so a
 * `.bat` ignored both switches. A `.bat` is a snapshot: change a setting,
 * then export again.
 */
fun exportBatFiles(installRoot: File, games: List<Game>, launcherFolder: File?): Int {
    val batchDir = File(installRoot, "batch")
    batchDir.mkdirs()

    // "Re-running export overwrites cleanly - no duplicate or stale
    // files left behind" (#20's own acceptance criterion) - a game
    // renamed or removed from the ROM set since the last export would
    // otherwise leave its old .bat behind forever. Clearing every
    // existing .bat first and regenerating from the current scan is the
    // simplest way to guarantee that; only .bat files are touched, not
    // whatever else might live in batch/.
    batchDir.listFiles { f -> f.isFile && f.extension.equals("bat", ignoreCase = true) }
        ?.forEach { it.delete() }

    val allOptions = if (launcherFolder != null) loadAllOptions(launcherFolder) else emptyMap()
    val appSettings = if (launcherFolder != null) loadAppSettings(launcherFolder) else AppSettings()

    for (game in games) {
        val options = allOptions[game.name] ?: GameOptions()
        val content = buildBatContent(installRoot, game, options, appSettings)
        File(batchDir, "${game.name}.bat").writeText(content)
    }

    return games.size
}

/**
 * A path relative to root - what hypseus resolves -framefile/-zlua/-script
 * against (its own homedir), not the .bat's own working directory. No
 * `..\` prefix - see this file's own doc comment for why that would point
 * one level too high. Only used with the Game Folder off (#132): with it on,
 * the file uses full paths.
 */
private fun relativeToRoot(root: File, absolutePath: String): String =
    root.toPath().relativize(File(absolutePath).toPath()).toString()

private fun batArg(arg: String): String = if (arg.contains(' ')) "\"$arg\"" else arg

/**
 * #108 - with the Game Folder on, the games live outside the install, so the
 * file needs an explicit -homedir there.
 *
 * #132 - that first version also wrote -framefile/-zlua/-script relative to
 * the game folder, on the belief that hypseus resolves them against -homedir.
 * It resolves them against its working directory (the install, or the
 * `batch` folder when the file is opened from Explorer), where they do not
 * exist, so hypseus stopped with "Game Data file ... does not exist" and the
 * game never started. Tests only checked the text of the file; nothing ran
 * one. So with the Game Folder on, the file now uses exactly the argument
 * list the live launch uses (buildLaunchArgs): full paths, -homedir, -datadir
 * (the install, so hypseus still finds its fonts, pictures, sound, bezels and
 * input files) and -ramdir. The file already had to hold a full -homedir, so
 * it already broke if the game folder moved: re-export. With the Game
 * Folder off, nothing changes: relative paths, no -homedir/-datadir/-ramdir.
 */
private fun buildBatContent(installRoot: File, game: Game, options: GameOptions, appSettings: AppSettings): String {
    val gameFolder = appSettings.activeGameFolder()
    val args = mutableListOf<String>()
    if (gameFolder != null) {
        args += buildLaunchArgs(game, installRoot, gameFolder)
    } else {
        when (game.category) {
            GameCategory.DAPHNE_NATIVE -> {
                args += game.name
                args += "vldp"
                args += listOf("-framefile", relativeToRoot(installRoot, game.framefilePath))
            }
            GameCategory.SINGE_ZIPPED -> {
                args += "singe"
                args += "vldp"
                args += listOf("-framefile", relativeToRoot(installRoot, game.framefilePath))
                args += listOf("-zlua", relativeToRoot(installRoot, game.romOrScriptPath))
                // #111 - see buildLaunchArgs(): a multi-game pack game needs its startup script named
                game.altScript?.let { args += listOf("-usealt", it) }
            }
            GameCategory.SINGE_SCRIPT -> {
                args += "singe"
                args += "vldp"
                args += listOf("-framefile", relativeToRoot(installRoot, game.framefilePath))
                args += listOf("-script", relativeToRoot(installRoot, game.romOrScriptPath))
            }
        }
    }
    // #133 - the same flag builder the live launch uses (launchFlagsFor):
    // bezel-family flags, the app-level switches, then custom arguments.
    //
    // Real, live-found bug: launchArgumentsFor() always appends
    // options.arguments *last* (its own doc comment), and each saved
    // entry can itself be a multi-token string (e.g. "-scanline_shunt 4"
    // - #19's own convention, matching Android). Launcher.kt's real live-
    // launch path already splits those on whitespace before handing them
    // to ProcessBuilder as separate argv tokens; this file wasn't doing
    // that at all, so a multi-token entry landed in the .bat as one
    // string containing a space, which batArg() then wrapped in quotes
    // as a single malformed token instead of two real arguments.
    // dropLast/take-last split the combined list back into "flags"
    // (never contain spaces, never need splitting) and "this game's own
    // custom arguments" (need it), rather than blindly splitting
    // everything and risking breaking a path that legitimately has a
    // space in it.
    //
    // #108 - both halves go through splitArgumentTokens(): the flags can
    // now contain a quoted -bezeldir path with spaces (which stays one
    // token, its quotes dropped here and re-added by batArg() below), and
    // custom arguments get the same quote-aware split the live launcher
    // uses.
    val extraArgs = launchFlagsFor(installRoot, options, game.name, appSettings)
    args += extraArgs.dropLast(options.arguments.size).flatMap { splitArgumentTokens(it) }
    args += options.arguments.flatMap { splitArgumentTokens(it) }

    val commandLine = (listOf("..\\hypseus.exe") + args).joinToString(" ") { batArg(it) }
    // CRLF line endings, matching real Windows .bat file convention
    // (and the real sample this was confirmed against).
    return "@echo off\r\n$commandLine\r\n"
}
