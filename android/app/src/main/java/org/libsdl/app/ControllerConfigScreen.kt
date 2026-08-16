package org.libsdl.app

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

private const val AXIS_THRESHOLD = 0.5f

// Most gamepad buttons arrive as discrete KeyEvents. A few controllers also
// report the analog triggers as a discrete digital press (KEYCODE_BUTTON_L2/
// R2) in addition to a continuous MotionEvent axis - both paths are handled,
// since which one a given controller actually uses can't be assumed without
// testing real hardware.
private fun mapKeyEventToButtonToken(keyCode: Int): String? = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_A -> "BUTTON_A"
    KeyEvent.KEYCODE_BUTTON_B -> "BUTTON_B"
    KeyEvent.KEYCODE_BUTTON_X -> "BUTTON_X"
    KeyEvent.KEYCODE_BUTTON_Y -> "BUTTON_Y"
    KeyEvent.KEYCODE_BUTTON_SELECT -> "BUTTON_BACK"
    KeyEvent.KEYCODE_BUTTON_MODE -> "BUTTON_GUIDE"
    KeyEvent.KEYCODE_BUTTON_START -> "BUTTON_START"
    KeyEvent.KEYCODE_BUTTON_THUMBL -> "BUTTON_LEFTSTICK"
    KeyEvent.KEYCODE_BUTTON_THUMBR -> "BUTTON_RIGHTSTICK"
    KeyEvent.KEYCODE_BUTTON_L1 -> "BUTTON_LEFTSHOULDER"
    KeyEvent.KEYCODE_BUTTON_R1 -> "BUTTON_RIGHTSHOULDER"
    KeyEvent.KEYCODE_DPAD_UP -> "BUTTON_DPAD_UP"
    KeyEvent.KEYCODE_DPAD_DOWN -> "BUTTON_DPAD_DOWN"
    KeyEvent.KEYCODE_DPAD_LEFT -> "BUTTON_DPAD_LEFT"
    KeyEvent.KEYCODE_DPAD_RIGHT -> "BUTTON_DPAD_RIGHT"
    KeyEvent.KEYCODE_BUTTON_L2 -> "AXIS_TRIGGER_LEFT"
    KeyEvent.KEYCODE_BUTTON_R2 -> "AXIS_TRIGGER_RIGHT"
    else -> null
}

// Left/right stick pushed past the threshold, for the AxisPad0 slot.
private fun mapMotionEventToAxisToken(event: MotionEvent): String? {
    val y = event.getAxisValue(MotionEvent.AXIS_Y)
    if (y < -AXIS_THRESHOLD) return "AXIS_LEFT_UP"
    if (y > AXIS_THRESHOLD) return "AXIS_LEFT_DOWN"
    val x = event.getAxisValue(MotionEvent.AXIS_X)
    if (x < -AXIS_THRESHOLD) return "AXIS_LEFT_LEFT"
    if (x > AXIS_THRESHOLD) return "AXIS_LEFT_RIGHT"
    val rz = event.getAxisValue(MotionEvent.AXIS_RZ)
    if (rz < -AXIS_THRESHOLD) return "AXIS_RIGHT_UP"
    if (rz > AXIS_THRESHOLD) return "AXIS_RIGHT_DOWN"
    val z = event.getAxisValue(MotionEvent.AXIS_Z)
    if (z < -AXIS_THRESHOLD) return "AXIS_RIGHT_LEFT"
    if (z > AXIS_THRESHOLD) return "AXIS_RIGHT_RIGHT"
    return null
}

// Analog trigger pulled past the threshold, for the Pad0Button slot (see
// GamepadIni.kt - triggers are parsed as "buttons" by hypseus, not axes).
// AXIS_LTRIGGER/RTRIGGER is the standard mapping; AXIS_BRAKE/GAS is a
// fallback some controllers use instead.
private fun mapMotionEventToTriggerToken(event: MotionEvent): String? {
    val lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER).let {
        if (it == 0f) event.getAxisValue(MotionEvent.AXIS_BRAKE) else it
    }
    if (lt > AXIS_THRESHOLD) return "AXIS_TRIGGER_LEFT"
    val rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER).let {
        if (it == 0f) event.getAxisValue(MotionEvent.AXIS_GAS) else it
    }
    if (rt > AXIS_THRESHOLD) return "AXIS_TRIGGER_RIGHT"
    return null
}

fun captureTokenForKeyEvent(event: KeyEvent, listeningForAxis: Boolean): String? {
    if (listeningForAxis || event.action != KeyEvent.ACTION_DOWN) return null
    return mapKeyEventToButtonToken(event.keyCode)
}

fun captureTokenForMotionEvent(event: MotionEvent, listeningForAxis: Boolean): String? {
    if (event.action != MotionEvent.ACTION_MOVE) return null
    return if (listeningForAxis) mapMotionEventToAxisToken(event) else mapMotionEventToTriggerToken(event)
}

private fun findConflict(rows: List<GamepadRow>, keyName: String, slot: BindingSlot, token: String): String? {
    if (token == "0") return null
    for (row in rows) {
        if (row.keyName == keyName) continue
        val existing = when (slot) {
            BindingSlot.PAD0_BUTTON -> row.pad0Button
            BindingSlot.AXIS_PAD0 -> row.axisPad0
        }
        if (existing == token) return row.keyName
    }
    return null
}

