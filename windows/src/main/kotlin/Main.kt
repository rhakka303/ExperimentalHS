import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    // #20 - real content on day one, not a blank stub: exporting a
    // standalone .bat per scanned game (BatExport.kt).
    data object Export : Screen
    data class GameOptionsFor(val game: Game) : Screen
    data class GameHackFor(val game: Game) : Screen
    // #92 - Cover Art/Bezel/Arguments each got their own dedicated page,
    // split out of GameOptionsFor's old inline grid.
    data class CoverArtSettingsFor(val game: Game) : Screen
    data class BezelSettingsFor(val game: Game) : Screen
    data class ArgumentsSettingsFor(val game: Game) : Screen
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
    // #94 - first real log line of every session, matching what
    // resolveLauncherFolder()'s own doc comment says about when this is
    // meaningful at all (the packaged app only - log() itself is a
    // silent no-op otherwise).
    remember(launcherFolder) { log(launcherFolder, "HypdroidDesktop started") }
    // #68/#69 - real gamepad input, confirmed live working (#68). Starts
    // once (remember(Unit), not per-recomposition) and runs for the
    // app's whole lifetime - individual screens react to it (or don't)
    // via GamepadInputBus, per their own LaunchedEffect.
    remember(Unit) { startGamepadInput(launcherFolder) }
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
                // #94 - logUnexpectedExceptions wraps these two real I/O
                // calls specifically (not the whole startup block) -
                // genuinely unforeseen exceptions get a durable stack
                // trace logged before they propagate, rather than
                // vanishing; behavior itself is unchanged since it still
                // rethrows. The known-outcome log lines (.also{}) live
                // inside the same remember{} as the value itself so they
                // fire exactly once per resolved value, not on every
                // recomposition of the Text/branch below.
                val installRoot = remember {
                    logUnexpectedExceptions(launcherFolder, "resolving the install root") { resolveInstallRoot() }
                        .also { root ->
                            log(
                                launcherFolder,
                                if (root == null) {
                                    "Could not determine install root - HypdroidDesktop is not running from inside a hypseus install"
                                } else {
                                    "Install root resolved: $root"
                                },
                            )
                        }
                }

                when {
                    installRoot == null -> Text(
                        "Could not determine where HypdroidDesktop is running from.",
                        modifier = Modifier.padding(16.dp),
                    )
                    else -> when (val result = remember(installRoot) {
                        logUnexpectedExceptions(launcherFolder, "scanning for games") { scanGames(installRoot) }
                            .also { r ->
                                when (r) {
                                    is ScanResult.NotAHypseusInstall -> log(launcherFolder, "Not a hypseus installation: ${r.checkedPath}")
                                    is ScanResult.Found -> log(launcherFolder, "Found ${r.games.size} game(s)")
                                }
                            }
                    }) {
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
            onOpenExport = { screen = Screen.Export },
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
        is Screen.About -> AboutScreen(onBack = { screen = Screen.Settings })
        is Screen.Export -> ExportScreen(
            games = games,
            installRoot = installRoot,
            launcherFolder = launcherFolder,
            onBack = { screen = Screen.Settings },
        )
        is Screen.GameOptionsFor -> GameOptionsScreen(
            game = s.game,
            onOpenCoverArtSettings = { screen = Screen.CoverArtSettingsFor(s.game) },
            onOpenBezelSettings = { screen = Screen.BezelSettingsFor(s.game) },
            onOpenGameHack = { screen = Screen.GameHackFor(s.game) },
            onOpenArgumentsSettings = { screen = Screen.ArgumentsSettingsFor(s.game) },
            onBack = { screen = Screen.Carousel },
        )
        is Screen.GameHackFor -> GameHackScreen(
            game = s.game,
            launcherFolder = launcherFolder,
            onBack = { screen = Screen.GameOptionsFor(s.game) },
        )
        is Screen.CoverArtSettingsFor -> CoverArtSettingsScreen(
            game = s.game,
            launcherFolder = launcherFolder,
            onBack = { screen = Screen.GameOptionsFor(s.game) },
        )
        is Screen.BezelSettingsFor -> BezelSettingsScreen(
            game = s.game,
            launcherFolder = launcherFolder,
            onBack = { screen = Screen.GameOptionsFor(s.game) },
        )
        is Screen.ArgumentsSettingsFor -> ArgumentsSettingsScreen(
            game = s.game,
            launcherFolder = launcherFolder,
            onBack = { screen = Screen.GameOptionsFor(s.game) },
        )
    }
}

