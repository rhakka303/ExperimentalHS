import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWGamepadState
import org.lwjgl.system.MemoryStack
import kotlin.math.abs

/**
 * #69 - the real actions a gamepad drives, matching the exact set the
 * carousel's own keyboard onKeyEvent already handles (#17/#19) - this
 * is a second real input source feeding the same existing actions, not
 * a new interaction model. BACK (owner's own addition while scoping
 * this story) completes the one screen transition already in bounds
 * (LAUNCH/OPTIONS open GameOptionsScreen) - without it, entering that
 * screen via a controller would strand the user needing a mouse/keyboard
 * just to leave it again. UP (#72) reaches the Settings gear in the top
 * bar - the carousel's own CarouselFocus model decides what it actually
 * does with it.
 */
enum class GamepadAction { LEFT, RIGHT, UP, LAUNCH, OPTIONS, BACK }

// #80 - GLFW's own standardized gamepad button indices, in order
// (GLFW_GAMEPAD_BUTTON_A=0 .. GLFW_GAMEPAD_BUTTON_DPAD_LEFT=14), mapped
// to the exact token strings hypseus's keycodes.cpp expects (see
// GamepadIni.kt's own VALID_BUTTON_TOKENS - case-sensitive, confirmed
// against Android's real GamepadIni.kt). Covers 15 of VALID_BUTTON_TOKENS'
// 17 entries; the remaining two (AXIS_TRIGGER_LEFT/RIGHT) aren't GLFW
// *buttons* at all - see the trigger-axis handling in startGamepadInput.
private val BUTTON_TOKEN_BY_GLFW_INDEX = listOf(
    "BUTTON_A", "BUTTON_B", "BUTTON_X", "BUTTON_Y",
    "BUTTON_LEFTSHOULDER", "BUTTON_RIGHTSHOULDER",
    "BUTTON_BACK", "BUTTON_START", "BUTTON_GUIDE",
    "BUTTON_LEFTSTICK", "BUTTON_RIGHTSTICK",
    "BUTTON_DPAD_UP", "BUTTON_DPAD_RIGHT", "BUTTON_DPAD_DOWN", "BUTTON_DPAD_LEFT",
)

// #80 - what a live-capture request is listening for: the next real
// button press (-> a VALID_BUTTON_TOKENS entry) or the next real stick
// movement past a deliberate-push threshold (-> a VALID_AXIS_TOKENS
// entry). Two different GLFW input classes (digital buttons vs analog
// sticks), so a request is scoped to one or the other rather than "any
// input" - matches which pill (Button vs Axis) the user actually clicked.
enum class CaptureKind { BUTTON, AXIS }

// #80 - the live-capture counterpart to GamepadInputBus above: a second,
// independent channel on the same background polling thread, active only
// while a Controls-screen pill is being listened to (activeCapture set by
// the UI thread when a pill's label is clicked). Kept fully separate from
// GamepadInputBus/GamepadAction - capturing a raw token for a *binding*
// and emitting a semantic navigation *action* are different concerns, and
// conflating them would mean every captured button press also fires
// whatever navigation meaning that button happens to have elsewhere.
object GamepadCaptureBus {
    // Written on the Compose/AWT thread (when a pill starts/stops
    // listening), read every frame on the dedicated gamepad-input thread
    // - @Volatile so the polling thread always sees the latest write
    // rather than a cached/stale value.
    @Volatile
    var activeCapture: CaptureKind? = null
        private set

    private val _captured = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val captured: SharedFlow<String> = _captured

    fun startCapture(kind: CaptureKind) {
        activeCapture = kind
    }

    fun cancelCapture() {
        activeCapture = null
    }

    fun emit(token: String) {
        activeCapture = null
        _captured.tryEmit(token)
    }
}

