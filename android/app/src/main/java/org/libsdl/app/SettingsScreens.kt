package org.libsdl.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * #55 - Settings redesigned as a 2-column grid of category cards (modeled on
 * Eden's own Settings screen, used purely as a visual reference), each
 * opening its own dedicated screen - replaces the old single scrolling list
 * of rows, which had already needed a scroll fix once just to keep
 * Controller Configuration reachable.
 */
@Composable
fun SettingsScreen(
    onOpenManageGameFolder: () -> Unit,
    onOpenManageMediaFolder: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenControllerConfig: () -> Unit,
    onOpenAbout: () -> Unit,
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

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            val cards = listOf(
                Triple("Manage Game Folder", "Pick where your games live", onOpenManageGameFolder),
                Triple("Manage Media Folder", "Pick where your artwork lives", onOpenManageMediaFolder),
                Triple("App Settings", "Global Cover Art override", onOpenAppSettings),
                Triple("Controls", "Assign gamepad buttons per action", onOpenControllerConfig),
                Triple("About", "Build info, credits, open source", onOpenAbout),
            )
            items(cards) { (title, description, onClick) ->
                SettingsCard(title = title, description = description, onClick = onClick)
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, description: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Shared by both the Game folder and Media folder cards - identical shape,
 * just a different label/path/change action. `instructions`, when non-null
 * (only the Game folder screen passes it - #60), shows the required
 * singe/roms/vldp layout on-screen, since that structure is a real
 * hypseus/Singe requirement, not obvious from the folder picker alone.
 */
@Composable
fun FolderManageScreen(
    title: String,
    path: String?,
    instructions: String? = null,
    onChange: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(path ?: "Not set", style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onChange) { Text("Change") }
        }

        if (instructions != null) {
            Spacer(modifier = Modifier.height(32.dp))
            Text("Recommended Folder Structure", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(instructions, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * #47's Global Cover Art override and #66's Background Art, side by side as
 * two double-width cards - matching the main Settings grid's card style,
 * just wider since each needs room for a toggle + conditional follow-up
 * control stacked underneath.
 */
@Composable
fun AppSettingsScreen(
    globalCoverArtEnabled: Boolean,
    globalCoverArtType: CoverArtType,
    onGlobalCoverArtToggle: (Boolean) -> Unit,
    onGlobalCoverArtTypeChange: (CoverArtType) -> Unit,
    backgroundArtEnabled: Boolean,
    defaultArtEnabled: Boolean,
    onBackgroundArtToggle: (Boolean) -> Unit,
    onDefaultArtToggle: (Boolean) -> Unit,
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
            Text("App Settings", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                        Switch(checked = backgroundArtEnabled, onCheckedChange = onBackgroundArtToggle)
                    }
                    // #66 - Default Art overrides every game's own bg art
                    // with bg/default.png, same override shape as Global
                    // Cover Art - only shown/meaningful while Background
                    // Art itself is on.
                    if (backgroundArtEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Default Art", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Uses default.png for all games.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Switch(checked = defaultArtEnabled, onCheckedChange = onDefaultArtToggle)
                        }
                    }
                }
            }
        }
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

/**
 * Build info, the wordmark logo, and open-source credit for the project
 * this app is built from (hypseus-singe by DirtBagXon, GPL-3.0) plus other
 * bundled dependencies - real attribution, not a game name, so naming it
 * directly is correct here (unlike copyrighted game titles elsewhere).
 */
@Composable
fun AboutScreen(context: Context, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("About", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Image(
            painter = painterResource(id = R.drawable.hypdroid_logo),
            contentDescription = "Hypdroid",
            modifier = Modifier.height(64.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text("Version $versionName", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(32.dp))

        Text("Open Source", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Built on Hypseus Singe by DirtBagXon (github.com/DirtBagXon/hypseus-singe), " +
                "licensed under GPL-3.0.\n\n" +
                "Also uses SDL3 and Coil.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
