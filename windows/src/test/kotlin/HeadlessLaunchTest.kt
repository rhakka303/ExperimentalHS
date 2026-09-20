import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * #106 - headless launch mode. Pure logic plus a fake Process: nothing
 * here starts a real hypseus.exe. The real end-to-end launch is verified
 * by hand against a real install.
 */
class HeadlessLaunchTest {

    @TempDir
    lateinit var tmp: File

    private lateinit var installRoot: File
    private lateinit var launcherFolder: File

    private class FakeProcess(private val code: Int) : Process() {
        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
        override fun getInputStream(): InputStream = InputStream.nullInputStream()
        override fun getErrorStream(): InputStream = InputStream.nullInputStream()
        override fun waitFor(): Int = code
        override fun exitValue(): Int = code
        override fun destroy() {}
    }

    private fun logText(): String =
        File(File(launcherFolder, "log"), "hypdroiddesktop.log").let { if (it.isFile) it.readText() else "" }

    private fun makeInstall(vararg gameNames: String) {
        installRoot = File(tmp, "install").apply { mkdirs() }
        launcherFolder = File(tmp, "launcher").apply { mkdirs() }
        File(installRoot, "hypseus.exe").writeText("")
        for (name in gameNames) {
            val dir = File(File(installRoot, "singe"), name).apply { mkdirs() }
            File(dir, "$name.txt").writeText("")
            File(dir, "$name.zip").writeText("")
        }
    }

    private fun game(name: String) = Game(name, GameCategory.SINGE_ZIPPED, "", "")

    private val neverLaunch: (Game, File, File?, List<String>) -> LaunchResult =
        { _, _, _, _ -> error("must not launch") }

    // ---- headlessGameArgument ----

    @Test
    fun `no --game means normal UI start`() {
        assertNull(headlessGameArgument(arrayOf()))
        assertNull(headlessGameArgument(arrayOf("--something", "else")))
    }

    @Test
    fun `--game returns the value after it`() {
        assertEquals("alpha", headlessGameArgument(arrayOf("--game", "alpha")))
        assertEquals("alpha", headlessGameArgument(arrayOf("--x", "--game", "alpha", "--y")))
    }

    @Test
    fun `--game with nothing after it is still headless, with an empty value`() {
        assertEquals("", headlessGameArgument(arrayOf("--game")))
    }

    // ---- findGameByToken ----

    private val games = listOf(game("alpha"), game("beta.v2"), game("Gamma_HD"))

    @Test
    fun `finds a game by exact name`() {
        assertEquals("alpha", findGameByToken(games, "alpha")?.name)
    }

    @Test
    fun `matches case-insensitively`() {
        assertEquals("Gamma_HD", findGameByToken(games, "gamma_hd")?.name)
    }

    @Test
    fun `finds a game from a file path, ignoring folder and extension`() {
        assertEquals("alpha", findGameByToken(games, File(File("some", "dir"), "alpha.txt").path)?.name)
        assertEquals("alpha", findGameByToken(games, "alpha.zip")?.name)
    }

    @Test
    fun `a game name that contains a dot matches as-is`() {
        assertEquals("beta.v2", findGameByToken(games, "beta.v2")?.name)
    }

    @Test
    fun `blank or unknown token finds nothing`() {
        assertNull(findGameByToken(games, ""))
        assertNull(findGameByToken(games, "   "))
        assertNull(findGameByToken(games, "nope"))
    }

    // ---- runHeadless ----

    @Test
    fun `unknown game logs, returns the not-found code, and never launches`() {
        makeInstall("alpha")

        val code = runHeadless("nope", installRoot, launcherFolder, neverLaunch)

        assertEquals(EXIT_GAME_NOT_FOUND, code)
        assertTrue(logText().contains("no game matching 'nope'"))
    }

    @Test
    fun `--game with no value reports no game given`() {
        makeInstall("alpha")

        val code = runHeadless("", installRoot, launcherFolder, neverLaunch)

        assertEquals(EXIT_GAME_NOT_FOUND, code)
        assertTrue(logText().contains("no game given"))
    }