/**
 * #69 - the bridge between GamepadInput's own dedicated background
 * thread (GLFW's polling loop can't run on the Compose/AWT thread - see
 * startGamepadInput's own doc comment) and whichever screen is currently
 * composed. A screen only reacts to this while it's actually on screen
 * (its own LaunchedEffect collecting events), which is what scopes
 * gamepad navigation to "only while that screen is showing" for free -
 * no extra state needed to track which screen owns input right now.
 */
object GamepadInputBus {
    private val _events = MutableSharedFlow<GamepadAction>(extraBufferCapacity = 8)
    val events: SharedFlow<GamepadAction> = _events

    fun emit(action: GamepadAction) {
        _events.tryEmit(action)
    }
}

/**
 * #68/#69 - starts GLFW's own polling loop on a dedicated background
 * thread, confirmed live (Retroid... no, a real connected USB/Bluetooth
 * controller, #68) to coexist cleanly with Compose Desktop's own
 * AWT-based event loop when confined this way - glfwPollEvents() is
 * designed around owning the application's main loop, which this app has
 * no room for. Every GLFW call stays on this one thread.
 *
 * Maps GLFW's standardized gamepad buttons (whose names already match
 * hypseus's own real BUTTON_A/BUTTON_B/BUTTON_DPAD_* convention almost
 * 1:1, confirmed during #68's own scoping) to the real carousel actions
 * above, edge-triggered (only on the press transition, matching a single
 * keyboard KeyDown) rather than firing repeatedly while held.
 */
// #80 - a stick pushed at least this far (GLFW axes range -1f..1f) counts
// as a deliberate capture input, not drift/noise. Deliberately more
// generous than a typical dead-zone (which only needs to reject near-
// zero noise) since this is a one-shot "did the user mean to push this
// stick" decision, not continuous movement tracking.
private const val AXIS_CAPTURE_THRESHOLD = 0.6f

// #80 - GLFW's trigger axes run -1f (released) to 1f (fully pressed);
// 0f is roughly a half-pull, which is already a deliberate, unambiguous
// "I'm pulling this trigger" gesture for a one-shot capture.
private const val TRIGGER_CAPTURE_THRESHOLD = 0f

/**
 * #80 - which of a stick's 4 directions (if any) is being deliberately
 * pushed past AXIS_CAPTURE_THRESHOLD right now, or null if neither axis
 * has crossed it. GLFW's Y axis convention (LWJGL's own GLFWGamepadState
 * docs): negative = up, positive = down - the opposite sign convention
 * from X's negative = left, positive = right, which is why up/down and
 * left/right need separate checks rather than one shared sign test.
 * Whichever axis (X or Y) has moved further wins, so a mostly-diagonal
 * push still resolves to one clear direction instead of capturing
 * nothing.
 */
private fun captureStickDirection(x: Float, y: Float, tokenPrefix: String): String? {
    if (abs(y) >= abs(x)) {
        if (abs(y) < AXIS_CAPTURE_THRESHOLD) return null
        return if (y < 0) "${tokenPrefix}_UP" else "${tokenPrefix}_DOWN"
    }
    if (abs(x) < AXIS_CAPTURE_THRESHOLD) return null
    return if (x < 0) "${tokenPrefix}_LEFT" else "${tokenPrefix}_RIGHT"
}

