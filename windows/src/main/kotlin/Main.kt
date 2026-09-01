import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

/**
 * #17 - carousel UI, replacing #11's plain list. The Android
 * `GameCarousel`/`GameCard` (MainActivity.kt) is the specification for the
 * shape: `HorizontalPager`, one card centered at full scale, neighbors
 * peeking in scaled down, fixed card width, click any card to launch it.
 * Two things do not carry over: the d-pad `onKeyEvent` wiring (Android's
 * OS translates controller presses into Compose KeyEvents for free;
 * Windows has no such thing - that's phase 4, #6) and long-press-to-open-
 * options (not a discoverable desktop mouse pattern - deferred to #19).
 *
 * No cover art yet (#6's phase 3) - a card falls back to plain text, the
 * same fallback Android already uses when a game has no art file.
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
                            GameCarousel(games = result.games, installRoot = installRoot)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameCarousel(games: List<Game>, installRoot: File) {
    val pagerState = rememberPagerState(pageCount = { games.size })
    val coroutineScope = rememberCoroutineScope()
    // Keyboard paging/launch isn't automatic the way LazyColumn's up/down
    // is - HorizontalPager needs it wired explicitly, same reasoning as
    // Android's own d-pad wiring (just keyboard here instead of gamepad).
    val focusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    fun pageLeft() {
        val target = pagerState.currentPage - 1
        if (target >= 0) coroutineScope.launch { pagerState.animateScrollToPage(target) }
    }
    fun pageRight() {
        val target = pagerState.currentPage + 1
        if (target < games.size) coroutineScope.launch { pagerState.animateScrollToPage(target) }
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
                // and the pager gets focus on the first click instead
                // (clicking a card still works regardless - #17's
                // acceptance criteria doesn't require keyboard paging to
                // work before any interaction at all).
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
                        // source - the ecosystem's standard confirm key,
                        // not something specific to one config.
                        Key.Enter, Key.NumPadEnter, Key.CtrlLeft -> {
                            launchGame(games[pagerState.currentPage], installRoot)
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
                    onClick = { launchGame(game, installRoot) },
                )
            }
        }

        // #17 scope addition, 2026-08-31: visible mouse-clickable paging,
        // the direct mouse equivalent of the arrow keys - a drag gesture
        // on the pager itself isn't a substitute, since nothing on screen
        // hints it exists.
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
    }
}

@Composable
private fun GameCard(game: Game, scale: Float, onClick: () -> Unit) {
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
    }
}
