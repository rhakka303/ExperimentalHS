import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File
import java.io.IOException
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface Screen {
    data object Carousel : Screen
    data object Settings : Screen
    // #24 - About is a genuinely blank page for now, matching #19's Cover
    // Art stub precedent: present so the Settings screen matches the
    // shape it will eventually need, not functional yet. Manage Game
    // Folder and Touch Controls (both real cards on Android's own
    // Settings screen) are deliberately excluded - no touchscreen on
    // desktop, and #41 ruled out a media folder picker permanently
    // (fixed, auto-created location, not user-configurable). Controls
    // itself got real content in #46 - Keyboard mode for now, Controller
    // 1/Controller 2/Mouse in #47/#48/#49.
    data object AppSettings : Screen
    // #43 - real content on day one, not a blank stub like About:
    // Preserve Video Aspect Ratio moved here from AppSettings, plus the
    // new Full Screen toggle.
    data object VideoSettings : Screen
    data object Controls : Screen
    data object About : Screen
    data class GameOptionsFor(val game: Game) : Screen
    data class GameHackFor(val game: Game) : Screen
}

/**
 * #17 - carousel UI, replacing #11's plain list. #19 adds the per-game
 * options screen, the Game Hacks screen, and navigation between all three.
 *
 * #43 - launcherFolder is resolved here, before Window() rather than
 * inside its content, specifically so the initial AppSettings load (and
 * therefore the initial WindowState.placement) is known before the
 * window is ever shown - avoids a windowed-then-fullscreen flash on
 * startup. windowState itself is created here and threaded down to
 * VideoSettingsScreen (to react to the Full Screen toggle) and
 * GameCarousel (to know whether Full Screen is on, for the X close icon
 * and Escape-quits behavior) - the first piece of state in this project
 * that needs to reach both the top-level Window and deep into the
 * composable tree at once.
 */