fun startGamepadInput() {
    Thread {
        try {
            if (!GLFW.glfwInit()) return@Thread

            val previouslyPressed = mutableMapOf<Int, Boolean>()

            fun wasPressed(jid: Int, button: Int) = previouslyPressed[jid * 100 + button] ?: false
            fun setPressed(jid: Int, button: Int, pressed: Boolean) {
                previouslyPressed[jid * 100 + button] = pressed
            }

            while (true) {
                GLFW.glfwPollEvents()

                for (jid in GLFW.GLFW_JOYSTICK_1..GLFW.GLFW_JOYSTICK_16) {
                    if (!GLFW.glfwJoystickPresent(jid)) continue
                    if (!GLFW.glfwJoystickIsGamepad(jid)) continue

                    MemoryStack.stackPush().use { stack ->
                        val state = GLFWGamepadState.malloc(stack)
                        if (!GLFW.glfwGetGamepadState(jid, state)) return@use
                        val buttons = state.buttons()
                        val axes = state.axes()

                        fun edgePress(button: Int): Boolean {
                            val isPressed = buttons.get(button) == GLFW.GLFW_PRESS.toByte()
                            val fired = isPressed && !wasPressed(jid, button)
                            setPressed(jid, button, isPressed)
                            return fired
                        }

                        // #80 - every button's edge-press is computed
                        // every frame (not just the 6 navigation actions
                        // below), both so a live-capture request can see
                        // a fresh press on *any* button, and so
                        // wasPressed stays correct for every index
                        // regardless of whether capture happens to be
                        // active this particular frame.
                        val pressedThisFrame = BUTTON_TOKEN_BY_GLFW_INDEX.indices.filter { edgePress(it) }

                        when (GamepadCaptureBus.activeCapture) {
                            CaptureKind.BUTTON -> {
                                val button = pressedThisFrame.firstOrNull()
                                if (button != null) {
                                    GamepadCaptureBus.emit(BUTTON_TOKEN_BY_GLFW_INDEX[button])
                                } else if (axes.get(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) > TRIGGER_CAPTURE_THRESHOLD) {
                                    // Triggers aren't GLFW *buttons* - hypseus
                                    // still lists them in the button token set
                                    // (VALID_BUTTON_TOKENS), so a deliberate
                                    // pull captures here too, checked only
                                    // once nothing already captured.
                                    GamepadCaptureBus.emit("AXIS_TRIGGER_LEFT")
                                } else if (axes.get(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) > TRIGGER_CAPTURE_THRESHOLD) {
                                    GamepadCaptureBus.emit("AXIS_TRIGGER_RIGHT")
                                }
                            }
                            CaptureKind.AXIS -> {
                                val token = captureStickDirection(
                                    axes.get(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X),
                                    axes.get(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y),
                                    "AXIS_LEFT",
                                ) ?: captureStickDirection(
                                    axes.get(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X),
                                    axes.get(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y),
                                    "AXIS_RIGHT",
                                )
                                if (token != null) GamepadCaptureBus.emit(token)
                            }
                            null -> {
                                // #80 - navigation actions are suppressed
                                // while a capture is active (the `when`
                                // above only reaches here when it isn't),
                                // so a button press meant to be captured
                                // for a binding never also fires whatever
                                // navigation meaning that same button has
                                // elsewhere in the app.
                                if (pressedThisFrame.contains(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT)) GamepadInputBus.emit(GamepadAction.LEFT)
                                if (pressedThisFrame.contains(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT)) GamepadInputBus.emit(GamepadAction.RIGHT)
                                if (pressedThisFrame.contains(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP)) GamepadInputBus.emit(GamepadAction.UP)
                                if (pressedThisFrame.contains(GLFW.GLFW_GAMEPAD_BUTTON_A)) GamepadInputBus.emit(GamepadAction.LAUNCH)
                                if (pressedThisFrame.contains(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN)) GamepadInputBus.emit(GamepadAction.OPTIONS)
                                if (pressedThisFrame.contains(GLFW.GLFW_GAMEPAD_BUTTON_B)) GamepadInputBus.emit(GamepadAction.BACK)
                            }
                        }
                    }
                }

                Thread.sleep(16)
            }
        } catch (e: Throwable) {
            // #68's own real finding: an uncaught exception on this
            // ad-hoc Thread has nowhere visible to go in a packaged .exe
            // with no attached console. Nothing to recover into here -
            // gamepad input simply stops working for the rest of this
            // run, same as if no controller were ever connected. Not
            // rethrown: a background input thread dying is not a reason
            // to take the rest of the app down with it.
        }
    }.apply {
        isDaemon = true
        name = "gamepad-input"
    }.start()
}
