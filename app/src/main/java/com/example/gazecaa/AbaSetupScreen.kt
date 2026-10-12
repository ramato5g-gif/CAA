package com.example.gazecaa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbaSetupScreen(initial: SessionConfig, onBack: () -> Unit, onStart: (SessionConfig) -> Unit) {
    var learner by remember { mutableStateOf(initial.learner) }
    var program by remember { mutableStateOf(initial.program) }
    var targets by remember { mutableStateOf(initial.targetIds.toSet()) }
    var field by remember { mutableIntStateOf(initial.fieldSize) }
    var trials by remember { mutableIntStateOf(initial.trials) }
    var prompt by remember { mutableStateOf(initial.prompt) }
    var timeout by remember { mutableIntStateOf(initial.timeoutSec) }
    var errCorr by remember { mutableStateOf(initial.errorCorrection) }
    var reinf by remember { mutableStateOf(initial.reinforcement) }
    var labels by remember { mutableStateOf(initial.showLabels) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Nuova sessione ABA", onBack) {
            Button(
                enabled = targets.isNotEmpty(),
                onClick = {
                    onStart(
                        SessionConfig(
                            learner.trim(), program,
                            SymbolLibrary.all.map { it.id }.filter { it in targets }, // ordine stabile
                            field, trials, prompt, timeout, errCorr, reinf, labels
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
                SectionLabel("Programma")
                ChoiceRow(ProgramType.entries, program, { it.title }) { program = it }
                SectionLabel("Immagini nel campo")
                ChoiceRow(listOf(1, 2, 3, 4), field, { "$it" }) { field = it }
                SectionLabel("Numero di prove")
                ChoiceRow(listOf(5, 10, 15, 20), trials, { "$it" }) { trials = it }
                SectionLabel("Livello di prompt iniziale")
                ChoiceRow(PromptLevel.entries, prompt, { it.title }) { prompt = it }
                SectionLabel("Tempo massimo di risposta")
                ChoiceRow(listOf(10, 15, 30, 60), timeout, { "$it s" }) { timeout = it }
                SwitchRow("Correzione dell'errore (mostra la risposta giusta)", errCorr) { errCorr = it }
                SwitchRow("Rinforzo (animazione + voce)", reinf) { reinf = it }
                SwitchRow("Mostra le parole sotto le immagini", labels) { labels = it }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Target (${targets.size} selezionati) — tocca per scegliere")
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SymbolLibrary.all, key = { it.id }) { s ->
                        val sel = s.id in targets
                        Box(
                            Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                                .background(if (sel) s.color else Color(0xFF263238))
                                .clickable { targets = if (sel) targets - s.id else targets + s.id },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(s.emoji, fontSize = 40.sp)
                                Text((if (sel) "✓ " else "") + s.label, fontSize = 14.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
