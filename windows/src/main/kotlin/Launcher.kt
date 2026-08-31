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
 */
fun launchGame(game: Game, installRoot: File): LaunchResult {
    val hypseusExe = File(installRoot, "hypseus.exe")
    if (!hypseusExe.isFile) {
        return LaunchResult.HypseusNotFound(hypseusExe)
    }

    val args = buildLaunchArgs(game, installRoot)
    val process = ProcessBuilder(listOf(hypseusExe.path) + args)
        .directory(installRoot)
        .start()

    return LaunchResult.Started(process)
}