    @Test
    fun `not a hypseus install returns its code and logs it`() {
        installRoot = File(tmp, "empty").apply { mkdirs() }
        launcherFolder = File(tmp, "launcher").apply { mkdirs() }

        val code = runHeadless("alpha", installRoot, launcherFolder, neverLaunch)

        assertEquals(EXIT_NOT_A_HYPSEUS_INSTALL, code)
        assertTrue(logText().contains("not a hypseus installation"))
    }

    @Test
    fun `unresolvable install root returns its code and logs it`() {
        launcherFolder = File(tmp, "launcher").apply { mkdirs() }

        val code = runHeadless("alpha", null, launcherFolder, neverLaunch)

        assertEquals(EXIT_NOT_A_HYPSEUS_INSTALL, code)
        assertTrue(logText().contains("could not determine install root"))
    }

    @Test
    fun `failures with no launcher folder still return their code`() {
        makeInstall("alpha")

        assertEquals(EXIT_GAME_NOT_FOUND, runHeadless("nope", installRoot, null, neverLaunch))
    }

    @Test
    fun `hypseus exe missing at launch returns its code and logs it`() {
        makeInstall("alpha")

        val code = runHeadless("alpha", installRoot, launcherFolder) { _, _, _, _ ->
            LaunchResult.HypseusNotFound(File(installRoot, "hypseus.exe"))
        }

        assertEquals(EXIT_HYPSEUS_NOT_FOUND, code)
        assertTrue(logText().contains("Launch failed for alpha: hypseus.exe not found"))
    }

    @Test
    fun `success waits for the process and returns its exit code`() {
        makeInstall("alpha")
        var launched: Game? = null

        val code = runHeadless("alpha", installRoot, launcherFolder) { g, _, _, _ ->
            launched = g
            LaunchResult.Started(FakeProcess(7))
        }

        assertEquals(7, code)
        assertEquals("alpha", launched?.name)
        assertFalse(logText().contains("failed"), "a successful launch logs no failure")
    }

    @Test
    fun `accepts a file path as the game token`() {
        makeInstall("alpha")
        var launched: Game? = null

        runHeadless(File(File("frontend", "roms"), "alpha.txt").path, installRoot, launcherFolder) { g, _, _, _ ->
            launched = g
            LaunchResult.Started(FakeProcess(0))
        }

        assertEquals("alpha", launched?.name)
    }

    @Test
    fun `passes the saved per-game options and app settings, same as the UI would`() {
        makeInstall("alpha")
        saveOptions(launcherFolder, "alpha", GameOptions(arguments = listOf("-scalefactor 50")))
        var extra: List<String> = emptyList()

        runHeadless("alpha", installRoot, launcherFolder) { _, _, _, e ->
            extra = e
            LaunchResult.Started(FakeProcess(0))
        }

        // default AppSettings: -fullscreen on, everything else off
        assertEquals(listOf("-fullscreen", "-scalefactor 50"), extra)
        assertEquals(
            extraLaunchArgsFor(installRoot, launcherFolder, loadAppSettings(launcherFolder), game("alpha")),
            extra,
        )
    }

    @Test
    fun `options changed between launches are picked up with no re-export`() {
        makeInstall("alpha")
        val seen = mutableListOf<List<String>>()
        val capture: (Game, File, File?, List<String>) -> LaunchResult = { _, _, _, e ->
            seen += e
            LaunchResult.Started(FakeProcess(0))
        }

        saveOptions(launcherFolder, "alpha", GameOptions(arguments = listOf("-first")))
        runHeadless("alpha", installRoot, launcherFolder, capture)
        saveOptions(launcherFolder, "alpha", GameOptions(arguments = listOf("-second")))
        runHeadless("alpha", installRoot, launcherFolder, capture)

        assertTrue(seen[0].contains("-first"))
        assertTrue(seen[1].contains("-second"))
        assertFalse(seen[1].contains("-first"))
    }

