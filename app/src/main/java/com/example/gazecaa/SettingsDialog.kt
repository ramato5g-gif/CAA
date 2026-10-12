package com.example.gazecaa

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(settings: GazeSettings, onDismiss: () -> Unit) {
    var select by remember { mutableStateOf(settings.selectMode) }
    var dwell by remember { mutableFloatStateOf(settings.dwellMs.toFloat()) }
    var stab by remember { mutableFloatStateOf(settings.stability.toFloat()) }
    var head by remember { mutableFloatStateOf(settings.headSens.toFloat()) }
    var cursor by remember { mutableStateOf(settings.cursorSize) }
    var magnet by remember { mutableStateOf(settings.magnet) }
    var drift by remember { mutableStateOf(settings.autoDrift) }
    var lock by remember { mutableStateOf(settings.childLock) }
    var sentence by remember { mutableStateOf(settings.caaSentence) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Impostazioni puntatore") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionLabel("Conferma della scelta")
                ChoiceRow(SelectMode.entries, select, { it.title }) { select = it }
                Spacer(Modifier.height(8.dp))
                if (select == SelectMode.SCAN) {
                    Text("Scansione: tocca una scheda qualsiasi, premi un sensore (Spazio/Invio) o ammicca a lungo per scegliere quella illuminata.")
                }
                Text((if (select == SelectMode.SCAN) "Velocità di scansione" else "Tempo di sosta") + ": %.1f s".format(dwell / 1000f))
                Slider(dwell, { dwell = it }, valueRange = 500f..4000f, steps = 13)
                Text("Stabilità: ${stab.roundToInt()}  (alto = più fermo ma più lento)")
                Slider(stab, { stab = it }, valueRange = 1f..10f, steps = 8)
                Text("Sensibilità modalità Testa: ${head.roundToInt()}  (alto = basta muovere meno la testa)")
                Slider(head, { head = it }, valueRange = 1f..10f, steps = 8)
                SectionLabel("Cursore")
                ChoiceRow(CursorSize.entries, cursor, { it.title }) { cursor = it }
                Spacer(Modifier.height(8.dp))
                SwitchRow("Magnetismo (aggancia l'immagine più vicina)", magnet) { magnet = it }
                SwitchRow("Correzione automatica della deriva a ogni scelta", drift) { drift = it }
                SectionLabel("Tabella e protezione")
                SwitchRow("Componi frasi nella tabella Comunica", sentence) { sentence = it }
                SwitchRow("Protezione bambino: nascondi i controlli (pressione lunga su ⚙ per mostrarli)", lock) { lock = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.update(select, dwell.roundToInt(), stab.roundToInt(), head.roundToInt(), cursor, magnet)
                settings.setFlags(drift, lock, sentence)
                onDismiss()
            }) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = {
                select = SelectMode.DWELL
                dwell = GazeConfig.DEFAULT_DWELL_MS.toFloat()
                stab = GazeConfig.DEFAULT_STABILITY.toFloat()
                head = GazeConfig.DEFAULT_HEAD_SENS.toFloat()
                cursor = CursorSize.MEDIUM
                magnet = true
                drift = true
            }) { Text("Predefiniti") }
        }
    )
}
