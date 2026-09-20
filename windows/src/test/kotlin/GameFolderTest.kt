import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * #108 - the Game Folder setting and everything it changes underneath:
 * which folder gets scanned, what hypseus is told, and what an exported
 * .bat says. Pure logic and files; the page itself and the native chooser
 * are verified by hand.
 */
class GameFolderTest {

    @TempDir
    lateinit var tmp: File

    private fun makeInstall(): File =
        File(tmp, "install").apply { mkdirs() }.also { File(it, "hypseus.exe").writeText("") }

    private fun addSingeGame(root: File, name: String) {
        val dir = File(File(root, "singe"), name).apply { mkdirs() }
        File(dir, "$name.txt").writeText("")
        File(dir, "$name.zip").writeText("")
    }

    private fun addDaphneGame(root: File, name: String) {
        File(File(root, "vldp"), name).apply { mkdirs() }.also { File(it, "$name.txt").writeText("") }
        File(root, "roms").mkdirs()
        File(File(root, "roms"), "$name.zip").writeText("")
    }

    private fun namesOf(result: ScanResult): List<String> =
        (result as ScanResult.Found).games.map { it.name }

    // ---- activeGameFolder ----

    @Test
    fun `default settings use the install's own folders`() {
        assertNull(AppSettings().activeGameFolder())
    }

    @Test
    fun `on with a chosen folder returns that folder`() {
        val settings = AppSettings(gameFolderEnabled = true, gameFolderPath = "D:/games")

        assertEquals(File("D:/games"), settings.activeGameFolder())
    }

    @Test
    fun `on with no folder chosen yet still uses the install's own folders`() {
        assertNull(AppSettings(gameFolderEnabled = true).activeGameFolder())
        assertNull(AppSettings(gameFolderEnabled = true, gameFolderPath = "  ").activeGameFolder())
    }

    @Test
    fun `off ignores a previously chosen folder`() {
        assertNull(AppSettings(gameFolderEnabled = false, gameFolderPath = "D:/games").activeGameFolder())
    }

    @Test
    fun `the setting survives save and load`() {
        val launcherFolder = File(tmp, "launcher").apply { mkdirs() }

        saveAppSettings(launcherFolder, AppSettings(gameFolderEnabled = true, gameFolderPath = "D:/games"))
        val loaded = loadAppSettings(launcherFolder)

        assertTrue(loaded.gameFolderEnabled)
        assertEquals("D:/games", loaded.gameFolderPath)
    }

    @Test
    fun `a settings file from before this setting existed still loads, with it off`() {
        val launcherFolder = File(tmp, "launcher").apply { mkdirs() }
        File(launcherFolder, "app_settings.json").writeText("""{"gamepadEnabled":true}""")

        val loaded = loadAppSettings(launcherFolder)

        assertTrue(loaded.gamepadEnabled)
        assertFalse(loaded.gameFolderEnabled)
        assertNull(loaded.gameFolderPath)
    }

    // ---- scanGames ----

    @Test
    fun `without a game folder the install's own folders are scanned, as before`() {
        val install = makeInstall()
        addSingeGame(install, "alpha")

        assertEquals(listOf("alpha"), namesOf(scanGames(install)))
        assertEquals(listOf("alpha"), namesOf(scanGames(install, null)))
    }

    @Test
    fun `with a game folder only that folder's games are listed`() {
        val install = makeInstall()
        addSingeGame(install, "alpha")
        val games = File(tmp, "games").apply { mkdirs() }
        addSingeGame(games, "external")
        addDaphneGame(games, "daphnegame")

        assertEquals(listOf("daphnegame", "external"), namesOf(scanGames(install, games)))
    }

    @Test
    fun `the install must still contain hypseus exe when a game folder is used`() {
        val notAnInstall = File(tmp, "empty").apply { mkdirs() }
        val games = File(tmp, "games").apply { mkdirs() }
        addSingeGame(games, "external")

        assertTrue(scanGames(notAnInstall, games) is ScanResult.NotAHypseusInstall)
    }

    @Test
    fun `a game folder with none of the expected subfolders lists nothing`() {
        val install = makeInstall()
        val games = File(tmp, "games").apply { mkdirs() }

        assertEquals(emptyList(), namesOf(scanGames(install, games)))
    }

    @Test
    fun `a game folder that does not exist lists nothing and does not throw`() {
        val install = makeInstall()

        assertEquals(emptyList(), namesOf(scanGames(install, File(tmp, "not-there"))))
    }

    // ---- buildLaunchArgs ----

    private val game = Game("external", GameCategory.SINGE_ZIPPED, "f.txt", "z.zip")

