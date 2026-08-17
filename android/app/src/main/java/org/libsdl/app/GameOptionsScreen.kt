package org.libsdl.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
    onCoverArtChange: (CoverArtType) -> Unit,
    onBezelToggle: (Boolean) -> Unit,
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

        Text("Cover Art", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text((options.coverArt ?: CoverArtType.BOX).name, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { showCoverArtPicker = true }) { Text("Change") }

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Bezel", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (options.bezelEnabled) "On" else "Off",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = options.bezelEnabled, onCheckedChange = onBezelToggle)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Arguments", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = argumentText,
                onValueChange = { argumentText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("-fastboot") },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
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

    if (showCoverArtPicker) {
        AlertDialog(
            onDismissRequest = { showCoverArtPicker = false },
            title = { Text("Cover Art") },
            text = {
                Column {
                    CoverArtType.entries.forEach { type ->
                        Text(
                            type.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCoverArtChange(type)
                                    showCoverArtPicker = false
                                }
                                .padding(12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCoverArtPicker = false }) { Text("Cancel") }
            },
        )
    }
}
