package org.libsdl.app

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// #88 - sampled from the actual logo's play-triangle green, not a generic
// Material color, so the one branded surface in the app (this screen) ties
// back to the real wordmark. Dark shade for the single filled primary
// action (Continue) so white text stays readable; the lighter shade is used
// only for outlines/status text, never as another filled button - ChatGPT's
// design review flagged that multiple solid-green buttons would compete for
// attention as "the" primary action.
private val HypdroidGreenDark = Color(0xFF1B5E20)
private val HypdroidGreenLight = Color(0xFF4CAF50)

private const val PREF_ONBOARDING_COMPLETE = "onboarding_complete"

/**
 * Only ever set to true by a deliberate Continue tap (see OnboardingScreen's
 * onContinue) - never just because this screen rendered, and never because a
 * permission redirect was opened. An install interrupted mid-permission-flow
 * (app backgrounded, process killed) must come back to onboarding next
 * launch rather than silently skip it with nothing resolved.
 */
fun loadOnboardingComplete(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(PREF_ONBOARDING_COMPLETE, false)
}

fun saveOnboardingComplete(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(PREF_ONBOARDING_COMPLETE, true)
        .apply()
}

/**
 * #88 - shown once, before Screen.Home, on any install that hasn't
 * completed it yet (a fresh install, or an existing install upgrading to
 * this feature - both look identical: the flag is simply unset). Two-column
 * layout finalized after a ChatGPT design review (see the issue for the
 * full discussion): permissions on the left, folder pickers on the right,
 * one always-enabled Continue at the bottom rather than per-row Skip
 * buttons - the "optional" wording plus each row's own status label already
 * make clear nothing here is required.
 *
 * Focus order for gamepad/D-pad navigation (Handheld is gamepad-first)
 * relies on Compose's built-in 2D directional focus search rather than
 * manual FocusRequesters, unlike GameCarousel's HorizontalPager - this is a
 * plain top-to-bottom, left-to-right form layout, not a custom paging
 * gesture, so the default traversal (logo skipped since it's not
 * focusable, then each card's rows in order, then Continue) should hold
 * without extra wiring. Verify on a real device before considering this
 * done - D-pad spatial focus search can still surprise you in a two-column
 * layout.
 */
@Composable
fun OnboardingScreen(
    mediaImagesRequired: Boolean,
    mediaImagesGranted: Boolean,
    onRequestMediaImages: () -> Unit,
    allFilesAccessRequired: Boolean,
    allFilesAccessGranted: Boolean,
    onRequestAllFilesAccess: () -> Unit,
    gameFolderPath: String?,
    onChooseGameFolder: () -> Unit,
    mediaFolderPath: String?,
    onChooseMediaFolder: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = R.drawable.hypdroid_logo),
            contentDescription = "Hypdroid",
            modifier = Modifier.height(72.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Welcome to Hypdroid", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Choose where your games and artwork live. Everything can be changed later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // #88 - OutlinedCard clips content to its own bounds, and on
                // a short landscape screen (confirmed on a real Retroid
                // Pocket 5 - roughly 480dp of logical height in landscape,
                // far shorter than a tablet) the card's available height can
                // be less than this content's natural height. Without
                // scrolling, that silently clipped the second permission
                // row off-screen entirely rather than showing it - verticalScroll
                // makes it reachable instead of lost.
                Column(
                    modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                ) {
                    Text("Storage access", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (mediaImagesRequired) {
                        OnboardingPermissionRow(
                            description = "Allow Hypdroid to display artwork stored on your device.",
                            granted = mediaImagesGranted,
                            grantedLabel = "Allowed",
                            actionLabel = "Allow artwork",
                            onAction = onRequestMediaImages,
                        )
                    }

                    if (allFilesAccessRequired) {
                        if (mediaImagesRequired) Spacer(modifier = Modifier.height(16.dp))
                        OnboardingPermissionRow(
                            description = "Hypdroid Touch needs access to launch games stored outside the app.",
                            granted = allFilesAccessGranted,
                            grantedLabel = "Access granted",
                            actionLabel = "Open Android settings",
                            // The app can only redirect to the system settings
                            // page here, not grant this permission itself -
                            // there's no runtime popup for it on API 30+.
                            helperText = "Turn on access for Hypdroid, then return here.",
                            onAction = onRequestAllFilesAccess,
                        )
                    }

                    if (!mediaImagesRequired && !allFilesAccessRequired) {
                        Text(
                            "Nothing needed here on this device.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            OutlinedCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                ) {
                    Text("Your folders", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    OnboardingFolderRow(
                        title = "Game folder",
                        description = "Contains your roms, vldp, and singe folders.",
                        path = gameFolderPath,
                        onChoose = onChooseGameFolder,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OnboardingFolderRow(
                        title = "Media folder",
                        description = "Contains box, disc, logo, and background artwork.",
                        path = mediaFolderPath,
                        onChoose = onChooseMediaFolder,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Folders and permissions are optional during setup.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(containerColor = HypdroidGreenDark),
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Continue")
        }
    }
}

/**
 * Shared by OnboardingScreen and the recovery-path rows added to
 * FolderManageScreen (Manage Game Folder / Manage Media Folder) - same
 * component either place, so the two never visually drift apart. Not
 * private for that reason.
 */
@Composable
internal fun OnboardingPermissionRow(
    description: String,
    granted: Boolean,
    grantedLabel: String,
    actionLabel: String,
    helperText: String? = null,
    onAction: () -> Unit,
) {
    Column {
        Text(description, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        if (granted) {
            Text(
                "✓ $grantedLabel",
                color = HypdroidGreenDark,
                style = MaterialTheme.typography.labelLarge,
            )
        } else {
            OutlinedButton(
                onClick = onAction,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HypdroidGreenDark),
            ) {
                Text(actionLabel)
            }
            if (helperText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(helperText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun OnboardingFolderRow(
    title: String,
    description: String,
    path: String?,
    onChoose: () -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(description, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                path ?: "Not selected",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onChoose,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HypdroidGreenLight),
            ) {
                Text("Choose folder")
            }
        }
    }
}
