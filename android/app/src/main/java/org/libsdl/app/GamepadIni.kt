package org.libsdl.app

import java.io.File

data class GamepadRow(
    val keyName: String,
    val pad0Button: String,
    val axisPad0: String,
)

enum class BindingSlot { PAD0_BUTTON, AXIS_PAD0 }

// Exact strings hypseus's keycodes.cpp expects (sdl3_controller_button() /
// sdl3_controller_axis()) - matched via case-SENSITIVE strcmp there, unlike
// the KEY_* name matching itself (strcasecmp) - so these must be emitted
// verbatim, not just case-insensitively similar.
val VALID_BUTTON_TOKENS = listOf(
    "BUTTON_A", "BUTTON_B", "BUTTON_X", "BUTTON_Y",
    "BUTTON_BACK", "BUTTON_GUIDE", "BUTTON_START",
    "BUTTON_LEFTSTICK", "BUTTON_RIGHTSTICK",
    "BUTTON_LEFTSHOULDER", "BUTTON_RIGHTSHOULDER",
    "BUTTON_DPAD_UP", "BUTTON_DPAD_DOWN", "BUTTON_DPAD_LEFT", "BUTTON_DPAD_RIGHT",
    // The two analog triggers are parsed by sdl3_controller_button(), not
    // sdl3_controller_axis() - they occupy the Pad0Button column despite
    // the "AXIS_" name (confirmed in input.cpp: val3 uses
    // sdl3_controller_button(), which includes these two).
    "AXIS_TRIGGER_LEFT", "AXIS_TRIGGER_RIGHT",
)

// Only the 8 stick-direction macros go through sdl3_controller_axis() and
// belong in the AxisPad0 column.
val VALID_AXIS_TOKENS = listOf(
    "AXIS_LEFT_UP", "AXIS_LEFT_DOWN", "AXIS_LEFT_LEFT", "AXIS_LEFT_RIGHT",
    "AXIS_RIGHT_UP", "AXIS_RIGHT_DOWN", "AXIS_RIGHT_LEFT", "AXIS_RIGHT_RIGHT",
)

private const val KEYBOARD_HEADER = "[KEYBOARD]"
private const val KEYBOARD_END = "END"

/**
 * #38's original per-category split (Daphne-native used the picked folder
 * itself as -datadir, Singe used its parent) was removed by #60 - -datadir
 * is always the picked Game folder itself now, for every category - so
 * hypinput_gamepad.ini only ever exists in one place. #72: this function
 * used to be two (singeIniPath()/daphneIniPath()) and only the Daphne one
 * was updated when #60 landed, leaving the Singe path - which
 * ControllerConfigScreen actually reads/writes through - still pointed at
 * the parent folder. Confirmed on real hardware: that wrote real button
 * remaps into a stale file in a shared multi-emulator ROMs directory that
 * hypseus never reads, so those remaps silently never took effect in game.
 */
fun gamepadIniPath(gameFolderPath: String): String =
    "$gameFolderPath/hypinput_gamepad.ini"

/**
 * Parses just enough of hypinput_gamepad.ini's [KEYBOARD] section to read
 * the Pad0Button/AxisPad0 columns (tokens 3 and 4 after "=" on each KEY_*
 * line). Mirrors input.cpp's own tokenizer closely enough to round-trip
 * real files: find_word() pulls whitespace-separated tokens one at a time,
 * and only the first 3 (Key1, Key2, Pad0Button) are required - trailing
 * tokens (AxisPad0, Pad1Button, AxisPad1) are read via nested optional
 * find_word() calls and default to "0"/absent if the line doesn't have
 * them, confirmed by a real line in the shipped template
 * ("KEY_COIN1 = SDLK_5 0 BUTTON_BACK 0 0", only 5 tokens - AxisPad1 omitted
 * entirely, not written as an explicit trailing "0").
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
        if (trimmed.equals(KEYBOARD_END, ignoreCase = true)) break
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

        val eqIndex = line.indexOf('=')
        if (eqIndex < 0) continue
        val keyName = line.substring(0, eqIndex).trim()
        if (!keyName.startsWith("KEY_")) continue

        val tokens = line.substring(eqIndex + 1).trim().split(Regex("\\s+"))
        rows.add(
            GamepadRow(
                keyName = keyName,
                pad0Button = tokens.getOrElse(2) { "0" },
                axisPad0 = tokens.getOrElse(3) { "0" },
            ),
        )
    }
    return rows
}

/**
 * Rewrites just the Pad0Button or AxisPad0 column on one KEY_* line,
 * leaving every other line in the file - and every other column on that
 * line - untouched. If the line has fewer than 6 tokens after "=" (a
 * legitimately common, valid shape - see parseGamepadRows()'s doc comment),
 * missing trailing tokens are filled in as literal "0" rather than left
 * absent. That's a real but semantically neutral change: input.cpp
 * defaults an absent val4/val5/val6 to 0 already, so an explicit "0" parses
 * identically - it only affects lines this function actually touches.
 */
fun updateGamepadBinding(
    iniText: String,
    keyName: String,
    slot: BindingSlot,
    newToken: String,
): String {
    val lines = iniText.lines().toMutableList()
    var inKeyboardSection = false
    for (i in lines.indices) {
        val line = lines[i]
        val trimmed = line.trim()
        if (!inKeyboardSection) {
            if (trimmed.equals(KEYBOARD_HEADER, ignoreCase = true)) inKeyboardSection = true
            continue
        }
        if (trimmed.equals(KEYBOARD_END, ignoreCase = true)) break

        val eqIndex = line.indexOf('=')
        if (eqIndex < 0) continue
        val lineKeyName = line.substring(0, eqIndex).trim()
        if (!lineKeyName.equals(keyName, ignoreCase = true)) continue

        val tokens = line.substring(eqIndex + 1).trim().split(Regex("\\s+")).toMutableList()
        while (tokens.size < 6) tokens.add("0")
        when (slot) {
            BindingSlot.PAD0_BUTTON -> tokens[2] = newToken
            BindingSlot.AXIS_PAD0 -> tokens[3] = newToken
        }
        lines[i] = "$lineKeyName = ${tokens.joinToString(" ")}"
        break
    }
    return lines.joinToString("\n")
}

/**
 * Applies a binding change to the one real hypinput_gamepad.ini (#72 -
 * previously wrote to two paths, back when #38's per-category split still
 * existed). Missing file is skipped, not created - this only edits a file
 * hypseus itself has already generated (see #41's "doesn't exist yet"
 * gating).
 */
fun applyBinding(
    gameFolderPath: String,
    keyName: String,
    slot: BindingSlot,
    newToken: String,
) {
    val file = File(gamepadIniPath(gameFolderPath))
    if (!file.exists()) return
    val updated = updateGamepadBinding(file.readText(), keyName, slot, newToken)
    file.writeText(updated)
}
