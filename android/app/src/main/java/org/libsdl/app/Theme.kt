package org.libsdl.app

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * #92 - navy, sampled to pair with the green accent (#84's Controller
 * Config focus ring, #88's onboarding) without competing with it the way
 * Material3's untouched default purple baseline did. Only
 * primary/onPrimary/primaryContainer are overridden here - every other
 * role (surface, error, etc.) stays Material3's own default rather than
 * guessing at colors nothing has actually been tested against on a real
 * device yet.
 */
private val HypdroidNavy = Color(0xFF1B2A4A)

// Same green used by #84's focus ring and #88's onboarding - one accent
// color used consistently everywhere it appears, not a new shade per screen.
val HypdroidGreenAccent = Color(0xFF4CAF50)

private val HypdroidColorScheme = lightColorScheme(
    primary = HypdroidNavy,
    onPrimary = Color.White,
    primaryContainer = HypdroidNavy,
    onPrimaryContainer = Color.White,
)

@Composable
fun HypdroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HypdroidColorScheme, content = content)
}

/**
 * #92 - the app's one real Button, used everywhere a filled/primary button
 * appears. Material3's default focus indication read as too subtle to
 * actually see which pill is D-pad-highlighted (confirmed on a real
 * device, originally found on Controller Config's capture buttons in #84)
 * - this gives every pill in the app the same clearly-visible green ring
 * on focus, not just the one screen that happened to get tested first.
 */
@Composable
fun HypdroidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isFocused) {
                    Modifier.border(3.dp, HypdroidGreenAccent, ButtonDefaults.shape)
                } else {
                    Modifier
                },
            ),
        content = { content() },
    )
}

/**
 * #92 - same focus-ring treatment as HypdroidButton, for Switch. Reported
 * as hard to tell when a toggle was D-pad-focused, for both the on and off
 * track colors - a white/light thumb on a light track gave the default
 * focus indication nothing to contrast against either way. The ring is
 * fully rounded (percent = 50) to match the track's own pill shape, with a
 * little padding so it sits cleanly around the switch rather than clipping
 * into the thumb.
 */
@Composable
fun HypdroidSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isFocused) {
                    Modifier
                        .padding(2.dp)
                        .border(3.dp, HypdroidGreenAccent, RoundedCornerShape(percent = 50))
                        .padding(2.dp)
                } else {
                    Modifier
                },
            ),
    )
}
