import java.io.File

/**
 * #110 - what the Export page's "Create TXT File" card reports back.
 */
data class TxtExportResult(val created: Int, val skipped: Int, val removed: Int)

// Every file this feature writes is just its own game name, so anything
// bigger than this cannot be one of ours - checked before reading, so a
// large real .txt in the folder is never loaded into memory.
private const val MAX_OWN_FILE_BYTES = 1024L

/**
 * #110 - writes one `<game name>.txt` per game into the root of
 * [gameFolder] (next to roms/, vldp/ and singe/, not inside them), for
 * frontends that build their game list by scanning a folder for one file
 * per game.
 *
 * - Each file contains the game's name, and nothing else.
 * - An existing `<game name>.txt` is skipped, never overwritten - even a
 *   real framefile that happens to share the name.
 * - A `.txt` in the folder root whose game is gone is deleted, but only if
 *   it is provably one of ours: its whole content, ignoring surrounding
 *   whitespace, is exactly its own name. Any other .txt (a real framefile,
 *   a note) is never touched, whether or not a game matches its name.
 * - Only the root of [gameFolder] is looked at; subfolders are not.
 *
 * [games] is the launcher's current list for that folder, so "its game is
 * gone" means the game is no longer in that list.
 */
fun exportTxtFiles(gameFolder: File, games: List<Game>): TxtExportResult {
    if (!gameFolder.isDirectory) return TxtExportResult(0, 0, 0)

    var created = 0
    var skipped = 0
    for (game in games) {
        val file = File(gameFolder, "${game.name}.txt")
        if (file.exists()) {
            skipped++
        } else {
            file.writeText(game.name)
            created++
        }
    }

    var removed = 0
    val gameNames = games.map { it.name.lowercase() }.toSet()
    gameFolder.listFiles { f -> f.isFile && f.extension.equals("txt", ignoreCase = true) }?.forEach { file ->
        val name = file.nameWithoutExtension
        if (name.lowercase() in gameNames) return@forEach
        if (file.length() > MAX_OWN_FILE_BYTES) return@forEach
        // unreadable or locked: can't prove it's ours, so leave it alone
        val content = try {
            file.readText()
        } catch (e: java.io.IOException) {
            return@forEach
        }
        if (content.trim() == name && file.delete()) removed++
    }

    return TxtExportResult(created, skipped, removed)
}