fun main() = application {
    val launcherFolder = remember { resolveLauncherFolder() }
    val initialFullscreenEnabled = remember(launcherFolder) {
        launcherFolder?.let { loadAppSettings(it).fullscreenEnabled } ?: false
    }
    // #43 - windowed mode always launches maximized, never minimized:
    // the app was observed launching minimized in practice, which turned
    // out to be nothing more than the OS/JVM default for a Window with
    // no explicit WindowState at all - not a deliberate choice anywhere
    // in this code. Passing a real WindowState here is the actual fix,
    // not a workaround.
    val windowState = rememberWindowState(
        placement = if (initialFullscreenEnabled) WindowPlacement.Fullscreen else WindowPlacement.Maximized,
    )

    Window(onCloseRequest = ::exitApplication, title = "HypdroidDesktop", state = windowState) {
        MaterialTheme(colorScheme = HypdroidColorScheme) {
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
                            HypdroidApp(
                                games = result.games,
                                installRoot = installRoot,
                                launcherFolder = launcherFolder,
                                windowState = windowState,
                                onQuit = ::exitApplication,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HypdroidApp(
    games: List<Game>,
    installRoot: File,
    launcherFolder: File?,
    windowState: WindowState,
    onQuit: () -> Unit,
) {
    // #19 - lifted out of GameCarousel so the pager's position survives a
    // trip to another screen and back ("Back returns to the carousel, on
    // the same card that was open"). GameCarousel gets torn down and
    // recreated when the screen switches away and back, so remembering
    // the page inside it (as #17 originally did) would reset to page 0
    // every time - the same bug Android's own #52 fix already addressed.
    var carouselPage by remember { mutableStateOf(0) }
    // #48 fix - same reasoning as carouselPage above: ControlsScreen gets
    // torn down and recreated on a trip to the carousel (to launch a
    // game) and back, so remembering the selected Controller Type inside
    // it reset to Keyboard every time - confirmed live (owner: "switched
    // to controller 1... came back to type and it defaulted to
    // keyboard").
    var controlsMode by remember { mutableStateOf("Keyboard") }
    var screen by remember { mutableStateOf<Screen>(Screen.Carousel) }

    when (val s = screen) {
        is Screen.Carousel -> GameCarousel(
            games = games,
            installRoot = installRoot,
            launcherFolder = launcherFolder,
            initialPage = carouselPage,
            onPageChanged = { carouselPage = it },
            onOpenOptions = { screen = Screen.GameOptionsFor(it) },
            onOpenSettings = { screen = Screen.Settings },
            onQuit = onQuit,
        )
        is Screen.Settings -> SettingsScreen(
            onOpenAppSettings = { screen = Screen.AppSettings },
            onOpenControls = { screen = Screen.Controls },
            onOpenAbout = { screen = Screen.About },
            onOpenVideoSettings = { screen = Screen.VideoSettings },
            onBack = { screen = Screen.Carousel },
        )
        is Screen.AppSettings -> AppSettingsScreen(launcherFolder = launcherFolder, onBack = { screen = Screen.Settings })
        is Screen.VideoSettings -> VideoSettingsScreen(
            launcherFolder = launcherFolder,
            windowState = windowState,
            onBack = { screen = Screen.Settings },
        )
        is Screen.Controls -> ControlsScreen(
            installRoot = installRoot,
            launcherFolder = launcherFolder,
            mode = controlsMode,
            onModeChanged = { controlsMode = it },
            onBack = { screen = Screen.Settings },
        )
        is Screen.About -> BlankPlaceholderScreen("About", onBack = { screen = Screen.Settings })
        is Screen.GameOptionsFor -> GameOptionsScreen(
            game = s.game,
            launcherFolder = launcherFolder,
            onOpenGameHack = { screen = Screen.GameHackFor(s.game) },
            onBack = { screen = Screen.Carousel },
        )
        is Screen.GameHackFor -> GameHackScreen(
            game = s.game,
            launcherFolder = launcherFolder,
            onBack = { screen = Screen.GameOptionsFor(s.game) },
        )
    }
}

// see GameCard's identical @Suppress comment - #29's background image
// reads the same runtime media/ files, not compile-time app resources.
@Suppress("DEPRECATION")
@Composable
private fun GameCarousel(
    games: List<Game>,
    installRoot: File,
    launcherFolder: File?,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onOpenOptions: (Game) -> Unit,
    onOpenSettings: () -> Unit,
    onQuit: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { games.size })
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    // #28 - loaded once per carousel composition, not per-card: GameCarousel
    // is torn down and recreated whenever the screen navigates away and
    // back (see #19's comment on carouselPage above), which is what keeps
    // this fresh after a trip to the options or settings screens without
    // needing its own reload trigger.
    //
    // #41 - mediaFolder now resolves inside launcherFolder, not
    // installRoot, and is nullable for the same reason appSettings/
    // gameOptionsMap already are: launcherFolder is only meaningful for
    // the packaged app (resolveLauncherFolder()'s own doc comment) - when
    // null, there's no folder to put media/ inside, so art resolution
    // simply comes back empty rather than resolving against nothing.
    val mediaFolder = remember(launcherFolder) { launcherFolder?.let { resolveMediaFolder(it) } }
    val appSettings = remember(launcherFolder) {
        launcherFolder?.let { loadAppSettings(it) } ?: AppSettings()
    }
    val gameOptionsMap = remember(launcherFolder, games) {
        launcherFolder?.let { loadAllOptions(it) } ?: emptyMap()
    }

    // #28 - see GameCardArt.kt's effectiveCoverArtFile for the real
    // Global-vs-per-game precedence rule this wraps.
    fun coverArtFileFor(game: Game): File? =
        mediaFolder?.let { effectiveCoverArtFile(it, game.name, appSettings, gameOptionsMap[game.name]) }

    // #29 - resolved from whichever game is currently centered, re-resolving
    // automatically on every recomposition since pagerState.currentPage is
    // itself observed state - no extra plumbing needed beyond what #28
    // already established. Default Art is an unconditional override, not a
    // missing-file fallback (see #27's backgroundArtFile doc comment).
    val focusedGame = games.getOrNull(pagerState.currentPage)
    val backgroundFile = if (mediaFolder != null && focusedGame != null) {
        backgroundArtFile(mediaFolder, focusedGame.name, appSettings.backgroundArtEnabled, appSettings.defaultArtEnabled)
    } else {
        null
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChanged(it) }
    }

    // #32/#46 - preserveAspectRatioEnabled/gamepadEnabled are app-level
    // (#31), not per-game; launchArgumentsFor slots them into the right
    // position in the argv.
    fun extraArgsFor(game: Game): List<String> =
        launcherFolder?.let {
            launchArgumentsFor(
                installRoot,
                loadOptions(it, game.name),
                game.name,
                appSettings.preserveAspectRatioEnabled,
                appSettings.gamepadEnabled,
            )
        } ?: emptyList()

    fun pageLeft() {
        val target = pagerState.currentPage - 1
        if (target >= 0) coroutineScope.launch { pagerState.animateScrollToPage(target) }
    }
    fun pageRight() {
        val target = pagerState.currentPage + 1
        if (target < games.size) coroutineScope.launch { pagerState.animateScrollToPage(target) }
    }
    fun launchCentered() {
        val game = games[pagerState.currentPage]
        launchGame(game, installRoot, extraArgsFor(game))
    }

    // #29 - full-screen background, the bottom-most layer. Crop, not Fit -
    // unlike box/CD art (#28), background images are meant to fully cover
    // the screen with no preserved-margin concern, matching Android's own
    // real background rendering exactly. Falls back to the existing plain
    // background (nothing rendered here) when resolution returns null,
    // for any of the reasons #27 documents.
    val backgroundBitmap = remember(backgroundFile) {
        backgroundFile?.let { file ->
            try {
                file.inputStream().buffered().use(::loadImageBitmap)
            } catch (e: IOException) {
                null
            }
        }
    }

    // #57 - real Hypdroid logo, the actual Android asset
    // (android/app/src/main/res/drawable/hypdroid_logo.png), copied in as
    // a plain classpath resource rather than wiring up Compose's full
    // resources system for one static image - see this story's own issue
    // for the tradeoff. Loaded once, not per-recomposition.
    val logoBitmap = remember { loadHypdroidLogo() }

    Box(modifier = Modifier.fillMaxSize()) {
        if (backgroundBitmap != null) {
            Image(
                bitmap = backgroundBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // #57/#65 - the top bar (logo + icons) is a real layout region
        // here, not an overlay floating on top of the carousel's own Box -
        // matches Android's real dashboard redesign exactly. The two
        // occupy separate, non-overlapping regions of this Column, which
        // is what avoids the touch/input-priority conflict Android's own
        // #49 fix (referenced in its #65 comment) had to work around back
        // when its icon row still overlaid the carousel directly - and
        // sets up cleaner input routing for phase 4's future gamepad
        // navigation, per the owner's own reasoning scoping this out.
        Column(modifier = Modifier.fillMaxSize()) {
            // #57/#66 - a real conditional scrim behind the whole row, not
            // per-icon circles (#19/#43's earlier approach here) - matches
            // Android's actual behavior: semi-transparent dark background
            // only while real background art is showing underneath, so
            // the logo/icons stay legible regardless of the art's own
            // tone/brightness. No scrim against the plain background -
            // already legible on its own.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (backgroundBitmap != null) {
                            Modifier.background(Color.Black.copy(alpha = 0.5f))
                        } else {
                            Modifier
                        },
                    )
                    .padding(8.dp),
            ) {
                if (logoBitmap != null) {
                    Image(
                        bitmap = logoBitmap,
                        contentDescription = "Hypdroid",
                        modifier = Modifier.height(40.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // White against the scrim always contrasts, since the
                // scrim itself is always dark - matches Android's own
                // real reasoning here exactly. Default theme color the
                // rest of the time, against the plain background.
                val iconTint = if (backgroundBitmap != null) Color.White else LocalContentColor.current
                if (appSettings.fullscreenEnabled) {
                    IconButton(onClick = onQuit) {
                        Icon(Icons.Filled.Close, contentDescription = "Quit", tint = iconTint)
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = iconTint)
                }
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val cardWidth = maxWidth * 0.32f

                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fixed(cardWidth),
                    contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
                    pageSpacing = 16.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester)
                        // A bare LaunchedEffect(Unit) calling requestFocus()
                        // raced this modifier's own attachment on desktop
                        // and threw "FocusRequester is not initialized" - a
                        // real timing difference from Android's equivalent
                        // code, not something to guess a retry-count for.
                        // onGloballyPositioned fires on every layout pass
                        // (window resizes included), not once, so it's
                        // guarded to request focus only the first time.
                        // Even guarded to fire after layout, requestFocus()
                        // still threw here in practice (confirmed twice on
                        // real hardware) - so this is wrapped rather than
                        // trusted further. Worst case if it still fails:
                        // auto-focus-on-launch doesn't happen, and the
                        // pager gets focus on the first click instead.
                        .onGloballyPositioned {
                            if (!hasRequestedInitialFocus) {
                                hasRequestedInitialFocus = true
                                try {
                                    focusRequester.requestFocus()
                                } catch (e: IllegalStateException) {
                                    // see comment above
                                }
                            }
                        }
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                // Ctrl matches hypseus's own KEY_BUTTON1
                                // default (SDLK_LCTRL, the confirm/action
                                // button) in both hypinput_gamepad.ini in
                                // this install and the plain hypinput.ini
                                // reference in the vendored source - the
                                // ecosystem's standard confirm key.
                                Key.Enter, Key.NumPadEnter, Key.CtrlLeft -> {
                                    launchCentered()
                                    true
                                }
                                // #19 - Down opens the centered card's
                                // options screen, alongside clicking its
                                // gear icon.
                                Key.DirectionDown -> {
                                    onOpenOptions(games[pagerState.currentPage])
                                    true
                                }
                                Key.DirectionLeft -> {
                                    pageLeft()
                                    true
                                }
                                Key.DirectionRight -> {
                                    pageRight()
                                    true
                                }
                                // #43 - carousel/home screen only: every
                                // other screen keeps its own existing
                                // Escape-to-back behavior untouched. Only
                                // quits while Full Screen is on - off
                                // means windowed mode, where Escape on the
                                // carousel does nothing (there's no back
                                // destination from the home screen),
                                // matching the owner's own description
                                // exactly.
                                Key.Escape -> {
                                    if (appSettings.fullscreenEnabled) {
                                        onQuit()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                else -> false
                            }
                        },
                ) { page ->
                    val game = games[page]
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                    val scale = 0.82f + (1f - 0.82f) * (1f - pageOffset.coerceIn(0f, 1f))
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GameCard(
                            game = game,
                            coverArtFile = coverArtFileFor(game),
                            scale = scale,
                            onClick = { launchGame(game, installRoot, extraArgsFor(game)) },
                            onOpenOptions = { onOpenOptions(game) },
                        )
                    }
                }

                // #17/#59 - visible mouse-clickable paging, the direct
                // mouse equivalent of the arrow keys. Local to this box,
                // not the top bar (#57) - a Windows-only addition, kept
                // where it's always been. Enlarged, white-on-scrim -
                // same real precedent as each card's own gear icon (#19):
                // a semi-transparent dark scrim circle keeps them legible
                // against any background, real art or plain.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(8.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(onClick = ::pageLeft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous game",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(8.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(onClick = ::pageRight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next game",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

// #57 - loads the real Hypdroid logo from a plain classpath resource
// (windows/src/main/resources/hypdroid_logo.png, copied in from the real
// Android asset). This genuinely is the compile-time bundled-asset case
// loadImageBitmap's own deprecation warning targets (unlike GameCard's
// runtime media/ files below) - the simpler classpath-resource path was
// a deliberate choice over wiring up Compose's full resources system for
// one static image, not an oversight. Returns null (silently, no crash)
// if the resource is ever missing - the top bar simply omits the logo.
@Suppress("DEPRECATION")
private fun loadHypdroidLogo(): ImageBitmap? =
    try {
        object {}.javaClass.getResourceAsStream("/hypdroid_logo.png")?.buffered()?.use(::loadImageBitmap)
    } catch (e: IOException) {
        null
    }

// loadImageBitmap is deprecated in favor of Compose's bundled resources
// library - that migration is for compile-time app assets, not runtime
// files from an external media/ folder chosen by the user (or, once #26
// has an override, a folder outside the app entirely), which is exactly
// what this reads. It's the right tool for this job, not an oversight.
@Suppress("DEPRECATION")
@Composable
private fun GameCard(game: Game, coverArtFile: File?, scale: Float, onClick: () -> Unit, onOpenOptions: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight(0.8f)
            .aspectRatio(0.7f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            // #54 - no background fill: with #29's real background art
            // live behind the carousel, an opaque surfaceVariant fill
            // blocked it out behind every card instead of letting it
            // show through. Android already hit and fixed this exact
            // problem (its own GameCard's real comment: "transparent
            // instead of surfaceVariant, so any remaining letterbox gap
            // blends into the background art... instead of showing a
            // gray box") - same fix here.
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // #28 - a TEXT type and a missing file both resolve to null from
        // #27's coverArtFile()/resolveCoverArtFile(), and deliberately
        // share this same plain-text fallback rather than two different
        // empty states. No caching/preloading beyond what remember()
        // already gives for free - not a performance story (#95's
        // crossfade equivalent is explicitly out of scope here).
        val coverArtBitmap = remember(coverArtFile) {
            coverArtFile?.let { file ->
                try {
                    file.inputStream().buffered().use(::loadImageBitmap)
                } catch (e: IOException) {
                    null
                }
            }
        }

        if (coverArtBitmap != null) {
            Image(
                bitmap = coverArtBitmap,
                contentDescription = game.name,
                // Fit, not Crop - real box/CD art PNGs include a
                // transparent shadow margin around the rendered art, and
                // Crop cuts into the top/bottom to fill the card's fixed
                // aspect ratio. Android hit and fixed this exact issue
                // (its own GameCard comment documents it); confirmed the
                // same problem live here with real CD art before fixing.
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(game.name, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
        }

        // #19 - gear icon, top-right corner, consistent position
        // regardless of card scale. A semi-transparent dark scrim behind
        // it (rather than relying on the icon's own color) keeps it
        // legible against any future cover art color, not just today's
        // plain placeholder background.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onOpenOptions),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Options for ${game.name}", tint = Color.White)
        }
    }
}

/**
 * #19 - matches Android's real GameOptionsScreen layout and save model:
 * Cover Art (stub - no media/cover-art system exists on Windows yet, that
 * is phase 3), Game Hacks (navigates to GameHackScreen below), Bezel/
 * Scorebezel Autofit/Overlay Bezel, and Arguments. Every field saves
 * immediately on change, no separate Save button - matching Android's own
 * documented behavior exactly, not a Windows invention.
 */
@Composable
private fun GameOptionsScreen(game: Game, launcherFolder: File?, onOpenGameHack: () -> Unit, onBack: () -> Unit) {
    var options by remember(game) {
        mutableStateOf(if (launcherFolder != null) loadOptions(launcherFolder, game.name) else GameOptions())
    }
    var newArgument by remember(game) { mutableStateOf("") }
    var showCoverArtPicker by remember(game) { mutableStateOf(false) }

    fun persist(updated: GameOptions) {
        options = updated
        if (launcherFolder != null) saveOptions(launcherFolder, game.name, updated)
    }

    // Escape matches hypseus's own KEY_QUIT default (SDLK_ESCAPE) - no
    // dedicated "back" binding exists in hypinput_gamepad.ini, but Escape
    // is the closest ecosystem convention, same reasoning as Ctrl for
    // launch in #17.
    val focusRequester = remember(game) { FocusRequester() }
    var hasRequestedInitialFocus by remember(game) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .focusRequester(focusRequester)
            .onGloballyPositioned {
                if (!hasRequestedInitialFocus) {
                    hasRequestedInitialFocus = true
                    try {
                        focusRequester.requestFocus()
                    } catch (e: IllegalStateException) {
                        // see #17's identical guard on GameCarousel
                    }
                }
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onBack()
                    true
                } else {
                    false
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Options: ${game.name}", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                // #30 - real per-game Cover Art override. Shows the
                // resolved effective type (override, else BOX) -
                // confirmed on a real device that a game with no override
                // still shows BOX, not a blank state.
                OutlinedCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Cover Art", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Button(onClick = { showCoverArtPicker = true }) { Text("Change") }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text((options.coverArtOverride ?: CoverArtType.BOX).name, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenGameHack)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Game Hacks", style = MaterialTheme.typography.titleMedium)
                        Text("Custom game fixes", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bezel", style = MaterialTheme.typography.titleMedium)
                            Text(if (options.bezelEnabled) "On" else "Off", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(
                            checked = options.bezelEnabled,
                            onCheckedChange = { persist(options.copy(bezelEnabled = it)) },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Scorebezel Autofit", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (options.scorebezelAutofit) "On" else "Off",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = options.scorebezelAutofit,
                            onCheckedChange = { persist(options.copy(scorebezelAutofit = it)) },
                        )
                    }

                    if (options.bezelEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Overlay Bezel", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (options.overlayBezel) "On" else "Off",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text("Makes overlays a priority", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = options.overlayBezel,
                                onCheckedChange = { persist(options.copy(overlayBezel = it)) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
                Text("Arguments", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newArgument,
                        onValueChange = { newArgument = it },
                        modifier = Modifier.weight(1f).onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                val trimmed = newArgument.trim()
                                if (trimmed.isNotEmpty()) {
                                    persist(options.copy(arguments = options.arguments + trimmed))
                                    newArgument = ""
                                }
                                true
                            } else {
                                false
                            }
                        },
                        singleLine = true,
                        placeholder = { Text("-fastboot") },
                    )
                    Button(
                        onClick = {
                            val trimmed = newArgument.trim()
                            if (trimmed.isNotEmpty()) {
                                persist(options.copy(arguments = options.arguments + trimmed))
                                newArgument = ""
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("Add") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    options.arguments.forEachIndexed { index, argument ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Text(argument, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                persist(options.copy(arguments = options.arguments.toMutableList().also { it.removeAt(index) }))
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove $argument")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCoverArtPicker) {
        CoverArtPickerDialog(
            onSelect = { type ->
                persist(options.copy(coverArtOverride = type))
                showCoverArtPicker = false
            },
            onDismiss = { showCoverArtPicker = false },
        )
    }
}

/**
 * #30 - shared cover art type picker: CD/LOGO/BOX/TEXT/Cancel, confirmed
 * against two real screenshots (per-game and, later, #31's Global Cover
 * Art) - both use the exact same dialog shape on the real Android app,
 * which is why this is a standalone composable rather than something
 * built separately per screen.
 */
@Composable
private fun CoverArtPickerDialog(onSelect: (CoverArtType) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 4.dp) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Cover Art", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                CoverArtType.entries.forEach { type ->
                    Text(
                        type.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(type) }
                            .padding(vertical = 12.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable(onClick = onDismiss)
                        .padding(8.dp),
                )
            }
        }
    }
}

/**
 * #19 - matches Android's GameHackScreen shape: one real toggle (Aspect
 * Ratio Bezel Fix) plus one genuinely empty placeholder card, exactly
 * mirroring Android's own current state (confirmed live on real hardware,
 * not assumed).
 */
@Composable
private fun GameHackScreen(game: Game, launcherFolder: File?, onBack: () -> Unit) {
    var options by remember(game) {
        mutableStateOf(if (launcherFolder != null) loadOptions(launcherFolder, game.name) else GameOptions())
    }

    // Escape matches hypseus's own KEY_QUIT default - same as #19's
    // GameOptionsScreen.
    val focusRequester = remember(game) { FocusRequester() }
    var hasRequestedInitialFocus by remember(game) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .focusRequester(focusRequester)
            .onGloballyPositioned {
                if (!hasRequestedInitialFocus) {
                    hasRequestedInitialFocus = true
                    try {
                        focusRequester.requestFocus()
                    } catch (e: IllegalStateException) {
                        // see #17's identical guard on GameCarousel
                    }
                }
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onBack()
                    true
                } else {
                    false
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Game Hacks: ${game.name}", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Aspect Ratio Bezel Fix", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Matches this game's bezel to the video - only confirmed on one gun game so far",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = options.aspectBezelFix,
                            onCheckedChange = {
                                val updated = options.copy(aspectBezelFix = it)
                                options = updated
                                if (launcherFolder != null) saveOptions(launcherFolder, game.name, updated)
                            },
                        )
                    }
                }
            }

            // Blank/TBD, matching Android's own current state exactly.
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {}
            }
        }
    }
}

/**
 * #31 - replaces #24's stub with real content, matching the real Android
 * AppSettingsScreen exactly (confirmed against two live screenshots from
 * a real device): Global Cover Art + Background Art side by side.
 *
 * Originally had a third row for Preserve Video Aspect Ratio too - #43
 * moved that toggle's UI to the new VideoSettingsScreen (the field itself,
 * AppSettings.preserveAspectRatioEnabled, is unchanged; this was purely a
 * relocation).
 */
@Composable
private fun AppSettingsScreen(launcherFolder: File?, onBack: () -> Unit) {
    var settings by remember {
        mutableStateOf(if (launcherFolder != null) loadAppSettings(launcherFolder) else AppSettings())
    }
    var showGlobalCoverArtPicker by remember { mutableStateOf(false) }

    fun persist(updated: AppSettings) {
        settings = updated
        if (launcherFolder != null) saveAppSettings(launcherFolder, updated)
    }

    // Escape matches #19/#20's established convention on every other
    // screen in this app.
    val focusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .focusRequester(focusRequester)
            .onGloballyPositioned {
                if (!hasRequestedInitialFocus) {
                    hasRequestedInitialFocus = true
                    try {
                        focusRequester.requestFocus()
                    } catch (e: IllegalStateException) {
                        // see #17's identical guard on GameCarousel
                    }
                }
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onBack()
                    true
                } else {
                    false
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("App Settings", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Global Cover Art", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "On: same art for every game. Off: each game picks its own.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = settings.globalCoverArtEnabled,
                            onCheckedChange = { persist(settings.copy(globalCoverArtEnabled = it)) },
                        )
                    }
                    if (settings.globalCoverArtEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                settings.globalCoverArtType.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = { showGlobalCoverArtPicker = true }) { Text("Change") }
                        }
                    }
                }
            }

            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Background Art", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "On: uses art from bg folder. Off: default white.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = settings.backgroundArtEnabled,
                            onCheckedChange = { persist(settings.copy(backgroundArtEnabled = it)) },
                        )
                    }
                    // #31 - Default Art is a real second toggle, only shown
                    // while Background Art itself is on, matching the real
                    // device exactly - and it's an unconditional override
                    // (every game gets bg/default.png), not a missing-file
                    // fallback (see #27's backgroundArtFile doc comment).
                    if (settings.backgroundArtEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Default Art", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Uses default.png for all games.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Switch(
                                checked = settings.defaultArtEnabled,
                                onCheckedChange = { persist(settings.copy(defaultArtEnabled = it)) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showGlobalCoverArtPicker) {
        CoverArtPickerDialog(
            onSelect = { type ->
                persist(settings.copy(globalCoverArtType = type))
                showGlobalCoverArtPicker = false
            },
            onDismiss = { showGlobalCoverArtPicker = false },
        )
    }
}

