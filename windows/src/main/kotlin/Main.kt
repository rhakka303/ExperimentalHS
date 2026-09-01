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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

private sealed interface Screen {
    data object Carousel : Screen
    data object Settings : Screen
    // #24 - each of these is a genuinely blank page for
    // now, matching #19's Cover Art stub precedent: present so the
    // Settings screen matches the shape it will eventually need, not
    // functional yet. Manage Game Folder and Touch Controls (both real
    // cards on Android's own Settings screen) are deliberately excluded -
    // no folder picker exists or is planned here at all (#6), and there
    // is no touchscreen on desktop.
    data object ManageMediaFolder : Screen
    data object AppSettings : Screen
    data object Controls : Screen
    data object About : Screen
    data class GameOptionsFor(val game: Game) : Screen
    data class GameHackFor(val game: Game) : Screen
}

/**
 * #17 - carousel UI, replacing #11's plain list. #19 adds the per-game
 * options screen, the Game Hacks screen, and navigation between all three.
 */
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "HypdroidDesktop") {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                val installRoot = remember { resolveInstallRoot() }
                val launcherFolder = remember { resolveLauncherFolder() }

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
                            HypdroidApp(games = result.games, installRoot = installRoot, launcherFolder = launcherFolder)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HypdroidApp(games: List<Game>, installRoot: File, launcherFolder: File?) {
    // #19 - lifted out of GameCarousel so the pager's position survives a
    // trip to another screen and back ("Back returns to the carousel, on
    // the same card that was open"). GameCarousel gets torn down and
    // recreated when the screen switches away and back, so remembering
    // the page inside it (as #17 originally did) would reset to page 0
    // every time - the same bug Android's own #52 fix already addressed.
    var carouselPage by remember { mutableStateOf(0) }
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
        )
        is Screen.Settings -> SettingsScreen(
            onOpenManageMediaFolder = { screen = Screen.ManageMediaFolder },
            onOpenAppSettings = { screen = Screen.AppSettings },
            onOpenControls = { screen = Screen.Controls },
            onOpenAbout = { screen = Screen.About },
            onBack = { screen = Screen.Carousel },
        )
        is Screen.ManageMediaFolder -> BlankPlaceholderScreen("Manage Media Folder", onBack = { screen = Screen.Settings })
        is Screen.AppSettings -> BlankPlaceholderScreen("App Settings", onBack = { screen = Screen.Settings })
        is Screen.Controls -> BlankPlaceholderScreen("Controls", onBack = { screen = Screen.Settings })
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

@Composable
private fun GameCarousel(
    games: List<Game>,
    installRoot: File,
    launcherFolder: File?,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onOpenOptions: (Game) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { games.size })
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChanged(it) }
    }

    fun extraArgsFor(game: Game): List<String> =
        launcherFolder?.let { launchArgumentsFor(installRoot, loadOptions(it, game.name), game.name) } ?: emptyList()

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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cardWidth = maxWidth * 0.32f

        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(cardWidth),
            contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
            pageSpacing = 16.dp,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                // A bare LaunchedEffect(Unit) calling requestFocus() raced
                // this modifier's own attachment on desktop and threw
                // "FocusRequester is not initialized" - a real timing
                // difference from Android's equivalent code, not something
                // to guess a retry-count for. onGloballyPositioned fires on
                // every layout pass (window resizes included), not once, so
                // it's guarded to request focus only the first time. Even
                // guarded to fire after layout, requestFocus() still threw
                // here in practice (confirmed twice on real hardware) - so
                // this is wrapped rather than trusted further. Worst case
                // if it still fails: auto-focus-on-launch doesn't happen,
                // and the pager gets focus on the first click instead.
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
                        // Ctrl matches hypseus's own KEY_BUTTON1 default
                        // (SDLK_LCTRL, the confirm/action button) in both
                        // hypinput_gamepad.ini in this install and the
                        // plain hypinput.ini reference in the vendored
                        // source - the ecosystem's standard confirm key.
                        Key.Enter, Key.NumPadEnter, Key.CtrlLeft -> {
                            launchCentered()
                            true
                        }
                        // #19 - Down opens the centered card's options
                        // screen, alongside clicking its gear icon.
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
                    scale = scale,
                    onClick = { launchGame(game, installRoot, extraArgsFor(game)) },
                    onOpenOptions = { onOpenOptions(game) },
                )
            }
        }

        // #17 - visible mouse-clickable paging, the direct mouse
        // equivalent of the arrow keys.
        IconButton(
            onClick = ::pageLeft,
            modifier = Modifier.align(Alignment.CenterStart).padding(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous game")
        }
        IconButton(
            onClick = ::pageRight,
            modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next game")
        }

        // #24 - Settings entry point, matching Android's top-bar gear
        // placement.
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun GameCard(game: Game, scale: Float, onClick: () -> Unit, onOpenOptions: () -> Unit) {
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
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(game.name, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))

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
                // #19 - stub: no cover-art system exists on Windows yet
                // (phase 3). Shown so the screen matches Android's layout
                // rather than looking sparse, but "Change" does nothing.
                OutlinedCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Cover Art", style = MaterialTheme.typography.titleMedium)
                        Text("Not available yet (phase 3)", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenGameHack)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Game Hacks", style = MaterialTheme.typography.titleMedium)
                        Text("Custom Game fixes", style = MaterialTheme.typography.bodySmall)
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
 * #24 - four cards matching the subset of Android's real
 * Settings screen that applies here (Manage Game Folder and Touch
 * Controls excluded - see Screen's own comment for why). Each card is a
 * genuinely blank destination for now, same "present but not yet
 * functional" precedent #19 set for Cover Art. No .bat export card here -
 * that's #20's original scope, deferred separately (owner, 2026-08-31:
 * low priority, phase 5+); this pass is purely about the screen's shape.
 */
@Composable
private fun SettingsScreen(
    onOpenManageMediaFolder: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenControls: () -> Unit,
    onOpenAbout: () -> Unit,
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
            SettingsCard("Manage Media Folder", "Pick where your artwork lives", Modifier.weight(1f), onOpenManageMediaFolder)
            SettingsCard("App Settings", "Global Cover Art override", Modifier.weight(1f), onOpenAppSettings)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard("Controls", "Assign gamepad buttons per action", Modifier.weight(1f), onOpenControls)
            SettingsCard("About", "Build info, credits, open source", Modifier.weight(1f), onOpenAbout)
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
