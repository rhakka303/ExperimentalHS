import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #46 - the exact real hypinput_gamepad.ini content from this project's
 * own install root (confirmed via a real screenshot of the file), not a
 * synthetic fixture. KEY_COIN2/KEY_START2's real Pad1-only bindings are
 * the specific case #48/#49 exist to get right, so they're included here
 * even though this story (#46) only reads key1.
 */
private val REAL_INI = """
    [KEYBOARD]
    KEY_UP = SDLK_UP 0 BUTTON_DPAD_UP AXIS_LEFT_UP 0 0
    KEY_DOWN = SDLK_DOWN 0 BUTTON_DPAD_DOWN AXIS_LEFT_DOWN 0 0
    KEY_LEFT = SDLK_LEFT 0 BUTTON_DPAD_LEFT AXIS_LEFT_LEFT 0 0
    KEY_RIGHT = SDLK_RIGHT 0 BUTTON_DPAD_RIGHT AXIS_LEFT_RIGHT 0 0
    KEY_COIN1 = SDLK_5 0 BUTTON_BACK 0 0 0
    KEY_COIN2 = SDLK_6 0 0 0 BUTTON_BACK 0
    KEY_START1 = SDLK_1 0 BUTTON_START 0 0 0
    KEY_START2 = SDLK_2 0 0 0 BUTTON_START 0
    KEY_BUTTON1 = SDLK_LCTRL 0 BUTTON_A 0 0 0
    KEY_BUTTON2 = SDLK_LALT 0 BUTTON_B 0 0 0
    KEY_BUTTON3 = SDLK_SPACE 0 AXIS_TRIGGER_RIGHT 0 0 0
    KEY_SKILL1 = SDLK_LSHIFT 0 0 0 0 0
    KEY_SKILL2 = SDLK_Z 0 0 0 0 0
    KEY_SKILL3 = SDLK_X 0 0 0 0 0
    KEY_SERVICE = SDLK_9 0 0 0 0 0
    KEY_TEST = SDLK_F2 SDLK_F4 0 0 0 0
    KEY_RESET = SDLK_0 0 0 0 0 0
    KEY_SCREENSHOT = SDLK_F12 0 0 0 0 0
    KEY_QUIT = SDLK_ESCAPE 0 0 0 0 0
    KEY_PAUSE = SDLK_P 0 0 0 0 0
    KEY_CONSOLE = SDLK_BACKSLASH 0 0 0 0 0
    KEY_TILT = SDLK_T 0 0 0 0 0
    END

    [MOUSE]
    MOUSE_BUTTON1 = KEY_BUTTON3
    MOUSE_BUTTON2 = KEY_BUTTON1
    MOUSE_BUTTON3 = KEY_BUTTON2
    END

    [GLOBAL]
    GAMEPAD = TRUE
    END
""".trimIndent()

class GamepadIniTest {

    @Test
    fun `parses every real KEY_ row from the actual install file`() {
        val rows = parseGamepadRows(REAL_INI)

        assertEquals(22, rows.size)
    }

    @Test
    fun `KEY_UP parses all 4 real columns correctly`() {
        val row = parseGamepadRows(REAL_INI).first { it.keyName == "KEY_UP" }

        assertEquals("SDLK_UP", row.key1)
        assertEquals("0", row.key2)
        assertEquals("BUTTON_DPAD_UP", row.pad0Button)
        assertEquals("AXIS_LEFT_UP", row.axisPad0)
        assertEquals("0", row.pad1Button)
        assertEquals("0", row.axisPad1)
    }

    @Test
    fun `KEY_COIN2 and KEY_START2 are bound through Pad1, not Pad0 - the real case 48-49 exist for`() {
        val rows = parseGamepadRows(REAL_INI)
        val coin2 = rows.first { it.keyName == "KEY_COIN2" }
        val start2 = rows.first { it.keyName == "KEY_START2" }

        assertEquals("0", coin2.pad0Button)
        assertEquals("BUTTON_BACK", coin2.pad1Button)
        assertEquals("0", start2.pad0Button)
        assertEquals("BUTTON_START", start2.pad1Button)
    }

    @Test
    fun `a real key with no gamepad binding at all reads as all zeros, not missing`() {
        val row = parseGamepadRows(REAL_INI).first { it.keyName == "KEY_SKILL1" }

        assertEquals("SDLK_LSHIFT", row.key1)
        assertEquals("0", row.pad0Button)
        assertEquals("0", row.axisPad0)
        assertEquals("0", row.pad1Button)
        assertEquals("0", row.axisPad1)
    }

    @Test
    fun `KEY_TEST has a real second keyboard key (key2)`() {
        val row = parseGamepadRows(REAL_INI).first { it.keyName == "KEY_TEST" }

        assertEquals("SDLK_F2", row.key1)
        assertEquals("SDLK_F4", row.key2)
    }

    @Test
    fun `stops at END and never reads into MOUSE or GLOBAL`() {
        val rows = parseGamepadRows(REAL_INI)

        assertEquals(null, rows.find { it.keyName.startsWith("MOUSE_") })
        assertEquals(null, rows.find { it.keyName == "GAMEPAD" })
    }

    @Test
    fun `updateGamepadBinding rewrites only KEY1 on the target line, leaving every other token and line untouched`() {
        val updated = updateGamepadBinding(REAL_INI, "KEY_UP", BindingSlot.KEY1, "SDLK_W")

        val rows = parseGamepadRows(updated)
        val up = rows.first { it.keyName == "KEY_UP" }
        assertEquals("SDLK_W", up.key1)
        // Every other real column on that same line, untouched.
        assertEquals("0", up.key2)
        assertEquals("BUTTON_DPAD_UP", up.pad0Button)
        assertEquals("AXIS_LEFT_UP", up.axisPad0)

        // A completely different line, untouched.
        val down = rows.first { it.keyName == "KEY_DOWN" }
        assertEquals("SDLK_DOWN", down.key1)
    }

    @Test
    fun `updateGamepadBinding rewrites PAD0_BUTTON and AXIS_PAD0 - the real case 48's list-picker exercises`() {
        val withButton = updateGamepadBinding(REAL_INI, "KEY_SKILL1", BindingSlot.PAD0_BUTTON, "BUTTON_X")
        val withBoth = updateGamepadBinding(withButton, "KEY_SKILL1", BindingSlot.AXIS_PAD0, "AXIS_LEFT_UP")

        val row = parseGamepadRows(withBoth).first { it.keyName == "KEY_SKILL1" }
        assertEquals("BUTTON_X", row.pad0Button)
        assertEquals("AXIS_LEFT_UP", row.axisPad0)
        // KEY_SKILL1's own keyboard binding, untouched by either write.
        assertEquals("SDLK_LSHIFT", row.key1)
    }

    @Test
    fun `updateGamepadBinding on a line with fewer than 6 tokens pads missing ones with 0`() {
        // KEY_SKILL1 has exactly 5 real tokens after "=" in the actual
        // file (SDLK_LSHIFT 0 0 0 0) - AxisPad1 is written explicitly
        // here, but real hypseus templates can omit trailing tokens
        // entirely, which this must still handle without index errors.
        val shortLineIni = "[KEYBOARD]\nKEY_COIN1 = SDLK_5 0 BUTTON_BACK\nEND"

        val updated = updateGamepadBinding(shortLineIni, "KEY_COIN1", BindingSlot.AXIS_PAD1, "AXIS_RIGHT_UP")

        val row = parseGamepadRows(updated).first()
        assertEquals("SDLK_5", row.key1)
        assertEquals("BUTTON_BACK", row.pad0Button)
        assertEquals("0", row.axisPad0)
        assertEquals("0", row.pad1Button)
        assertEquals("AXIS_RIGHT_UP", row.axisPad1)
    }
}