    @Test
    fun `with the game folder on, scans and launches from that folder instead of the install`() {
        makeInstall("alpha")
        val gameFolder = File(tmp, "games").apply { mkdirs() }
        val dir = File(File(gameFolder, "singe"), "external").apply { mkdirs() }
        File(dir, "external.txt").writeText("")
        File(dir, "external.zip").writeText("")
        saveAppSettings(launcherFolder, AppSettings(gameFolderEnabled = true, gameFolderPath = gameFolder.path))
        var launched: Game? = null
        var folderSeen: File? = null

        val code = runHeadless("external", installRoot, launcherFolder) { g, _, folder, _ ->
            launched = g
            folderSeen = folder
            LaunchResult.Started(FakeProcess(0))
        }

        assertEquals(0, code)
        assertEquals("external", launched?.name)
        assertEquals(gameFolder, folderSeen)
        // the install's own game is not in the chosen folder, so not launchable
        assertEquals(EXIT_GAME_NOT_FOUND, runHeadless("alpha", installRoot, launcherFolder, neverLaunch))
    }

    // ---- #120: --bezel on|off ----

    private fun putBezelPng(name: String) {
        File(File(installRoot, "bezels").apply { mkdirs() }, "$name.png").writeText("")
    }

    private fun optionsFile() = File(launcherFolder, "options.json")

    private fun capturing(seen: MutableList<List<String>>): (Game, File, File?, List<String>) -> LaunchResult =
        { _, _, _, extra ->
            seen += extra
            LaunchResult.Started(FakeProcess(0))
        }

    @Test
    fun `no --bezel means the game's saved options are left alone`() {
        assertEquals(BezelArgument.NotGiven, headlessBezelArgument(arrayOf()))
        assertEquals(BezelArgument.NotGiven, headlessBezelArgument(arrayOf("--game", "alpha")))
    }

    @Test
    fun `--bezel takes on or off, ignoring case`() {
        assertEquals(BezelArgument.On, headlessBezelArgument(arrayOf("--game", "alpha", "--bezel", "on")))
        assertEquals(BezelArgument.Off, headlessBezelArgument(arrayOf("--bezel", "off", "--game", "alpha")))
        assertEquals(BezelArgument.On, headlessBezelArgument(arrayOf("--bezel", "ON")))
        assertEquals(BezelArgument.Off, headlessBezelArgument(arrayOf("--bezel", "Off")))
    }

    @Test
    fun `--bezel with nothing after it, or anything but on or off, is invalid`() {
        assertEquals(BezelArgument.Invalid(""), headlessBezelArgument(arrayOf("--game", "alpha", "--bezel")))
        assertEquals(BezelArgument.Invalid("maybe"), headlessBezelArgument(arrayOf("--bezel", "maybe")))
        assertEquals(BezelArgument.Invalid("true"), headlessBezelArgument(arrayOf("--bezel", "true")))
    }

    @Test
    fun `--bezel on saves it, keeps the other saved options, and launches with the bezel`() {
        makeInstall("alpha")
        putBezelPng("alpha")
        saveOptions(launcherFolder, "alpha", GameOptions(arguments = listOf("-scanlines"), coverArtOverride = CoverArtType.CD))
        val seen = mutableListOf<List<String>>()

        val code = runHeadless("alpha", installRoot, launcherFolder, BezelArgument.On, capturing(seen))

        assertEquals(0, code)
        val saved = loadOptions(launcherFolder, "alpha")
        assertTrue(saved.bezelEnabled)
        assertEquals(listOf("-scanlines"), saved.arguments)
        assertEquals(CoverArtType.CD, saved.coverArtOverride)
        assertTrue(seen.single().containsAll(listOf("-bezel", "alpha.png")), seen.single().toString())
    }

