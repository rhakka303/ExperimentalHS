import java.io.File

enum class GameCategory { SINGE_ZIPPED, SINGE_SCRIPT, DAPHNE_NATIVE }

data class Game(
    val name: String,
    val category: GameCategory,
    val framefilePath: String,
    val romOrScriptPath: String,
)

sealed interface ScanResult {
    data class Found(val games: List<Game>) : ScanResult
    data class NotAHypseusInstall(val checkedPath: File) : ScanResult
}

/**
 * #8 - given a hypseus install root, produce the games it contains. Folder
 * conventions match the Android GameScanner exactly (that file is the
 * specification, not something copied from): Singe games live under
 * singe/<name>/<name>.txt with either <name>.zip or <name>.singe
 * alongside; Daphne-native games live under vldp/<name>/<name>.txt with
 * roms/<name>.zip.
 *
 * Any folder that doesn't match a category's required layout is silently
 * excluded, matching the Android scanner's own rule - this is also what
 * lets shared library folders like singe/Framework and
 * singe/FrameworkKimmy sit alongside real games with no special-casing:
 * they have no matching <name>.txt framefile, so they're never
 * candidates.
 */
fun scanGames(installRoot: File): ScanResult {
    // hypseus.exe is the one file every real install has, regardless of
    // whether singe/roms/vldp happen to be empty (a fresh install's are -
    // confirmed against a real one in smoke/).
    if (!File(installRoot, "hypseus.exe").isFile) {
        return ScanResult.NotAHypseusInstall(installRoot)
    }

    val games = mutableListOf<Game>()

    val singeDir = File(installRoot, "singe")
    singeDir.listFiles { f -> f.isDirectory }?.forEach { gameDir ->
        val name = gameDir.name
        val framefile = File(gameDir, "$name.txt")
        if (!framefile.isFile) return@forEach

        val zip = File(gameDir, "$name.zip")
        val script = File(gameDir, "$name.singe")
        when {
            zip.isFile -> games += Game(name, GameCategory.SINGE_ZIPPED, framefile.path, zip.path)
            script.isFile -> games += Game(name, GameCategory.SINGE_SCRIPT, framefile.path, script.path)
        }
    }

    val vldpDir = File(installRoot, "vldp")
    val romsDir = File(installRoot, "roms")
    if (vldpDir.isDirectory) {
        vldpDir.listFiles { f -> f.isDirectory }?.forEach { gameDir ->
            val name = gameDir.name
            val framefile = File(gameDir, "$name.txt")
            val rom = File(romsDir, "$name.zip")
            if (framefile.isFile && rom.isFile) {
                games += Game(name, GameCategory.DAPHNE_NATIVE, framefile.path, rom.path)
            }
        }
    }

    return ScanResult.Found(games.sortedBy { it.name })
}

/**
 * #8 - the install root is one level up from wherever HypdroidDesktop is
 * running from: the launcher sits in its own folder, which sits directly
 * inside the hypseus install (per #6's scope decision - never configured,
 * never chosen).
 *
 * Uses the running process's own executable path rather than the working
 * directory, since the working directory isn't guaranteed to match where
 * the exe actually lives (e.g. a shortcut with a different "Start in").
 * Only meaningful for the packaged app: jpackage's native launcher execs
 * the JVM in-process, so ProcessHandle reports the launcher exe's own
 * path. Running via `gradlew run` reports the JDK's own java.exe instead,
 * which resolves to nowhere useful - that path is proven separately by #7
 * and isn't this function's job to handle.
 */
fun resolveInstallRoot(): File? {
    val exePath = ProcessHandle.current().info().command().orElse(null) ?: return null
    return File(exePath).parentFile?.parentFile
}
