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

    private val neverLaunch: (Game, File, List<String>) -> LaunchResult =
        { _, _, _ -> error("must not launch") }

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

        val code = runHeadless("alpha", installRoot, launcherFolder) { _, _, _ ->
            LaunchResult.HypseusNotFound(File(installRoot, "hypseus.exe"))
        }

        assertEquals(EXIT_HYPSEUS_NOT_FOUND, code)
        assertTrue(logText().contains("Launch failed for alpha: hypseus.exe not found"))
    }

    @Test
    fun `success waits for the process and returns its exit code`() {
        makeInstall("alpha")
        var launched: Game? = null

        val code = runHeadless("alpha", installRoot, launcherFolder) { g, _, _ ->
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

        runHeadless(File(File("frontend", "roms"), "alpha.txt").path, installRoot, launcherFolder) { g, _, _ ->
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

        runHeadless("alpha", installRoot, launcherFolder) { _, _, e ->
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
        val capture: (Game, File, List<String>) -> LaunchResult = { _, _, e ->
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
}
