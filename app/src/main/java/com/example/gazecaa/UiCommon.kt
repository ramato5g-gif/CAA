package com.example.gazecaa

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
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

enum class DistStatus { NO_FACE, FAR, NEAR, OK }

fun distStatus(face: Boolean, eyeDist: Float): DistStatus = when {
    !face -> DistStatus.NO_FACE
    eyeDist < GazeConfig.EYE_DIST_MIN -> DistStatus.FAR
    eyeDist > GazeConfig.EYE_DIST_MAX -> DistStatus.NEAR
    else -> DistStatus.OK
}

/** Indicatore volto/distanza per il caregiver. */
@Composable
fun FaceStatus(status: DistStatus) {
    val (text, color) = when (status) {
        DistStatus.NO_FACE -> "⚠ Volto non rilevato" to Color(0xFFFFB74D)
        DistStatus.FAR -> "↔ Avvicina il tablet" to Color(0xFFFFB74D)
        DistStatus.NEAR -> "↔ Allontana il tablet" to Color(0xFFFFB74D)
        DistStatus.OK -> "👁 Posizione OK" to Color(0xFF81C784)
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
    infoText: String,
    inputMode: InputMode,
    onInputMode: (InputMode) -> Unit,
    onCaa: () -> Unit,
    onAba: () -> Unit,
    onHistory: () -> Unit,
    onEditCaa: () -> Unit,
    onCalibrate: () -> Unit,
    onRecenter: () -> Unit,
    onSettings: () -> Unit,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    profileName: String,
    onProfiles: () -> Unit,
    onAccuracyTest: () -> Unit,
    onBackup: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sguardo CAA", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onProfiles, modifier = Modifier.padding(start = 16.dp)) { Text("👤 $profileName") }
            Spacer(Modifier.weight(1f))
            status()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Puntatore:  ")
            ChoiceRow(InputMode.entries, inputMode, { it.title }, onInputMode)
        }
        Text(infoText, color = Color(0xFFB0BEC5))
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HomeTile("💬", "Comunica", "Tabella CAA", Color(0xFF1565C0), Modifier.weight(1f), onCaa)
            HomeTile("🎯", "Sessione ABA", "Prove discrete con raccolta dati", Color(0xFF6A1B9A), Modifier.weight(1f), onAba)
            HomeTile("📊", "Dati", "Storico ed esportazione CSV", Color(0xFF00695C), Modifier.weight(1f), onHistory)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (inputMode == InputMode.EYES) Button(onClick = onCalibrate) { Text("🎯 Calibra (9 punti)") }
            if (inputMode != InputMode.TOUCH) OutlinedButton(onClick = onRecenter) { Text("⊕ Ricentra") }
            if (inputMode != InputMode.TOUCH) OutlinedButton(onClick = onAccuracyTest) { Text("📏 Test precisione") }
            OutlinedButton(onClick = onEditCaa) { Text("✏ Modifica tabella") }
            OutlinedButton(onClick = onSettings) { Text("⚙ Impostazioni") }
            OutlinedButton(onClick = onBackup) { Text("💾 Backup") }
            OutlinedButton(onClick = onTogglePin) { Text(if (pinned) "🔓 Sblocca app" else "📌 Blocca app") }
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

/**
 * Protezione bambino: se [locked], i controlli sono nascosti e compaiono per qualche
 * secondo tenendo premuto l'ingranaggio nell'angolo.
 */
@Composable
fun ChildLockArea(locked: Boolean, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) { delay(GazeConfig.REVEAL_MS); visible = false }
    }
    if (!locked || visible) {
        content()
    } else {
        Box(Modifier.fillMaxWidth().height(40.dp)) {
            Box(
                Modifier.align(Alignment.CenterEnd).size(56.dp, 40.dp)
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { visible = true }) },
                contentAlignment = Alignment.Center
            ) { Text("⚙", color = Color.White.copy(alpha = 0.3f), fontSize = 20.sp) }
        }
    }
}

/** Grafico a linee 0-100% con riga del criterio di acquisizione. */
@Composable
fun LineChart(series: List<Pair<List<Int>, Color>>, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        listOf(0, 50, GazeConfig.MASTERY_PCT, 100).forEach { v ->
            val y = h - h * v / 100f
            val a = if (v == GazeConfig.MASTERY_PCT) 0.5f else 0.15f
            drawLine(Color.White.copy(alpha = a), Offset(0f, y), Offset(w, y), 1.dp.toPx())
        }
        series.forEach { (vals, col) ->
            if (vals.isEmpty()) return@forEach
            val step = if (vals.size > 1) w / (vals.size - 1) else 0f
            val pts = vals.mapIndexed { i, v -> Offset(if (vals.size > 1) i * step else w / 2f, h - h * v / 100f) }
            for (i in 1 until pts.size) drawLine(col, pts[i - 1], pts[i], 3.dp.toPx())
            pts.forEach { drawCircle(col, 4.dp.toPx(), it) }
        }
    }
}
