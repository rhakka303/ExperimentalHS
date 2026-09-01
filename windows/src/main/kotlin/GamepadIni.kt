import java.io.File

/**
 * #46 - one row per KEY_* action in hypinput_gamepad.ini's [KEYBOARD]
 * section. All 6 real columns are captured here even though this story
 * (#46, Keyboard) only reads/writes key1 - #47 (Mouse), #48 (Controller
 * 1) and #49 (Controller 2) are the same file and the same rows, just
 * different columns, so this is written as the one complete, reusable
 * parser rather than four narrow ones.
 *
 * Windows has exactly one hypinput_gamepad.ini, at the install root -
 * -homedir/-datadir is always the install root itself here (unlike
 * Android, which is per-game folder, per its own #60/#72). gamepadIniPath
 * below is deliberately not a port of Android's function of the same
 * name for that reason.
 */
data class GamepadRow(
    val keyName: String,
    val key1: String,
    val key2: String,
    val pad0Button: String,
    val axisPad0: String,
    val pad1Button: String,
    val axisPad1: String,
)

enum class BindingSlot { KEY1, KEY2, PAD0_BUTTON, AXIS_PAD0, PAD1_BUTTON, AXIS_PAD1 }

// Exact strings hypseus's keycodes.cpp expects, confirmed against
// Android's real GamepadIni.kt (itself confirmed against
// sdl3_controller_button()/sdl3_controller_axis() - case-SENSITIVE
// strcmp there, so these must be emitted verbatim). #48/#49 are the
// stories that actually use these lists (the list-picker path); kept
// here since they're properties of the file format itself, not of any
// one story.
val VALID_BUTTON_TOKENS = listOf(
    "BUTTON_A", "BUTTON_B", "BUTTON_X", "BUTTON_Y",
    "BUTTON_BACK", "BUTTON_GUIDE", "BUTTON_START",
    "BUTTON_LEFTSTICK", "BUTTON_RIGHTSTICK",
    "BUTTON_LEFTSHOULDER", "BUTTON_RIGHTSHOULDER",
    "BUTTON_DPAD_UP", "BUTTON_DPAD_DOWN", "BUTTON_DPAD_LEFT", "BUTTON_DPAD_RIGHT",
    "AXIS_TRIGGER_LEFT", "AXIS_TRIGGER_RIGHT",
)

val VALID_AXIS_TOKENS = listOf(
    "AXIS_LEFT_UP", "AXIS_LEFT_DOWN", "AXIS_LEFT_LEFT", "AXIS_LEFT_RIGHT",
    "AXIS_RIGHT_UP", "AXIS_RIGHT_DOWN", "AXIS_RIGHT_LEFT", "AXIS_RIGHT_RIGHT",
)

private const val KEYBOARD_HEADER = "[KEYBOARD]"
private const val SECTION_END = "END"

fun gamepadIniPath(installRoot: File): File = File(installRoot, "hypinput_gamepad.ini")

/**
 * Parses the [KEYBOARD] section's KEY_* lines. Mirrors input.cpp's own
 * tokenizer closely enough to round-trip real files (confirmed against
 * Android's real GamepadIni.kt and the real file at this project's own
 * install root): find_word() pulls whitespace-separated tokens one at a
 * time, and a line can legitimately have fewer than 6 - trailing missing
 * tokens default to "0", same as input.cpp's own val3/val4/val5/val6
 * defaults.
 */
fun parseGamepadRows(iniText: String): List<GamepadRow> {
    val rows = mutableListOf<GamepadRow>()
    var inKeyboardSection = false
    for (line in iniText.lineSequence()) {
        val trimmed = line.trim()
        if (!inKeyboardSection) {
            if (trimmed.equals(KEYBOARD_HEADER, ignoreCase = true)) inKeyboardSection = true
            continue
        }
        if (trimmed.equals(SECTION_END, ignoreCase = true)) break
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

        val eqIndex = line.indexOf('=')
        if (eqIndex < 0) continue
        val keyName = line.substring(0, eqIndex).trim()
        if (!keyName.startsWith("KEY_")) continue

        val tokens = line.substring(eqIndex + 1).trim().split(Regex("\\s+"))
        rows.add(
            GamepadRow(
                keyName = keyName,
                key1 = tokens.getOrElse(0) { "0" },
                key2 = tokens.getOrElse(1) { "0" },
                pad0Button = tokens.getOrElse(2) { "0" },
                axisPad0 = tokens.getOrElse(3) { "0" },
                pad1Button = tokens.getOrElse(4) { "0" },
                axisPad1 = tokens.getOrElse(5) { "0" },
            ),
        )
    }
    return rows
}

/**
 * Rewrites just one column on one KEY_* line, leaving every other line -
 * and every other column on that line - untouched. Missing trailing
 * tokens are filled in as literal "0" rather than left absent, same
 * semantically-neutral behavior Android's real updateGamepadBinding
 * already established (input.cpp defaults an absent token to 0 anyway,
 * so this only ever affects lines this function actually touches).
 */
fun updateGamepadBinding(iniText: String, keyName: String, slot: BindingSlot, newToken: String): String {
    val lines = iniText.lines().toMutableList()
    var inKeyboardSection = false
    for (i in lines.indices) {
        val line = lines[i]
        val trimmed = line.trim()
        if (!inKeyboardSection) {
            if (trimmed.equals(KEYBOARD_HEADER, ignoreCase = true)) inKeyboardSection = true
            continue
        }
        if (trimmed.equals(SECTION_END, ignoreCase = true)) break

        val eqIndex = line.indexOf('=')
        if (eqIndex < 0) continue
        val lineKeyName = line.substring(0, eqIndex).trim()
        if (!lineKeyName.equals(keyName, ignoreCase = true)) continue

        val tokens = line.substring(eqIndex + 1).trim().split(Regex("\\s+")).toMutableList()
        while (tokens.size < 6) tokens.add("0")
        val index = when (slot) {
            BindingSlot.KEY1 -> 0
            BindingSlot.KEY2 -> 1
            BindingSlot.PAD0_BUTTON -> 2
            BindingSlot.AXIS_PAD0 -> 3
            BindingSlot.PAD1_BUTTON -> 4
            BindingSlot.AXIS_PAD1 -> 5
        }
        tokens[index] = newToken
        lines[i] = "$lineKeyName = ${tokens.joinToString(" ")}"
        break
    }
    return lines.joinToString("\n")
}
