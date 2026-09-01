import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir

/**
 * #27 - real fixture files on a real temp filesystem, not mocks: these
 * functions are just java.io.File existence checks, so a fake File
 * abstraction would only test the fake, not the actual logic.
 */
class CoverArtTest {

    @TempDir
    lateinit var mediaFolder: File

    private fun art(subfolder: String, fileName: String) {
        val dir = File(mediaFolder, subfolder).apply { mkdirs() }
        File(dir, "$fileName.png").writeText("fixture")
    }

    // --- coverArtFile ---

    @Test
    fun `coverArtFile returns the file when it exists, for CD, LOGO and BOX`() {
        art("cd", "dragons_lair")
        art("logo", "dragons_lair")
        art("box", "dragons_lair")

        assertEquals(File(mediaFolder, "cd/dragons_lair.png"), coverArtFile(mediaFolder, "dragons_lair", CoverArtType.CD))
        assertEquals(File(mediaFolder, "logo/dragons_lair.png"), coverArtFile(mediaFolder, "dragons_lair", CoverArtType.LOGO))
        assertEquals(File(mediaFolder, "box/dragons_lair.png"), coverArtFile(mediaFolder, "dragons_lair", CoverArtType.BOX))
    }

    @Test
    fun `coverArtFile returns null when the file is missing, for CD, LOGO and BOX`() {
        assertNull(coverArtFile(mediaFolder, "no_such_game", CoverArtType.CD))
        assertNull(coverArtFile(mediaFolder, "no_such_game", CoverArtType.LOGO))
        assertNull(coverArtFile(mediaFolder, "no_such_game", CoverArtType.BOX))
    }

    @Test
    fun `coverArtFile TEXT always resolves to null, even if a matching file exists`() {
        // A folder literally named "text" with a matching file must not
        // fool this into returning it - TEXT has no file lookup at all.
        art("text", "dragons_lair")

        assertNull(coverArtFile(mediaFolder, "dragons_lair", CoverArtType.TEXT))
    }

    // --- resolveCoverArtFile ---

    @Test
    fun `resolveCoverArtFile with no override resolves to BOX`() {
        art("box", "dragons_lair")
        art("cd", "dragons_lair")

        val resolved = resolveCoverArtFile(mediaFolder, "dragons_lair", override = null)

        assertEquals(File(mediaFolder, "box/dragons_lair.png"), resolved)
    }

    @Test
    fun `resolveCoverArtFile with an explicit override uses it instead of BOX`() {
        art("box", "dragons_lair")
        art("logo", "dragons_lair")

        val resolved = resolveCoverArtFile(mediaFolder, "dragons_lair", override = CoverArtType.LOGO)

        assertEquals(File(mediaFolder, "logo/dragons_lair.png"), resolved)
    }

    // --- backgroundArtFile ---

    @Test
    fun `backgroundArtFile is null when Background Art is disabled, regardless of Default Art or existing files`() {
        art("bg", "dragons_lair")
        art("bg", "default")

        assertNull(backgroundArtFile(mediaFolder, "dragons_lair", backgroundArtEnabled = false, defaultArtEnabled = false))
        assertNull(backgroundArtFile(mediaFolder, "dragons_lair", backgroundArtEnabled = false, defaultArtEnabled = true))
    }

    @Test
    fun `backgroundArtFile with Default Art on always uses default png, even when a per-game file also exists`() {
        art("bg", "dragons_lair")
        art("bg", "default")

        val resolved = backgroundArtFile(mediaFolder, "dragons_lair", backgroundArtEnabled = true, defaultArtEnabled = true)

        assertEquals(File(mediaFolder, "bg/default.png"), resolved)
    }

    @Test
    fun `backgroundArtFile with Default Art off uses the per-game file when it exists`() {
        art("bg", "dragons_lair")

        val resolved = backgroundArtFile(mediaFolder, "dragons_lair", backgroundArtEnabled = true, defaultArtEnabled = false)

        assertEquals(File(mediaFolder, "bg/dragons_lair.png"), resolved)
    }

    @Test
    fun `backgroundArtFile with Default Art off does NOT fall back to default png when the per-game file is missing`() {
        // The corrected behavior this story exists to lock in: Default
        // Art is an override, not a missing-file fallback. default.png
        // exists here on purpose - it must still be ignored.
        art("bg", "default")

        val resolved = backgroundArtFile(mediaFolder, "no_bg_for_this_game", backgroundArtEnabled = true, defaultArtEnabled = false)

        assertNull(resolved)
    }
}
