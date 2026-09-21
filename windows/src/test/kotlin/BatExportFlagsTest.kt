import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

/**
 * #133 - an exported `.bat` carries the same flags a live launch sends.
 * Pure files and argument lists; no real hypseus. The paths in a `.bat` are
 * meant to differ from a live launch (relative, no -datadir), so the parity
 * check leaves them out and compares everything else, in order.
 */
class BatExportFlagsTest {

    @TempDir
    lateinit var tmp: File

    private lateinit var install: File
    private lateinit var gameFolder: File
    private lateinit var launcherFolder: File

    private fun frameText(video: String) = ".\n\n1       $video.m2v\n355     sub/$video.m2v\n"

    /** One of each kind: zipped Singe, script Singe, native ROM, and two games in a pack. */
    private fun layOutGames(root: File) {
        fun singeDir(name: String) = File(File(root, "singe"), name).apply { mkdirs() }

        singeDir("zipped").let {
            File(it, "zipped.txt").writeText(frameText("zipped"))
            File(it, "zipped.zip").writeText("")
        }
        singeDir("scripted").let {
            File(it, "scripted.txt").writeText(frameText("scripted"))
            File(it, "scripted.singe").writeText("")
        }
        singeDir("mega").let {
            File(it, "mega.zip").writeText("")
            File(it, "gamea.txt").writeText(frameText("gamea"))
            File(it, "gameb.txt").writeText(frameText("gameb"))
        }
        File(File(root, "vldp"), "native").apply { mkdirs() }.also { File(it, "native.txt").writeText(frameText("native")) }
        File(root, "roms").mkdirs()
        File(File(root, "roms"), "native.zip").writeText("")

        File(root, "bezels").mkdirs()
        File(File(root, "bezels"), "zipped.png").writeText("")
    }

    private fun makeInstall() {
        install = File(tmp, "install").apply { mkdirs() }
        gameFolder = File(tmp, "my games").apply { mkdirs() }
        launcherFolder = File(tmp, "launcher").apply { mkdirs() }
        File(install, "hypseus.exe").writeText("")
        layOutGames(install)
        layOutGames(gameFolder)
    }

    private fun settings(gamepad: Boolean, preserve: Boolean, fullscreen: Boolean, folder: Boolean) = AppSettings(
        preserveAspectRatioEnabled = preserve,
        gamepadEnabled = gamepad,
        gameFullscreenEnabled = fullscreen,
        gameFolderEnabled = folder,
        gameFolderPath = if (folder) gameFolder.path else null,
    )

    /** Saves the settings, scans, exports, and returns the scanned games. */
    private fun export(settings: AppSettings): List<Game> {
        saveAppSettings(launcherFolder, settings)
        val games = (scanGames(install, settings.activeGameFolder()) as ScanResult.Found).games
        exportBatFiles(install, games, launcherFolder)
        return games
    }

    private fun batFile(name: String) = File(File(install, "batch"), "$name.bat")

    /** The .bat's command line as tokens, without the leading ..\hypseus.exe. */
    private fun batTokens(name: String): List<String> =
        splitArgumentTokens(batFile(name).readLines()[1]).drop(1)

    /** The full argument list a live launch of this game would start hypseus with. */
    private fun liveTokens(game: Game): List<String> {
        val settings = loadAppSettings(launcherFolder)
        return hypseusArguments(
            game,
            install,
            extraLaunchArgsFor(install, launcherFolder, settings, game),
            settings.activeGameFolder(),
        )
    }

    private val pathFlags = setOf("-framefile", "-zlua", "-script", "-homedir", "-datadir", "-ramdir")

    /** Drops what is meant to differ: the game-type and vldp head, and every path flag with its value. */
    private fun withoutPaths(tokens: List<String>): List<String> {
        val kept = mutableListOf<String>()
        var i = 2
        while (i < tokens.size) {
            if (tokens[i] in pathFlags) i += 2 else kept += tokens[i++]
        }
        return kept
    }

    private fun allBatNames(): List<String> =
        File(install, "batch").listFiles { f -> f.extension == "bat" }!!.map { it.nameWithoutExtension }.sorted()

    private fun count(tokens: List<String>, flag: String) = tokens.count { it == flag }

    // ---- parity with a live launch ----