private fun withBinding(rows: List<GamepadRow>, keyName: String, slot: BindingSlot, token: String): List<GamepadRow> =
    rows.map { row ->
        if (row.keyName != keyName) {
            row
        } else {
            when (slot) {
                BindingSlot.PAD0_BUTTON -> row.copy(pad0Button = token)
                BindingSlot.AXIS_PAD0 -> row.copy(axisPad0 = token)
            }
        }
    }

private data class ConflictState(
    val keyName: String,
    val slot: BindingSlot,
    val token: String,
    val conflictingKeyName: String,
)

/**
 * #41 - live gamepad button mapping. Reads/displays the Singe-category ini
 * (the one with real manual customization already, per the owner's SD
 * card) as the canonical view; every save writes to both the Singe and
 * Daphne-native files (applyBindingToBothFiles(), see GamepadIni.kt) so
 * they don't independently drift the way they already had before this
 * screen existed.
 *
 * Capture works through Android's own KeyEvent/MotionEvent APIs, not
 * hypseus/SDL - this screen is plain Compose in MainActivity, which never
 * runs SDL at all (SDL only exists inside HypseusActivity during actual
 * gameplay). MainActivity.dispatchKeyEvent()/dispatchGenericMotionEvent()
 * forward real input events here via gamepadCaptureListener while a slot is
 * being listened to.
 */
@Composable
fun ControllerConfigScreen(
    activity: MainActivity,
    gameFolderPath: String,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var rows by remember { mutableStateOf<List<GamepadRow>?>(null) }
    var fileMissing by remember { mutableStateOf(false) }

    LaunchedEffect(gameFolderPath) {
        val file = File(singeIniPath(gameFolderPath))
        if (file.exists()) {
            rows = parseGamepadRows(file.readText())
            fileMissing = false
        } else {
            fileMissing = true
        }
    }

    var listening by remember { mutableStateOf<Pair<String, BindingSlot>?>(null) }
    var conflict by remember { mutableStateOf<ConflictState?>(null) }

    DisposableEffect(listening) {
        val current = listening
        if (current == null) {
            activity.gamepadCaptureListener = null
        } else {
            val (keyName, slot) = current
            activity.gamepadCaptureListeningForAxis = (slot == BindingSlot.AXIS_PAD0)
            activity.gamepadCaptureListener = capture@{ token ->
                val currentRows = rows ?: return@capture
                val conflictingKey = findConflict(currentRows, keyName, slot, token)
                if (conflictingKey != null) {
                    conflict = ConflictState(keyName, slot, token, conflictingKey)
                } else {
                    applyBindingToBothFiles(gameFolderPath, keyName, slot, token)
                    rows = withBinding(currentRows, keyName, slot, token)
                }
                listening = null
            }
        }
        onDispose { activity.gamepadCaptureListener = null }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Controller Configuration", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))

        val currentRows = rows
        if (fileMissing) {
            Text("Launch a game once first to generate the default controller settings.")
        } else if (currentRows == null) {
            Text("Loading...")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(currentRows, key = { it.keyName }) { row ->
                    ControllerRow(
                        row = row,
                        isListeningButton = listening == (row.keyName to BindingSlot.PAD0_BUTTON),
                        isListeningAxis = listening == (row.keyName to BindingSlot.AXIS_PAD0),
                        onTapButton = { listening = row.keyName to BindingSlot.PAD0_BUTTON },
                        onTapAxis = { listening = row.keyName to BindingSlot.AXIS_PAD0 },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    conflict?.let { c ->
        AlertDialog(
            onDismissRequest = { conflict = null },
            title = { Text("Already assigned") },
            text = {
                Text("${c.token} is currently assigned to ${c.conflictingKeyName} too. Assign it to ${c.keyName} as well?")
            },
            confirmButton = {
                TextButton(onClick = {
                    applyBindingToBothFiles(gameFolderPath, c.keyName, c.slot, c.token)
                    rows = withBinding(rows ?: emptyList(), c.keyName, c.slot, c.token)
                    conflict = null
                }) { Text("Assign anyway") }
            },
            dismissButton = {
                TextButton(onClick = { conflict = null }) { Text("Cancel") }
            },
        )
    }
}

// Only the 4 directional rows use AxisPad0 in the real file (a left-stick
// push as a redundant alternative to the d-pad button) - every other row
// only ever has a Pad0Button, so there's nothing meaningful to show or
// capture for an Axis slot there.
private val DIRECTIONAL_KEYS = setOf("KEY_UP", "KEY_DOWN", "KEY_LEFT", "KEY_RIGHT")

@Composable
private fun ControllerRow(
    row: GamepadRow,
    isListeningButton: Boolean,
    isListeningAxis: Boolean,
    onTapButton: () -> Unit,
    onTapAxis: () -> Unit,
) {
    Column {
        Text(row.keyName, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onTapButton) {
                Text(if (isListeningButton) "Press a button..." else "Button: ${displayToken(row.pad0Button)}")
            }
            if (row.keyName in DIRECTIONAL_KEYS) {
                Button(onClick = onTapAxis) {
                    Text(if (isListeningAxis) "Move a stick..." else "Axis: ${displayToken(row.axisPad0)}")
                }
            }
        }
    }
}

private fun displayToken(token: String): String = if (token == "0") "None" else token
