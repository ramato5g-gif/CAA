package com.example.gazecaa

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Impostazioni per il caregiver, modificabili senza ricompilare. */
@Composable
fun SettingsDialog(settings: GazeSettings, onDismiss: () -> Unit) {
    var dwell by remember { mutableFloatStateOf(settings.dwellMs.toFloat()) }
    var smooth by remember { mutableFloatStateOf(settings.smoothing.toFloat()) }
    var head by remember { mutableFloatStateOf(settings.headWeight) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Impostazioni") },
        text = {
            Column {
                Text("Tempo di sosta: %.1f s".format(dwell / 1000f))
                Slider(dwell, { dwell = it }, valueRange = 500f..4000f, steps = 13)
                Spacer(Modifier.height(8.dp))

                Text("Stabilità cursore: ${smooth.roundToInt()} (alto = più stabile, più lento)")
                Slider(smooth, { smooth = it }, valueRange = 2f..20f, steps = 17)
                Spacer(Modifier.height(8.dp))

                Text("Uso della testa: ${(head * 100).roundToInt()}% (0 = solo occhi)")
                Slider(head, { head = it }, valueRange = 0f..1.5f)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.update(dwell.roundToInt(), smooth.roundToInt(), head)
                onDismiss()
            }) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = {
                dwell = GazeConfig.DEFAULT_DWELL_MS.toFloat()
                smooth = GazeConfig.DEFAULT_SMOOTHING.toFloat()
                head = GazeConfig.DEFAULT_HEAD_WEIGHT
            }) { Text("Predefiniti") }
        }
    )
}