    @Test
    fun `a bat carries exactly the flags a live launch of the same game sends, for every combination of settings`() {
        makeInstall()
        // A bezel, an overlay bezel and manual arguments (one multi-token, one with a quoted path)
        // on one game, plain manual arguments on another, so those flags are part of the comparison.
        saveOptions(
            launcherFolder,
            "zipped",
            GameOptions(
                bezelEnabled = true,
                overlayBezel = true,
                arguments = listOf("-scalefactor 50", "-keymapfile \"my folder\\keys.ini\""),
            ),
        )
        saveOptions(launcherFolder, "gamea", GameOptions(arguments = listOf("-scanlines")))

        val onOff = listOf(false, true)
        var compared = 0
        for (gamepad in onOff) for (preserve in onOff) for (fullscreen in onOff) for (folder in onOff) {
            val games = export(settings(gamepad, preserve, fullscreen, folder))
            assertEquals(5, games.size)
            for (game in games) {
                val label = "gamepad=$gamepad preserve=$preserve fullscreen=$fullscreen gameFolder=$folder game=${game.name}"
                assertEquals(withoutPaths(liveTokens(game)), withoutPaths(batTokens(game.name)), label)
                compared++
            }
        }
        assertEquals(80, compared)
    }

    // ---- the two switches ----

    @Test
    fun `gamepad on at export gives exactly one -gamepad in every bat, off gives none`() {
        makeInstall()

        export(settings(gamepad = true, preserve = false, fullscreen = true, folder = false))
        assertEquals(5, allBatNames().size)
        assertEquals(setOf(1), allBatNames().map { count(batTokens(it), "-gamepad") }.toSet())

        export(settings(gamepad = false, preserve = false, fullscreen = true, folder = false))
        assertEquals(setOf(0), allBatNames().map { count(batTokens(it), "-gamepad") }.toSet())
    }

    @Test
    fun `preserve video aspect ratio on at export gives exactly one -preserve_aspect_ratio in every bat, off gives none`() {
        makeInstall()

        export(settings(gamepad = false, preserve = true, fullscreen = true, folder = false))
        assertEquals(5, allBatNames().size)
        assertEquals(setOf(1), allBatNames().map { count(batTokens(it), "-preserve_aspect_ratio") }.toSet())

        export(settings(gamepad = false, preserve = false, fullscreen = true, folder = false))
        assertEquals(setOf(0), allBatNames().map { count(batTokens(it), "-preserve_aspect_ratio") }.toSet())
    }

    @Test
    fun `the two switches also reach the bats when the game folder is on`() {
        makeInstall()

        export(settings(gamepad = true, preserve = true, fullscreen = false, folder = true))

        for (name in allBatNames()) {
            val tokens = batTokens(name)
            assertEquals(1, count(tokens, "-gamepad"), name)
            assertEquals(1, count(tokens, "-preserve_aspect_ratio"), name)
            assertEquals(0, count(tokens, "-fullscreen"), name)
        }
    }

    // ---- nothing else changes ----

    @Test
    fun `with both switches off the exported file is what it was before this change`() {
        makeInstall()
        val sep = File.separator
        val game = "singe${sep}zipped${sep}zipped"

        export(settings(gamepad = false, preserve = false, fullscreen = true, folder = false))
        assertEquals(
            "@echo off\r\n..\\hypseus.exe singe vldp -framefile $game.txt -zlua $game.zip -fullscreen\r\n",
            batFile("zipped").readText(),
        )

        export(settings(gamepad = false, preserve = false, fullscreen = false, folder = false))
        assertEquals(
            "@echo off\r\n..\\hypseus.exe singe vldp -framefile $game.txt -zlua $game.zip\r\n",
            batFile("zipped").readText(),
        )
    }

    @Test
    fun `with both switches on, the flags come in the same order as a live launch`() {
        makeInstall()

        export(settings(gamepad = true, preserve = true, fullscreen = true, folder = false))

        assertEquals(
            listOf("-preserve_aspect_ratio", "-gamepad", "-fullscreen"),
            withoutPaths(batTokens("zipped")),
        )
    }

    // ---- the shared builder ----

    @Test
    fun `each app switch maps to its own flag in launchFlagsFor`() {
        val root = File(tmp, "root")

        fun flags(gamepad: Boolean = false, preserve: Boolean = false, fullscreen: Boolean = false) = launchFlagsFor(
            root,
            GameOptions(),
            "g",
            AppSettings(gamepadEnabled = gamepad, preserveAspectRatioEnabled = preserve, gameFullscreenEnabled = fullscreen),
        )

        assertEquals(emptyList<String>(), flags())
        assertEquals(listOf("-gamepad"), flags(gamepad = true))
        assertEquals(listOf("-preserve_aspect_ratio"), flags(preserve = true))
        assertEquals(listOf("-fullscreen"), flags(fullscreen = true))
        assertEquals(listOf("-preserve_aspect_ratio", "-gamepad", "-fullscreen"), flags(gamepad = true, preserve = true, fullscreen = true))
    }
}
