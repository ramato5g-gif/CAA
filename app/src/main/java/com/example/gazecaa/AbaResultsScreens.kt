package com.example.gazecaa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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

@Composable
fun AbaSummaryScreen(session: SessionRecord, onExport: () -> Unit, onRepeat: () -> Unit, onHome: () -> Unit) {
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
            "${session.config.learner.ifBlank { "—" }}  ·  ${session.config.program.title}  ·  campo ${session.config.fieldSize}  ·  ${formatDate(session.startedAt)}",
            color = Color(0xFFB0BEC5), modifier = Modifier.padding(horizontal = 16.dp)
        )
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(session.perTarget()) { (id, ok, tot) ->
                val s = SymbolLibrary.byId(id)
                val pct = if (tot == 0) 0 else ok * 100 / tot
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF263238)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${s?.emoji ?: ""}  ${s?.label ?: id}", fontSize = 20.sp, modifier = Modifier.weight(1f))
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
fun AbaHistoryScreen(repo: SessionRepository, onBack: () -> Unit, onExport: (List<SessionRecord>) -> Unit) {
    var sessions by remember { mutableStateOf(repo.all()) }
    var toDelete by remember { mutableStateOf<SessionRecord?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Dati sessioni (${sessions.size})", onBack) {
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