/**
 * #43 - real content on day one, not a blank stub: Preserve Video Aspect
 * Ratio (relocated verbatim from AppSettingsScreen, including #32's
 * version-risk caption) plus a new Full Screen toggle.
 *
 * Full Screen is a genuinely different category of setting from every
 * other toggle in this app - it controls the *launcher's own window*
 * (windowState, threaded all the way down from main()), not a hypseus
 * launch arg or a plain persisted value nothing else reads live. Flipping
 * it mutates windowState.placement directly, in addition to persisting
 * fullscreenEnabled - the persisted value is what main() reads on the
 * *next* launch (see its own comment), not what drives the *current*
 * window, which windowState.placement already does live via Window's own
 * observation of it.
 */
@Composable
private fun VideoSettingsScreen(launcherFolder: File?, windowState: WindowState, onBack: () -> Unit) {
    var settings by remember {
        mutableStateOf(if (launcherFolder != null) loadAppSettings(launcherFolder) else AppSettings())
    }
    val coroutineScope = rememberCoroutineScope()

    fun persist(updated: AppSettings) {
        settings = updated
        if (launcherFolder != null) saveAppSettings(launcherFolder, updated)
    }

    // Real, observed bugs working through this live: going straight from
    // WindowPlacement.Fullscreen to Maximized left the window stuck
    // fullscreen. Splitting the transition through Floating first (with a
    // delay) got further, but landed on minimized instead - AWT's
    // iconified bit apparently gets set somewhere during the fullscreen
    // exit and doesn't clear on its own. isMinimized = false is set
    // explicitly, in the same coroutine, before the final Maximized
    // assignment, specifically to clear that bit rather than trusting the
    // Floating step to have already done it.
    fun setFullscreen(enabled: Boolean) {
        if (enabled) {
            windowState.placement = WindowPlacement.Fullscreen
        } else {
            coroutineScope.launch {
                windowState.placement = WindowPlacement.Floating
                delay(100)
                windowState.isMinimized = false
                windowState.placement = WindowPlacement.Maximized
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .focusRequester(focusRequester)
            .onGloballyPositioned {
                if (!hasRequestedInitialFocus) {
                    hasRequestedInitialFocus = true
                    try {
                        focusRequester.requestFocus()
                    } catch (e: IllegalStateException) {
                        // see #17's identical guard on GameCarousel
                    }
                }
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onBack()
                    true
                } else {
                    false
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Video Settings", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Preserve Video Aspect Ratio", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "On: adds black bars if the video doesn't match your screen. Off: fills the screen.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            // #32 - real version risk, not hedging: this
                            // flag was added to hypseus-singe in 3.0.2. The
                            // launcher never bundles or version-checks its
                            // host install (epic non-goal), so an older
                            // hypseus.exe drop-in doesn't silently ignore
                            // an unrecognized arg - io/error.cpp's
                            // printerror() pops a real Windows MessageBox
                            // and interrupts launch. Documented visibly
                            // here rather than assumed away, per #32's own
                            // acceptance criteria.
                            Text(
                                "Requires hypseus-singe 3.0.2 or newer. Older installs will show an error on launch if this is on.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Switch(
                            checked = settings.preserveAspectRatioEnabled,
                            onCheckedChange = { persist(settings.copy(preserveAspectRatioEnabled = it)) },
                        )
                    }
                }
            }

            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Full Screen", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "On: full screen. Off: windowed mode.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = settings.fullscreenEnabled,
                            onCheckedChange = { enabled ->
                                persist(settings.copy(fullscreenEnabled = enabled))
                                setFullscreen(enabled)
                            },
                        )
                    }
                }
            }
        }
    }
}

