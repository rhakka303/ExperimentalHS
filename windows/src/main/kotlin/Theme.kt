import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * #61 - navy, matching Android's own real Theme.kt value exactly
 * (`HypdroidNavy = Color(0xFF1B2A4A)`) - every pill (Button) and Switch in
 * this app was using Compose's untouched Material3 default (purple) until
 * now, since no custom ColorScheme had been set up at all. Only
 * primary/primaryContainer (plus their "on" counterparts) are overridden,
 * matching Android's own restraint here - every other role (surface,
 * error, etc.) stays Material3's own default rather than guessing at
 * colors nothing has been tested against.
 *
 * A single ColorScheme applied once at the app's root MaterialTheme call
 * (main()) - every Button/Switch across the whole app inherits navy
 * automatically since they already read MaterialTheme.colorScheme.primary
 * internally, not something touched at each call site.
 *
 * Android's own Theme.kt also has a real green D-pad-focus-ring system
 * (HypdroidButton/HypdroidSwitch/etc. wrappers) built for gamepad
 * navigation - real future reference for phase 4, deliberately not part
 * of this pass.
 */
private val HypdroidNavy = Color(0xFF1B2A4A)

val HypdroidColorScheme = lightColorScheme(
    primary = HypdroidNavy,
    onPrimary = Color.White,
    primaryContainer = HypdroidNavy,
    onPrimaryContainer = Color.White,
)

// #72 - same real green Android's own Theme.kt uses for its D-pad focus
// ring (HypdroidGreenAccent, #92 there) - reused verbatim now that this
// project has real gamepad/keyboard focus navigation of its own (#69/#72)
// to actually indicate, rather than inventing a new color for it.
val HypdroidGreenAccent = Color(0xFF4CAF50)
