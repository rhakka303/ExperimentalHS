import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #127 - the jump-to-letter grouping and targets. Pure logic; the dropdown
 * itself and the focus behaviour around it are checked by hand on the real
 * launcher.
 */
class LetterJumpTest {

    private fun game(name: String) = Game(name, GameCategory.SINGE_ZIPPED, "$name.txt", "$name.zip")

    private fun sorted(vararg names: String): List<Game> = sortGames(names.map(::game))

    @Test
    fun `the entries are # then A to Z`() {
        assertEquals(27, LETTER_ENTRIES.size)
        assertEquals("#", LETTER_ENTRIES.first())
        assertEquals("A", LETTER_ENTRIES[1])
        assertEquals("Z", LETTER_ENTRIES.last())
        assertEquals(('A'..'Z').map { it.toString() }, LETTER_ENTRIES.drop(1))
    }

    @Test
    fun `a name belongs to its first letter, ignoring case`() {
        assertEquals("A", letterEntryFor("alpha"))
        assertEquals("A", letterEntryFor("Alpha"))
        assertEquals("S", letterEntryFor("space_ace_1080"))
        assertEquals("Z", letterEntryFor("zebra"))
    }

    @Test
    fun `digits, symbols and an empty name all go under #`() {
        assertEquals("#", letterEntryFor("38ambush"))
        assertEquals("#", letterEntryFor("7up"))
        assertEquals("#", letterEntryFor("_hidden"))
        assertEquals("#", letterEntryFor("-x"))
        assertEquals("#", letterEntryFor(""))
    }

    @Test
    fun `a non-ASCII letter is grouped under # rather than dropped`() {
        assertEquals("#", letterEntryFor("éclair"))
    }

    @Test
    fun `each letter jumps to the first game that starts with it`() {
        val games = sorted("38ambush", "alpha", "Beta", "Charlie", "delta")

        val targets = letterJumpTargets(games)

        assertEquals(0, targets["#"])
        assertEquals(1, targets["A"])
        assertEquals(2, targets["B"])
        assertEquals(3, targets["C"])
        assertEquals(4, targets["D"])
    }

    @Test
    fun `capitalized and lowercase names of one letter share one target, the earliest`() {
        val games = sorted("Sonic", "space", "Samurai", "sugar")

        val targets = letterJumpTargets(games)

        assertEquals(0, targets["S"])
        assertEquals(1, targets.size)
        assertEquals("Samurai", games[targets.getValue("S")].name)
    }

    @Test
    fun `a letter with no games has no target, so the picker can dim it`() {
        val targets = letterJumpTargets(sorted("alpha", "charlie"))

        assertTrue("A" in targets)
        assertTrue("C" in targets)
        assertNull(targets["B"])
        assertFalse("Z" in targets)
    }

    @Test
    fun `an empty list has no targets at all`() {
        assertTrue(letterJumpTargets(emptyList()).isEmpty())
    }

    @Test
    fun `jumping never changes the list, and the target is a real index into it`() {
        val games = sorted("Beta", "alpha", "Charlie", "delta", "Echo", "echo2")
        val before = games.map { it.name }

        val targets = letterJumpTargets(games)

        assertEquals(before, games.map { it.name })
        for ((entry, index) in targets) {
            assertTrue(index in games.indices, "$entry -> $index")
            assertEquals(entry, letterEntryFor(games[index].name))
            // it really is the FIRST game of that entry
            assertTrue(games.take(index).none { letterEntryFor(it.name) == entry }, entry)
        }
    }

    @Test
    fun `the current letter follows whichever game is centred`() {
        val games = sorted("alpha", "Beta", "Charlie")

        assertEquals(listOf("A", "B", "C"), games.map { letterEntryFor(it.name) })
    }
}
