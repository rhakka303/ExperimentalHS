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
        assertFalse(settings.fullscreenEnabled)
        assertFalse(settings.gamepadEnabled)
    }

    @Test
    fun `an app_settings file written before fullscreenEnabled existed still loads, defaulting to false`() {
        // #43 - a real pre-#43 app_settings.json: fullscreenEnabled is
        // simply absent from the JSON, not present-and-false.
        File(launcherFolder, "app_settings.json").writeText(
            """{"globalCoverArtEnabled":true,"globalCoverArtType":"LOGO","backgroundArtEnabled":false,"defaultArtEnabled":false,"preserveAspectRatioEnabled":true}""",
        )

        val settings = loadAppSettings(launcherFolder)

        assertFalse(settings.fullscreenEnabled)
        assertEquals(CoverArtType.LOGO, settings.globalCoverArtType)
        assertEquals(true, settings.preserveAspectRatioEnabled)
    }

    @Test
    fun `settings round-trip through save and load`() {
        val saved = AppSettings(
            globalCoverArtEnabled = true,
            globalCoverArtType = CoverArtType.LOGO,
            backgroundArtEnabled = true,
            defaultArtEnabled = true,
            preserveAspectRatioEnabled = true,
            fullscreenEnabled = true,
            gamepadEnabled = true,
        )

        saveAppSettings(launcherFolder, saved)

        assertEquals(saved, loadAppSettings(launcherFolder))
    }

    @Test
    fun `an app_settings file written before gamepadEnabled existed still loads, defaulting to false`() {
        // #46 - a real pre-#46 app_settings.json: gamepadEnabled is
        // simply absent from the JSON, not present-and-false.
        File(launcherFolder, "app_settings.json").writeText(
            """{"globalCoverArtEnabled":false,"globalCoverArtType":"BOX","backgroundArtEnabled":false,"defaultArtEnabled":false,"preserveAspectRatioEnabled":false,"fullscreenEnabled":true}""",
        )

        val settings = loadAppSettings(launcherFolder)

        assertFalse(settings.gamepadEnabled)
        assertEquals(true, settings.fullscreenEnabled)
    }

    @Test
    fun `a malformed app_settings json falls back to defaults instead of crashing`() {
        File(launcherFolder, "app_settings.json").writeText("not valid json")

        val settings = loadAppSettings(launcherFolder)

        assertEquals(AppSettings(), settings)
    }
}
