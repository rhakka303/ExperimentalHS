package org.libsdl.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

private enum class Screen { HOME, SETTINGS }

private const val PREFS_NAME = "hypdroid_prefs"
private const val PREF_GAME_FOLDER_URI = "game_folder_uri"
private const val PREF_MEDIA_FOLDER_URI = "media_folder_uri"

// Generalized over a pref key so the same save/load/clear logic covers both
// the game folder (#36) and the media folder (#30) without duplicating it.
private fun savePersistedFolderUri(context: Context, key: String, uri: Uri) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(key, uri.toString())
        .apply()
}

private fun clearPersistedFolderUri(context: Context, key: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .remove(key)
        .apply()
}

private fun loadPersistedFolderUri(context: Context, key: String): Uri? {
    val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(key, null) ?: return null
    val uri = Uri.parse(stored)
    // takePersistableUriPermission() survives restarts on its own, but not a
    // permission revocation (e.g. user cleared it in Android's own Settings)
    // or the SD card the tree lived on being removed - persistedUriPermissions
    // is the actual source of truth for whether we still really hold it.
    val stillGranted = context.contentResolver.persistedUriPermissions.any {
        it.uri == uri && it.isReadPermission
    }
    return if (stillGranted) uri else null
}

@Composable
private fun HypdroidApp(context: Context) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var gameFolderPath by remember { mutableStateOf<String?>(null) }
    var pathResolutionFailed by remember { mutableStateOf(false) }
    var games by remember { mutableStateOf<List<Game>>(emptyList()) }
    var mediaFolderPath by remember { mutableStateOf<String?>(null) }

    fun applyGameFolder(uri: Uri) {
        val realPath = resolveRealPath(uri)
        if (realPath == null) {
            pathResolutionFailed = true
            gameFolderPath = null
            games = emptyList()
            clearPersistedFolderUri(context, PREF_GAME_FOLDER_URI)
        } else {
            pathResolutionFailed = false
            gameFolderPath = realPath
            // hypseus hard-fails video init without its own pics/ assets in
            // the home dir (confirmed via a real on-device boot test) - make
            // sure they're there before anything tries to launch a game.
            ensureHypseusAssets(context)
            // Scan automatically as soon as a folder is picked - no separate
            // manual "scan" action, per the owner's UX guidance.
            games = scanGames(File(realPath))
        }
    }

    fun applyMediaFolder(uri: Uri) {
        // No scanning here - nothing consumes the media folder yet (that's
        // Phase E). Just resolve + persist, same as the game folder, minus
        // the game-specific side effects.
        val realPath = resolveRealPath(uri)
        mediaFolderPath = realPath
        if (realPath == null) {
            clearPersistedFolderUri(context, PREF_MEDIA_FOLDER_URI)
        }
    }

    // Re-resolve previously-picked folders on every fresh launch, so the
    // dashboard doesn't reset to empty on every restart/crash (#36) - the
    // SAF grant itself already survives restarts via
    // takePersistableUriPermission(), this just re-runs the same
    // resolve(+scan) pipeline the pickers use, automatically.
    LaunchedEffect(Unit) {
        val persistedGameUri = loadPersistedFolderUri(context, PREF_GAME_FOLDER_URI)
        if (persistedGameUri == null) {
            // Either nothing was ever picked, or the grant is no longer
            // valid (revoked, SD card removed) - fall back to the empty
            // "+" state rather than showing a stale/broken path.
            clearPersistedFolderUri(context, PREF_GAME_FOLDER_URI)
        } else {
            applyGameFolder(persistedGameUri)
        }

        val persistedMediaUri = loadPersistedFolderUri(context, PREF_MEDIA_FOLDER_URI)
        if (persistedMediaUri == null) {
            clearPersistedFolderUri(context, PREF_MEDIA_FOLDER_URI)
        } else {
            applyMediaFolder(persistedMediaUri)
        }
    }

    val pickGameFolder = rememberLauncherForActivityResult(
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
        savePersistedFolderUri(context, PREF_GAME_FOLDER_URI, uri)
        applyGameFolder(uri)
    }

    // Used from both the dashboard's "+" and Settings' "Game folder" row -
    // same launcher instance, so both really do share one underlying value
    // rather than each keeping their own copy (#30 acceptance criteria).
    val onChooseGameFolder = { pickGameFolder.launch(null) }

    val pickMediaFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        savePersistedFolderUri(context, PREF_MEDIA_FOLDER_URI, uri)
        applyMediaFolder(uri)
    }

    when (currentScreen) {
        Screen.HOME -> HomeScreen(
            gameFolderPath = gameFolderPath,
            pathResolutionFailed = pathResolutionFailed,
            games = games,
            onChooseFolder = onChooseGameFolder,
            onOpenSettings = { currentScreen = Screen.SETTINGS },
            onPlay = { game ->
                val homeDir = gameFolderPath
                if (homeDir != null) {
                    val intent = Intent(context, HypseusActivity::class.java)
                        .putExtra(HypseusActivity.EXTRA_ARGS, buildLaunchArgs(game, homeDir))
                    context.startActivity(intent)
                }
            },
        )
        Screen.SETTINGS -> SettingsScreen(
            gameFolderPath = gameFolderPath,
            mediaFolderPath = mediaFolderPath,
            onChangeGameFolder = onChooseGameFolder,
            onChangeMediaFolder = { pickMediaFolder.launch(null) },
            onBack = { currentScreen = Screen.HOME },
        )
    }
}