    @Test
    fun `homedir and datadir are both the install by default`() {
        val install = makeInstall()

        val args = buildLaunchArgs(game, install)

        assertEquals("${install.path}/", args[args.indexOf("-homedir") + 1])
        assertEquals("${install.path}/", args[args.indexOf("-datadir") + 1])
    }

    @Test
    fun `with a game folder, homedir is the game folder and datadir stays the install`() {
        val install = makeInstall()
        val games = File(tmp, "games").apply { mkdirs() }

        val args = buildLaunchArgs(game, install, games)

        assertEquals("${games.path}/", args[args.indexOf("-homedir") + 1])
        assertEquals("${install.path}/", args[args.indexOf("-datadir") + 1])
    }

    // ---- #115: -ramdir ----

    @Test
    fun `no ramdir is passed when the game folder is off`() {
        val install = makeInstall()

        assertFalse(buildLaunchArgs(game, install).contains("-ramdir"))
    }

    @Test
    fun `with a game folder, ramdir is the ram folder inside it`() {
        val install = makeInstall()
        val games = File(tmp, "games").apply { mkdirs() }

        val args = buildLaunchArgs(game, install, games)

        assertEquals(ramDirFor(games), args[args.indexOf("-ramdir") + 1])
        assertEquals(games.path.replace('\\', '/') + "/ram", ramDirFor(games))
    }

    @Test
    fun `ramdir uses forward slashes and no trailing slash`() {
        assertEquals("D:/my games/roms/ram", ramDirFor(File("D:\\my games\\roms")))
        assertEquals("D:/games/ram", ramDirFor(File("D:/games/")))
    }

    @Test
    fun `ramdir is passed for every kind of game, not only zipped Singe ones`() {
        val install = makeInstall()
        val games = File(tmp, "games").apply { mkdirs() }
        val daphne = Game("cobra", GameCategory.DAPHNE_NATIVE, "f.txt", "z.zip")
        val script = Game("plain", GameCategory.SINGE_SCRIPT, "f.txt", "s.singe")

        assertTrue(buildLaunchArgs(daphne, install, games).contains("-ramdir"))
        assertTrue(buildLaunchArgs(script, install, games).contains("-ramdir"))
    }

    // ---- exportBatFiles ----

    @Test
    fun `exported bat files point at the game folder when it is on`() {
        val install = makeInstall()
        val launcherFolder = File(tmp, "launcher").apply { mkdirs() }
        val games = File(tmp, "games").apply { mkdirs() }
        addSingeGame(games, "external")
        saveAppSettings(launcherFolder, AppSettings(gameFolderEnabled = true, gameFolderPath = games.path))
        val scanned = (scanGames(install, games) as ScanResult.Found).games

        val count = exportBatFiles(install, scanned, launcherFolder)

        assertEquals(1, count)
        val bat = File(File(install, "batch"), "external.bat").readText()
        val sep = File.separator
        assertTrue(bat.contains("..${sep}hypseus.exe"), bat)
        assertTrue(bat.contains("singe${sep}external${sep}external.txt"), bat)
        assertTrue(bat.contains("-homedir"), bat)
        assertTrue(bat.contains(games.path), bat)
    }

    @Test
    fun `exported bat files are unchanged when the game folder is off`() {
        val install = makeInstall()
        val launcherFolder = File(tmp, "launcher").apply { mkdirs() }
        addSingeGame(install, "alpha")
        val scanned = (scanGames(install) as ScanResult.Found).games

        exportBatFiles(install, scanned, launcherFolder)

        val bat = File(File(install, "batch"), "alpha.bat").readText()
        assertFalse(bat.contains("-homedir"), bat)
        assertFalse(bat.contains("-ramdir"), bat)
        assertTrue(bat.contains("singe${File.separator}alpha${File.separator}alpha.txt"), bat)
    }

    @Test
    fun `exported bat files carry ramdir next to homedir when the game folder is on`() {
        val install = makeInstall()
        val launcherFolder = File(tmp, "launcher").apply { mkdirs() }
        val games = File(tmp, "my games").apply { mkdirs() }
        addSingeGame(games, "external")
        saveAppSettings(launcherFolder, AppSettings(gameFolderEnabled = true, gameFolderPath = games.path))
        val scanned = (scanGames(install, games) as ScanResult.Found).games

        exportBatFiles(install, scanned, launcherFolder)

        val bat = File(File(install, "batch"), "external.bat").readText()
        // the path has a space, so it must be quoted as one argument
        assertTrue(bat.contains("-ramdir \"${ramDirFor(games)}\""), bat)
    }