// Mouse removed, owner's decision 2026-09-01: MOUSE_BUTTON1/2/3's real
// shape (a fixed physical button mapped to a KEY_* action) doesn't fit
// either interaction model already built - not a live-capture target
// like Keyboard (there's nothing to "wait for a press" on; the button is
// already fixed by which row you're on), and a plain list-picker felt
// like more complexity than it was worth for 3 fixed rows. #47 closed.
private val CONTROLLER_TYPE_OPTIONS = listOf("Keyboard", "Controller 1", "Controller 2")

// #48 - which row/slot a token picker dialog is currently open for, plus
// the token list to show (VALID_BUTTON_TOKENS or VALID_AXIS_TOKENS,
// GamepadIni.kt) and a title. A plain data holder, not a sealed type -
// every field is already unambiguous together.
private data class TokenPickerRequest(
    val keyName: String,
    val slot: BindingSlot,
    val title: String,
    val options: List<String>,
)

/**
 * #46 - real content for Keyboard mode: every KEY_* action from the one
 * real hypinput_gamepad.ini at the install root (Windows has exactly one
 * - its -homedir/-datadir is always the install root itself, unlike
 * Android's per-game copy), with a real live-capture rebind. Controller
 * 1/Controller 2/Mouse are real, selectable modes in the "Controller
 * Type" picker (the full shape #47/#48/#49 need) but show a plain
 * placeholder until those stories land - #19's "present but not yet
 * functional" precedent, applied per-mode here rather than per-screen.
 *
 * Escape has two different jobs on this screen depending on state: while
 * actively listening for a key, it cancels the capture without writing
 * anything or navigating back; otherwise it's this screen's usual
 * back-navigation key, same as everywhere else in this app. The two
 * must never be conflated - confirmed as its own acceptance criterion,
 * not an incidental detail.
 */
