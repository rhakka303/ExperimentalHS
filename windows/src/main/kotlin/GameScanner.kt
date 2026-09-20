import java.io.File

enum class GameCategory { SINGE_ZIPPED, SINGE_SCRIPT, DAPHNE_NATIVE }

/**
 * #111 - altScript is set only for a game that lives in a multi-game pack:
 * the name of its startup .singe file inside the pack's shared zip, passed
 * to hypseus as -usealt. Null for every ordinary game.
 */
data class Game(
    val name: String,
    val category: GameCategory,
    val framefilePath: String,
    val romOrScriptPath: String,
    val altScript: String? = null,
)

// #111 - a framefile has at least one line of the form "<frame number>
// <video file>.m2v". Only this content check tells one apart from a readme
// or notes file sitting in the same folder.
private val FRAME_LINE = Regex("""^\s*\d+\s+\S+\.m2v""", RegexOption.IGNORE_CASE)
private const val FRAMEFILE_LINES_TO_CHECK = 200

private fun looksLikeFramefile(file: File): Boolean =
    try {
        file.useLines { lines -> lines.take(FRAMEFILE_LINES_TO_CHECK).any { FRAME_LINE.containsMatchIn(it) } }
    } catch (e: java.io.IOException) {
        false
    }

/**
 * #111 - the games in a multi-game pack: a folder under singe/ holding one
 * shared Lua zip (<folder>.zip) and no <folder>.txt of its own, with each
 * game's framefile alongside. Every real framefile in it is one game, named
 * after the file, launched with the pack's zip plus -usealt <name>.
 */
private fun packGames(packDir: File): List<Game> {
    val zip = File(packDir, "${packDir.name}.zip")
    if (!zip.isFile) return emptyList()
    val framefiles = packDir.listFiles { f -> f.isFile && f.extension.equals("txt", ignoreCase = true) }
        ?.filter { looksLikeFramefile(it) }
        ?.sortedBy { it.name.lowercase() }
        ?: return emptyList()
    return framefiles.map { Game(it.nameWithoutExtension, GameCategory.SINGE_ZIPPED, it.path, zip.path, it.nameWithoutExtension) }
}

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
 *
 * #108 - gameFolder, when non-null, is where singe/, vldp/ and roms/ are
 * read from instead of installRoot itself. installRoot is still what has
 * to contain hypseus.exe: the engine always lives in the install, only
 * the games can live elsewhere.
 *
 * #111 - a singe/ folder with <folder>.zip but no <folder>.txt is a
 * multi-game pack: each framefile in it is its own game (see packGames()).
 * Such folders used to be silently skipped, like the shared library
 * folders above.
 */
fun scanGames(installRoot: File, gameFolder: File? = null): ScanResult {
    // hypseus.exe is the one file every real install has, regardless of
    // whether singe/roms/vldp happen to be empty (a fresh install's are -
    // confirmed against a real one in smoke/).
    if (!File(installRoot, "hypseus.exe").isFile) {
        return ScanResult.NotAHypseusInstall(installRoot)
    }

    val gamesRoot = gameFolder ?: installRoot
    val games = mutableListOf<Game>()

    val singeDir = File(gamesRoot, "singe")
    val packedGames = mutableListOf<Game>()
    singeDir.listFiles { f -> f.isDirectory }?.forEach { gameDir ->
        val name = gameDir.name
        val framefile = File(gameDir, "$name.txt")
        if (!framefile.isFile) {
            packedGames += packGames(gameDir)
            return@forEach
        }

        val zip = File(gameDir, "$name.zip")
        val script = File(gameDir, "$name.singe")
        when {
            zip.isFile -> games += Game(name, GameCategory.SINGE_ZIPPED, framefile.path, zip.path)
            script.isFile -> games += Game(name, GameCategory.SINGE_SCRIPT, framefile.path, script.path)
        }
    }

    val vldpDir = File(gamesRoot, "vldp")
    val romsDir = File(gamesRoot, "roms")
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

    // #111 - pack games are added last so an ordinary game always wins a
    // name clash (ignoring case): two games must never share one name.
    val taken = games.map { it.name.lowercase() }.toMutableSet()
    for (packed in packedGames) {
        if (taken.add(packed.name.lowercase())) games += packed
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

/**
 * #18 - one level up from resolveInstallRoot(): the launcher's own
 * folder (`HypdroidDesktop/`), where its own state lives per #6's
 * ownership rule. Same "only meaningful for the packaged app" caveat as
 * resolveInstallRoot() above.
 */
fun resolveLauncherFolder(): File? {
    val exePath = ProcessHandle.current().info().command().orElse(null) ?: return null
    return File(exePath).parentFile
}