// #72 - which real element on the carousel screen currently has input
// focus. GEAR deliberately never includes the X (quit) icon - Up must
// never be able to land focus there, so there's nothing to accidentally
// confirm into quitting the app.
private enum class CarouselFocus { CARDS, GEAR }

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
                appSettings.gameFullscreenEnabled,
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
    // #69 - real, live-found bug: GLFW's gamepad polling reads the raw
    // physical device state directly, with no concept of window focus at
    // all (unlike keyboard/mouse, which the OS naturally scopes to
    // whichever window is focused) - it kept firing into the carousel
    // even while hypseus had focus and a game was actually being played,
    // moving the carousel and launching a second game mid-play. isGameRunning
    // gates the carousel's own gamepad handling explicitly while a
    // launched game's process is still alive - tracked the same real way
    // the reverted #45 attempt did (Process.waitFor() on Dispatchers.IO),
    // but only ever touching this plain boolean, never windowState, which
    // is what made that attempt risky. Keyboard/mouse never needed this:
    // hypseus's own window naturally has focus during play, so the
    // carousel's onKeyEvent/onClick simply never fire during that time.
    var isGameRunning by remember { mutableStateOf(false) }

    // #72 - real focus model: input either targets the game cards or the
    // top bar's Settings gear, never both at once. Deliberately excludes
    // the X (quit) icon entirely - Up must never land focus there, so
    // there's nothing to accidentally confirm into quitting. Shared by
    // both keyboard and gamepad below, matching #69's own "second input
    // source for the same actions" framing rather than duplicating this
    // logic per input path.
    var carouselFocus by remember { mutableStateOf(CarouselFocus.CARDS) }

    fun launchAndTrack(game: Game) {
        val result = launchGame(game, installRoot, extraArgsFor(game))
        // #94 follow-up, real live feedback: a successful launch isn't
        // logged here - hypseus itself already writes its own real logs
        // for the actual game session (#6's own non-goal: that's the
        // engine writing, not the launcher, and the launcher leaves them
        // alone entirely). hypseus.exe not found IS still worth logging
        // here specifically - hypseus never even runs in that case, so
        // its own logs can't help at all; this is the one launch outcome
        // only the launcher itself can capture.
        if (result is LaunchResult.HypseusNotFound) {
            log(launcherFolder, "Launch failed for ${game.name}: hypseus.exe not found at ${result.expectedPath}")
        }
        if (result is LaunchResult.Started) {
            isGameRunning = true
            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) { result.process.waitFor() }
                } catch (e: InterruptedException) {
                    // fall through - isGameRunning still needs clearing
                }
                isGameRunning = false
            }
        }
    }

    // #97 - real, live-found bug: pageLeft()/pageRight() kick off an
    // *animated* scroll (pagerState.animateScrollToPage()) on a
    // background coroutine, not an instant snap. Reading currentPage
    // here could catch it mid-flight, still showing the old page, so a
    // quick Left/Right then launch/options could act on the wrong game.
    // settledPage only updates once the pager has actually come to
    // rest, so it can never read an in-flight, ambiguous value -
    // "confirm/open whatever's actually settled," not "whatever I just
    // requested." pageLeft()/pageRight()'s own target math keeps using
    // currentPage - that's about responsive navigation, not this
    // decision.
    fun launchCentered() {
        launchAndTrack(games[pagerState.settledPage])
    }

    fun moveFocusUp() {
        if (carouselFocus == CarouselFocus.CARDS) carouselFocus = CarouselFocus.GEAR
    }

    // #72 - context-dependent, matching what Down already meant before
    // this story existed (open the centered card's options) while adding
    // the new meaning only the gear-focused state needs (return focus to
    // the cards) - not two different keys for what's conceptually one
    // "down" action.
    fun handleDown() {
        when (carouselFocus) {
            // #97 - same settledPage fix as launchCentered() above, same
            // reason: opening Options mid-scroll could otherwise act on
            // the wrong game.
            CarouselFocus.CARDS -> onOpenOptions(games[pagerState.settledPage])
            CarouselFocus.GEAR -> carouselFocus = CarouselFocus.CARDS
        }
    }

    fun confirmFocused() {
        when (carouselFocus) {
            CarouselFocus.CARDS -> launchCentered()
            CarouselFocus.GEAR -> onOpenSettings()
        }
    }

    // #69 - a second real input source feeding the exact same actions
    // the carousel's own keyboard onKeyEvent already handles below - not
    // a new interaction model. Only collects while GameCarousel is
    // actually composed (this LaunchedEffect's own lifecycle), which is
    // what scopes gamepad navigation to "only while the carousel is
    // showing" for free.
    LaunchedEffect(Unit) {
        GamepadInputBus.events.collect { action ->
            if (isGameRunning) return@collect
            when (action) {
                GamepadAction.UP -> moveFocusUp()
                GamepadAction.LEFT -> if (carouselFocus == CarouselFocus.CARDS) pageLeft()
                GamepadAction.RIGHT -> if (carouselFocus == CarouselFocus.CARDS) pageRight()
                GamepadAction.LAUNCH -> confirmFocused()
                GamepadAction.OPTIONS -> handleDown()
                // #72 - B also returns focus to the cards from the gear,
                // alongside its existing meaning elsewhere (back out of
                // GameOptionsScreen) - a natural "back" here too, not a
                // new binding.
                GamepadAction.BACK -> if (carouselFocus == CarouselFocus.GEAR) carouselFocus = CarouselFocus.CARDS
            }
        }
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

    // #57 - real logo, loaded as a plain classpath resource rather than
    // wiring up Compose's full resources system for one static image -
    // see this story's own issue for the tradeoff. #91 swapped the
    // underlying asset from the original Android "Hypdroid" wordmark to
    // this app's own "Hypdroid Desktop" wordmark (same filename, same
    // loading path, just a different PNG). Loaded once, not per-
    // recomposition.
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
                // #76 follow-up - IconButton already has its own real
                // circular hover/press state layer; see
                // rememberFocusInteractionSource's own doc comment for
                // why gamepad/keyboard focus now drives that same layer
                // rather than painting a second highlight on top of it.
                IconButton(
                    onClick = onOpenSettings,
                    interactionSource = rememberFocusInteractionSource(
                        isFocused = carouselFocus == CarouselFocus.GEAR,
                        onRealHover = { carouselFocus = CarouselFocus.GEAR },
                    ),
                ) {
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
                                    confirmFocused()
                                    true
                                }
                                // #19/#72 - Down opens the centered card's
                                // options screen (alongside clicking its
                                // gear icon) while the cards have focus,
                                // or returns focus to the cards from the
                                // Settings gear - context-dependent,
                                // matching what Down already meant before
                                // #72 added the second meaning.
                                Key.DirectionDown -> {
                                    handleDown()
                                    true
                                }
                                // #72 - reaches the Settings gear in the
                                // top bar; never the X (quit) icon even
                                // when it's also showing, so there's
                                // nothing to accidentally confirm into
                                // quitting.
                                Key.DirectionUp -> {
                                    moveFocusUp()
                                    true
                                }
                                Key.DirectionLeft -> {
                                    if (carouselFocus == CarouselFocus.CARDS) pageLeft()
                                    true
                                }
                                Key.DirectionRight -> {
                                    if (carouselFocus == CarouselFocus.CARDS) pageRight()
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
                            onClick = { launchAndTrack(game) },
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

// #57 - loads the real logo from a plain classpath resource
// (windows/src/main/resources/hypdroid_logo.png - #91 swapped this to
// the app's own "Hypdroid Desktop" wordmark, was originally the Android
// asset). This genuinely is the compile-time bundled-asset case
// loadImageBitmap's own deprecation warning targets (unlike GameCard's
// runtime media/ files below) - the simpler classpath-resource path was
// a deliberate choice over wiring up Compose's full resources system for
// one static image, not an oversight. Returns null (silently, no crash)
// if the resource is ever missing - the top bar simply omits the logo.
// #91 - also reused for the About screen's own logo, same function.
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
 * Overlay Bezel, and Arguments. Every field saves immediately on change,
 * no separate Save button - matching Android's own documented behavior
 * exactly, not a Windows invention.
 *
 * #87 - Scorebezel Autofit's own row is commented out (not deleted) -
 * see the comment right above it in this function's own body.
 */
// #92 - the real grid-navigation topology (SettingsFocus's own model,
// #76/#91's established pattern) for the 5-card summary redesign:
// row1 Cover Art Settings|Bezel Settings, row2 Game Hacks Settings|
// Video Snaps Settings (non-interactive placeholder, no focus value of
// its own - the owner's own call: not a real tappable stub), row3
// Arguments Settings|blank.
private enum class GameOptionsFocus { COVER_ART_SETTINGS, BEZEL_SETTINGS, GAME_HACKS_SETTINGS, ARGUMENTS_SETTINGS }

private fun GameOptionsFocus.moveUp(): GameOptionsFocus = when (this) {
    GameOptionsFocus.GAME_HACKS_SETTINGS -> GameOptionsFocus.COVER_ART_SETTINGS
    GameOptionsFocus.ARGUMENTS_SETTINGS -> GameOptionsFocus.GAME_HACKS_SETTINGS
    else -> this
}

private fun GameOptionsFocus.moveDown(): GameOptionsFocus = when (this) {
    GameOptionsFocus.COVER_ART_SETTINGS -> GameOptionsFocus.GAME_HACKS_SETTINGS
    GameOptionsFocus.GAME_HACKS_SETTINGS -> GameOptionsFocus.ARGUMENTS_SETTINGS
    else -> this
}

private fun GameOptionsFocus.moveLeft(): GameOptionsFocus = when (this) {
    GameOptionsFocus.BEZEL_SETTINGS -> GameOptionsFocus.COVER_ART_SETTINGS
    else -> this
}

private fun GameOptionsFocus.moveRight(): GameOptionsFocus = when (this) {
    GameOptionsFocus.COVER_ART_SETTINGS -> GameOptionsFocus.BEZEL_SETTINGS
    else -> this
}

/**
 * #92 - redesigned into 5 consistent summary cards, mirroring Hypdroid#161
 * and matching the same small-cards-tap-through-to-a-dedicated-page shape
 * Settings already uses: no controls sit inline here anymore. Cover Art
 * Settings/Bezel Settings/Game Hacks Settings/Arguments Settings are real
 * destinations; Video Snaps Settings is a non-interactive placeholder
 * (the owner's own call - not part of the flat-list/grid navigation at
 * all, matching every other blank-placeholder card already in this app).
 */
@Composable
private fun GameOptionsScreen(
    game: Game,
    onOpenCoverArtSettings: () -> Unit,
    onOpenBezelSettings: () -> Unit,
    onOpenGameHack: () -> Unit,
    onOpenArgumentsSettings: () -> Unit,
    onBack: () -> Unit,
) {
    var focus by remember(game) { mutableStateOf(GameOptionsFocus.COVER_ART_SETTINGS) }

    fun openFocused() {
        when (focus) {
            GameOptionsFocus.COVER_ART_SETTINGS -> onOpenCoverArtSettings()
            GameOptionsFocus.BEZEL_SETTINGS -> onOpenBezelSettings()
            GameOptionsFocus.GAME_HACKS_SETTINGS -> onOpenGameHack()
            GameOptionsFocus.ARGUMENTS_SETTINGS -> onOpenArgumentsSettings()
        }
    }

    // #69's own framing, reused: a second real input source feeding the
    // exact same actions the keyboard onKeyEvent below already handles.
    LaunchedEffect(Unit) {
        GamepadInputBus.events.collect { action ->
            when (action) {
                GamepadAction.UP -> focus = focus.moveUp()
                GamepadAction.LEFT -> focus = focus.moveLeft()
                GamepadAction.RIGHT -> focus = focus.moveRight()
                GamepadAction.LAUNCH -> openFocused()
                GamepadAction.OPTIONS -> focus = focus.moveDown()
                GamepadAction.BACK -> onBack()
            }
        }
    }

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
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        onBack()
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.CtrlLeft -> {
                        openFocused()
                        true
                    }
                    Key.DirectionUp -> {
                        focus = focus.moveUp()
                        true
                    }
                    Key.DirectionDown -> {
                        focus = focus.moveDown()
                        true
                    }
                    Key.DirectionLeft -> {
                        focus = focus.moveLeft()
                        true
                    }
                    Key.DirectionRight -> {
                        focus = focus.moveRight()
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
            Text("Options: ${game.name}", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard("Cover Art Settings", "Per-game cover art override", Modifier.weight(1f), focus == GameOptionsFocus.COVER_ART_SETTINGS, { focus = GameOptionsFocus.COVER_ART_SETTINGS }, onOpenCoverArtSettings)
            SettingsCard("Bezel Settings", "Bezel and overlay bezel", Modifier.weight(1f), focus == GameOptionsFocus.BEZEL_SETTINGS, { focus = GameOptionsFocus.BEZEL_SETTINGS }, onOpenBezelSettings)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard("Game Hacks Settings", "Custom game fixes", Modifier.weight(1f), focus == GameOptionsFocus.GAME_HACKS_SETTINGS, { focus = GameOptionsFocus.GAME_HACKS_SETTINGS }, onOpenGameHack)
            // #92 - non-interactive placeholder, the owner's own call:
            // not a real tappable stub, not part of the grid navigation
            // at all - a plain OutlinedCard with just its title text,
            // matching every other blank-placeholder card in this app
            // except it keeps the text so it doesn't look broken.
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Video Snaps Settings (Future Place Holder)", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard("Arguments Settings", "Custom launch arguments", Modifier.weight(1f), focus == GameOptionsFocus.ARGUMENTS_SETTINGS, { focus = GameOptionsFocus.ARGUMENTS_SETTINGS }, onOpenArgumentsSettings)
            // Blank/TBD, matching GameHackScreen's own identical
            // blank-second-card precedent.
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {}
            }
        }
    }
}

// #92 - CoverArtSettingsScreen's own flat list is 0 or 1 items
// depending on Global Cover Art (matching #85's own precedent) -
// maxOf(0, controls.lastIndex) guards focusIndex.coerceIn() against the
// empty-list case (lastIndex == -1), which coerceIn() would otherwise
// throw on.
private enum class CoverArtSettingsControl { CHANGE }

/**
 * #92 - Cover Art's own dedicated page, split out of the old inline
 * GameOptionsScreen grid. Content and #85's greyed-out-while-Global-
 * Cover-Art-is-on behavior are unchanged, just relocated to their own
 * two-column page (a blank second card, matching every other single-
 * card destination page in this app).
 */
@Composable
private fun CoverArtSettingsScreen(game: Game, launcherFolder: File?, onBack: () -> Unit) {
    var options by remember(game) {
        mutableStateOf(if (launcherFolder != null) loadOptions(launcherFolder, game.name) else GameOptions())
    }
    var showCoverArtPicker by remember(game) { mutableStateOf(false) }
    val appSettings = remember(launcherFolder) {
        if (launcherFolder != null) loadAppSettings(launcherFolder) else AppSettings()
    }

    fun persist(updated: GameOptions) {
        options = updated
        if (launcherFolder != null) saveOptions(launcherFolder, game.name, updated)
    }

    val controls = remember(appSettings.globalCoverArtEnabled) {
        if (!appSettings.globalCoverArtEnabled) listOf(CoverArtSettingsControl.CHANGE) else emptyList()
    }
    var focusIndex by remember(game) { mutableStateOf(0) }
    val focusedControl = controls.getOrNull(focusIndex.coerceIn(0, maxOf(0, controls.lastIndex)))
    val currentFocusedControl by rememberUpdatedState(focusedControl)

    fun moveFocusUp() {
        focusIndex = (focusIndex - 1).coerceAtLeast(0)
    }
    fun moveFocusDown() {
        focusIndex = (focusIndex + 1).coerceAtMost(maxOf(0, controls.lastIndex))
    }
    fun activateFocused() {
        when (currentFocusedControl) {
            CoverArtSettingsControl.CHANGE -> showCoverArtPicker = true
            null -> Unit
        }
    }

    LaunchedEffect(Unit) {
        GamepadInputBus.events.collect { action ->
            if (showCoverArtPicker) return@collect
            when (action) {
                GamepadAction.UP -> moveFocusUp()
                GamepadAction.OPTIONS -> moveFocusDown()
                GamepadAction.LAUNCH -> activateFocused()
                GamepadAction.BACK -> onBack()
                GamepadAction.LEFT, GamepadAction.RIGHT -> Unit
            }
        }
    }

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
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        onBack()
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.CtrlLeft -> {
                        activateFocused()
                        true
                    }
                    Key.DirectionUp -> {
                        moveFocusUp()
                        true
                    }
                    Key.DirectionDown -> {
                        moveFocusDown()
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
            Text("Cover Art Settings: ${game.name}", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // #30 - real per-game Cover Art override. Shows the resolved
            // effective type (override, else BOX) - confirmed on a real
            // device that a game with no override still shows BOX, not a
            // blank state.
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cover Art", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Button(
                            onClick = { showCoverArtPicker = true },
                            // #85 - disabled while Global Cover Art is on:
                            // a per-game override has zero effect then
                            // (GameCardArt.kt's own effectiveCoverArtFile()
                            // never reads it), matching the real app
                            // rather than leaving this reachable-but-
                            // pointless.
                            enabled = !appSettings.globalCoverArtEnabled,
                            interactionSource = rememberFocusInteractionSource(
                                isFocused = focusedControl == CoverArtSettingsControl.CHANGE,
                                onRealHover = { focusIndex = controls.indexOf(CoverArtSettingsControl.CHANGE) },
                            ),
                        ) { Text("Change") }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // #85 - the actually-effective type while Global
                    // Cover Art is on is the global type, not this game's
                    // own (ignored) override - showing the per-game value
                    // here would be misleading about what actually
                    // renders on the carousel.
                    val effectiveType = if (appSettings.globalCoverArtEnabled) {
                        appSettings.globalCoverArtType
                    } else {
                        options.coverArtOverride ?: CoverArtType.BOX
                    }
                    Text(effectiveType.name, style = MaterialTheme.typography.bodyMedium)
                    if (appSettings.globalCoverArtEnabled) {
                        Text(
                            "Controlled by Settings > Global Cover Art",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            // Blank/TBD, matching GameHackScreen's own identical
            // blank-second-card precedent.
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {}
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

// #92 - the real, ordered flat-list stops this screen can have.
// OVERLAY_BEZEL_SWITCH only ever appears in the real list while
// BEZEL_SWITCH is on, matching the screen's own real conditional layout
// - same reasoning as AppSettingsControl's own identical comment.
private enum class BezelSettingsControl { BEZEL_SWITCH, OVERLAY_BEZEL_SWITCH }

/**
 * #92 - Bezel's own dedicated page, split out of the old inline
 * GameOptionsScreen grid. Both real toggles (including #89's
 * hypseus-singe-3.0.2 warning on Overlay Bezel) are unchanged, just
 * relocated to their own two-column page (a blank second card, matching
 * every other single-card destination page in this app).
 */
@Composable
private fun BezelSettingsScreen(game: Game, launcherFolder: File?, onBack: () -> Unit) {
    var options by remember(game) {
        mutableStateOf(if (launcherFolder != null) loadOptions(launcherFolder, game.name) else GameOptions())
    }

    fun persist(updated: GameOptions) {
        options = updated
        if (launcherFolder != null) saveOptions(launcherFolder, game.name, updated)
    }

    val controls = remember(options.bezelEnabled) {
        buildList {
            add(BezelSettingsControl.BEZEL_SWITCH)
            if (options.bezelEnabled) add(BezelSettingsControl.OVERLAY_BEZEL_SWITCH)
        }
    }
    var focusIndex by remember(game) { mutableStateOf(0) }
    val focusedControl = controls.getOrNull(focusIndex.coerceIn(0, maxOf(0, controls.lastIndex)))
    val currentFocusedControl by rememberUpdatedState(focusedControl)

    fun moveFocusUp() {
        focusIndex = (focusIndex - 1).coerceAtLeast(0)
    }
    fun moveFocusDown() {
        focusIndex = (focusIndex + 1).coerceAtMost(maxOf(0, controls.lastIndex))
    }
    fun activateFocused() {
        when (currentFocusedControl) {
            BezelSettingsControl.BEZEL_SWITCH -> persist(options.copy(bezelEnabled = !options.bezelEnabled))
            BezelSettingsControl.OVERLAY_BEZEL_SWITCH -> persist(options.copy(overlayBezel = !options.overlayBezel))
            null -> Unit
        }
    }

    LaunchedEffect(Unit) {
        GamepadInputBus.events.collect { action ->
            when (action) {
                GamepadAction.UP -> moveFocusUp()
                GamepadAction.OPTIONS -> moveFocusDown()
                GamepadAction.LAUNCH -> activateFocused()
                GamepadAction.BACK -> onBack()
                GamepadAction.LEFT, GamepadAction.RIGHT -> Unit
            }
        }
    }

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
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        onBack()
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.CtrlLeft -> {
                        activateFocused()
                        true
                    }
                    Key.DirectionUp -> {
                        moveFocusUp()
                        true
                    }
                    Key.DirectionDown -> {
                        moveFocusDown()
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
            Text("Bezel Settings: ${game.name}", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            interactionSource = rememberFocusInteractionSource(
                                isFocused = focusedControl == BezelSettingsControl.BEZEL_SWITCH,
                                onRealHover = { focusIndex = controls.indexOf(BezelSettingsControl.BEZEL_SWITCH) },
                            ),
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
                                // #89 - real version risk, same reasoning
                                // and wording as Preserve Video Aspect
                                // Ratio's own identical warning (#32):
                                // per the owner, -overlaybezel is a new
                                // fix specific to hypseus-singe 3.0.2.
                                Text(
                                    "Requires hypseus-singe 3.0.2 or newer. Older installs will show an error on launch if this is on.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Switch(
                                checked = options.overlayBezel,
                                onCheckedChange = { persist(options.copy(overlayBezel = it)) },
                                interactionSource = rememberFocusInteractionSource(
                                    isFocused = focusedControl == BezelSettingsControl.OVERLAY_BEZEL_SWITCH,
                                    onRealHover = { focusIndex = controls.indexOf(BezelSettingsControl.OVERLAY_BEZEL_SWITCH) },
                                ),
                            )
                        }
                    }
                }
            }

            // Blank/TBD, matching GameHackScreen's own identical
            // blank-second-card precedent.
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {}
            }
        }
    }
}

