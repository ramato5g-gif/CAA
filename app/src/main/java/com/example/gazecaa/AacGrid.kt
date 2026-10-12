package com.example.gazecaa

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class AacItem(val label: String, val emoji: String, val color: Color)

val defaultAacItems = listOf(
    AacItem("Sì", "👍", Color(0xFF2E7D32)),
    AacItem("No", "👎", Color(0xFFC62828)),
    AacItem("Acqua", "💧", Color(0xFF1565C0)),
    AacItem("Aiuto", "🆘", Color(0xFFEF6C00)),
)

/** Contenitore mutabile che NON provoca ricomposizioni (per bounds e stato precedente). */
private class Ref<T>(var value: T)

/**
 * Griglia 2x2 con selezione tramite Dwell Time.
 *
 * @param gazePx punto dello sguardo in pixel, coordinate ROOT di Compose (null = nessuno sguardo)
 * @param onSelect chiamato al completamento del dwell (o al tocco, per il caregiver)
 */
@Composable
fun AacGrid(
    gazePx: Offset?,
    onSelect: (AacItem) -> Unit,
    modifier: Modifier = Modifier,
    dwellMs: Int = GazeConfig.DEFAULT_DWELL_MS,
    items: List<AacItem> = defaultAacItems,
) {
    val currentOnSelect by rememberUpdatedState(onSelect)
    val marginPx = with(LocalDensity.current) { GazeConfig.HIT_MARGIN_DP.dp.toPx() }

    val containerBounds = remember { Ref(Rect.Zero) }
    val buttonBounds = remember { HashMap<Int, Rect>() }
    val lastHovered = remember { Ref<Int?>(null) }

    // Hit-test con isteresi: il pulsante già agganciato ha un'area allargata di HIT_MARGIN_DP
    val hovered = hitTest(gazePx, lastHovered.value, buttonBounds, marginPx)
    SideEffect { lastHovered.value = hovered }

    val progress = remember { Animatable(0f) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    // ---- LOGICA DWELL ----
    // Si riavvia ogni volta che cambia il pulsante puntato: uscire dal pulsante azzera il timer.
    LaunchedEffect(hovered) {
        progress.snapTo(0f)
        val idx = hovered ?: return@LaunchedEffect
        // DWELL TIME: valore da Impostazioni (default GazeConfig.DEFAULT_DWELL_MS)
        progress.animateTo(1f, tween(dwellMs, easing = LinearEasing))
        currentOnSelect(items[idx])
        selectedIndex = idx
        delay(GazeConfig.COOLDOWN_MS)
        selectedIndex = null
        progress.snapTo(0f)
        // Per riselezionare lo stesso pulsante l'utente deve uscire e rientrare
        // (evita ripetizioni involontarie se lo sguardo resta fermo).
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { containerBounds.value = it.boundsInRoot() }
    ) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            for (row in 0 until 2) {
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    for (col in 0 until 2) {
                        val i = row * 2 + col
                        AacButton(
                            item = items[i],
                            progress = if (hovered == i) progress.value else 0f,
                            selected = selectedIndex == i,
                            onClick = { currentOnSelect(items[i]) }, // fallback touch per caregiver
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onGloballyPositioned { buttonBounds[i] = it.boundsInRoot() }
                        )
                    }
                }
            }
        }

        // Cursore dello sguardo
        if (gazePx != null) {
            Canvas(Modifier.fillMaxSize()) {
                val c = gazePx - containerBounds.value.topLeft
                val r = 18.dp.toPx()
                drawCircle(Color.Yellow.copy(alpha = 0.75f), radius = r, center = c)
                drawCircle(Color.Black, radius = r, center = c, style = Stroke(3.dp.toPx()))
            }
        }
    }
}

private fun hitTest(p: Offset?, current: Int?, bounds: Map<Int, Rect>, margin: Float): Int? {
    if (p == null) return null
    if (current != null && bounds[current]?.inflate(margin)?.contains(p) == true) return current
    return bounds.entries.firstOrNull { it.value.contains(p) }?.key
}

@Composable
private fun AacButton(
    item: AacItem,
    progress: Float,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) Color.White else item.color)
            .border(if (progress > 0f) 6.dp else 0.dp, Color.Yellow, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(item.emoji, fontSize = 72.sp)
            Text(
                item.label,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) item.color else Color.White
            )
        }
        // Feedback visivo del dwell: anello che si riempie
        if (progress > 0f) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(200.dp),
                color = Color.Yellow,
                strokeWidth = 14.dp,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
        }
    }
}
