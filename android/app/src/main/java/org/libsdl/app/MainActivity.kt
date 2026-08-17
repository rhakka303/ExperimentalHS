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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

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
    // #41 - set by ControllerConfigScreen only while it's actively listening
    // for a capture. dispatchKeyEvent()/dispatchGenericMotionEvent() forward
    // matching real input events here; both are null/false the rest of the
    // time, so normal Activity input handling (e.g. Compose's own click
    // handling) is completely unaffected outside that screen.
    var gamepadCaptureListener: ((token: String) -> Unit)? = null
    var gamepadCaptureListeningForAxis: Boolean = false

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val listener = gamepadCaptureListener
        if (listener != null) {
            val token = captureTokenForKeyEvent(event, gamepadCaptureListeningForAxis)
            if (token != null) {
                listener(token)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        val listener = gamepadCaptureListener
        if (listener != null) {
            val token = captureTokenForMotionEvent(event, gamepadCaptureListeningForAxis)
            if (token != null) {
                listener(token)
                return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

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

// A sealed class rather than a plain enum since #31's per-game options
// screen needs to carry which game it's scoped to.
private sealed class Screen {
    object Home : Screen()
    object Settings : Screen()
    object ControllerConfig : Screen()
    data class GameOptionsFor(val gameName: String) : Screen()
}

// Not private - #47's global Cover Art prefs (GameOptions.kt) live in this
// same prefs file, alongside the folder paths below.
const val PREFS_NAME = "hypdroid_prefs"
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
private fun HypdroidApp(context: MainActivity) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var gameFolderPath by remember { mutableStateOf<String?>(null) }
    var pathResolutionFailed by remember { mutableStateOf(false) }
    var games by remember { mutableStateOf<List<Game>>(emptyList()) }
    var mediaFolderPath by remember { mutableStateOf<String?>(null) }
    // #31 - per-game Cover Art/Bezel/Arguments, keyed by game name. Loaded
    // from SharedPreferences whenever the game list changes (including the
    // very first scan), kept in Compose state after that so edits made in
    // GameOptionsScreen show up immediately (e.g. the carousel's cover art)
    // without needing a rescan.
    var gameOptionsMap by remember { mutableStateOf<Map<String, GameOptions>>(emptyMap()) }
    // #52 fix - GameCarousel's own pagerState lives inside GameCarousel,
    // which (along with the rest of HomeScreen) is torn down entirely
    // whenever currentScreen leaves Screen.Home - so the focused page was
    // being lost every time the per-game Options screen (#31) was opened
    // and backed out of. Hoisting the current page here, one level up in a
    // composable that survives screen navigation, lets it be restored.
    var carouselPage by remember { mutableStateOf(0) }
    // #47 - Global Cover Art override (Settings). Plain synchronous
    // SharedPreferences reads, unlike the folder pickers above, so no
    // LaunchedEffect needed - the initial remember{} read is enough.
    var globalCoverArtEnabled by remember { mutableStateOf(loadGlobalCoverArtEnabled(context)) }
    var globalCoverArtType by remember { mutableStateOf(loadGlobalCoverArtType(context)) }

    LaunchedEffect(games) {
        gameOptionsMap = games.associate { it.name to loadGameOptions(context, it.name) }
    }

    fun updateGameOptions(gameName: String, updated: GameOptions) {
        gameOptionsMap = gameOptionsMap + (gameName to updated)
    }

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

    when (val screen = currentScreen) {
        Screen.Home -> HomeScreen(
            gameFolderPath = gameFolderPath,
            mediaFolderPath = mediaFolderPath,
            pathResolutionFailed = pathResolutionFailed,
            games = games,
            gameOptionsMap = gameOptionsMap,
            globalCoverArtEnabled = globalCoverArtEnabled,
            globalCoverArtType = globalCoverArtType,
            carouselPage = carouselPage,
            onCarouselPageChanged = { carouselPage = it },
            onChooseFolder = onChooseGameFolder,
            onOpenSettings = { currentScreen = Screen.Settings },
            onOpenOptions = { game -> currentScreen = Screen.GameOptionsFor(game.name) },
            onPlay = { game ->
                val homeDir = gameFolderPath
                if (homeDir != null) {
                    val args = buildLaunchArgs(game, homeDir).toMutableList()
                    val options = gameOptionsMap[game.name]
                    if (options?.bezelEnabled == true) {
                        args += bezelLaunchArgs(mediaFolderPath, game.name)
                    }
                    options?.arguments?.forEach { entry ->
                        args += entry.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    }
                    val intent = Intent(context, HypseusActivity::class.java)
                        .putExtra(HypseusActivity.EXTRA_ARGS, args.toTypedArray())
                    context.startActivity(intent)
                }
            },
        )
        Screen.Settings -> SettingsScreen(
            gameFolderPath = gameFolderPath,
            mediaFolderPath = mediaFolderPath,
            globalCoverArtEnabled = globalCoverArtEnabled,
            globalCoverArtType = globalCoverArtType,
            onChangeGameFolder = onChooseGameFolder,
            onChangeMediaFolder = { pickMediaFolder.launch(null) },
            onGlobalCoverArtToggle = { enabled ->
                saveGlobalCoverArtEnabled(context, enabled)
                globalCoverArtEnabled = enabled
            },
            onGlobalCoverArtTypeChange = { type ->
                saveGlobalCoverArtType(context, type)
                globalCoverArtType = type
            },
            onOpenControllerConfig = { currentScreen = Screen.ControllerConfig },
            onBack = { currentScreen = Screen.Home },
        )
        Screen.ControllerConfig -> {
            val homeDir = gameFolderPath
            if (homeDir == null) {
                // Shouldn't normally be reachable (the row that opens this
                // screen only exists once a game folder is set), but fall
                // back to Settings rather than crash if it somehow is - as
                // a state change, this has to happen in an effect, not
                // directly in the composable body.
                LaunchedEffect(Unit) { currentScreen = Screen.Settings }
            } else {
                ControllerConfigScreen(
                    activity = context,
                    gameFolderPath = homeDir,
                    onBack = { currentScreen = Screen.Settings },
                )
            }
        }
        is Screen.GameOptionsFor -> {
            val game = games.find { it.name == screen.gameName }
            if (game == null) {
                // Game list changed out from under this screen (folder
                // re-picked, etc.) - fall back rather than crash.
                LaunchedEffect(Unit) { currentScreen = Screen.Home }
            } else {
                val options = gameOptionsMap[game.name] ?: GameOptions(null, false, emptyList())
                GameOptionsScreen(
                    game = game,
                    options = options,
                    globalCoverArtEnabled = globalCoverArtEnabled,
                    onCoverArtChange = { type ->
                        saveCoverArt(context, game.name, type)
                        updateGameOptions(game.name, options.copy(coverArt = type))
                    },
                    onBezelToggle = { enabled ->
                        saveBezelEnabled(context, game.name, enabled)
                        updateGameOptions(game.name, options.copy(bezelEnabled = enabled))
                    },
                    onAddArgument = { arg ->
                        val updated = options.arguments + arg
                        saveArguments(context, game.name, updated)
                        updateGameOptions(game.name, options.copy(arguments = updated))
                    },
                    onRemoveArgument = { arg ->
                        val updated = options.arguments - arg
                        saveArguments(context, game.name, updated)
                        updateGameOptions(game.name, options.copy(arguments = updated))
                    },
                    onBack = { currentScreen = Screen.Home },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    gameFolderPath: String?,
    mediaFolderPath: String?,
    pathResolutionFailed: Boolean,
    games: List<Game>,
    gameOptionsMap: Map<String, GameOptions>,
    globalCoverArtEnabled: Boolean,
    globalCoverArtType: CoverArtType,
    carouselPage: Int,
    onCarouselPageChanged: (Int) -> Unit,
    onChooseFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOptions: (Game) -> Unit,
    onPlay: (Game) -> Unit,
) {
    // Per the owner's #36 redesign: the dashboard never shows the raw
    // folder path - just the game carousel, plus a "+" (add/change game
    // folder) and gear (Settings, #30) icon pair in the upper right.
    //
    // The icon Row is declared LAST, not first - #44's full-screen
    // HorizontalPager (inside GameCarousel) claims pointer input across its
    // entire fillMaxSize() bounds, including the corner where these icons
    // sit, even though no card is visually there. A composable declared
    // earlier in a Box is drawn/hit-tested underneath one declared later,
    // so putting the Row first left it visually correct but untouchable -
    // a real regression caught live on-device once the carousel landed.
    // Declaring it last restores the icons' touch priority.
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                GameCarousel(
                    games = games,
                    mediaFolderPath = mediaFolderPath,
                    gameOptionsMap = gameOptionsMap,
                    globalCoverArtEnabled = globalCoverArtEnabled,
                    globalCoverArtType = globalCoverArtType,
                    initialPage = carouselPage,
                    onPageChanged = onCarouselPageChanged,
                    onPlay = onPlay,
                    onOpenOptions = onOpenOptions,
                )
            }
        }

        // Brand wordmark, upper-left - balances the +/gear icons on the
        // right. Declared after the carousel `Box`, same reasoning as the
        // icon Row below: a static image doesn't need touch priority itself,
        // but keeping every top-bar element declared after the full-screen
        // carousel is the established, working pattern in this file.
        Image(
            painter = painterResource(id = R.drawable.hypdroid_logo),
            contentDescription = "Hypdroid",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .height(40.dp),
        )

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
    }
}

/**
 * #44 - Eden-style carousel: one game centered/highlighted, neighbors
 * peeking in at reduced scale, swipe to page between them, tap the
 * centered card to launch. First pass shows box art only (the owner's
 * available content right now) - per-game choice of which media type to
 * show (#31's future "Cover Art" field: CD/Logo/Box/Text) isn't built yet,
 * so every card just tries box art and falls back to a plain text card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCarousel(
    games: List<Game>,
    mediaFolderPath: String?,
    gameOptionsMap: Map<String, GameOptions>,
    globalCoverArtEnabled: Boolean,
    globalCoverArtType: CoverArtType,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onPlay: (Game) -> Unit,
    onOpenOptions: (Game) -> Unit,
) {
    // #52 fix - restores whatever page was focused before navigating away
    // (e.g. to #31's Options screen and back), instead of always starting
    // over at page 0 - this composable itself gets torn down and recreated
    // on every trip through Screen.Home, so the initial value has to come
    // from outside (HypdroidApp), not this function's own remembered state.
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (games.size - 1).coerceAtLeast(0)),
        pageCount = { games.size },
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChanged(it) }
    }
    val coroutineScope = rememberCoroutineScope()
    // HorizontalPager doesn't respond to d-pad left/right on its own the
    // way LazyColumn responds to up/down "for free" (Compose's built-in
    // focus-traversal handles simple linear lists, but not page-changing
    // gestures like this) - this is gamepad-first hardware, so real d-pad
    // paging needs to be wired up explicitly, not left as a touch-only gap.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    HorizontalPager(
        state = pagerState,
        // A fixed, modest page width (rather than the default full-width
        // page) is what actually produces the Eden-style look on this wide
        // landscape screen - a full-width page left the narrow portrait
        // card pinned to the page's start edge with a huge empty gap
        // before the next page, instead of a tight, centered carousel.
        pageSize = PageSize.Fixed(420.dp),
        contentPadding = PaddingValues(horizontal = (LocalConfiguration.current.screenWidthDp.dp - 420.dp) / 2),
        pageSpacing = 16.dp,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                // #31 - pressing down opens the focused game's options
                // screen, the gamepad equivalent of a touch long-press
                // (see GameCard's combinedClickable below).
                if (event.key == Key.DirectionDown) {
                    onOpenOptions(games[pagerState.currentPage])
                    return@onKeyEvent true
                }
                val targetPage = when (event.key) {
                    Key.DirectionLeft -> pagerState.currentPage - 1
                    Key.DirectionRight -> pagerState.currentPage + 1
                    else -> return@onKeyEvent false
                }
                if (targetPage in games.indices) {
                    coroutineScope.launch { pagerState.animateScrollToPage(targetPage) }
                }
                true
            },
    ) { page ->
        val game = games[page]
        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
        val scale = lerp(0.82f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
        // #47 - Global Cover Art (Settings) wins over each game's own #31
        // choice while it's on; resolveCoverArtFile's fallback-to-BOX
        // behavior only kicks in for the null (no override at all) case, so
        // this always passes a concrete type when global is enabled.
        val coverArtOverride = if (globalCoverArtEnabled) {
            globalCoverArtType
        } else {
            gameOptionsMap[game.name]?.coverArt
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GameCard(
                game = game,
                coverArtFile = resolveCoverArtFile(mediaFolderPath, game.name, coverArtOverride),
                scale = scale,
                onClick = { onPlay(game) },
                onLongClick = { onOpenOptions(game) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCard(
    game: Game,
    coverArtFile: File?,
    scale: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxHeight(0.8f)
            .aspectRatio(0.7f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // #31 - long-press is touch's equivalent of pressing down on
            // the d-pad (see GameCarousel's onKeyEvent above) - opens this
            // game's options screen (Cover Art/Bezel/Arguments).
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        if (coverArtFile != null) {
            AsyncImage(
                model = coverArtFile,
                contentDescription = game.name,
                // Fit, not Crop - the real box art PNGs include a
                // transparent shadow margin around the rendered box, and
                // Crop was cutting into the top/bottom of that to fill the
                // card's fixed aspect ratio (confirmed by comparing the
                // in-app render against the source file directly).
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // No art for this game's resolved Cover Art type (or "Text"
            // was explicitly chosen, or no media folder set) - falls back
            // to a plain text card rather than an error/blank space,
            // matching the app's existing missing-content pattern.
            Text(
                game.name,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/**
 * #30's Settings screen: Game folder and Media folder pickers (reusing the
 * exact same SAF pattern/persisted state as #36 for the game folder - not a
 * separate copy), plus a "Controller Configuration" row opening #41's live
 * gamepad-mapping screen. There's no navigation-library backstack in this
 * app (just a plain Screen enum toggle in HypdroidApp), so this screen has
 * to handle its own way back to the dashboard: a back arrow in a simple top
 * bar, and a BackHandler so Android's system back button returns to Home
 * instead of falling through to exit the whole app.
 */
@Composable
private fun SettingsScreen(
    gameFolderPath: String?,
    mediaFolderPath: String?,
    globalCoverArtEnabled: Boolean,
    globalCoverArtType: CoverArtType,
    onChangeGameFolder: () -> Unit,
    onChangeMediaFolder: () -> Unit,
    onGlobalCoverArtToggle: (Boolean) -> Unit,
    onGlobalCoverArtTypeChange: (CoverArtType) -> Unit,
    onOpenControllerConfig: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var showGlobalCoverArtPicker by remember { mutableStateOf(false) }

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

        // #47 - global override for every game's Cover Art at once, instead
        // of setting each one individually via #31's per-game screen. Off by
        // default, matching #31's existing per-game behavior until this was
        // added.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Global Cover Art", style = MaterialTheme.typography.titleMedium)
                Text(
                    "On: same art for every game. Off: each game picks its own.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = globalCoverArtEnabled, onCheckedChange = onGlobalCoverArtToggle)
        }
        if (globalCoverArtEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    globalCoverArtType.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { showGlobalCoverArtPicker = true }) { Text("Change") }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsRow(
            label = "Controller Configuration",
            subtitle = if (gameFolderPath == null) {
                "Set a game folder first"
            } else {
                "Assign gamepad buttons per action"
            },
            buttonLabel = "Configure",
            onClick = onOpenControllerConfig,
            enabled = gameFolderPath != null,
        )
    }

    if (showGlobalCoverArtPicker) {
        CoverArtPickerDialog(
            onSelect = { type ->
                onGlobalCoverArtTypeChange(type)
                showGlobalCoverArtPicker = false
            },
            onDismiss = { showGlobalCoverArtPicker = false },
        )
    }
}

@Composable
private fun SettingsRow(
    label: String,
    subtitle: String,
    buttonLabel: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onClick, enabled = enabled) {
            Text(buttonLabel)
        }
    }
}
