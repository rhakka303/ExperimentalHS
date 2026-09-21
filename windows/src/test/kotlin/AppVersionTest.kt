import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #126 - the About screen's version text. Pure formatting plus the one real
 * system property read; the screen itself and the packaged app's value are
 * checked by hand on the built app.
 */
class AppVersionTest {

    @Test
    fun `a version is shown as it is`() {
        assertEquals("Version 1.0.0", appVersionText("1.0.0"))
        assertEquals("Version 2.3.4", appVersionText("2.3.4"))
    }

    @Test
    fun `padding around the version is trimmed`() {
        assertEquals("Version 1.0.0", appVersionText("  1.0.0 \n"))
    }

    @Test
    fun `no version means a development build, never a made-up number`() {
        assertEquals("Development build", appVersionText(null))
    }

    @Test
    fun `an empty or blank version counts as not there`() {
        assertEquals("Development build", appVersionText(""))
        assertEquals("Development build", appVersionText("   "))
    }

    @Test
    fun `the default reads the jpackage app-version setting`() {
        val before = System.getProperty(APP_VERSION_PROPERTY)
        try {
            System.setProperty(APP_VERSION_PROPERTY, "9.8.7")
            assertEquals("Version 9.8.7", appVersionText())

            System.clearProperty(APP_VERSION_PROPERTY)
            assertEquals("Development build", appVersionText())
        } finally {
            if (before == null) System.clearProperty(APP_VERSION_PROPERTY) else System.setProperty(APP_VERSION_PROPERTY, before)
        }
    }

    @Test
    fun `the property name is the one jpackage writes`() {
        assertEquals("jpackage.app-version", APP_VERSION_PROPERTY)
    }
}
