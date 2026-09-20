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
 * #111 - multi-game packs: one folder under singe/ holds a single shared
 * Lua zip and several framefiles, each of which is its own game launched
 * with -usealt. Pure file layout and argument logic; no real hypseus.
 */
class PackGamesTest {

    @TempDir
    lateinit var tmp: File

    private class FakeProcess(private val code: Int) : Process() {
        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
        override fun getInputStream(): InputStream = InputStream.nullInputStream()
        override fun getErrorStream(): InputStream = InputStream.nullInputStream()
        override fun waitFor(): Int = code
        override fun exitValue(): Int = code
        override fun destroy() {}
    }

    private fun makeInstall(): File =
        File(tmp, "install").apply { mkdirs() }.also { File(it, "hypseus.exe").writeText("") }

    private fun frameText(video: String) = ".\n\n1       $video.m2v\n355     sub/$video.m2v\n"

    private fun addPack(root: File, pack: String, vararg games: String, readme: Boolean = false, zip: Boolean = true) {
        val dir = File(File(root, "singe"), pack).apply { mkdirs() }
        if (zip) File(dir, "$pack.zip").writeText("")
        for (g in games) File(dir, "$g.txt").writeText(frameText(g))
        if (readme) File(dir, "_README.txt").writeText("-- some notes\n-- written by someone\n")
    }

    private fun addNormalGame(root: File, name: String) {
        val dir = File(File(root, "singe"), name).apply { mkdirs() }
        File(dir, "$name.txt").writeText(frameText(name))
        File(dir, "$name.zip").writeText("")
    }

    private fun games(root: File, gameFolder: File? = null): List<Game> =
        (scanGames(root, gameFolder) as ScanResult.Found).games

    // ---- scanner ----

    @Test
    fun `each framefile in a pack is its own game, with the shared zip and its own alt script`() {
        val install = makeInstall()
        addPack(install, "megapack", "gamea", "gameb", "gamec")

        val found = games(install)

        assertEquals(listOf("gamea", "gameb", "gamec"), found.map { it.name })
        val a = found.first { it.name == "gamea" }
        assertEquals(GameCategory.SINGE_ZIPPED, a.category)
        assertEquals(File(File(File(install, "singe"), "megapack"), "gamea.txt").path, a.framefilePath)
        assertEquals(File(File(File(install, "singe"), "megapack"), "megapack.zip").path, a.romOrScriptPath)
        assertEquals("gamea", a.altScript)
    }

    @Test
    fun `a readme or notes txt in a pack folder is not a game`() {
        val install = makeInstall()
        addPack(install, "megapack", "gamea", readme = true)

        assertEquals(listOf("gamea"), games(install).map { it.name })
    }

    @Test
    fun `an ordinary game is listed exactly as before, with no alt script`() {
        val install = makeInstall()
        addNormalGame(install, "alpha")

        val found = games(install)

        assertEquals(listOf("alpha"), found.map { it.name })
        assertNull(found[0].altScript)
    }

    @Test
    fun `packs and ordinary games sort together by name`() {
        val install = makeInstall()
        addNormalGame(install, "beta")
        addPack(install, "megapack", "alpha", "gamma")

        assertEquals(listOf("alpha", "beta", "gamma"), games(install).map { it.name })
    }

    @Test
    fun `an ordinary game wins a name clash with a pack game, ignoring case`() {
        val install = makeInstall()
        addNormalGame(install, "alpha")
        addPack(install, "megapack", "Alpha", "other")

        val found = games(install)

        assertEquals(listOf("alpha", "other"), found.map { it.name })
        assertNull(found.first { it.name == "alpha" }.altScript)
    }

    @Test
    fun `a Daphne game also wins a name clash with a pack game`() {
        val install = makeInstall()
        File(File(File(install, "vldp"), "cobra").apply { mkdirs() }, "cobra.txt").writeText(frameText("cobra"))
        File(File(install, "roms").apply { mkdirs() }, "cobra.zip").writeText("")
        addPack(install, "megapack", "cobra", "other")

        val found = games(install)

        assertEquals(listOf("cobra", "other"), found.map { it.name })
        assertEquals(GameCategory.DAPHNE_NATIVE, found.first { it.name == "cobra" }.category)
    }

    @Test
    fun `a folder with a zip but no framefiles lists nothing`() {
        val install = makeInstall()
        addPack(install, "megapack", readme = true)

        assertEquals(emptyList(), games(install))
    }

    @Test
    fun `a folder with no zip is not a pack, so a shared library folder stays invisible`() {
        val install = makeInstall()
        addPack(install, "Framework", "helper", zip = false)

        assertEquals(emptyList(), games(install))
    }

