package com.example.gazecaa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbaSummaryScreen(
    session: SessionRecord,
    onSaveNotes: (String) -> Unit,
    onExport: () -> Unit,
    onRepeat: () -> Unit,
    onHome: () -> Unit,
) {
    var notes by remember { mutableStateOf(session.notes) }
    var saved by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Riepilogo sessione", onHome) {
            OutlinedButton(onClick = onExport) { Text("📤 Esporta CSV") }
            Button(onClick = onRepeat) { Text("↻ Nuova sessione uguale") }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatBox("Corrette", "${session.pctCorrect}%", Modifier.weight(1f))
            StatBox("Indipendenti", "${session.pctIndependent}%", Modifier.weight(1f))
            StatBox("Prove", "${session.trials.size}", Modifier.weight(1f))
            StatBox("Latenza media", "%.1f s".format(session.avgLatencySec), Modifier.weight(1f))
        }
        Text(
            "${session.config.learner.ifBlank { "—" }}  ·  ${session.config.program.title}  ·  ${session.config.inputMode.title}  ·  campo ${session.config.fieldSize}  ·  ${formatDate(session.startedAt)}",
            color = Color(0xFFB0BEC5), modifier = Modifier.padding(horizontal = 16.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = notes, onValueChange = { notes = it; saved = false },
                label = { Text("Note del terapista (comportamento, rinforzatori, contesto)") },
                modifier = Modifier.weight(1f)
            )
            Button(enabled = !saved, onClick = { onSaveNotes(notes); saved = true }, modifier = Modifier.padding(start = 8.dp)) {
                Text(if (saved) "✓ Salvate" else "Salva note")
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(session.perTarget()) { (id, ok, tot) ->
                val s = Symbols.byId(id)
                val pct = if (tot == 0) 0 else ok * 100 / tot
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF263238)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (s != null) SymbolThumb(s, 32)
                    Text("  ${s?.label ?: id}", fontSize = 20.sp, modifier = Modifier.weight(1f))
                    Text("$ok/$tot  ($pct%)", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = if (pct >= 80) Color(0xFF81C784) else Color.White)
                }
            }
        }
    }
}

@Composable
private fun StatBox(title: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF37474F)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(title, color = Color(0xFFB0BEC5))
    }
}

@Composable
fun AbaHistoryScreen(
    repo: SessionRepository,
    onBack: () -> Unit,
    onExport: (List<SessionRecord>) -> Unit,
    onProgress: () -> Unit,
) {
    var sessions by remember { mutableStateOf(repo.all()) }
    var toDelete by remember { mutableStateOf<SessionRecord?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Dati sessioni (${sessions.size})", onBack) {
            OutlinedButton(enabled = sessions.isNotEmpty(), onClick = onProgress) { Text("📈 Andamento") }
            Button(enabled = sessions.isNotEmpty(), onClick = { onExport(sessions) }) { Text("📤 Esporta tutto (CSV)") }
        }
        if (sessions.isEmpty()) {
            Text("Nessuna sessione registrata.", modifier = Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sessions, key = { it.id }) { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF263238)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${formatDate(s.startedAt)}  ·  ${s.config.learner.ifBlank { "—" }}  ·  ${s.config.program.title}",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Corrette ${s.pctCorrect}%  ·  Indipendenti ${s.pctIndependent}%  ·  ${s.trials.size} prove  ·  campo ${s.config.fieldSize}",
                            color = Color(0xFFB0BEC5)
                        )
                    }
                    OutlinedButton(onClick = { onExport(listOf(s)) }) { Text("📤") }
                    OutlinedButton(onClick = { toDelete = s }) { Text("🗑") }
                }
            }
        }
    }

    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminare la sessione?") },
            text = { Text("${formatDate(s.startedAt)} — l'operazione non si può annullare.") },
            confirmButton = {
                TextButton(onClick = { repo.delete(s.id); sessions = repo.all(); toDelete = null }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annulla") } }
        )
    }
}

/**
 * Andamento per utente e programma: % corrette indipendenti per sessione e per target,
 * con il criterio di acquisizione (GazeConfig.MASTERY_PCT per MASTERY_SESSIONS sessioni di fila).
 */
@Composable
fun AbaProgressScreen(
    sessions: List<SessionRecord>,
    onBack: () -> Unit,
    onReport: (String, ProgramType, List<SessionRecord>) -> Unit,
) {
    val learners = remember(sessions) { sessions.map { it.config.learner.ifBlank { "—" } }.distinct() }
    var learner by remember { mutableStateOf(learners.firstOrNull() ?: "—") }
    var program by remember { mutableStateOf(ProgramType.RECEPTIVE) }
    val list = sessions
        .filter { it.config.learner.ifBlank { "—" } == learner && it.config.program == program }
        .sortedBy { it.startedAt }
    val targetIds = list.flatMap { s -> s.trials.map { it.targetId } }.distinct()

    Column(Modifier.fillMaxSize()) {
        TopBar("Andamento", onBack) {
            Button(enabled = list.isNotEmpty(), onClick = { onReport(learner, program, list) }) { Text("📄 Report PDF") }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("Utente:  ")
            ChoiceRow(learners, learner, { it }) { learner = it }
            SectionLabel("     Programma:  ")
            ChoiceRow(ProgramType.entries, program, { it.title }) { program = it }
        }
        if (list.isEmpty()) {
            Text("Nessuna sessione per questa combinazione.", modifier = Modifier.padding(24.dp))
        } else {
            Text(
                "Sessioni (${list.size}):  — indipendenti   — corrette totali   · linea = criterio ${GazeConfig.MASTERY_PCT}%",
                color = Color(0xFFB0BEC5), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            LineChart(
                listOf(list.map { it.pctIndependent } to Color(0xFF81C784), list.map { it.pctCorrect } to Color(0xFF64B5F6)),
                Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 16.dp)
            )
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(targetIds) { id ->
                    val sym = Symbols.byId(id)
                    val vals = list.mapNotNull { it.independentPctFor(id) }
                    val lastN = vals.takeLast(GazeConfig.MASTERY_SESSIONS)
                    val mastered = lastN.size == GazeConfig.MASTERY_SESSIONS && lastN.all { it >= GazeConfig.MASTERY_PCT }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF263238)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (sym != null) SymbolThumb(sym, 36)
                        Text(sym?.label ?: id, fontSize = 18.sp, modifier = Modifier.width(140.dp))
                        LineChart(listOf(vals to Color(0xFF81C784)), Modifier.weight(1f).height(48.dp))
                        Text("${vals.lastOrNull() ?: 0}%", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (mastered) "✓ Acquisito" else "In corso",
                            color = if (mastered) Color(0xFF81C784) else Color(0xFFB0BEC5),
                            modifier = Modifier.width(110.dp)
                        )
                    }
                }
            }
        }
    }
}
