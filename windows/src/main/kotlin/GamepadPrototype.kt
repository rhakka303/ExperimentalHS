import java.io.File
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWGamepadState
import org.lwjgl.system.MemoryStack

/**
 * #68 - real prototype only, not a finished feature: confirms LWJGL/GLFW's
 * joystick polling can run alongside Compose Desktop's own AWT-based
 * event loop in the same process, and that the native .dll actually
 * bundles into the real jpackage distributable - not just `gradlew run`.
 * Real navigation (#69) and real Controller 1/2 capture (this same
 * story, once the prototype is confirmed solid) are later work built on
 * top of this, not part of it.
 *
 * Runs on its own dedicated background thread, never the Compose/AWT
 * thread - GLFW's own event loop (`glfwPollEvents()`) is designed
 * around owning the application's main loop, which this app has no room
 * for (Compose Desktop already owns it). Every GLFW call stays confined
 * to this one thread, matching GLFW's own single-thread-per-context
 * expectation - the real open question #68 exists to answer.
 *
 * Uses GLFW's standardized gamepad API (`glfwGetGamepadState`), not the
 * raw joystick API - its button names (GLFW_GAMEPAD_BUTTON_A, _B, _X, _Y,
 * _DPAD_UP, etc.) map almost 1:1 to hypseus's own real BUTTON_A/BUTTON_B/
 * BUTTON_DPAD_UP convention (confirmed against the real
 * hypinput_gamepad.ini this project already tests against), a real sign
 * this is the right API for #68/#69's eventual actual work, not just
 * this prototype.
 *
 * Logs to a real file (`HypdroidDesktop/gamepad_prototype.log`), not
 * stdout - the packaged .exe has no attached console to read println()
 * from.
 */
fun startGamepadPrototype(launcherFolder: File?) {
    val logFile = launcherFolder?.let { File(it, "gamepad_prototype.log") } ?: return

    Thread {
        fun log(message: String) {
            try {
                logFile.appendText("[${System.currentTimeMillis()}] $message\n")
            } catch (e: java.io.IOException) {
                // Nothing meaningful to do if the log itself can't be
                // written - this is throwaway prototype code, not a
                // feature anything else depends on.
            }
        }

        // #68 - this whole body needs to be inside one try/catch, not
        // just the file write - a native-loading failure
        // (UnsatisfiedLinkError etc.) thrown from glfwInit() itself is a
        // real, plausible outcome to catch here, and an uncaught
        // Throwable on an ad-hoc Thread has nowhere visible to go in a
        // packaged .exe with no attached console (confirmed live: the
        // first real prototype run produced zero log output at all,
        // meaning it crashed before ever reaching the first log() call
        // below it).
        try {
            log("Starting gamepad prototype thread")
            if (!GLFW.glfwInit()) {
                log("glfwInit() returned false")
                return@Thread
            }
            log("GLFW initialized (${GLFW.glfwGetVersionString()}) - watching for a connected gamepad")

            // jid*100+button as the map key: simple, collision-free for
            // the real range (16 joystick slots, 15 gamepad buttons
            // each) without needing a data class for throwaway prototype
            // code.
            val previouslyPressed = mutableMapOf<Int, Boolean>()

            while (true) {
                GLFW.glfwPollEvents()

                for (jid in GLFW.GLFW_JOYSTICK_1..GLFW.GLFW_JOYSTICK_16) {
                    if (!GLFW.glfwJoystickPresent(jid)) continue
                    if (!GLFW.glfwJoystickIsGamepad(jid)) continue

                    MemoryStack.stackPush().use { stack ->
                        val state = GLFWGamepadState.malloc(stack)
                        if (GLFW.glfwGetGamepadState(jid, state)) {
                            val buttons = state.buttons()
                            for (button in 0 until buttons.limit()) {
                                val isPressed = buttons.get(button) == GLFW.GLFW_PRESS.toByte()
                                val key = jid * 100 + button
                                val wasPressed = previouslyPressed[key] ?: false
                                if (isPressed && !wasPressed) {
                                    log("${GLFW.glfwGetGamepadName(jid)} (joystick $jid): button $button pressed")
                                }
                                previouslyPressed[key] = isPressed
                            }
                        }
                    }
                }

                Thread.sleep(16)
            }
        } catch (e: Throwable) {
            log("Gamepad prototype thread crashed: ${e.stackTraceToString()}")
        }
    }.apply {
        isDaemon = true
        name = "gamepad-prototype"
    }.start()
}
