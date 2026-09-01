import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * #18 - per-game options, stored in `HypdroidDesktop/options.json` (the
 * launcher's own folder, per #6's ownership rule - resolved via
 * resolveLauncherFolder(), not the hypseus install root).
 *
 * Phase 2's only field: `arguments`, custom launch arguments per game.
 * Android's GameOptions.kt (`android/app/src/main/java/org/libsdl/app/`)
 * is the specification for the shape and the storage semantics, not
 * something copied from - each entry can itself be a multi-token string
 * (e.g. "-scalefactor 50"), split on whitespace at launch time
 * (MainActivity.kt ~line 396), which is why launchGame() (#10) does the
 * same split rather than appending each entry as a single verbatim
 * argv token.
 */
@Serializable
data class GameOptions(val arguments: List<String> = emptyList())

private val json = Json { ignoreUnknownKeys = true }

private fun optionsFile(launcherFolder: File) = File(launcherFolder, "options.json")

/**
 * Never throws on a missing or malformed file - an absent or corrupt
 * options.json means every game simply has no saved options yet, not a
 * crash. Matches #18's own acceptance criteria.
 */
fun loadAllOptions(launcherFolder: File): Map<String, GameOptions> {
    val file = optionsFile(launcherFolder)
    if (!file.isFile) return emptyMap()
    return try {
        json.decodeFromString(file.readText())
    } catch (e: SerializationException) {
        emptyMap()
    }
}

fun loadOptions(launcherFolder: File, gameName: String): GameOptions =
    loadAllOptions(launcherFolder)[gameName] ?: GameOptions()

fun saveOptions(launcherFolder: File, gameName: String, options: GameOptions) {
    val all: Map<String, GameOptions> = loadAllOptions(launcherFolder) + (gameName to options)
    optionsFile(launcherFolder).writeText(json.encodeToString(all))
}