@Composable
private fun ControlsScreen(
    installRoot: File,
    launcherFolder: File?,
    mode: String,
    onModeChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    val iniFile = remember(installRoot) { gamepadIniPath(installRoot) }
    var iniText by remember { mutableStateOf(if (iniFile.isFile) iniFile.readText() else null) }
    val rows = remember(iniText) { iniText?.let { parseGamepadRows(it) } ?: emptyList() }

    var showModePicker by remember { mutableStateOf(false) }
    var listeningForKeyName by remember { mutableStateOf<String?>(null) }

    // #48 - which row/slot's token picker is open, or null. The chevron
    // path (Controller 1/Controller 2), not live capture - no gamepad
    // input API exists to capture from yet (phase 4).
    var tokenPickerRequest by remember { mutableStateOf<TokenPickerRequest?>(null) }
    fun writeBinding(keyName: String, slot: BindingSlot, token: String) {
        val current = iniText ?: return
        val updated = updateGamepadBinding(current, keyName, slot, token)
        iniFile.writeText(updated)
        iniText = updated
    }

    // #46 - gamepadEnabled is app-level (AppSettings, #31's storage),
    // same as every other flag-style toggle already on this screen's
    // sibling screens (App Settings, Video Settings).
    var appSettings by remember {
        mutableStateOf(if (launcherFolder != null) loadAppSettings(launcherFolder) else AppSettings())
    }
    fun persistAppSettings(updated: AppSettings) {
        appSettings = updated
        if (launcherFolder != null) saveAppSettings(launcherFolder, updated)
    }

    fun captureKey(keyName: String, key: Key) {
        if (key == Key.Escape) {
            listeningForKeyName = null
            return
        }
        val token = sdlkTokenFor(key) ?: return // unmapped key: keep listening
        val current = iniText ?: return
        val updated = updateGamepadBinding(current, keyName, BindingSlot.KEY1, token)
        iniFile.writeText(updated)
        iniText = updated
        listeningForKeyName = null
    }

    val focusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .focusRequester(focusRequester)
            .onGloballyPositioned {
                if (!hasRequestedInitialFocus) {
                    hasRequestedInitialFocus = true
                    try {
                        focusRequester.requestFocus()
                    } catch (e: IllegalStateException) {
                        // see #17's identical guard on GameCarousel
                    }
                }
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val listening = listeningForKeyName
                when {
                    listening != null -> {
                        captureKey(listening, event.key)
                        true
                    }
                    event.key == Key.Escape -> {
                        onBack()
                        true
                    }
                    else -> false
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Controls", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Controller Type", style = MaterialTheme.typography.titleMedium)
                        Text(mode, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = { showModePicker = true }) { Text("Change") }
                }
            }
            // #46 - Gamepad: a real launch-arg flag (-gamepad, confirmed
            // in doc/CmdLine.md/cmdline.cpp), same plain-flag category as
            // Preserve Video Aspect Ratio (#32) - app-level (AppSettings),
            // not per-game.
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gamepad", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "On: using a gamepad. Off: keyboard.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = appSettings.gamepadEnabled,
                        onCheckedChange = { persistAppSettings(appSettings.copy(gamepadEnabled = it)) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(mode, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        when {
            iniText == null -> Text(
                "hypinput_gamepad.ini not found.",
                style = MaterialTheme.typography.bodyMedium,
            )
            // #46 - two SEPARATE cards side by side, not one card with an
            // internal 2-column grid - matching the actual layout
            // convention every other settings screen in this app uses
            // (App Settings, Video Settings: two independent OutlinedCards
            // in a Row), per the owner's explicit correction. Each card
            // gets its own scroll state/scrollbar, not one shared list
            // split visually into two columns.
            mode == "Keyboard" -> {
                val half = (rows.size + 1) / 2
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    KeyboardBindingsCard(rows = rows.take(half), listeningForKeyName = listeningForKeyName, onRowClick = { listeningForKeyName = it }, modifier = Modifier.weight(1f))
                    KeyboardBindingsCard(rows = rows.drop(half), listeningForKeyName = listeningForKeyName, onRowClick = { listeningForKeyName = it }, modifier = Modifier.weight(1f))
                }
            }
            // #48 - Controller 1: the chevron/list-picker path, not live
            // capture - no gamepad input API exists to capture from yet
            // (phase 4). Pad0Button/AxisPad0 are the real columns for the
            // first controller.
            mode == "Controller 1" -> {
                val half = (rows.size + 1) / 2
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ControllerBindingsCard(
                        rows = rows.take(half),
                        buttonValue = { it.pad0Button },
                        axisValue = { it.axisPad0 },
                        onPickButton = { keyName -> tokenPickerRequest = TokenPickerRequest(keyName, BindingSlot.PAD0_BUTTON, "Button", VALID_BUTTON_TOKENS) },
                        onPickAxis = { keyName -> tokenPickerRequest = TokenPickerRequest(keyName, BindingSlot.AXIS_PAD0, "Axis", VALID_AXIS_TOKENS) },
                        modifier = Modifier.weight(1f),
                    )
                    ControllerBindingsCard(
                        rows = rows.drop(half),
                        buttonValue = { it.pad0Button },
                        axisValue = { it.axisPad0 },
                        onPickButton = { keyName -> tokenPickerRequest = TokenPickerRequest(keyName, BindingSlot.PAD0_BUTTON, "Button", VALID_BUTTON_TOKENS) },
                        onPickAxis = { keyName -> tokenPickerRequest = TokenPickerRequest(keyName, BindingSlot.AXIS_PAD0, "Axis", VALID_AXIS_TOKENS) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // #49 - Controller 2: identical shape to Controller 1 (#48),
            // same ControllerBindingsCard, only the columns read/written
            // differ (Pad1Button/AxisPad1). Real, not speculative: the
            // actual smoke/ install's KEY_COIN2/KEY_START2 are genuinely
            // bound through Pad1 (a real two-controller cabinet setup),
            // confirmed against the real hypinput_gamepad.ini during
            // #48/#49's scoping.
            mode == "Controller 2" -> {
                val half = (rows.size + 1) / 2
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ControllerBindingsCard(
                        rows = rows.take(half),
                        buttonValue = { it.pad1Button },
                        axisValue = { it.axisPad1 },
                        onPickButton = { keyName -> tokenPickerRequest = TokenPickerRequest(keyName, BindingSlot.PAD1_BUTTON, "Button", VALID_BUTTON_TOKENS) },
                        onPickAxis = { keyName -> tokenPickerRequest = TokenPickerRequest(keyName, BindingSlot.AXIS_PAD1, "Axis", VALID_AXIS_TOKENS) },
                        modifier = Modifier.weight(1f),
                    )
                    ControllerBindingsCard(
                        rows = rows.drop(half),
                        buttonValue = { it.pad1Button },
                        axisValue = { it.axisPad1 },
                        onPickButton = { keyName -> tokenPickerRequest = TokenPickerRequest(keyName, BindingSlot.PAD1_BUTTON, "Button", VALID_BUTTON_TOKENS) },
                        onPickAxis = { keyName -> tokenPickerRequest = TokenPickerRequest(keyName, BindingSlot.AXIS_PAD1, "Axis", VALID_AXIS_TOKENS) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (showModePicker) {
        Dialog(onDismissRequest = { showModePicker = false }) {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 4.dp) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Controller Type", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    CONTROLLER_TYPE_OPTIONS.forEach { option ->
                        Text(
                            option,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onModeChanged(option)
                                    showModePicker = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable { showModePicker = false }
                            .padding(8.dp),
                    )
                }
            }
        }
    }

    val pickerRequest = tokenPickerRequest
    if (pickerRequest != null) {
        TokenPickerDialog(
            title = "${pickerRequest.title}: ${pickerRequest.keyName}",
            options = pickerRequest.options,
            onSelect = { token ->
                writeBinding(pickerRequest.keyName, pickerRequest.slot, token)
                tokenPickerRequest = null
            },
            onDismiss = { tokenPickerRequest = null },
        )
    }
}

/**
 * #46 - one of the two side-by-side cards Keyboard mode splits its rows
 * across (see ControlsScreen's own comment for why this is two real
 * cards, not one card with an internal grid). Independently scrollable,
 * own scrollbar - each half is its own list, not two visual columns of
 * one shared list.
 */
@Composable
private fun KeyboardBindingsCard(
    rows: List<GamepadRow>,
    listeningForKeyName: String?,
    onRowClick: (String) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    OutlinedCard(modifier = modifier.fillMaxHeight()) {
        Box(modifier = Modifier.fillMaxSize()) {
            // VerticalScrollbar below is an overlay, not reserved layout
            // space - end padding keeps row content from sitting
            // underneath it.
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(12.dp),
                contentPadding = PaddingValues(end = 20.dp),
            ) {
                items(rows, key = { it.keyName }) { row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        Text(row.keyName, modifier = Modifier.weight(1f))
                        val isListening = listeningForKeyName == row.keyName
                        val label = if (row.key1 == "0") "Key: None" else "Key: ${row.key1}"
                        Button(onClick = { onRowClick(row.keyName) }) {
                            Text(if (isListening) "Press a key…" else label)
                        }
                    }
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(listState),
            )
        }
    }
}

/**
 * #48 - one of the two side-by-side cards Controller 1/Controller 2 mode
 * splits its rows across, same pattern as #46's KeyboardBindingsCard.
 * Each row shows two pills (Button, Axis) - matching Android's real
 * ControllerConfigScreen layout exactly (confirmed live, Retroid Pocket
 * 5) - but neither pill is clickable itself here, only its chevron: no
 * live capture exists for controllers yet (phase 4), so a clickable pill
 * body would either do nothing or start a capture that could never
 * resolve. buttonValue/axisValue are passed in rather than hardcoded so
 * this same composable serves both Controller 1 (Pad0) and Controller 2
 * (Pad1) - only which columns get read/written differs between them.
 */
@Composable
private fun ControllerBindingsCard(
    rows: List<GamepadRow>,
    buttonValue: (GamepadRow) -> String,
    axisValue: (GamepadRow) -> String,
    onPickButton: (String) -> Unit,
    onPickAxis: (String) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    OutlinedCard(modifier = modifier.fillMaxHeight()) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(12.dp),
                contentPadding = PaddingValues(end = 20.dp),
            ) {
                items(rows, key = { it.keyName }) { row ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
                        Text(row.keyName, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val buttonToken = buttonValue(row)
                            PillWithChevron(
                                label = if (buttonToken == "0") "Button: None" else "Button: $buttonToken",
                                onChevronClick = { onPickButton(row.keyName) },
                            )
                            val axisToken = axisValue(row)
                            PillWithChevron(
                                label = if (axisToken == "0") "Axis: None" else "Axis: $axisToken",
                                onChevronClick = { onPickAxis(row.keyName) },
                            )
                        }
                    }
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(listState),
            )
        }
    }
}

