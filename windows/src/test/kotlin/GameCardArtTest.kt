import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir

/**
 * #28 - locks in the precedence rule an earlier draft of this story had
 * backwards: Global Cover Art wins over a game's own per-game override
 * while it's on, confirmed against the real Android source.
 */
class GameCardArtTest {

    @TempDir
    lateinit var mediaFolder: File

    private fun art(subfolder: String, fileName: String) {
        val dir = File(mediaFolder, subfolder).apply { mkdirs() }
        File(dir, "$fileName.png").writeText("fixture")
    }

    @Test
    fun `Global Cover Art off uses the game's own override`() {
        art("logo", "dragons_lair")
        art("box", "dragons_lair")

        val resolved = effectiveCoverArtFile(
            mediaFolder,
            "dragons_lair",
            AppSettings(globalCoverArtEnabled = false, globalCoverArtType = CoverArtType.BOX),
            GameOptions(coverArtOverride = CoverArtType.LOGO),
        )

        assertEquals(File(mediaFolder, "logo/dragons_lair.png"), resolved)
    }

    @Test
    fun `Global Cover Art on overrides the game's own choice`() {
        art("logo", "dragons_lair")
        art("cd", "dragons_lair")

        val resolved = effectiveCoverArtFile(
            mediaFolder,
            "dragons_lair",
            AppSettings(globalCoverArtEnabled = true, globalCoverArtType = CoverArtType.CD),
            GameOptions(coverArtOverride = CoverArtType.LOGO),
        )

        assertEquals(File(mediaFolder, "cd/dragons_lair.png"), resolved)
    }

    @Test
    fun `Global Cover Art on applies even to a game with no saved options at all`() {
        art("cd", "never_configured")

        val resolved = effectiveCoverArtFile(
            mediaFolder,
            "never_configured",
            AppSettings(globalCoverArtEnabled = true, globalCoverArtType = CoverArtType.CD),
            gameOptions = null,
        )

        assertEquals(File(mediaFolder, "cd/never_configured.png"), resolved)
    }

    @Test
    fun `no override anywhere resolves to the plain BOX default`() {
        art("box", "dragons_lair")

        val resolved = effectiveCoverArtFile(
            mediaFolder,
            "dragons_lair",
            AppSettings(),
            gameOptions = null,
        )

        assertEquals(File(mediaFolder, "box/dragons_lair.png"), resolved)
    }

    @Test
    fun `a missing file resolves to null even when an override points at it`() {
        val resolved = effectiveCoverArtFile(
            mediaFolder,
            "no_art_for_this_game",
            AppSettings(globalCoverArtEnabled = true, globalCoverArtType = CoverArtType.LOGO),
            gameOptions = null,
        )

        assertNull(resolved)
    }
}
