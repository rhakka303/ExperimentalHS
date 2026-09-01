import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.io.TempDir

class AppSettingsTest {

    @TempDir
    lateinit var launcherFolder: File

    @Test
    fun `defaults are all off, with BOX as the default cover art type`() {
        val settings = loadAppSettings(launcherFolder)

        assertFalse(settings.globalCoverArtEnabled)
        assertEquals(CoverArtType.BOX, settings.globalCoverArtType)
        assertFalse(settings.backgroundArtEnabled)
        assertFalse(settings.defaultArtEnabled)
        assertFalse(settings.preserveAspectRatioEnabled)
    }

    @Test
    fun `settings round-trip through save and load`() {
        val saved = AppSettings(
            globalCoverArtEnabled = true,
            globalCoverArtType = CoverArtType.LOGO,
            backgroundArtEnabled = true,
            defaultArtEnabled = true,
            preserveAspectRatioEnabled = true,
        )

        saveAppSettings(launcherFolder, saved)

        assertEquals(saved, loadAppSettings(launcherFolder))
    }

    @Test
    fun `a malformed app_settings json falls back to defaults instead of crashing`() {
        File(launcherFolder, "app_settings.json").writeText("not valid json")

        val settings = loadAppSettings(launcherFolder)

        assertEquals(AppSettings(), settings)
    }
}
