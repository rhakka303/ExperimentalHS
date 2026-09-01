import androidx.compose.ui.input.key.Key

/**
 * #46 - maps a real Compose Desktop key press to the SDLK_* token
 * hypinput_gamepad.ini expects. Confirmed against the real file at this
 * project's own install root that letter/utility keys are emitted
 * uppercase (SDLK_Z, SDLK_X, SDLK_T, SDLK_P, ...) - followed here for
 * consistency with what's actually on disk, regardless of SDL's own
 * internal representation.
 *
 * Covers the common keyboard - letters, digits, function keys, arrows,
 * modifiers, and the punctuation/whitespace keys most likely to matter
 * for a real binding - not an exhaustive SDL keysym table. An unmapped
 * key returns null; the capture flow that calls this treats null as
 * "ignore this press, keep listening" rather than writing a wrong or
 * empty token.
 */
fun sdlkTokenFor(key: Key): String? = when (key) {
    Key.A -> "SDLK_A"
    Key.B -> "SDLK_B"
    Key.C -> "SDLK_C"
    Key.D -> "SDLK_D"
    Key.E -> "SDLK_E"
    Key.F -> "SDLK_F"
    Key.G -> "SDLK_G"
    Key.H -> "SDLK_H"
    Key.I -> "SDLK_I"
    Key.J -> "SDLK_J"
    Key.K -> "SDLK_K"
    Key.L -> "SDLK_L"
    Key.M -> "SDLK_M"
    Key.N -> "SDLK_N"
    Key.O -> "SDLK_O"
    Key.P -> "SDLK_P"
    Key.Q -> "SDLK_Q"
    Key.R -> "SDLK_R"
    Key.S -> "SDLK_S"
    Key.T -> "SDLK_T"
    Key.U -> "SDLK_U"
    Key.V -> "SDLK_V"
    Key.W -> "SDLK_W"
    Key.X -> "SDLK_X"
    Key.Y -> "SDLK_Y"
    Key.Z -> "SDLK_Z"

    Key.Zero -> "SDLK_0"
    Key.One -> "SDLK_1"
    Key.Two -> "SDLK_2"
    Key.Three -> "SDLK_3"
    Key.Four -> "SDLK_4"
    Key.Five -> "SDLK_5"
    Key.Six -> "SDLK_6"
    Key.Seven -> "SDLK_7"
    Key.Eight -> "SDLK_8"
    Key.Nine -> "SDLK_9"

    Key.F1 -> "SDLK_F1"
    Key.F2 -> "SDLK_F2"
    Key.F3 -> "SDLK_F3"
    Key.F4 -> "SDLK_F4"
    Key.F5 -> "SDLK_F5"
    Key.F6 -> "SDLK_F6"
    Key.F7 -> "SDLK_F7"
    Key.F8 -> "SDLK_F8"
    Key.F9 -> "SDLK_F9"
    Key.F10 -> "SDLK_F10"
    Key.F11 -> "SDLK_F11"
    Key.F12 -> "SDLK_F12"

    Key.DirectionUp -> "SDLK_UP"
    Key.DirectionDown -> "SDLK_DOWN"
    Key.DirectionLeft -> "SDLK_LEFT"
    Key.DirectionRight -> "SDLK_RIGHT"

    Key.CtrlLeft -> "SDLK_LCTRL"
    Key.CtrlRight -> "SDLK_RCTRL"
    Key.AltLeft -> "SDLK_LALT"
    Key.AltRight -> "SDLK_RALT"
    Key.ShiftLeft -> "SDLK_LSHIFT"
    Key.ShiftRight -> "SDLK_RSHIFT"

    Key.Spacebar -> "SDLK_SPACE"
    Key.Escape -> "SDLK_ESCAPE"
    Key.Enter, Key.NumPadEnter -> "SDLK_RETURN"
    Key.Tab -> "SDLK_TAB"
    Key.Backspace -> "SDLK_BACKSPACE"
    Key.CapsLock -> "SDLK_CAPSLOCK"
    Key.Backslash -> "SDLK_BACKSLASH"
    Key.Comma -> "SDLK_COMMA"
    Key.Period -> "SDLK_PERIOD"
    Key.Slash -> "SDLK_SLASH"
    Key.Semicolon -> "SDLK_SEMICOLON"
    Key.Apostrophe -> "SDLK_QUOTE"
    Key.Grave -> "SDLK_BACKQUOTE"
    Key.Minus -> "SDLK_MINUS"
    Key.Equals -> "SDLK_EQUALS"
    Key.LeftBracket -> "SDLK_LEFTBRACKET"
    Key.RightBracket -> "SDLK_RIGHTBRACKET"

    Key.MoveHome -> "SDLK_HOME"
    Key.MoveEnd -> "SDLK_END"
    Key.PageUp -> "SDLK_PAGEUP"
    Key.PageDown -> "SDLK_PAGEDOWN"
    Key.Insert -> "SDLK_INSERT"
    Key.Delete -> "SDLK_DELETE"

    else -> null
}
