package com.example.gazecaa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbaSetupScreen(
    initial: SessionConfig,
    defaultLearner: String,
    onBack: () -> Unit,
    onVoice: (AacSymbol) -> Unit,
    hasVoice: (String) -> Boolean,
    onAddPhoto: () -> Unit,
    onDeleteSymbol: (AacSymbol) -> Unit,
    onStart: (SessionConfig) -> Unit,
) {
    var learner by remember { mutableStateOf(initial.learner.ifBlank { defaultLearner }) }
    var program by remember { mutableStateOf(initial.program) }
    var targets by remember { mutableStateOf(initial.targetIds.filter { Symbols.byId(it) != null }) }
    var field by remember { mutableIntStateOf(initial.fieldSize) }
    var trials by remember { mutableIntStateOf(initial.trials) }
    var prompt by remember { mutableStateOf(initial.prompt) }
    var timeout by remember { mutableIntStateOf(initial.timeoutSec) }
    var errCorr by remember { mutableStateOf(initial.errorCorrection) }
    var reinf by remember { mutableStateOf(initial.reinforcement) }
    var labels by remember { mutableStateOf(initial.showLabels) }
    var input by remember { mutableStateOf(initial.inputMode) }
    var tokens by remember { mutableIntStateOf(initial.tokens) }
    var breakSec by remember { mutableIntStateOf(initial.breakSec) }
    var autoFade by remember { mutableStateOf(initial.autoFade) }
    var sound by remember { mutableStateOf(initial.sound) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Nuova sessione ABA", onBack) {
            Button(
                enabled = targets.isNotEmpty(),
                onClick = {
                    onStart(
                        SessionConfig(
                            learner = learner.trim(), program = program, targetIds = targets,
                            fieldSize = field, trials = trials, prompt = prompt, timeoutSec = timeout,
                            errorCorrection = errCorr, reinforcement = reinf, showLabels = labels,
                            inputMode = input, tokens = tokens, breakSec = breakSec,
                            autoFade = autoFade, sound = sound,
                        )
                    )
                }
            ) { Text("▶ Avvia sessione") }
        }
        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = learner, onValueChange = { learner = it },
                    label = { Text("Utente (iniziali o codice, non il nome completo)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                SectionLabel("Risposta del bambino")
                ChoiceRow(InputMode.entries, input, { it.title }) { input = it }
                SectionLabel("Programma")
                ChoiceRow(ProgramType.entries, program, { it.title }) { program = it }
                SectionLabel("Immagini nel campo")
                ChoiceRow(listOf(1, 2, 3, 4), field, { "$it" }) { field = it }
                SectionLabel("Numero di prove")
                ChoiceRow(listOf(5, 10, 15, 20), trials, { "$it" }) { trials = it }
                SectionLabel("Livello di prompt iniziale")
                ChoiceRow(PromptLevel.entries, prompt, { it.title }) { prompt = it }
                SwitchRow("Riduzione automatica del prompt (sfumatura)", autoFade) { autoFade = it }
                SectionLabel("Tempo massimo di risposta")
                ChoiceRow(listOf(10, 15, 30, 60), timeout, { "$it s" }) { timeout = it }
                SectionLabel("Token board: gettoni per il premio")
                ChoiceRow(listOf(0, 3, 5, 10), tokens, { if (it == 0) "No" else "$it" }) { tokens = it }
                if (tokens > 0) {
                    SectionLabel("Durata del premio")
                    ChoiceRow(listOf(10, 15, 30, 60), breakSec, { "$it s" }) { breakSec = it }
                }
                SwitchRow("Correzione dell'errore (mostra la risposta giusta)", errCorr) { errCorr = it }
                SwitchRow("Rinforzo (animazione + voce)", reinf) { reinf = it }
                SwitchRow("Suono e vibrazione sulla risposta corretta", sound) { sound = it }
                SwitchRow("Mostra le parole sotto le immagini", labels) { labels = it }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Target (${targets.size}) — tocca per scegliere · 📷 foto · 🎙 registra la voce")
                SymbolPicker(
                    symbols = Symbols.abaAll,
                    selected = targets,
                    numbered = false,
                    onToggle = { s -> targets = if (s.id in targets) targets - s.id else targets + s.id },
                    onAddPhoto = onAddPhoto,
                    onDelete = { s -> targets = targets - s.id; onDeleteSymbol(s) },
                    modifier = Modifier.fillMaxSize(),
                    onVoice = onVoice,
                    hasVoice = hasVoice,
                )
            }
        }
    }
}
