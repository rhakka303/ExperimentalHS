package org.libsdl.app

import java.io.File

enum class GameCategory { SINGE_ZIPPED, SINGE_SCRIPT, DAPHNE_NATIVE }

data class Game(
    val name: String,
    val category: GameCategory,
    val framefilePath: String,
    val romOrScriptPath: String,
)

// roms/ and vldp/ are reserved for Daphne-native games (see below) - a
// fan-made game folder can't use either name at the top level of the home dir.
private val RESERVED_SUBFOLDERS = setOf("roms", "vldp")

/**
 * Scans a chosen home folder for both game categories hypseus supports.
 * Any folder/file that doesn't match a category's required layout is just
 * excluded from the result - never an error, per #28's acceptance criteria.
 */
fun scanGames(homeDir: File): List<Game> {
    val games = mutableListOf<Game>()

    // Fan-made (Singe) games: each one is its own folder directly under the
    // home dir, e.g. <home>/AlteredCarbonResleeved/AlteredCarbonResleeved.txt
    homeDir.listFiles { f -> f.isDirectory && f.name !in RESERVED_SUBFOLDERS }?.forEach { gameDir ->
        val name = gameDir.name
        val framefile = File(gameDir, "$name.txt")
        if (!framefile.isFile) return@forEach

        val zip = File(gameDir, "$name.zip")
        val script = File(gameDir, "$name.singe")
        when {
            zip.isFile -> games += Game(name, GameCategory.SINGE_ZIPPED, framefile.path, zip.path)
            script.isFile -> games += Game(name, GameCategory.SINGE_SCRIPT, framefile.path, script.path)
        }
    }

    // Daphne-native games: framefile under vldp/<name>/<name>.txt, ROM zip
    // under roms/<name>.zip - both required, both live at the home dir's top level.
    val vldpDir = File(homeDir, "vldp")
    val romsDir = File(homeDir, "roms")
    if (vldpDir.isDirectory) {
        vldpDir.listFiles { f -> f.isDirectory }?.forEach { gameDir ->
            val name = gameDir.name
            val framefile = File(gameDir, "$name.txt")
            val rom = File(romsDir, "$name.zip")
            if (framefile.isFile && rom.isFile) {
                games += Game(name, GameCategory.DAPHNE_NATIVE, framefile.path, rom.path)
            }
        }
    }

    return games.sortedBy { it.name }
}
