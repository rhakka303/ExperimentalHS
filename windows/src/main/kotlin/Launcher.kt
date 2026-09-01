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
 */
fun launchGame(game: Game, installRoot: File, extraArguments: List<String> = emptyList()): LaunchResult {
    val hypseusExe = File(installRoot, "hypseus.exe")
    if (!hypseusExe.isFile) {
        return LaunchResult.HypseusNotFound(hypseusExe)
    }

    val args = buildLaunchArgs(game, installRoot) +
        extraArguments.flatMap { it.trim().split(Regex("\\s+")).filter { token -> token.isNotEmpty() } }
    val process = ProcessBuilder(listOf(hypseusExe.path) + args)
        .directory(installRoot)
        .start()

    return LaunchResult.Started(process)
}
