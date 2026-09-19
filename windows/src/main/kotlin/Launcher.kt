import java.io.File

sealed interface LaunchResult {
    data class Started(val process: Process) : LaunchResult
    data class HypseusNotFound(val expectedPath: File) : LaunchResult
}

/**
 * #10 - spawns hypseus.exe with the argv #9 builds for a given game.
 *
 * hypseus.exe specifically, never hypseus_subsystem.exe: upstream ships
 * point releases as an exe-only drop-in that replaces hypseus.exe alone
 * (confirmed against a real install in smoke/), so the subsystem build
 * can silently be an older engine version. Using a hardcoded filename
 * here, rather than any kind of search, is what guarantees that.
 *
 * The returned Process is not waited on, and its exit code is not read or
 * discarded here - that is the caller's to observe via
 * Process.waitFor()/onExit(). Not swallowing it is the entire point of
 * returning the real Process object rather than, say, a plain Boolean.
 *
 * #18 - extraArguments are #18's per-game custom arguments, appended
 * after #9's own argv. Each entry is split on whitespace before being
 * appended, matching Android's MainActivity.kt (~line 396) exactly: a
 * saved entry can itself be a multi-token string (e.g. "-scalefactor
 * 50"), not necessarily one argv token per entry.
 *
 * #106 - discardOutput is for the headless path only, which waits on the
 * process for the whole session: an unread stdout/stderr pipe eventually
 * fills and would block hypseus mid-game. Defaults to false so the UI
 * path's behavior is untouched.
 */
fun launchGame(
    game: Game,
    installRoot: File,
    extraArguments: List<String> = emptyList(),
    discardOutput: Boolean = false,
): LaunchResult {
    val hypseusExe = File(installRoot, "hypseus.exe")
    if (!hypseusExe.isFile) {
        return LaunchResult.HypseusNotFound(hypseusExe)
    }

    val args = buildLaunchArgs(game, installRoot) +
        extraArguments.flatMap { it.trim().split(Regex("\\s+")).filter { token -> token.isNotEmpty() } }
    val builder = ProcessBuilder(listOf(hypseusExe.path) + args)
        .directory(installRoot)
    if (discardOutput) {
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        builder.redirectError(ProcessBuilder.Redirect.DISCARD)
    }

    return LaunchResult.Started(builder.start())
}

/**
 * #106 - the extra-argument list for a game's launch, shared by the UI
 * (Main.kt) and the headless path (HeadlessLaunch.kt) so the two can never
 * drift apart. Both read the saved per-game options and app settings from
 * disk at the moment of launch. launcherFolder is only non-null for the
 * packaged app (resolveLauncherFolder()); without it there is nowhere to
 * read saved options from, so no extra arguments.
 */
fun extraLaunchArgsFor(
    installRoot: File,
    launcherFolder: File?,
    appSettings: AppSettings,
    game: Game,
): List<String> =
    launcherFolder?.let {
        launchArgumentsFor(
            installRoot,
            loadOptions(it, game.name),
            game.name,
            appSettings.preserveAspectRatioEnabled,
            appSettings.gamepadEnabled,
            appSettings.gameFullscreenEnabled,
        )
    } ?: emptyList()
