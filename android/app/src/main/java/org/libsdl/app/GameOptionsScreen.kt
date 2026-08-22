package org.libsdl.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * #31 - per-game options: Cover Art (CD/Logo/Box/Text), Bezel (on/off),
 * and Arguments (free-text list). Opened by pressing down on the d-pad (or
 * long-press for touch) on the focused carousel card. Each field saves
 * immediately on change - no separate Save button, matching #30/#36's
 * folder pickers and #41's controller bindings.
 */
@Composable
fun GameOptionsScreen(
    game: Game,
    options: GameOptions,
    globalCoverArtEnabled: Boolean,
    onCoverArtChange: (CoverArtType) -> Unit,
    onBezelToggle: (Boolean) -> Unit,
    onScorebezelAutofitToggle: (Boolean) -> Unit,
    onAddArgument: (String) -> Unit,
    onRemoveArgument: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var showCoverArtPicker by remember { mutableStateOf(false) }
    var argumentText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Options: ${game.name}", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // #58 - Cover Art and Bezel sit side by side, each in its own
        // thin-bordered card, instead of two full-width stacked rows - frees
        // up vertical room for the Arguments card below.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Cover Art", style = MaterialTheme.typography.titleMedium)
                    Text(
                        (options.coverArt ?: CoverArtType.BOX).name,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    // #47 - while the Settings "Global Cover Art" override is
                    // on, every game shows that single chosen type
                    // regardless of what's saved here, so changing it here
                    // would have no visible effect. Greyed out rather than
                    // hidden - the per-game choice underneath is still
                    // there, just not applied, and picking it back up when
                    // Global is turned off shouldn't require re-entering it.
                    if (globalCoverArtEnabled) {
                        Text(
                            "Controlled by Settings > Global Cover Art",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HypdroidButton(
                        onClick = { showCoverArtPicker = true },
                        enabled = !globalCoverArtEnabled,
                    ) { Text("Change") }
                }
            }

            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bezel", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (options.bezelEnabled) "On" else "Off",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        HypdroidSwitch(checked = options.bezelEnabled, onCheckedChange = onBezelToggle)
                    }

                    // #111 - fits the scoreboard bezel (score/lives/credits
                    // panel) to the real black-bar space next to the video
                    // instead of a fixed fraction of the video's own width.
                    // Same card as Bezel above since it's the same general
                    // area of settings, but independent - matters for any
                    // game with a scoreboard bezel active, whether that's
                    // this Bezel toggle or the game's own script turning
                    // its scoreboard bezel on directly (like Esh's
                    // Aurunmilla does).
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Scorebezel Autofit", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (options.scorebezelAutofit) "On" else "Off",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        HypdroidSwitch(
                            checked = options.scorebezelAutofit,
                            onCheckedChange = onScorebezelAutofitToggle,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
                Text("Arguments", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HypdroidOutlinedTextField(
                        value = argumentText,
                        onValueChange = { argumentText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("-fastboot") },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    HypdroidButton(onClick = {
                        val trimmed = argumentText.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddArgument(trimmed)
                            argumentText = ""
                        }
                    }) { Text("Add") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(options.arguments, key = { it }) { arg ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Text(arg, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onRemoveArgument(arg) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove")
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
                onCoverArtChange(type)
                showCoverArtPicker = false
            },
            onDismiss = { showCoverArtPicker = false },
        )
    }
}

/**
 * The 4-choice (CD/Logo/Box/Text) Cover Art picker, shared between #31's
 * per-game Options screen (above) and #47's Settings "Global Cover Art" row
 * - same list, same behavior, just a different caller for what "selecting a
 * type" means.
 */
@Composable
fun CoverArtPickerDialog(onSelect: (CoverArtType) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cover Art") },
        text = {
            Column {
                CoverArtType.entries.forEach { type ->
                    Text(
                        type.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(type) }
                            .padding(12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
