package com.example.gazecaa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    status: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onBack != null) TextButton(onClick = onBack) { Text("← Home") }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        status?.invoke()
        actions()
    }
}

/** Indicatore volto/distanza per il caregiver. */
@Composable
fun FaceStatus(face: Boolean, eyeDist: Float) {
    val (text, color) = when {
        !face -> "⚠ Volto non rilevato" to Color(0xFFFFB74D)
        eyeDist < GazeConfig.EYE_DIST_MIN -> "↔ Avvicina il tablet" to Color(0xFFFFB74D)
        eyeDist > GazeConfig.EYE_DIST_MAX -> "↔ Allontana il tablet" to Color(0xFFFFB74D)
        else -> "👁 Posizione OK" to Color(0xFF81C784)
    }
    Text(text, color = color)
}

fun calibrationLabel(errPct: Float): String = when {
    errPct < 0f -> "Non calibrato"
    errPct < 5f -> "Calibrazione ottima (±%.0f%% schermo)".format(errPct)
    errPct < 8f -> "Calibrazione buona (±%.0f%% schermo)".format(errPct)
    errPct < 12f -> "Calibrazione sufficiente (±%.0f%%): usa campi da 2-3 immagini".format(errPct)
    else -> "Calibrazione scarsa (±%.0f%%): ricalibra".format(errPct)
}

@Composable
fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            if (o == selected) Button(onClick = { onSelect(o) }) { Text(label(o)) }
            else OutlinedButton(onClick = { onSelect(o) }) { Text(label(o)) }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, fontWeight = FontWeight.Bold, color = Color(0xFFB0BEC5))
}

@Composable
fun HomeScreen(
    status: @Composable () -> Unit,
    calibText: String,
    onCaa: () -> Unit,
    onAba: () -> Unit,
    onHistory: () -> Unit,
    onCalibrate: () -> Unit,
    onRecenter: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sguardo CAA", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            status()
        }
        Text(calibText, color = Color(0xFFB0BEC5))
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HomeTile("💬", "Comunica", "Tabella CAA a sguardo", Color(0xFF1565C0), Modifier.weight(1f), onCaa)
            HomeTile("🎯", "Sessione ABA", "Prove discrete con raccolta dati", Color(0xFF6A1B9A), Modifier.weight(1f), onAba)
            HomeTile("📊", "Dati", "Storico ed esportazione CSV", Color(0xFF00695C), Modifier.weight(1f), onHistory)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onCalibrate) { Text("🎯 Calibra (9 punti)") }
            OutlinedButton(onClick = onRecenter) { Text("⊕ Ricentra") }
            OutlinedButton(onClick = onSettings) { Text("⚙ Impostazioni") }
        }
    }
}

@Composable
private fun HomeTile(emoji: String, title: String, subtitle: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(color).clickable(onClick = onClick).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 72.sp)
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(subtitle, fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f))
    }
}
