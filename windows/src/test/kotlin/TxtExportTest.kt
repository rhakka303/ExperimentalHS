import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * #110 - the Create TXT File card's logic: one placeholder .txt per game in
 * the game folder root, never overwriting, and deleting only stale files
 * that are provably its own. Pure file I/O, no UI.
 */
class TxtExportTest {

    @TempDir
    lateinit var folder: File

    private fun game(name: String) = Game(name, GameCategory.SINGE_ZIPPED, "", "")

    private fun txt(name: String) = File(folder, "$name.txt")

    @Test
    fun `writes one txt per game containing only the game name`() {
        val result = exportTxtFiles(folder, listOf(game("alpha"), game("beta")))

        assertEquals(TxtExportResult(created = 2, skipped = 0, removed = 0), result)
        assertEquals("alpha", txt("alpha").readText())
        assertEquals("beta", txt("beta").readText())
    }

    @Test
    fun `an existing txt is skipped and left exactly as it was`() {
        txt("alpha").writeText("some real framefile\nwith lines\n")
        txt("alpha").setLastModified(1_000_000_000_000L)

        val result = exportTxtFiles(folder, listOf(game("alpha")))

        assertEquals(TxtExportResult(created = 0, skipped = 1, removed = 0), result)
        assertEquals("some real framefile\nwith lines\n", txt("alpha").readText())
        assertEquals(1_000_000_000_000L, txt("alpha").lastModified())
    }

    @Test
    fun `running again after a game is added creates only the new file`() {
        exportTxtFiles(folder, listOf(game("alpha")))

        val result = exportTxtFiles(folder, listOf(game("alpha"), game("beta")))

        assertEquals(TxtExportResult(created = 1, skipped = 1, removed = 0), result)
        assertTrue(txt("beta").isFile)
    }

    @Test
    fun `a stale file this feature wrote is deleted once its game is gone`() {
        exportTxtFiles(folder, listOf(game("alpha"), game("beta")))

        val result = exportTxtFiles(folder, listOf(game("alpha")))

        assertEquals(TxtExportResult(created = 0, skipped = 1, removed = 1), result)
        assertTrue(txt("alpha").isFile)
        assertFalse(txt("beta").exists())
    }

    @Test
    fun `surrounding whitespace in our own file does not stop it being recognized`() {
        txt("gone").writeText("  gone\r\n")

        val result = exportTxtFiles(folder, listOf(game("alpha")))

        assertEquals(1, result.removed)
        assertFalse(txt("gone").exists())
    }

    @Test
    fun `a txt whose content is not exactly its own name is never deleted, even with no matching game`() {
        txt("notes").writeText("remember to back this up")
        txt("framefile").writeText("0 1 2 3 4\n5 6 7 8 9\n")
        txt("other").writeText("some other game name")

        val result = exportTxtFiles(folder, listOf(game("alpha")))

        assertEquals(0, result.removed)
        assertTrue(txt("notes").isFile)
        assertTrue(txt("framefile").isFile)
        assertTrue(txt("other").isFile)
    }

    @Test
    fun `an empty txt is not treated as ours`() {
        txt("empty").writeText("")

        exportTxtFiles(folder, listOf(game("alpha")))

        assertTrue(txt("empty").isFile)
    }

    @Test
    fun `a large txt is left alone`() {
        txt("big").writeText("big".padEnd(5000, 'x'))

        val result = exportTxtFiles(folder, listOf(game("alpha")))

        assertEquals(0, result.removed)
        assertTrue(txt("big").isFile)
    }

    @Test
    fun `a file for a game that still exists is not deleted`() {
        txt("alpha").writeText("alpha")

        val result = exportTxtFiles(folder, listOf(game("alpha")))

        assertEquals(0, result.removed)
        assertTrue(txt("alpha").isFile)
    }

    @Test
    fun `a name differing only by case still counts as the same game`() {
        txt("Alpha").writeText("Alpha")

        val result = exportTxtFiles(folder, listOf(game("alpha")))

        assertEquals(0, result.removed)
    }

    @Test
    fun `only the root is looked at, subfolders are untouched`() {
        val sub = File(File(folder, "singe"), "alpha").apply { mkdirs() }
        File(sub, "alpha.txt").writeText("real framefile")
        File(sub, "stale.txt").writeText("stale")

        val result = exportTxtFiles(folder, listOf(game("alpha")))

        assertEquals(TxtExportResult(created = 1, skipped = 0, removed = 0), result)
        assertEquals("real framefile", File(sub, "alpha.txt").readText())
        assertTrue(File(sub, "stale.txt").isFile)
    }

    @Test
    fun `only txt files are considered for deletion`() {
        File(folder, "stale.log").writeText("stale")

        exportTxtFiles(folder, listOf(game("alpha")))

        assertTrue(File(folder, "stale.log").isFile)
    }

    @Test
    fun `a folder that does not exist does nothing and does not throw`() {
        val result = exportTxtFiles(File(folder, "not-there"), listOf(game("alpha")))

        assertEquals(TxtExportResult(0, 0, 0), result)
        assertFalse(File(folder, "not-there").exists())
    }
}