/**
 * #92 - Arguments' own dedicated page, split out of the old inline
 * GameOptionsScreen grid where it competed for leftover vertical space.
 * Same internal layout as before (free-text field + Add button + full
 * list of added arguments), full width - the one deliberate exception
 * to every other destination page's two-column shape, since this is the
 * one page that actually needs the room. No flat-list keyboard/gamepad
 * navigation here, same #83 scoping decision carried forward: a
 * controller can't type into the text field regardless of reachability,
 * so only B/Escape (back) is wired.
 */
@Composable
private fun ArgumentsSettingsScreen(game: Game, launcherFolder: File?, onBack: () -> Unit) {
    var options by remember(game) {
        mutableStateOf(if (launcherFolder != null) loadOptions(launcherFolder, game.name) else GameOptions())
    }
    var newArgument by remember(game) { mutableStateOf("") }

    fun persist(updated: GameOptions) {
        options = updated
        if (launcherFolder != null) saveOptions(launcherFolder, game.name, updated)
    }

    LaunchedEffect(Unit) {
        GamepadInputBus.events.collect { action ->
            if (action == GamepadAction.BACK) onBack()
        }
    }

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
            Text("Arguments Settings: ${game.name}", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

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
    // #76 - this dialog is now reachable via A/Enter on a focused Change
    // button (AppSettingsScreen), not just a mouse click - it needs its
    // own real navigation regardless of how it was opened. Up/Down move
    // between the 4 real options, A/Enter selects (same as clicking),
    // B/Escape cancels (same as clicking Cancel) - matching the mouse
    // behavior already there rather than adding a second interaction
    // model.
    var focusIndex by remember { mutableStateOf(0) }
    val options = CoverArtType.entries
    val focusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        GamepadInputBus.events.collect { action ->
            when (action) {
                GamepadAction.UP -> focusIndex = (focusIndex - 1).coerceAtLeast(0)
                GamepadAction.OPTIONS -> focusIndex = (focusIndex + 1).coerceAtMost(options.lastIndex)
                GamepadAction.LAUNCH -> onSelect(options[focusIndex.coerceIn(0, options.lastIndex)])
                GamepadAction.BACK -> onDismiss()
                GamepadAction.LEFT, GamepadAction.RIGHT -> Unit
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
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
                        when (event.key) {
                            Key.DirectionUp -> {
                                focusIndex = (focusIndex - 1).coerceAtLeast(0)
                                true
                            }
                            Key.DirectionDown -> {
                                focusIndex = (focusIndex + 1).coerceAtMost(options.lastIndex)
                                true
                            }
                            Key.Enter, Key.NumPadEnter, Key.CtrlLeft -> {
                                onSelect(options[focusIndex.coerceIn(0, options.lastIndex)])
                                true
                            }
                            Key.Escape -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    },
            ) {
                Text("Cover Art", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                options.forEachIndexed { index, type ->
                    Text(
                        type.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            // #76 follow-up - real, live feedback: the
                            // green border (this app's focus indicator
                            // everywhere else) looked out of sync sitting
                            // next to Compose's own default grey mouse-
                            // hover highlight on the row below it. Rather
                            // than run two different-looking indicators
                            // side by side, keyboard/gamepad focus now
                            // uses that same grey, not a second visual
                            // language.
                            //
                            // clip() before clickable() so that same
                            // mouse-hover indication is bounded by this
                            // row's own rounded shape rather than painting
                            // square across the full row.
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(
                                if (index == focusIndex.coerceIn(0, options.lastIndex)) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
                                } else {
                                    Color.Transparent
                                },
                                MaterialTheme.shapes.extraSmall,
                            )
                            // #76 follow-up - mouse hover moves focusIndex
                            // here too, same reasoning as focusableClickable's
                            // own doc comment.
                            .focusableClickable(onHover = { focusIndex = index }, onClick = { onSelect(type) })
                            .padding(horizontal = 8.dp, vertical = 12.dp),
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
 * #19 - originally matched Android's GameHackScreen shape: one real
 * toggle (Aspect Ratio Bezel Fix) plus one genuinely empty placeholder
 * card, mirroring Android's own current state (confirmed live on real
 * hardware, not assumed).
 *
 * #87 - that one real toggle is commented out (not deleted): the
 * -aspectbezelfix argument it sent turned out not to exist in hypseus-
 * singe at all, checked against the real upstream source. Both cards
 * are genuinely blank placeholders again for now.
 */
/* #87 - the whole #83 addition below (GameHackControl, its flat-list
 * nav, and the Aspect Ratio Bezel Fix switch itself) is commented out
 * as one unit, not deleted: -aspectbezelfix isn't a real hypseus
 * argument (see GameOptions.kt's own launchArgumentsFor() comment), and
 * with that switch gone this screen has no other real control left to
 * navigate - #83's own navigation only existed to reach it. Trivial to
 * restore together if a future hypseus-singe release adds real support.

// #83 - a flat list of exactly one real control, same model as every
// other screen this story touches - the second card is a genuine blank
// placeholder (Android's own current state too), not a focus stop, so
// there's nothing else for this list to hold yet.
private enum class GameHackControl { ASPECT_RATIO_BEZEL_FIX_SWITCH }
*/

@Composable
private fun GameHackScreen(game: Game, launcherFolder: File?, onBack: () -> Unit) {
    /* #87 - see this file's own comment just above GameHackControl.
    var options by remember(game) {
        mutableStateOf(if (launcherFolder != null) loadOptions(launcherFolder, game.name) else GameOptions())
    }

    fun persist(updated: GameOptions) {
        options = updated
        if (launcherFolder != null) saveOptions(launcherFolder, game.name, updated)
    }

    val controls = GameHackControl.entries
    var focusIndex by remember(game) { mutableStateOf(0) }
    val focusedControl = controls.getOrNull(focusIndex.coerceIn(0, controls.lastIndex))
    val currentFocusedControl by rememberUpdatedState(focusedControl)

    fun moveFocusUp() {
        focusIndex = (focusIndex - 1).coerceAtLeast(0)
    }
    fun moveFocusDown() {
        focusIndex = (focusIndex + 1).coerceAtMost(controls.lastIndex)
    }
    fun activateFocused() {
        when (currentFocusedControl) {
            GameHackControl.ASPECT_RATIO_BEZEL_FIX_SWITCH -> persist(options.copy(aspectBezelFix = !options.aspectBezelFix))
            null -> Unit
        }
    }
    */

    // #92 follow-up - real, live-found regression: #87's revert
    // commented out the *whole* gamepad block above, including plain
    // B-back, which isn't tied to the removed Aspect Ratio Bezel Fix
    // feature at all and should have stayed - every screen gets at
    // least this much, same #69 baseline every other simple screen
    // (ArgumentsSettingsScreen, the pre-#83 version of this screen
    // itself) already uses.
    LaunchedEffect(Unit) {
        GamepadInputBus.events.collect { action ->
            if (action == GamepadAction.BACK) onBack()
        }
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
            /* #87 - Aspect Ratio Bezel Fix's own card - see this file's
             * own comment just above GameHackControl.
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
                            onCheckedChange = { persist(options.copy(aspectBezelFix = it)) },
                            interactionSource = rememberFocusInteractionSource(
                                isFocused = focusedControl == GameHackControl.ASPECT_RATIO_BEZEL_FIX_SWITCH,
                                onRealHover = { focusIndex = controls.indexOf(GameHackControl.ASPECT_RATIO_BEZEL_FIX_SWITCH) },
                            ),
                        )
                    }
                }
            }
            */

            // #87 - "Future Placeholder", not silently empty: this card
            // held Aspect Ratio Bezel Fix until #87 pulled it (not a
            // real hypseus argument), and a blank card in this spot
            // would otherwise look broken rather than deliberate.
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                    Text("Future Placeholder", style = MaterialTheme.typography.titleMedium)
                }
            }

            // Blank/TBD, matching Android's own current state exactly.
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {}
            }
        }
    }
}

// #76 follow-up - real, live feedback: mouse hover and keyboard/gamepad
// focus were two genuinely independent states (each could highlight a
// different control at once, "out of sync") now that every navigable
// screen shows its own focus highlight. Rather than run both, mouse
// hover becomes a third input source feeding the exact same shared focus
// state the keyboard/gamepad already drive - same "shared functions
// across input sources" pattern #69/#72 established, just extended to a
// third source. Compose's own default hover/press indication is
// suppressed (indication = null) since this app's own focusBorder/
// background highlight is the single visual language for "this is
// focused" now, matching what the mouse itself also causes via onHover.
@Composable
private fun Modifier.focusableClickable(onHover: () -> Unit, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    LaunchedEffect(isHovered) {
        if (isHovered) onHover()
    }
    return this
        .hoverable(interactionSource)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

// #76 follow-up - real, live question: "why is controller using a
// separate system from mouse?" It shouldn't, for a Switch/Button/
// IconButton - each is a real Material3 component with its own built-in
// hover/press state layer that real mouse hover already drives correctly
// on its own. Layering a second, custom-painted highlight on top for
// gamepad/keyboard focus (this app's own separate app-level focus
// concept, not something Compose's real interaction system knows about)
// is exactly what produced the double-circle bug just above.
//
// This returns ONE interactionSource, handed directly to the real
// component (Switch(interactionSource = ...), etc.) so mouse and
// controller drive the exact same native visual, not two: real pointer
// hover already writes into it via the component's own internals; this
// additionally writes a synthetic Enter/Exit into that same source
// whenever this app's own isFocused flag says gamepad/keyboard focus is
// here, so the same native state layer lights up for that too. Reading
// hover back off this same source (collectIsHoveredAsState) is what lets
// real mouse hover feed back into isFocused/onRealHover - one shared
// state in both directions, not a parallel one.
@Composable
private fun rememberFocusInteractionSource(isFocused: Boolean, onRealHover: () -> Unit): MutableInteractionSource {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    LaunchedEffect(isHovered) {
        if (isHovered) onRealHover()
    }
    var activeSyntheticHover by remember { mutableStateOf<HoverInteraction.Enter?>(null) }
    LaunchedEffect(isFocused) {
        if (isFocused && activeSyntheticHover == null) {
            val enter = HoverInteraction.Enter()
            interactionSource.emit(enter)
            activeSyntheticHover = enter
        } else if (!isFocused) {
            activeSyntheticHover?.let { interactionSource.emit(HoverInteraction.Exit(it)) }
            activeSyntheticHover = null
        }
    }
    return interactionSource
}

// #76 - the real, ordered flat-list stops AppSettingsScreen can have -
// GLOBAL_COVER_ART_CHANGE and DEFAULT_ART_SWITCH only ever appear in the
// real visible list while their parent toggle is on, matching the
// screen's own real conditional layout.
private enum class AppSettingsControl { GLOBAL_COVER_ART_SWITCH, GLOBAL_COVER_ART_CHANGE, BACKGROUND_ART_SWITCH, DEFAULT_ART_SWITCH }

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

    // #76 - one flat, ordered list of every real focusable control on
    // this screen, not per-card focus - Global Cover Art's Change button
    // and Background Art's Default Art switch only exist in this list
    // while their parent toggle is actually on, matching when they're
    // actually visible. Up/Down move an index through this list rather
    // than a 2D grid (#73's own model) - these controls read top-to-
    // bottom within each card, not left-right between cards.
    val controls = remember(settings.globalCoverArtEnabled, settings.backgroundArtEnabled) {
        buildList {
            add(AppSettingsControl.GLOBAL_COVER_ART_SWITCH)
            if (settings.globalCoverArtEnabled) add(AppSettingsControl.GLOBAL_COVER_ART_CHANGE)
            add(AppSettingsControl.BACKGROUND_ART_SWITCH)
            if (settings.backgroundArtEnabled) add(AppSettingsControl.DEFAULT_ART_SWITCH)
        }
    }
    var focusIndex by remember { mutableStateOf(0) }
    val focusedControl = controls.getOrNull(focusIndex.coerceIn(0, controls.lastIndex))
    // #76 follow-up - real, live bug: LaunchedEffect(Unit) below launches
    // its coroutine exactly once and never restarts (its key never
    // changes), so it keeps running the *first* composition's
    // activateFocused() forever - which had closed over that first
    // composition's own focusedControl (a plain val, frozen at whichever
    // control was focused when this screen first opened). Up/Down still
    // worked because they write straight into the shared focusIndex
    // MutableState object, which any closure reads live regardless of
    // which "generation" it's from - but activateFocused() was reading a
    // frozen snapshot, so gamepad A kept acting on the *original* focus
    // no matter where Up/Down had since moved the visible highlight.
    // rememberUpdatedState gives activateFocused() a stable indirection
    // that always resolves to the latest value, even from a stale
    // closure. (Keyboard's own onKeyEvent never had this problem - it's
    // a fresh lambda every recomposition, not a long-lived coroutine.)
    val currentFocusedControl by rememberUpdatedState(focusedControl)

    fun moveFocusUp() {
        focusIndex = (focusIndex - 1).coerceAtLeast(0)
    }
    fun moveFocusDown() {
        focusIndex = (focusIndex + 1).coerceAtMost(controls.lastIndex)
    }
    fun activateFocused() {
        when (currentFocusedControl) {
            AppSettingsControl.GLOBAL_COVER_ART_SWITCH -> persist(settings.copy(globalCoverArtEnabled = !settings.globalCoverArtEnabled))
            AppSettingsControl.GLOBAL_COVER_ART_CHANGE -> showGlobalCoverArtPicker = true
            AppSettingsControl.BACKGROUND_ART_SWITCH -> persist(settings.copy(backgroundArtEnabled = !settings.backgroundArtEnabled))
            AppSettingsControl.DEFAULT_ART_SWITCH -> persist(settings.copy(defaultArtEnabled = !settings.defaultArtEnabled))
            null -> Unit
        }
    }

    // #69's own framing, reused: a second real input source feeding the
    // exact same actions the keyboard onKeyEvent below already handles.
    // Real bug caught before it shipped: the Dialog below is an
    // additional composable, not a replacement - this screen's own
    // Column (and this LaunchedEffect) stays composed and still
    // collecting underneath it, so without the showGlobalCoverArtPicker
    // guard, a single button press would move both this screen's flat-
    // list focus AND the dialog's own focus at the same time.
    LaunchedEffect(Unit) {
        GamepadInputBus.events.collect { action ->
            if (showGlobalCoverArtPicker) return@collect
            when (action) {
                GamepadAction.UP -> moveFocusUp()
                GamepadAction.OPTIONS -> moveFocusDown()
                GamepadAction.LAUNCH -> activateFocused()
                GamepadAction.BACK -> onBack()
                GamepadAction.LEFT, GamepadAction.RIGHT -> Unit
            }
        }
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
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        onBack()
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.CtrlLeft -> {
                        activateFocused()
                        true
                    }
                    Key.DirectionUp -> {
                        moveFocusUp()
                        true
                    }
                    Key.DirectionDown -> {
                        moveFocusDown()
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
                            interactionSource = rememberFocusInteractionSource(
                                isFocused = focusedControl == AppSettingsControl.GLOBAL_COVER_ART_SWITCH,
                                onRealHover = { focusIndex = controls.indexOf(AppSettingsControl.GLOBAL_COVER_ART_SWITCH) },
                            ),
                        )
                    }
                    if (settings.globalCoverArtEnabled) {
                        // #76 follow-up - real, live, repeated bug report:
                        // clicking Change was toggling the Switch above it
                        // off instead. Switch's own real minimum touch
                        // target is 48dp tall - noticeably bigger than its
                        // ~32dp visible track - so an 8dp gap wasn't
                        // enough clearance before this row starts; a click
                        // aimed at Change could still land inside the
                        // Switch's own (invisible) hit box. 24dp gives
                        // real clearance past that inflated area.
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                settings.globalCoverArtType.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = { showGlobalCoverArtPicker = true },
                                interactionSource = rememberFocusInteractionSource(
                                    isFocused = focusedControl == AppSettingsControl.GLOBAL_COVER_ART_CHANGE,
                                    onRealHover = { focusIndex = controls.indexOf(AppSettingsControl.GLOBAL_COVER_ART_CHANGE) },
                                ),
                            ) { Text("Change") }
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
                            interactionSource = rememberFocusInteractionSource(
                                isFocused = focusedControl == AppSettingsControl.BACKGROUND_ART_SWITCH,
                                onRealHover = { focusIndex = controls.indexOf(AppSettingsControl.BACKGROUND_ART_SWITCH) },
                            ),
                        )
                    }
                    // #31 - Default Art is a real second toggle, only shown
                    // while Background Art itself is on, matching the real
                    // device exactly - and it's an unconditional override
                    // (every game gets bg/default.png), not a missing-file
                    // fallback (see #27's backgroundArtFile doc comment).
                    if (settings.backgroundArtEnabled) {
                        // #76 follow-up - same real touch-target-overlap
                        // risk as Global Cover Art's own Switch/Change gap
                        // above (both Switches here have the same 48dp
                        // touch target vs ~32dp visible track) - matched
                        // preventively even though this specific pair
                        // wasn't the one reported.
                        Spacer(modifier = Modifier.height(24.dp))
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
                                interactionSource = rememberFocusInteractionSource(
                                    isFocused = focusedControl == AppSettingsControl.DEFAULT_ART_SWITCH,
                                    onRealHover = { focusIndex = controls.indexOf(AppSettingsControl.DEFAULT_ART_SWITCH) },
                                ),
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
// #76 - same flat-list model as AppSettingsControl, simpler here: both
// controls are always visible, no conditional entries.
// #20 follow-up - GAME_FULL_SCREEN_SWITCH is a real, live-found gap:
// -fullscreen (the hypseus arg that puts the game itself full screen)
// used to be hardcoded unconditionally rather than a real setting - see
// AppSettings.gameFullscreenEnabled's own doc comment. FULL_SCREEN_SWITCH
// (App Full Screen) is a genuinely different thing - this launcher's own
// window, not the game.
private enum class VideoSettingsControl { PRESERVE_ASPECT_RATIO_SWITCH, FULL_SCREEN_SWITCH, GAME_FULL_SCREEN_SWITCH }

@Composable
private fun VideoSettingsScreen(launcherFolder: File?, windowState: WindowState, onBack: () -> Unit) {
    var settings by remember {
        mutableStateOf(if (launcherFolder != null) loadAppSettings(launcherFolder) else AppSettings())
    }
    val coroutineScope = rememberCoroutineScope()

    // #76 - same flat-list model as AppSettingsScreen, simpler here: both
    // controls are always visible, no conditional items and no nested
    // dialog to guard against.
    val controls = VideoSettingsControl.entries
    var focusIndex by remember { mutableStateOf(0) }
    val focusedControl = controls.getOrNull(focusIndex.coerceIn(0, controls.lastIndex))
    // #76 follow-up - see AppSettingsScreen's own identical fix/comment:
    // LaunchedEffect(Unit) below never restarts, so activateFocused()
    // needs this stable indirection to read the live focus instead of a
    // frozen first-composition snapshot.
    val currentFocusedControl by rememberUpdatedState(focusedControl)

    fun moveFocusUp() {
        focusIndex = (focusIndex - 1).coerceAtLeast(0)
    }
    fun moveFocusDown() {
        focusIndex = (focusIndex + 1).coerceAtMost(controls.lastIndex)
    }

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

    fun activateFocused() {
        when (currentFocusedControl) {
            VideoSettingsControl.PRESERVE_ASPECT_RATIO_SWITCH ->
                persist(settings.copy(preserveAspectRatioEnabled = !settings.preserveAspectRatioEnabled))
            VideoSettingsControl.FULL_SCREEN_SWITCH -> {
                val enabled = !settings.fullscreenEnabled
                persist(settings.copy(fullscreenEnabled = enabled))
                setFullscreen(enabled)
            }
            VideoSettingsControl.GAME_FULL_SCREEN_SWITCH ->
                persist(settings.copy(gameFullscreenEnabled = !settings.gameFullscreenEnabled))
            null -> Unit
        }
    }

    // #69's own framing, reused: a second real input source feeding the
    // exact same actions the keyboard onKeyEvent below already handles.
    LaunchedEffect(Unit) {
        GamepadInputBus.events.collect { action ->
            when (action) {
                GamepadAction.UP -> moveFocusUp()
                GamepadAction.OPTIONS -> moveFocusDown()
                GamepadAction.LAUNCH -> activateFocused()
                GamepadAction.BACK -> onBack()
                GamepadAction.LEFT, GamepadAction.RIGHT -> Unit
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
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Escape -> { onBack(); true }
                    Key.Enter, Key.NumPadEnter, Key.CtrlLeft -> { activateFocused(); true }
                    Key.DirectionUp -> { moveFocusUp(); true }
                    Key.DirectionDown -> { moveFocusDown(); true }
                    else -> false
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
                            interactionSource = rememberFocusInteractionSource(
                                isFocused = focusedControl == VideoSettingsControl.PRESERVE_ASPECT_RATIO_SWITCH,
                                onRealHover = { focusIndex = controls.indexOf(VideoSettingsControl.PRESERVE_ASPECT_RATIO_SWITCH) },
                            ),
                        )
                    }
                }
            }

            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            // #20 follow-up - renamed from "Full Screen":
                            // this toggles the launcher's own window, not
                            // the game - see GAME_FULL_SCREEN_SWITCH's own
                            // card below for that.
                            Text("App Full Screen", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "On: full screen. Off: windowed mode.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            // #65 - real, known, currently-unresolved
                            // issue (#45): the launcher can stay hidden on
                            // the taskbar after exiting a game in Full
                            // Screen mode. Two fix attempts were reverted
                            // (one caused a worse regression - see #45's
                            // own comments), so this is flagged visibly
                            // rather than presented as fully solid.
                            Text(
                                "Experimental: May hide on taskbar after exiting game",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Switch(
                            checked = settings.fullscreenEnabled,
                            onCheckedChange = { enabled ->
                                persist(settings.copy(fullscreenEnabled = enabled))
                                setFullscreen(enabled)
                            },
                            interactionSource = rememberFocusInteractionSource(
                                isFocused = focusedControl == VideoSettingsControl.FULL_SCREEN_SWITCH,
                                onRealHover = { focusIndex = controls.indexOf(VideoSettingsControl.FULL_SCREEN_SWITCH) },
                            ),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // #20 follow-up - real, live-found gap: -fullscreen (the game
            // itself, not this launcher's own window - see App Full
            // Screen above) was hardcoded unconditionally in LaunchArgs.kt
            // instead of being a real setting. Same live launcher +
            // exported .bat reach as every other flag here (see
            // AppSettings.gameFullscreenEnabled's own doc comment).
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Game Full Screen", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "On: launch game full screen. Off: launch game windowed.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = settings.gameFullscreenEnabled,
                            onCheckedChange = { persist(settings.copy(gameFullscreenEnabled = it)) },
                            interactionSource = rememberFocusInteractionSource(
                                isFocused = focusedControl == VideoSettingsControl.GAME_FULL_SCREEN_SWITCH,
                                onRealHover = { focusIndex = controls.indexOf(VideoSettingsControl.GAME_FULL_SCREEN_SWITCH) },
                            ),
                        )
                    }
                }
            }
            // Blank/TBD, matching GameHackScreen's own identical
            // blank-second-card precedent - every card row in this app
            // pairs two, per the owner's own correction here.
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {}
            }
        }
    }
}

