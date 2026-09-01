import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SdlKeyMappingTest {

    @Test
    fun `maps every real token seen in the actual install file`() {
        // Every SDLK_ token that appears in the real hypinput_gamepad.ini
        // this project tests against (see GamepadIniTest's REAL_INI) -
        // if any of these regressed, real capture would silently produce
        // the wrong token for a key genuinely in use.
        assertEquals("SDLK_UP", sdlkTokenFor(Key.DirectionUp))
        assertEquals("SDLK_DOWN", sdlkTokenFor(Key.DirectionDown))
        assertEquals("SDLK_LEFT", sdlkTokenFor(Key.DirectionLeft))
        assertEquals("SDLK_RIGHT", sdlkTokenFor(Key.DirectionRight))
        assertEquals("SDLK_5", sdlkTokenFor(Key.Five))
        assertEquals("SDLK_6", sdlkTokenFor(Key.Six))
        assertEquals("SDLK_1", sdlkTokenFor(Key.One))
        assertEquals("SDLK_2", sdlkTokenFor(Key.Two))
        assertEquals("SDLK_LCTRL", sdlkTokenFor(Key.CtrlLeft))
        assertEquals("SDLK_LALT", sdlkTokenFor(Key.AltLeft))
        assertEquals("SDLK_SPACE", sdlkTokenFor(Key.Spacebar))
        assertEquals("SDLK_LSHIFT", sdlkTokenFor(Key.ShiftLeft))
        assertEquals("SDLK_Z", sdlkTokenFor(Key.Z))
        assertEquals("SDLK_X", sdlkTokenFor(Key.X))
        assertEquals("SDLK_9", sdlkTokenFor(Key.Nine))
        assertEquals("SDLK_F2", sdlkTokenFor(Key.F2))
        assertEquals("SDLK_F4", sdlkTokenFor(Key.F4))
        assertEquals("SDLK_0", sdlkTokenFor(Key.Zero))
        assertEquals("SDLK_F12", sdlkTokenFor(Key.F12))
        assertEquals("SDLK_ESCAPE", sdlkTokenFor(Key.Escape))
        assertEquals("SDLK_P", sdlkTokenFor(Key.P))
        assertEquals("SDLK_BACKSLASH", sdlkTokenFor(Key.Backslash))
        assertEquals("SDLK_T", sdlkTokenFor(Key.T))
    }

    @Test
    fun `an unmapped key returns null rather than a guessed token`() {
        assertNull(sdlkTokenFor(Key.VolumeUp))
    }
}
