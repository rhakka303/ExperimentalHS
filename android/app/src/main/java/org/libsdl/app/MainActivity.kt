package org.libsdl.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * The real app entry point (see AndroidManifest.xml - this replaced
 * HypseusActivity as the launcher). HypseusActivity is launched explicitly
 * from here, with constructed CLI-equivalent args, once the user picks a
 * game - it's not meant to be started directly.
 *
 * Deliberately does NOT request All Files Access (MANAGE_EXTERNAL_STORAGE) -
 * that grants read/write/delete over the *entire device*, not just the
 * folders the user picks here. Uses Storage Access Framework instead:
 * genuinely scoped to exactly the folder(s) selected, nothing else.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HypdroidApp(context = this)
                }
            }
        }
    }
}

/**
 * SAF hands back a content:// tree URI, not a real path - hypseus's file
 * I/O is plain fopen() and can't use that. This resolves the real
 * filesystem path from the URI's document ID, which encodes it as
 * "<volume>:<relative path>" - "primary" for internal storage, or the
 * SD card's actual volume ID otherwise. This mapping isn't official/
 * guaranteed-stable API, but is widely relied on in practice and verified
 * working on the actual target hardware (Retroid Pocket 5).
 *
 * Returns null if the path can't be resolved or doesn't exist - caller
 * must treat that as "unsupported", not silently proceed with a broken path.
 */
private fun resolveRealPath(treeUri: Uri): String? {
    val docId = try {
        DocumentsContract.getTreeDocumentId(treeUri)
    } catch (e: Exception) {
        return null
    }
    val split = docId.split(":", limit = 2)
    if (split.size < 2) return null
    val volume = split[0]
    val relativePath = split[1]

    val path = if (volume.equals("primary", ignoreCase = true)) {
        "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
    } else {
        "/storage/$volume/$relativePath"
    }

    val file = File(path)
    return if (file.exists() && file.isDirectory) path else null
}

private enum class Screen { HOME }

@Composable
private fun HypdroidApp(context: Context) {
    var gameFolderPath by remember { mutableStateOf<String?>(null) }
    var pathResolutionFailed by remember { mutableStateOf(false) }
    var games by remember { mutableStateOf<List<Game>>(emptyList()) }

    val pickFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Persist access to just this folder tree across app restarts -
        // this is the SAF equivalent of "remembering" the folder, scoped
        // to only what was picked, unlike a blanket storage permission.
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val realPath = resolveRealPath(uri)
        if (realPath == null) {
            pathResolutionFailed = true
            gameFolderPath = null
            games = emptyList()
        } else {
            pathResolutionFailed = false
            gameFolderPath = realPath
            // Scan automatically as soon as a folder is picked - no separate
            // manual "scan" action, per the owner's UX guidance.
            games = scanGames(File(realPath))
        }
    }

    HomeScreen(
        gameFolderPath = gameFolderPath,
        pathResolutionFailed = pathResolutionFailed,
        games = games,
        onChooseFolder = { pickFolder.launch(null) },
    )
}

@Composable
private fun HomeScreen(
    gameFolderPath: String?,
    pathResolutionFailed: Boolean,
    games: List<Game>,
    onChooseFolder: () -> Unit,
) {
    // Empty-dashboard-with-a-"+"-button state, per the owner's UX guidance.
    // The full visual gallery (game tiles, box art) is Phase E; this is
    // just a plain list - the scanning mechanics are Phase D's job (#28).
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (pathResolutionFailed) {
                Text(
                    "Couldn't resolve a real filesystem path for that folder " +
                        "on this device. Try a different folder.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onChooseFolder) {
                    Text("Choose Game Folder")
                }
            } else if (gameFolderPath == null) {
                Text("No games yet.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onChooseFolder) {
                    Text("+  Choose Game Folder")
                }
            } else {
                Text("Game folder:")
                Text(gameFolderPath, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onChooseFolder) {
                    Text("Change Game Folder")
                }
                Spacer(modifier = Modifier.height(24.dp))
                if (games.isEmpty()) {
                    Text("No games found in this folder.")
                } else {
                    Text("${games.size} game(s) found:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        games.forEach { game ->
                            Text("${game.name}  (${game.category})")
                        }
                    }
                }
            }
        }
    }
}
