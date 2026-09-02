import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * #94 - the real log file the launcher writes to its own folder. Pure
 * File I/O, no real hypseus.exe needed to verify, same category of test
 * phase 3 established this project's first automated tests for.
 */
class LauncherLogTest {

    @TempDir
    lateinit var launcherFolder: File

    private fun logFile() = File(File(launcherFolder, "log"), "hypdroiddesktop.log")

    @Test
    fun `log creates log slash hypdroiddesktop log under the launcher folder`() {
        log(launcherFolder, "hello")

        assertTrue(logFile().isFile)
    }

    @Test
    fun `log appends, newest line last`() {
        log(launcherFolder, "first")
        log(launcherFolder, "second")

        val lines = logFile().readLines()

        assertEquals(2, lines.size)
        assertTrue(lines[0].endsWith("first"))
        assertTrue(lines[1].endsWith("second"))
    }

    @Test
    fun `log line is timestamped`() {
        log(launcherFolder, "hello")

        val line = logFile().readLines().single()

        assertTrue(line.startsWith("["))
        assertTrue(line.contains("] hello"))
    }

    @Test
    fun `log is a no-op when launcherFolder is null`() {
        // Only meaningful for the packaged app (resolveLauncherFolder()'s
        // own doc comment) - must never throw when it's null.
        log(null, "hello")
    }

    @Test
    fun `logUnexpectedExceptions returns the block's value on success and logs nothing`() {
        val result = logUnexpectedExceptions(launcherFolder, "a test") { 42 }

        assertEquals(42, result)
        assertFalse(logFile().exists())
    }

    @Test
    fun `logUnexpectedExceptions logs the exception and rethrows it unchanged`() {
        val thrown = RuntimeException("boom")

        assertFailsWith<RuntimeException> {
            logUnexpectedExceptions(launcherFolder, "a test") { throw thrown }
        }

        // A stack trace is genuinely multi-line - this asserts against
        // the whole file's content, not readLines().single(), since a
        // single log() call legitimately produces more than one raw
        // line here.
        val content = logFile().readText()
        assertTrue(content.contains("Unexpected error during a test"))
        assertTrue(content.contains("boom"))
    }
}
