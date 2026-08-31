import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/**
 * #11 - the phase 1 milestone: run this from inside a hypseus install, pick
 * a game, it plays. No folder picker - #8's resolveInstallRoot() already
 * finds the install from this exe's own location, per #6's scope decision.
 * No styling, per-game options, sorting, gamepad support, or anything else
 * beyond the list-and-launch itself: see the story's own non-goals.
 */
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "HypdroidDesktop") {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                val installRoot = remember { resolveInstallRoot() }

                when {
                    installRoot == null -> Text(
                        "Could not determine where HypdroidDesktop is running from.",
                        modifier = Modifier.padding(16.dp),
                    )
                    else -> when (val result = remember(installRoot) { scanGames(installRoot) }) {
                        is ScanResult.NotAHypseusInstall -> Text(
                            "Not inside a hypseus installation: ${result.checkedPath}",
                            modifier = Modifier.padding(16.dp),
                        )
                        is ScanResult.Found -> if (result.games.isEmpty()) {
                            Text(
                                "No games found in this install.",
                                modifier = Modifier.padding(16.dp),
                            )
                        } else {
                            LazyColumn(modifier = Modifier.padding(16.dp)) {
                                items(result.games) { game ->
                                    Button(onClick = { launchGame(game, installRoot) }) {
                                        Text(game.name)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
