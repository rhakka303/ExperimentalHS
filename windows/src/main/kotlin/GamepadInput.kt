import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWGamepadState
import org.lwjgl.system.MemoryStack

/**
 * #69 - the real actions a gamepad drives, matching the exact set the
 * carousel's own keyboard onKeyEvent already handles (#17/#19) - this
 * is a second real input source feeding the same existing actions, not
 * a new interaction model. BACK (owner's own addition while scoping
 * this story) completes the one screen transition already in bounds
 * (LAUNCH/OPTIONS open GameOptionsScreen) - without it, entering that
 * screen via a controller would strand the user needing a mouse/keyboard
 * just to leave it again.
 */
enum class GamepadAction { LEFT, RIGHT, LAUNCH, OPTIONS, BACK }

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

                        fun edgePress(button: Int): Boolean {
                            val isPressed = buttons.get(button) == GLFW.GLFW_PRESS.toByte()
                            val fired = isPressed && !wasPressed(jid, button)
                            setPressed(jid, button, isPressed)
                            return fired
                        }

                        if (edgePress(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT)) GamepadInputBus.emit(GamepadAction.LEFT)
                        if (edgePress(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT)) GamepadInputBus.emit(GamepadAction.RIGHT)
                        if (edgePress(GLFW.GLFW_GAMEPAD_BUTTON_A)) GamepadInputBus.emit(GamepadAction.LAUNCH)
                        if (edgePress(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN)) GamepadInputBus.emit(GamepadAction.OPTIONS)
                        if (edgePress(GLFW.GLFW_GAMEPAD_BUTTON_B)) GamepadInputBus.emit(GamepadAction.BACK)
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