/**
 * #20 - real content on day one, not a blank stub: one action, exporting
 * a standalone `.bat` per scanned game into `batch/` (BatExport.kt does
 * the actual work - relative paths, no baseline flags, see its own doc
 * comment for why this can't reuse the live launcher's buildLaunchArgs()
 * as-is).
 */
@Composable
private fun ExportScreen(games: List<Game>, installRoot: File, launcherFolder: File?, onBack: () -> Unit) {
    var resultMessage by remember { mutableStateOf<String?>(null) }

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
            Text("Export", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Create Bat Files", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Writes one .bat per game into the install's batch/ folder. Re-running overwrites cleanly.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Button(onClick = {
                        val count = exportBatFiles(installRoot, games, launcherFolder)
                        resultMessage = "Exported $count file${if (count == 1) "" else "s"} to batch/"
                    }) { Text("Generate") }
                }
            }
            // Blank/TBD, matching GameHackScreen's own identical
            // blank-second-card precedent.
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {}
            }
        }

        resultMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
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

// #63 - one uniform fixed width for every pill in Controls (Keyboard's
// Key pill, Controller 1/2's Button and Axis pills), not auto-sized to
// each pill's own text - real screenshot showed a row with a long real
// token ("Button: BUTTON_RIGHTSHOULDER") visibly wider than a neighboring
// "Button: None" row, ragged across the grid. Sized to comfortably fit
// the real longest case: VALID_BUTTON_TOKENS' own longest entries
// (BUTTON_LEFTSHOULDER/BUTTON_RIGHTSHOULDER) with the "Button: " prefix.
private val CONTROLS_PILL_WIDTH = 240.dp

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
    // path (Controller 1/Controller 2's manual list), unchanged by #80's
    // own addition below.
    var tokenPickerRequest by remember { mutableStateOf<TokenPickerRequest?>(null) }

    // #80 - live capture's own listening state: which row+slot is
    // currently waiting for a real button press/stick movement, or null.
    // A separate pair of vars (not one combined object) so each reads
    // cleanly as its own condition at every call site below - same
    // reasoning as listeningForKeyName just above staying its own var
    // rather than folding into some larger "what's this screen doing"
    // state.
    var listeningForControllerKeyName by remember { mutableStateOf<String?>(null) }
    var listeningForControllerSlot by remember { mutableStateOf<BindingSlot?>(null) }

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

    // #80 - starts a live-capture request: records which row+slot this
    // screen itself is waiting on (drives the pill's "Press a button…"/
    // "Move a stick…" label) and tells the background polling thread
    // what kind of input to listen for. slot decides both which
    // BindingSlot writeBinding eventually uses and which CaptureKind the
    // polling thread listens for - Button pills always resolve to a
    // *_BUTTON slot, Axis pills to an AXIS_* slot, so the mapping below
    // is exhaustive without needing a separate kind parameter here.
    fun startListeningController(keyName: String, slot: BindingSlot) {
        listeningForControllerKeyName = keyName
        listeningForControllerSlot = slot
        val kind = when (slot) {
            BindingSlot.PAD0_BUTTON, BindingSlot.PAD1_BUTTON -> CaptureKind.BUTTON
            BindingSlot.AXIS_PAD0, BindingSlot.AXIS_PAD1 -> CaptureKind.AXIS
            BindingSlot.KEY1, BindingSlot.KEY2 -> return // unreachable from this screen's controller UI
        }
        GamepadCaptureBus.startCapture(kind)
    }

    fun cancelListeningController() {
        listeningForControllerKeyName = null
        listeningForControllerSlot = null
        GamepadCaptureBus.cancelCapture()
    }

    // #80 - the background polling thread (GamepadCaptureBus, its own
    // separate channel from GamepadInputBus above) emits here once it
    // captures a real token. listeningForControllerKeyName/Slot are read
    // live (State-backed vars, not a derived val closed over at
    // composition time) so this stays correct even though - like every
    // other gamepad LaunchedEffect(Unit) in this app - the coroutine
    // itself is only ever launched once. See #76's own identical
    // rememberUpdatedState fix/comment for the failure mode this avoids.
    LaunchedEffect(Unit) {
        GamepadCaptureBus.captured.collect { token ->
            val keyName = listeningForControllerKeyName ?: return@collect
            val slot = listeningForControllerSlot ?: return@collect
            writeBinding(keyName, slot, token)
            listeningForControllerKeyName = null
            listeningForControllerSlot = null
        }
    }

    // #80 - a successful capture already clears GamepadCaptureBus's own
    // activeCapture (see its emit()), but an *incomplete* one wouldn't:
    // switching to Keyboard mode mid-listen, or backing out of this
    // screen entirely, otherwise leaves activeCapture permanently set -
    // silently suppressing every gamepad navigation action everywhere
    // else in the app from then on, since the polling thread treats any
    // non-null activeCapture as "don't emit GamepadInputBus actions this
    // frame" (see startGamepadInput's own comment). Two different exit
    // paths, two different Compose tools: mode is plain screen state
    // (still composed, just showing something else) so a key-based
    // LaunchedEffect catches every value it changes to; leaving the
    // screen entirely un-composes it, which only DisposableEffect's own
    // onDispose observes.
    LaunchedEffect(mode) { cancelListeningController() }
    DisposableEffect(Unit) { onDispose { GamepadCaptureBus.cancelCapture() } }

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
                    // #80 - Escape (keyboard only) cancels an active
                    // controller live-capture without writing anything;
                    // every other key is swallowed rather than falling
                    // through to onBack() below, so a stray keystroke
                    // while waiting for a gamepad press can't accidentally
                    // back out of the screen instead.
                    listeningForControllerKeyName != null && event.key == Key.Escape -> {
                        cancelListeningController()
                        true
                    }
                    listeningForControllerKeyName != null -> true
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
            // #48/#80 - Controller 1: the chevron/manual-list path (#48)
            // plus live capture (#80) side by side. Pad0Button/AxisPad0
            // are the real columns for the first controller.
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
                        listeningKeyName = listeningForControllerKeyName,
                        listeningSlot = listeningForControllerSlot,
                        onStartListenButton = { keyName -> startListeningController(keyName, BindingSlot.PAD0_BUTTON) },
                        onStartListenAxis = { keyName -> startListeningController(keyName, BindingSlot.AXIS_PAD0) },
                        modifier = Modifier.weight(1f),
                    )
                    ControllerBindingsCard(
                        rows = rows.drop(half),
                        buttonValue = { it.pad0Button },
                        axisValue = { it.axisPad0 },
                        onPickButton = { keyName -> tokenPickerRequest = TokenPickerRequest(keyName, BindingSlot.PAD0_BUTTON, "Button", VALID_BUTTON_TOKENS) },
                        onPickAxis = { keyName -> tokenPickerRequest = TokenPickerRequest(keyName, BindingSlot.AXIS_PAD0, "Axis", VALID_AXIS_TOKENS) },
                        listeningKeyName = listeningForControllerKeyName,
                        listeningSlot = listeningForControllerSlot,
                        onStartListenButton = { keyName -> startListeningController(keyName, BindingSlot.PAD0_BUTTON) },
                        onStartListenAxis = { keyName -> startListeningController(keyName, BindingSlot.AXIS_PAD0) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // #49/#80 - Controller 2: identical shape to Controller 1
            // (#48/#80), same ControllerBindingsCard, only the columns
            // read/written differ (Pad1Button/AxisPad1). Real, not
            // speculative: the actual smoke/ install's KEY_COIN2/
            // KEY_START2 are genuinely bound through Pad1 (a real two-
            // controller cabinet setup), confirmed against the real
            // hypinput_gamepad.ini during #48/#49's scoping.
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
                        listeningKeyName = listeningForControllerKeyName,
                        listeningSlot = listeningForControllerSlot,
                        onStartListenButton = { keyName -> startListeningController(keyName, BindingSlot.PAD1_BUTTON) },
                        onStartListenAxis = { keyName -> startListeningController(keyName, BindingSlot.AXIS_PAD1) },
                        modifier = Modifier.weight(1f),
                    )
                    ControllerBindingsCard(
                        rows = rows.drop(half),
                        buttonValue = { it.pad1Button },
                        axisValue = { it.axisPad1 },
                        onPickButton = { keyName -> tokenPickerRequest = TokenPickerRequest(keyName, BindingSlot.PAD1_BUTTON, "Button", VALID_BUTTON_TOKENS) },
                        onPickAxis = { keyName -> tokenPickerRequest = TokenPickerRequest(keyName, BindingSlot.AXIS_PAD1, "Axis", VALID_AXIS_TOKENS) },
                        listeningKeyName = listeningForControllerKeyName,
                        listeningSlot = listeningForControllerSlot,
                        onStartListenButton = { keyName -> startListeningController(keyName, BindingSlot.PAD1_BUTTON) },
                        onStartListenAxis = { keyName -> startListeningController(keyName, BindingSlot.AXIS_PAD1) },
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
                    // #63 - label on its own line, pill below it - matches
                    // ControllerBindingsCard's real row shape exactly,
                    // rather than the label+pill-pushed-far-right layout
                    // this used before (the same visual gap problem #46
                    // itself had to fix once already, on a wide/maximized
                    // window).
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
                        Text(row.keyName, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        val isListening = listeningForKeyName == row.keyName
                        val label = if (row.key1 == "0") "Key: None" else "Key: ${row.key1}"
                        Button(
                            onClick = { onRowClick(row.keyName) },
                            modifier = Modifier.width(CONTROLS_PILL_WIDTH),
                        ) {
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
    // #80 - which row+slot is currently listening for a live capture (or
    // null), plus the click handlers that start it. Separate from
    // onPickButton/onPickAxis above (the chevron's own manual-list path,
    // unchanged) - the label half of each pill was reserved for exactly
    // this from #48's own original doc comment.
    listeningKeyName: String?,
    listeningSlot: BindingSlot?,
    onStartListenButton: (String) -> Unit,
    onStartListenAxis: (String) -> Unit,
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
                                label = when {
                                    listeningKeyName == row.keyName && (listeningSlot == BindingSlot.PAD0_BUTTON || listeningSlot == BindingSlot.PAD1_BUTTON) -> "Press a button…"
                                    buttonToken == "0" -> "Button: None"
                                    else -> "Button: $buttonToken"
                                },
                                onLabelClick = { onStartListenButton(row.keyName) },
                                onChevronClick = { onPickButton(row.keyName) },
                            )
                            val axisToken = axisValue(row)
                            PillWithChevron(
                                label = when {
                                    listeningKeyName == row.keyName && (listeningSlot == BindingSlot.AXIS_PAD0 || listeningSlot == BindingSlot.AXIS_PAD1) -> "Move a stick…"
                                    axisToken == "0" -> "Axis: None"
                                    else -> "Axis: $axisToken"
                                },
                                onLabelClick = { onStartListenAxis(row.keyName) },
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
 * ControllerConfigScreen uses). The chevron opens the manual token list
 * (unchanged since #48); #80 makes the label itself clickable too, to
 * start live capture - the label was deliberately inert until now, per
 * this comment's own original wording.
 */
@Composable
private fun PillWithChevron(label: String, onLabelClick: (() -> Unit)? = null, onChevronClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(CONTROLS_PILL_WIDTH)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f).let { if (onLabelClick != null) it.clickable(onClick = onLabelClick) else it },
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
// #73 - the real 2x2 grid Settings actually lays out as (#41's own
// layout decision): App Settings top-left, Controls top-right, About
// bottom-left, Video Settings bottom-right. Movement functions below are
// the grid's real topology, not a generic "next/previous" cycle - moving
// off a grid edge stays put rather than wrapping, matching how #72's
// CarouselFocus movement already behaves (Up from CARDS does nothing
// further once GEAR is reached, not cycle back around).
// #20 - EXPORT (now #91: ABOUT) is a 3rd row's left cell; its own right
// cell is a plain unfocusable placeholder card (matching
// GameHackScreen's identical blank-second-card precedent), so it has no
// transition to a 3rd-row right-side value - moveDown/moveRight below
// simply have no transition off the right column's bottom edge or off
// that cell's own right edge, same "edges stay put, no wraparound" rule
// #73 already established for the first two rows.
//
// #91 - ABOUT and EXPORT swapped physical positions (About moved to row
// 3, Export took About's old row-2 spot) - this topology reflects the
// new layout, not the enum's own declaration order (which is otherwise
// unrelated to grid position).
private enum class SettingsFocus { APP_SETTINGS, CONTROLS, ABOUT, VIDEO_SETTINGS, EXPORT }

private fun SettingsFocus.moveUp(): SettingsFocus = when (this) {
    SettingsFocus.EXPORT -> SettingsFocus.APP_SETTINGS
    SettingsFocus.VIDEO_SETTINGS -> SettingsFocus.CONTROLS
    SettingsFocus.ABOUT -> SettingsFocus.EXPORT
    else -> this
}

private fun SettingsFocus.moveDown(): SettingsFocus = when (this) {
    SettingsFocus.APP_SETTINGS -> SettingsFocus.EXPORT
    SettingsFocus.CONTROLS -> SettingsFocus.VIDEO_SETTINGS
    SettingsFocus.EXPORT -> SettingsFocus.ABOUT
    else -> this
}

private fun SettingsFocus.moveLeft(): SettingsFocus = when (this) {
    SettingsFocus.CONTROLS -> SettingsFocus.APP_SETTINGS
    SettingsFocus.VIDEO_SETTINGS -> SettingsFocus.EXPORT
    else -> this
}

private fun SettingsFocus.moveRight(): SettingsFocus = when (this) {
    SettingsFocus.APP_SETTINGS -> SettingsFocus.CONTROLS
    SettingsFocus.EXPORT -> SettingsFocus.VIDEO_SETTINGS
    else -> this
}

@Composable
private fun SettingsScreen(
    onOpenAppSettings: () -> Unit,
    onOpenControls: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenVideoSettings: () -> Unit,
    onOpenExport: () -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    // #73 - real keyboard/gamepad navigation of this screen, following
    // directly from #72 (Up from the carousel reaches Settings, but
    // previously stranded the user there with no way to move around or
    // back out except a mouse or Escape). Starts on App Settings
    // (top-left) - the natural landing spot coming from #72's own entry
    // point.
    var focus by remember { mutableStateOf(SettingsFocus.APP_SETTINGS) }

    fun openFocused() {
        when (focus) {
            SettingsFocus.APP_SETTINGS -> onOpenAppSettings()
            SettingsFocus.CONTROLS -> onOpenControls()
            SettingsFocus.ABOUT -> onOpenAbout()
            SettingsFocus.VIDEO_SETTINGS -> onOpenVideoSettings()
            SettingsFocus.EXPORT -> onOpenExport()
        }
    }

    // #69's own framing, reused: a second real input source feeding the
    // exact same actions the keyboard onKeyEvent below already handles,
    // not a new interaction model. Only collects while this screen is
    // actually composed, which is what scopes it to "only while Settings
    // is showing" for free.
    LaunchedEffect(Unit) {
        GamepadInputBus.events.collect { action ->
            when (action) {
                GamepadAction.UP -> focus = focus.moveUp()
                GamepadAction.LEFT -> focus = focus.moveLeft()
                GamepadAction.RIGHT -> focus = focus.moveRight()
                GamepadAction.LAUNCH -> openFocused()
                GamepadAction.OPTIONS -> focus = focus.moveDown()
                GamepadAction.BACK -> onBack()
            }
        }
    }

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
                when (event.key) {
                    Key.Escape -> {
                        onBack()
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.CtrlLeft -> {
                        openFocused()
                        true
                    }
                    Key.DirectionUp -> {
                        focus = focus.moveUp()
                        true
                    }
                    Key.DirectionDown -> {
                        focus = focus.moveDown()
                        true
                    }
                    Key.DirectionLeft -> {
                        focus = focus.moveLeft()
                        true
                    }
                    Key.DirectionRight -> {
                        focus = focus.moveRight()
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
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard("App Settings", "Global media override", Modifier.weight(1f), focus == SettingsFocus.APP_SETTINGS, { focus = SettingsFocus.APP_SETTINGS }, onOpenAppSettings)
            SettingsCard("Controls", "Assign gamepad buttons per action", Modifier.weight(1f), focus == SettingsFocus.CONTROLS, { focus = SettingsFocus.CONTROLS }, onOpenControls)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard("Export", "Create standalone .bat files", Modifier.weight(1f), focus == SettingsFocus.EXPORT, { focus = SettingsFocus.EXPORT }, onOpenExport)
            SettingsCard("Video Settings", "Aspect ratio, full screen mode", Modifier.weight(1f), focus == SettingsFocus.VIDEO_SETTINGS, { focus = SettingsFocus.VIDEO_SETTINGS }, onOpenVideoSettings)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard("About", "Build info, credits, open source", Modifier.weight(1f), focus == SettingsFocus.ABOUT, { focus = SettingsFocus.ABOUT }, onOpenAbout)
            // Blank/TBD, matching GameHackScreen's own identical
            // blank-second-card precedent - not a SettingsCard, no focus
            // stop, purely a visual placeholder.
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {}
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String, modifier: Modifier, isFocused: Boolean, onHover: () -> Unit, onClick: () -> Unit) {
    // #76 follow-up, corrected against a real screenshot of the real
    // Android app: the padding-halo trick used elsewhere on this pass
    // added an extra ring around the card's own existing outline, which
    // doesn't match - Android just swaps the card's own fill to grey,
    // same outline weight/size as an unfocused card. Container color,
    // not an added border/ring.
    OutlinedCard(
        // #76 follow-up - real, live feedback: the default mouse-hover
        // ripple painted square (clip() first so it's bounded by the
        // same rounded shape the card itself uses), and mouse hover and
        // keyboard/gamepad focus could each highlight a different card
        // at once ("out of sync") - onHover now moves the same shared
        // focus state clicking would land on anyway.
        modifier = modifier.clip(MaterialTheme.shapes.medium).focusableClickable(onHover = onHover, onClick = onClick),
        colors = if (isFocused) {
            CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        } else {
            CardDefaults.outlinedCardColors()
        },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * #91 - real content on day one, not the #24 blank stub this replaces:
 * the app's own logo, its version (matching Hypdroid's own current
 * versioning, 5.0 - not this launcher's own separate release history,
 * since it isn't cut releases of its own yet), and a credit line for
 * the one real thing this app launches - Hypseus Singe by DirtBagXon,
 * GPL-3.0.
 */
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val logoBitmap = remember { loadHypdroidLogo() }

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
            Text("About", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(48.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (logoBitmap != null) {
                Image(
                    bitmap = logoBitmap,
                    contentDescription = "Hypdroid Desktop",
                    modifier = Modifier.height(120.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text("Version 5.0", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Launches Hypseus Singe by DirtBagXon (GPL-3.0)",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
