import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * #41 - resolveMediaFolder() didn't have tests under #26 (it predates the
 * test framework, added in #27); adding them now that it gained real
 * behavior worth locking in (auto-create, and not clobbering existing art).
 */
class MediaFolderTest {

    @TempDir
    lateinit var launcherFolder: File

    @Test
    fun `resolves media inside the launcher folder`() {
        val resolved = resolveMediaFolder(launcherFolder)

        assertEquals(File(launcherFolder, "media"), resolved)
    }

    @Test
    fun `auto-creates box, cd, logo and bg, empty, if they don't exist`() {
        val mediaFolder = resolveMediaFolder(launcherFolder)

        for (subfolder in listOf("box", "cd", "logo", "bg")) {
            val dir = File(mediaFolder, subfolder)
            assertTrue(dir.isDirectory, "$subfolder should exist")
            assertEquals(0, dir.listFiles()?.size, "$subfolder should be empty")
        }
    }

    @Test
    fun `does not touch an already-populated subfolder`() {
        // Resolve once (creates the structure), drop in real art, then
        // resolve again - the second call must not clobber it.
        val mediaFolder = resolveMediaFolder(launcherFolder)
        File(mediaFolder, "box/dragons_lair.png").writeText("fixture")

        resolveMediaFolder(launcherFolder)

        assertTrue(File(mediaFolder, "box/dragons_lair.png").isFile)
    }
}