    @Test
    fun `--bezel off saves it, keeps the other saved options, and launches without the bezel`() {
        makeInstall("alpha")
        putBezelPng("alpha")
        saveOptions(launcherFolder, "alpha", GameOptions(arguments = listOf("-scanlines"), bezelEnabled = true))
        val seen = mutableListOf<List<String>>()

        val code = runHeadless("alpha", installRoot, launcherFolder, BezelArgument.Off, capturing(seen))

        assertEquals(0, code)
        val saved = loadOptions(launcherFolder, "alpha")
        assertFalse(saved.bezelEnabled)
        assertEquals(listOf("-scanlines"), saved.arguments)
        assertFalse(seen.single().contains("-bezel"), seen.single().toString())
    }

    @Test
    fun `--bezel on for a game with no options yet creates them, and touches no other game`() {
        makeInstall("alpha", "beta")
        putBezelPng("alpha")
        saveOptions(launcherFolder, "beta", GameOptions(arguments = listOf("-keep")))

        runHeadless("alpha", installRoot, launcherFolder, BezelArgument.On, capturing(mutableListOf()))

        assertTrue(loadOptions(launcherFolder, "alpha").bezelEnabled)
        assertEquals(GameOptions(arguments = listOf("-keep")), loadOptions(launcherFolder, "beta"))
    }

    @Test
    fun `--bezel on is saved even when the game has no bezel image, and that launch simply has none`() {
        makeInstall("alpha")
        val seen = mutableListOf<List<String>>()

        val code = runHeadless("alpha", installRoot, launcherFolder, BezelArgument.On, capturing(seen))

        assertEquals(0, code)
        assertTrue(loadOptions(launcherFolder, "alpha").bezelEnabled)
        assertFalse(seen.single().contains("-bezel"), seen.single().toString())
    }

    @Test
    fun `without --bezel a saved bezel setting is neither changed nor lost`() {
        makeInstall("alpha")
        putBezelPng("alpha")
        saveOptions(launcherFolder, "alpha", GameOptions(bezelEnabled = true))
        val before = optionsFile().readText()
        val seen = mutableListOf<List<String>>()

        runHeadless("alpha", installRoot, launcherFolder, capturing(seen))
        runHeadless("alpha", installRoot, launcherFolder, BezelArgument.NotGiven, capturing(seen))

        assertEquals(before, optionsFile().readText())
        assertTrue(seen.all { it.contains("-bezel") }, seen.toString())
    }

    @Test
    fun `an invalid --bezel launches nothing, saves nothing, logs why, and returns its own code`() {
        makeInstall("alpha")

        val missing = runHeadless("alpha", installRoot, launcherFolder, BezelArgument.Invalid(""), neverLaunch)
        val wrong = runHeadless("alpha", installRoot, launcherFolder, BezelArgument.Invalid("maybe"), neverLaunch)

        assertEquals(EXIT_BAD_ARGUMENTS, missing)
        assertEquals(EXIT_BAD_ARGUMENTS, wrong)
        assertFalse(optionsFile().exists())
        assertTrue(logText().contains("--bezel needs 'on' or 'off', got nothing"), logText())
        assertTrue(logText().contains("got 'maybe'"), logText())
    }

    @Test
    fun `an unknown game with --bezel returns the not-found code and saves nothing`() {
        makeInstall("alpha")

        val code = runHeadless("nope", installRoot, launcherFolder, BezelArgument.On, neverLaunch)

        assertEquals(EXIT_GAME_NOT_FOUND, code)
        assertFalse(optionsFile().exists())
    }

    @Test
    fun `the saved bezel is what the UI's own options loading sees afterwards`() {
        makeInstall("alpha")

        runHeadless("alpha", installRoot, launcherFolder, BezelArgument.On, capturing(mutableListOf()))
        assertTrue(loadAllOptions(launcherFolder)["alpha"]?.bezelEnabled == true)

        runHeadless("alpha", installRoot, launcherFolder, BezelArgument.Off, capturing(mutableListOf()))
        assertTrue(loadAllOptions(launcherFolder)["alpha"]?.bezelEnabled == false)
    }
}
