import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

/**
 * #113 - the game list reads A to Z whatever the capitalization of each
 * name. Pure ordering logic plus one scan of real (temporary) folders.
 */
class GameSortTest {

    @TempDir
    lateinit var tmp: File

    private fun game(name: String) = Game(name, GameCategory.SINGE_ZIPPED, "$name.txt", "$name.zip")

    private fun namesOf(games: List<Game>): List<String> = sortGames(games).map { it.name }

    @Test
    fun `mixed case names are listed A to Z ignoring case`() {
        val sorted = namesOf(listOf("Beta", "alpha", "Charlie", "delta").map(::game))

        assertEquals(listOf("alpha", "Beta", "Charlie", "delta"), sorted)
    }

    @Test
    fun `a capitalized name no longer sorts before every lowercase name`() {
        val sorted = namesOf(listOf("Zebra", "apple", "DL_TV", "Daitarn_3", "daffy").map(::game))

        assertEquals(listOf("apple", "daffy", "Daitarn_3", "DL_TV", "Zebra"), sorted)
    }

    @Test
    fun `names that differ only in case keep the same order whatever order they arrive in`() {
        val one = namesOf(listOf("abc", "ABC", "Abc").map(::game))
        val two = namesOf(listOf("Abc", "abc", "ABC").map(::game))
        val three = namesOf(listOf("ABC", "Abc", "abc").map(::game))

        assertEquals(listOf("ABC", "Abc", "abc"), one)
        assertEquals(one, two)
        assertEquals(one, three)
    }

    @Test
    fun `digits keep plain character order, with no natural number ordering`() {
        val sorted = namesOf(listOf("game2", "game10", "38ambush", "alpha", "Game1").map(::game))

        assertEquals(listOf("38ambush", "alpha", "Game1", "game10", "game2"), sorted)
    }

    @Test
    fun `the scan lists capitalized and lowercase games interleaved A to Z`() {
        val install = File(tmp, "install").apply { mkdirs() }.also { File(it, "hypseus.exe").writeText("") }
        for (name in listOf("Beta", "alpha", "Charlie", "delta")) {
            val dir = File(File(install, "singe"), name).apply { mkdirs() }
            File(dir, "$name.txt").writeText("")
            File(dir, "$name.zip").writeText("")
        }

        val found = (scanGames(install) as ScanResult.Found).games.map { it.name }

        assertEquals(listOf("alpha", "Beta", "Charlie", "delta"), found)
    }
}
