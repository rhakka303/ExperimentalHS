import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * #130 - the Controls page's Gamepad switch alone decides whether -gamepad is
 * sent to hypseus. These check the exact command line after hypseus.exe
 * (hypseusArguments(), the same list launchGame() starts the process with),
 * for the live launch and for headless --game. Nothing here starts a real
 * hypseus.exe; the real command line is checked by hand.
 */
class GamepadSwitchTest {

    @TempDir
    lateinit var tmp: File

    private lateinit var installRoot: File
    private lateinit var launcherFolder: File

    private class FakeProcess : Process() {
        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
        override fun getInputStream(): InputStream = InputStream.nullInputStream()
        override fun getErrorStream(): InputStream = InputStream.nullInputStream()
        override fun waitFor(): Int = 0
        override fun exitValue(): Int = 0
        override fun destroy() {}
    }

    private fun makeInstall(name: String = "alpha") {
        installRoot = File(tmp, "install").apply { mkdirs() }
        launcherFolder = File(tmp, "launcher").apply { mkdirs() }
        File(installRoot, "hypseus.exe").writeText("")
        val dir = File(File(installRoot, "singe"), name).apply { mkdirs() }
        File(dir, "$name.txt").writeText("")
        File(dir, "$name.zip").writeText("")
    }

    private fun writeSettings(json: String) {
        File(launcherFolder, "app_settings.json").writeText(json)
    }

    private fun count(args: List<String>) = args.count { it == "-gamepad" }

    /** The full command line the live launch (Main.kt) would start hypseus with. */
    private fun liveArgs(game: Game): List<String> {
        val settings = loadAppSettings(launcherFolder)
        return hypseusArguments(
            game,
            installRoot,
            extraLaunchArgsFor(installRoot, launcherFolder, settings, game),
            settings.activeGameFolder(),
        )
    }

    /** The full command line a headless --game launch would start hypseus with. */
    private fun headlessArgs(): List<String> {
        var full: List<String>? = null
        val code = runHeadless("alpha", installRoot, launcherFolder) { game, root, gameFolder, extra ->
            full = hypseusArguments(game, root, extra, gameFolder)
            LaunchResult.Started(FakeProcess())
        }
        assertEquals(0, code)
        return full ?: error("nothing was launched")
    }

    private fun scanned(): Game =
        (scanGames(installRoot) as ScanResult.Found).games.single { it.name == "alpha" }

    // ---- the built-in argument list never carries it ----

    @Test
    fun `buildLaunchArgs no longer adds -gamepad for any kind of game`() {
        makeInstall()
        val games = listOf(
            Game("alpha", GameCategory.DAPHNE_NATIVE, "f.txt", ""),
            Game("alpha", GameCategory.SINGE_ZIPPED, "f.txt", "z.zip"),
            Game("alpha", GameCategory.SINGE_SCRIPT, "f.txt", "s.singe"),
        )

        for (g in games) {
            assertEquals(0, count(buildLaunchArgs(g, installRoot)), g.category.toString())
            assertEquals(0, count(buildLaunchArgs(g, installRoot, File(tmp, "games"))), g.category.toString())
        }
    }

    // ---- live launch ----

    @Test
    fun `live launch, switch off, sends no -gamepad`() {
        makeInstall()
        writeSettings("""{"gamepadEnabled":false}""")

        assertEquals(0, count(liveArgs(scanned())), liveArgs(scanned()).toString())
    }

    @Test
    fun `live launch, switch on, sends -gamepad exactly once`() {
        makeInstall()
        writeSettings("""{"gamepadEnabled":true}""")

        assertEquals(1, count(liveArgs(scanned())), liveArgs(scanned()).toString())
    }

    @Test
    fun `live launch with no settings file at all sends no -gamepad`() {
        makeInstall()

        assertEquals(0, count(liveArgs(scanned())))
    }

    @Test
    fun `live launch, an old settings file without the key still means off`() {
        makeInstall()
        writeSettings("""{"preserveAspectRatioEnabled":true}""")

        assertFalse(loadAppSettings(launcherFolder).gamepadEnabled)
        assertEquals(0, count(liveArgs(scanned())))
    }

    // ---- headless --game ----

    @Test
    fun `headless, switch off, sends no -gamepad`() {
        makeInstall()
        writeSettings("""{"gamepadEnabled":false}""")

        assertEquals(0, count(headlessArgs()))
    }

    @Test
    fun `headless, switch on, sends -gamepad exactly once`() {
        makeInstall()
        writeSettings("""{"gamepadEnabled":true}""")

        assertEquals(1, count(headlessArgs()), headlessArgs().toString())
    }

    @Test
    fun `headless with no settings file at all sends no -gamepad`() {
        makeInstall()

        assertEquals(0, count(headlessArgs()))
    }

    @Test
    fun `headless, an old settings file without the key still means off`() {
        makeInstall()
        writeSettings("""{"gameFullscreenEnabled":false}""")

        assertEquals(0, count(headlessArgs()))
    }

    @Test
    fun `headless with the switch on and the game folder on still sends -gamepad once`() {
        makeInstall()
        val gameFolder = File(tmp, "games")
        File(File(File(gameFolder, "singe"), "alpha"), "alpha.txt").apply { parentFile.mkdirs() }.writeText("")
        File(gameFolder, "singe/alpha/alpha.zip").writeText("")
        writeSettings("""{"gamepadEnabled":true,"gameFolderEnabled":true,"gameFolderPath":"${gameFolder.path.replace("\\", "\\\\")}"}""")

        val args = headlessArgs()

        assertEquals(1, count(args), args.toString())
        assertTrue(args.contains("-ramdir"), args.toString())
    }

    // ---- the switch changes nothing else on the line ----

    @Test
    fun `turning the switch on adds only -gamepad, nothing else moves`() {
        makeInstall()
        writeSettings("""{"gamepadEnabled":false}""")
        val off = liveArgs(scanned())
        writeSettings("""{"gamepadEnabled":true}""")
        val on = liveArgs(scanned())

        assertEquals(off, on.filter { it != "-gamepad" })
        assertEquals(off.size + 1, on.size)
    }

    // ---- the pieces still in place ----

    @Test
    fun `launchArgumentsFor adds -gamepad only when asked`() {
        makeInstall()

        assertFalse(launchArgumentsFor(installRoot, GameOptions(), "alpha").contains("-gamepad"))
        assertFalse(launchArgumentsFor(installRoot, GameOptions(), "alpha", gamepadEnabled = false).contains("-gamepad"))
        assertEquals(1, count(launchArgumentsFor(installRoot, GameOptions(), "alpha", gamepadEnabled = true)))
    }
}
