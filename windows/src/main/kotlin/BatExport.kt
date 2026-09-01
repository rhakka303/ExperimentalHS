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
 * No baseline flags (-gamepad, the live launcher's own baked-in default)
 * get added here - #20's own scope decision. A game's saved GameOptions
 * (bezel-family flags + custom arguments, #19's launchArgumentsFor())
 * still applies, same as the live launcher; that's the one thing a
 * `.bat` and the live launcher genuinely share.
 *
 * Real, live-found exception: -fullscreen was originally excluded here
 * too (it used to be one of the "baseline" flags), but it isn't a
 * hardcoded baseline anymore - AppSettings.gameFullscreenEnabled is a
 * real, user-toggled setting now (Video Settings' own "Game Full
 * Screen"), so an exported `.bat` reads and honors it just like the live
 * launcher does, rather than silently omitting whatever the toggle says.
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
        val content = buildBatContent(installRoot, game, options, appSettings.gameFullscreenEnabled)
        File(batchDir, "${game.name}.bat").writeText(content)
    }

    return games.size
}

/**
 * A path relative to installRoot itself - what hypseus resolves
 * -framefile/-zlua/-script against (its own inferred homedir), not the
 * .bat's own working directory. No `..\` prefix - see this file's own
 * doc comment for why that would point one level too high.
 */
private fun relativeToInstallRoot(installRoot: File, absolutePath: String): String =
    installRoot.toPath().relativize(File(absolutePath).toPath()).toString()

private fun batArg(arg: String): String = if (arg.contains(' ')) "\"$arg\"" else arg

private fun buildBatContent(installRoot: File, game: Game, options: GameOptions, gameFullscreenEnabled: Boolean): String {
    val args = mutableListOf<String>()
    when (game.category) {
        GameCategory.DAPHNE_NATIVE -> {
            args += game.name
            args += "vldp"
            args += listOf("-framefile", relativeToInstallRoot(installRoot, game.framefilePath))
        }
        GameCategory.SINGE_ZIPPED -> {
            args += "singe"
            args += "vldp"
            args += listOf("-framefile", relativeToInstallRoot(installRoot, game.framefilePath))
            args += listOf("-zlua", relativeToInstallRoot(installRoot, game.romOrScriptPath))
        }
        GameCategory.SINGE_SCRIPT -> {
            args += "singe"
            args += "vldp"
            args += listOf("-framefile", relativeToInstallRoot(installRoot, game.framefilePath))
            args += listOf("-script", relativeToInstallRoot(installRoot, game.romOrScriptPath))
        }
    }
    // #19's own extra-argument builder - bezel-family flags plus custom
    // arguments. preserveAspectRatioEnabled/gamepadEnabled default to
    // false (omitted) here on purpose: those are app-level toggles, not
    // part of a game's own saved GameOptions, and #20 is explicit that no
    // baseline flags get added automatically. gameFullscreenEnabled is
    // the one exception - see this file's own doc comment for why.
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
    val extraArgs = launchArgumentsFor(installRoot, options, game.name, gameFullscreenEnabled = gameFullscreenEnabled)
    args += extraArgs.dropLast(options.arguments.size)
    args += options.arguments.flatMap { it.trim().split(Regex("\\s+")).filter { token -> token.isNotEmpty() } }

    val commandLine = (listOf("..\\hypseus.exe") + args).joinToString(" ") { batArg(it) }
    // CRLF line endings, matching real Windows .bat file convention
    // (and the real sample this was confirmed against).
    return "@echo off\r\n$commandLine\r\n"
}
