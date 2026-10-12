package com.example.gazecaa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(settings: GazeSettings, onDismiss: () -> Unit) {
    var dwell by remember { mutableFloatStateOf(settings.dwellMs.toFloat()) }
    var stab by remember { mutableFloatStateOf(settings.stability.toFloat()) }
    var cursor by remember { mutableStateOf(settings.showCursor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Impostazioni") },
        text = {
            Column {
                Text("Tempo di sosta: %.1f s".format(dwell / 1000f))
                Slider(dwell, { dwell = it }, valueRange = 500f..4000f, steps = 13)
                Spacer(Modifier.height(8.dp))
                Text("Stabilità cursore: ${stab.roundToInt()} (alto = più fermo, più lento)")
                Slider(stab, { stab = it }, valueRange = 1f..10f, steps = 8)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mostra il cursore dello sguardo")
                    Switch(cursor, { cursor = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.update(dwell.roundToInt(), stab.roundToInt(), cursor)
                onDismiss()
            }) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = {
                dwell = GazeConfig.DEFAULT_DWELL_MS.toFloat()
                stab = GazeConfig.DEFAULT_STABILITY.toFloat()
                cursor = true
            }) { Text("Predefiniti") }
        }
    )
}