@Composable
private fun HomeScreen(
    gameFolderPath: String?,
    pathResolutionFailed: Boolean,
    games: List<Game>,
    onChooseFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlay: (Game) -> Unit,
) {
    // Per the owner's #36 redesign: the dashboard never shows the raw
    // folder path - just the game list, plus a "+" (add/change game
    // folder) and gear (Settings, #30) icon pair in the upper right. The
    // full visual gallery (game tiles, box art) is Phase E; this is still
    // just a plain list - the scanning mechanics are Phase D's job (#28).
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopEnd),
        ) {
            IconButton(onClick = onChooseFolder) {
                Icon(Icons.Filled.Add, contentDescription = "Choose game folder")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (pathResolutionFailed) {
                    Text(
                        "Couldn't resolve a real filesystem path for that folder " +
                            "on this device. Try a different folder.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                } else if (gameFolderPath == null) {
                    Text("No games yet. Tap + to choose a game folder.")
                } else if (games.isEmpty()) {
                    Text("No games found in this folder.")
                } else {
                    Text("${games.size} game(s) found - tap to play:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        games.forEach { game ->
                            Text(
                                "${game.name}  (${game.category})",
                                modifier = Modifier
                                    .clickable { onPlay(game) }
                                    .padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * #30's Settings screen: Game folder and Media folder pickers (reusing the
 * exact same SAF pattern/persisted state as #36 for the game folder - not a
 * separate copy), plus a stub "Controller Configuration" row that will be
 * wired up by #41. There's no navigation-library backstack in this app (just
 * a plain Screen enum toggle in HypdroidApp), so this screen has to handle
 * its own way back to the dashboard: a back arrow in a simple top bar, and a
 * BackHandler so Android's system back button returns to Home instead of
 * falling through to exit the whole app.
 */
@Composable
private fun SettingsScreen(
    gameFolderPath: String?,
    mediaFolderPath: String?,
    onChangeGameFolder: () -> Unit,
    onChangeMediaFolder: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsRow(
            label = "Game folder",
            subtitle = gameFolderPath ?: "Not set",
            buttonLabel = "Change",
            onClick = onChangeGameFolder,
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsRow(
            label = "Media folder",
            subtitle = mediaFolderPath ?: "Not set",
            buttonLabel = "Change",
            onClick = onChangeMediaFolder,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Real functionality is #41 - this row just needs to exist in the
        // layout for now, per the same stub precedent #36 set for the gear
        // icon itself.
        SettingsRow(
            label = "Controller Configuration",
            subtitle = "Coming soon",
            buttonLabel = "Configure",
            onClick = { /* stub - real screen is #41 */ },
        )
    }
}

@Composable
private fun SettingsRow(
    label: String,
    subtitle: String,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onClick) {
            Text(buttonLabel)
        }
    }
}
