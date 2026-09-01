import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir

/**
 * #30 - coverArtOverride is a new field on an existing, already-shipped
 * storage format. The real risk isn't the happy path, it's an existing
 * options.json file saved before this field existed - it must still load
 * without crashing (loadAllOptions already never throws, per #18) and the
 * new field must come back null rather than causing a parse failure.
 */
class GameOptionsTest {

    @TempDir
    lateinit var launcherFolder: File

    @Test
    fun `coverArtOverride round-trips through save and load`() {
        saveOptions(launcherFolder, "dragons_lair", GameOptions(coverArtOverride = CoverArtType.LOGO))

        val loaded = loadOptions(launcherFolder, "dragons_lair")

        assertEquals(CoverArtType.LOGO, loaded.coverArtOverride)
    }

    @Test
    fun `a game with no saved options has no cover art override`() {
        val loaded = loadOptions(launcherFolder, "never_saved")

        assertNull(loaded.coverArtOverride)
    }

    @Test
    fun `an options file written before coverArtOverride existed still loads, defaulting to null`() {
        // A real pre-#30 options.json: every field #30 added is simply
        // absent from the JSON, not present-and-null.
        File(launcherFolder, "options.json").writeText(
            """{"dragons_lair":{"arguments":["-fastboot"],"bezelEnabled":true,"scorebezelAutofit":false,"overlayBezel":false,"aspectBezelFix":false}}""",
        )

        val loaded = loadOptions(launcherFolder, "dragons_lair")

        assertNull(loaded.coverArtOverride)
        assertEquals(listOf("-fastboot"), loaded.arguments)
        assertEquals(true, loaded.bezelEnabled)
    }

    // --- launchArgumentsFor / #32 ---

    @Test
    fun `preserveAspectRatioEnabled defaults to off and adds no argument`() {
        val args = launchArgumentsFor(launcherFolder, GameOptions(), "dragons_lair")

        assertEquals(emptyList(), args)
    }

    @Test
    fun `preserveAspectRatioEnabled appends -preserve_aspect_ratio after aspectBezelFix and before custom arguments`() {
        val options = GameOptions(
            aspectBezelFix = true,
            arguments = listOf("-fastboot"),
        )

        val args = launchArgumentsFor(launcherFolder, options, "dragons_lair", preserveAspectRatioEnabled = true)

        assertEquals(listOf("-aspectbezelfix", "-preserve_aspect_ratio", "-fastboot"), args)
    }
}