    // ---- splitArgumentTokens ----

    @Test
    fun `an entry with no quotes splits on whitespace exactly as before`() {
        assertEquals(listOf("-scalefactor", "50"), splitArgumentTokens("-scalefactor 50"))
        assertEquals(listOf("-a", "-b"), splitArgumentTokens("  -a    -b  "))
        assertEquals(emptyList(), splitArgumentTokens("   "))
    }

    @Test
    fun `whitespace inside quotes does not split, and the quotes are dropped`() {
        assertEquals(
            listOf("-bezeldir", "D:\\My Games\\bezels"),
            splitArgumentTokens("-bezeldir \"D:\\My Games\\bezels\""),
        )
    }

    // ---- bezels ----

    private fun addBezel(dir: File, gameName: String) {
        File(dir, "bezels").mkdirs()
        File(File(dir, "bezels"), "$gameName.png").writeText("")
    }

    @Test
    fun `no bezel anywhere means no bezel arguments`() {
        val install = makeInstall()
        val games = File(tmp, "games").apply { mkdirs() }

        assertEquals(emptyList(), bezelLaunchArgs(install, "alpha"))
        assertEquals(emptyList(), bezelLaunchArgs(install, "alpha", games))
    }

    @Test
    fun `an install bezel is used as before, with no bezeldir`() {
        val install = makeInstall()
        addBezel(install, "alpha")

        assertEquals(listOf("-bezel", "alpha.png"), bezelLaunchArgs(install, "alpha"))
    }

    @Test
    fun `a game folder bezel is found through bezeldir`() {
        val install = makeInstall()
        val games = File(tmp, "games").apply { mkdirs() }
        addBezel(games, "alpha")

        assertEquals(
            listOf("-bezel", "alpha.png", "-bezeldir", File(games, "bezels").path),
            bezelLaunchArgs(install, "alpha", games),
        )
    }

    @Test
    fun `a game folder path with spaces is quoted so it stays one argument`() {
        val install = makeInstall()
        val games = File(tmp, "my games").apply { mkdirs() }
        addBezel(games, "alpha")

        val args = bezelLaunchArgs(install, "alpha", games)

        assertEquals("\"${File(games, "bezels").path}\"", args[3])
        // and through the launcher's own splitting it is still one token, unquoted
        assertEquals(
            listOf("-bezel", "alpha.png", "-bezeldir", File(games, "bezels").path),
            args.flatMap { splitArgumentTokens(it) },
        )
    }

    @Test
    fun `the game folder's bezel wins over the install's`() {
        val install = makeInstall()
        val games = File(tmp, "games").apply { mkdirs() }
        addBezel(install, "alpha")
        addBezel(games, "alpha")

        assertTrue(bezelLaunchArgs(install, "alpha", games).contains("-bezeldir"))
    }

    @Test
    fun `a game with its bezel only in the install still finds it while a game folder is on`() {
        val install = makeInstall()
        val games = File(tmp, "games").apply { mkdirs() }
        addBezel(install, "alpha")

        assertEquals(listOf("-bezel", "alpha.png"), bezelLaunchArgs(install, "alpha", games))
    }

    @Test
    fun `bezel arguments only appear when the game's bezel option is on`() {
        val install = makeInstall()
        val games = File(tmp, "games").apply { mkdirs() }
        addBezel(games, "alpha")

        val off = launchArgumentsFor(install, GameOptions(bezelEnabled = false), "alpha", gameFolder = games)
        val on = launchArgumentsFor(install, GameOptions(bezelEnabled = true), "alpha", gameFolder = games)

        assertFalse(off.contains("-bezel"))
        assertTrue(on.contains("-bezel") && on.contains("-bezeldir"))
    }

    @Test
    fun `exported bat files carry the game folder bezel with its path quoted`() {
        val install = makeInstall()
        val launcherFolder = File(tmp, "launcher").apply { mkdirs() }
        val games = File(tmp, "my games").apply { mkdirs() }
        addSingeGame(games, "external")
        addBezel(games, "external")
        saveAppSettings(launcherFolder, AppSettings(gameFolderEnabled = true, gameFolderPath = games.path))
        saveOptions(launcherFolder, "external", GameOptions(bezelEnabled = true))
        val scanned = (scanGames(install, games) as ScanResult.Found).games

        exportBatFiles(install, scanned, launcherFolder)

        val bat = File(File(install, "batch"), "external.bat").readText()
        assertTrue(bat.contains("-bezel external.png"), bat)
        assertTrue(bat.contains("-bezeldir \"${File(games, "bezels").path}\""), bat)
    }
}