    @Test
    fun `a folder with its own framefile is one ordinary game even if it holds other framefiles`() {
        val install = makeInstall()
        addNormalGame(install, "alpha")
        File(File(File(install, "singe"), "alpha"), "beta.txt").writeText(frameText("beta"))

        assertEquals(listOf("alpha"), games(install).map { it.name })
    }

    @Test
    fun `a txt is a framefile only if it has a frame number and video line`() {
        val install = makeInstall()
        addPack(install, "megapack", "real")
        File(File(File(install, "singe"), "megapack"), "prose.txt").writeText("1 is the loneliest number\nsee also 2 items\n")

        assertEquals(listOf("real"), games(install).map { it.name })
    }

    @Test
    fun `pack games are found in the chosen game folder too`() {
        val install = makeInstall()
        val gameFolder = File(tmp, "games").apply { mkdirs() }
        addPack(gameFolder, "megapack", "gamea")

        assertEquals(emptyList(), games(install))
        assertEquals(listOf("gamea"), games(install, gameFolder).map { it.name })
    }

    // ---- #118: names hypseus would reject as a -usealt value ----

    // A valid framefile with a fixed video name, so the only thing that can keep
    // it out of the list is the file's own name.
    private fun addPackFile(root: File, pack: String, fileName: String) {
        File(File(File(root, "singe"), pack), fileName).writeText(frameText("video"))
    }

    @Test
    fun `a pack framefile with a space in its name is not listed, its neighbours are`() {
        val install = makeInstall()
        addPack(install, "megapack", "gamea", "gameb")
        addPackFile(install, "megapack", "has space.txt")

        assertEquals(listOf("gamea", "gameb"), games(install).map { it.name })
    }

    @Test
    fun `a pack framefile with parentheses in its name is not listed`() {
        val install = makeInstall()
        addPack(install, "megapack", "gamea")
        addPackFile(install, "megapack", "game(1).txt")

        assertEquals(listOf("gamea"), games(install).map { it.name })
    }

    @Test
    fun `a pack framefile with a non-ASCII letter in its name is not listed`() {
        val install = makeInstall()
        addPack(install, "megapack", "gamea")
        addPackFile(install, "megapack", "café.txt")

        assertEquals(listOf("gamea"), games(install).map { it.name })
    }

    @Test
    fun `a pack framefile named with only letters, digits, underscore, hyphen and dot is listed`() {
        val install = makeInstall()
        addPack(install, "megapack", "gamea")
        addPackFile(install, "megapack", "Game_B-2.v1.txt")
        addPackFile(install, "megapack", "38ambush.txt")

        assertEquals(listOf("38ambush", "Game_B-2.v1", "gamea"), games(install).map { it.name })
    }

    @Test
    fun `an ordinary game with a space in its name is still listed and launched with no usealt`() {
        val install = makeInstall()
        addNormalGame(install, "my game")

        val game = games(install).single()

        assertEquals("my game", game.name)
        assertNull(game.altScript)
        assertFalse(buildLaunchArgs(game, install).contains("-usealt"))
    }

    // ---- launch arguments ----

    @Test
    fun `a pack game is launched with the shared zip and usealt naming its script`() {
        val install = makeInstall()
        addPack(install, "megapack", "gamea")
        val game = games(install).single()

        val args = buildLaunchArgs(game, install)

        assertEquals("-usealt", args[args.indexOf("-zlua") + 2])
        assertEquals("gamea", args[args.indexOf("-zlua") + 3])
        assertEquals(game.romOrScriptPath, args[args.indexOf("-zlua") + 1])
        assertEquals(game.framefilePath, args[args.indexOf("-framefile") + 1])
    }

    @Test
    fun `an ordinary zipped game gets no usealt`() {
        val install = makeInstall()
        addNormalGame(install, "alpha")

        assertFalse(buildLaunchArgs(games(install).single(), install).contains("-usealt"))
    }

    // ---- headless ----

    @Test
    fun `--game finds and launches a pack game`() {
        val install = makeInstall()
        addPack(install, "megapack", "gamea", "gameb")
        var launched: Game? = null

        val code = runHeadless("gameb", install, null) { g, _, _, _ ->
            launched = g
            LaunchResult.Started(FakeProcess(0))
        }

        assertEquals(0, code)
        assertEquals("gameb", launched?.name)
        assertEquals("gameb", launched?.altScript)
    }

    // ---- .bat export ----

    @Test
    fun `an exported bat file for a pack game includes usealt and relative paths`() {
        val install = makeInstall()
        addPack(install, "megapack", "gamea")

        exportBatFiles(install, games(install), null)

        val bat = File(File(install, "batch"), "gamea.bat").readText()
        val sep = File.separator
        assertTrue(bat.contains("-usealt gamea"), bat)
        assertTrue(bat.contains("singe${sep}megapack${sep}gamea.txt"), bat)
        assertTrue(bat.contains("singe${sep}megapack${sep}megapack.zip"), bat)
    }
}