/**
 * #48 - a real pill (matching the dark rounded look Android's own
 * ControllerConfigScreen uses), display-only except for its chevron -
 * see ControllerBindingsCard's own comment for why the pill body itself
 * is deliberately not clickable in this stage.
 */
@Composable
private fun PillWithChevron(label: String, onChevronClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium,
        )
        IconButton(onClick = onChevronClick, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Choose",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * #48 - the chevron path: a static list of valid token names, no live
 * input capture needed. Same interaction shape as Cover Art's picker
 * (#30's CoverArtPickerDialog), generalized to an arbitrary string list
 * since VALID_BUTTON_TOKENS/VALID_AXIS_TOKENS aren't an enum. Wrapped in
 * a scrollable, height-capped list rather than CoverArtPickerDialog's
 * plain Column - VALID_BUTTON_TOKENS alone has 17 entries, too many to
 * assume they always fit on screen.
 */
@Composable
private fun TokenPickerDialog(title: String, options: List<String>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 4.dp) {
            Column(modifier = Modifier.padding(24.dp).heightIn(max = 480.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(options) { option ->
                        Text(
                            option,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable(onClick = onDismiss)
                        .padding(8.dp),
                )
            }
        }
    }
}

/**
 * #24 - three cards matching the subset of Android's real Settings screen
 * that applies here (Manage Game Folder and Touch Controls excluded - see
 * Screen's own comment for why). About is a genuinely blank destination
 * for now, same "present but not yet functional" precedent #19 set for
 * Cover Art. Controls got real content in #46. No .bat export card here -
 * that's #20's original scope, deferred separately (owner, 2026-08-31:
 * low priority, phase 5+); this pass is purely about the screen's shape.
 *
 * #41 - originally a 2x2 grid with a fourth "Manage Media Folder" card;
 * removed once the media folder location became fixed and auto-created
 * (owner's decision: no folder picker, ever - nothing left for that card
 * to manage). Owner's layout: App Settings top-left, Controls top-right,
 * About underneath App Settings - bottom-right cell left empty rather
 * than stretched.
 *
 * #43 - the empty bottom-right cell gets a fourth real card, "Video
 * Settings", rather than staying empty forever.
 */
@Composable
private fun SettingsScreen(
    onOpenAppSettings: () -> Unit,
    onOpenControls: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenVideoSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .focusRequester(focusRequester)
            .onGloballyPositioned {
                if (!hasRequestedInitialFocus) {
                    hasRequestedInitialFocus = true
                    try {
                        focusRequester.requestFocus()
                    } catch (e: IllegalStateException) {
                        // see #17's identical guard on GameCarousel
                    }
                }
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onBack()
                    true
                } else {
                    false
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard("App Settings", "Global media override", Modifier.weight(1f), onOpenAppSettings)
            SettingsCard("Controls", "Assign gamepad buttons per action", Modifier.weight(1f), onOpenControls)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard("About", "Build info, credits, open source", Modifier.weight(1f), onOpenAbout)
            SettingsCard("Video Settings", "Aspect ratio, full screen mode", Modifier.weight(1f), onOpenVideoSettings)
        }
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * #24 - a genuinely empty destination, shared by all
 * four Settings cards above. Just a header and Back/Escape, matching the
 * same navigation shell every other screen already uses.
 */
@Composable
private fun BlankPlaceholderScreen(title: String, onBack: () -> Unit) {
    val focusRequester = remember(title) { FocusRequester() }
    var hasRequestedInitialFocus by remember(title) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .focusRequester(focusRequester)
            .onGloballyPositioned {
                if (!hasRequestedInitialFocus) {
                    hasRequestedInitialFocus = true
                    try {
                        focusRequester.requestFocus()
                    } catch (e: IllegalStateException) {
                        // see #17's identical guard on GameCarousel
                    }
                }
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onBack()
                    true
                } else {
                    false
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
    }
}
