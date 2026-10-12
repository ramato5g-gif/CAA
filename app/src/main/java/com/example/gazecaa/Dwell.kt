package com.example.gazecaa

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Stato del dwell condiviso da una schermata. Ogni elemento selezionabile si
 * registra con Modifier.dwellTarget(state, key).
 */
class DwellState {
    internal val bounds = HashMap<String, Rect>()
    internal var lastKey: String? = null
    internal val progress = Animatable(0f)
    var hovered: String? = null
        internal set
    var justSelected by mutableStateOf<String?>(null)
        internal set

    /** Avanzamento 0..1 del dwell per l'elemento [key]. */
    fun progressOf(key: String): Float = if (hovered == key) progress.value else 0f
}

/**
 * @param enabled false = nessuna selezione (es. durante il rinforzo)
 * @param resetKey cambiandolo si azzerano posizioni e timer (es. nuova prova)
 */
@Composable
fun rememberDwellState(
    gazePx: Offset?,
    dwellMs: Int,
    enabled: Boolean = true,
    resetKey: Any? = Unit,
    onSelect: (String) -> Unit,
): DwellState {
    val state = remember(resetKey) { DwellState() }
    val callback by rememberUpdatedState(onSelect)
    val marginPx = with(LocalDensity.current) { GazeConfig.HIT_MARGIN_DP.dp.toPx() }

    val hit = if (enabled) hitTest(gazePx, state.lastKey, state.bounds, marginPx) else null
    state.hovered = hit
    SideEffect { state.lastKey = hit }

    LaunchedEffect(state, hit) {
        state.progress.snapTo(0f)
        val key = hit ?: return@LaunchedEffect
        // DWELL TIME: durata da Impostazioni
        state.progress.animateTo(1f, tween(dwellMs, easing = LinearEasing))
        state.justSelected = key
        callback(key)
        delay(GazeConfig.COOLDOWN_MS)
        state.justSelected = null
        state.progress.snapTo(0f)
        // Per ripetere la stessa scelta bisogna uscire e rientrare.
    }
    return state
}

fun Modifier.dwellTarget(state: DwellState, key: String): Modifier =
    this.onGloballyPositioned { state.bounds[key] = it.boundsInRoot() }

/** Hit-test con isteresi: l'elemento già agganciato ha un'area allargata. */
private fun hitTest(p: Offset?, current: String?, bounds: Map<String, Rect>, margin: Float): String? {
    if (p == null) return null
    if (current != null && bounds[current]?.inflate(margin)?.contains(p) == true) return current
    return bounds.entries.firstOrNull { it.value.contains(p) }?.key
}

/** Scheda con immagine (emoji), etichetta opzionale, anello di dwell e prompt visivo. */
@Composable
fun GazeCard(
    emoji: String,
    label: String?,
    color: Color,
    progress: Float,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    pulse: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(28.dp)
    val scale = if (pulse) {
        val t = rememberInfiniteTransition(label = "pulse")
        val s by t.animateFloat(1f, 1.08f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "s")
        s
    } else 1f

    Box(
        modifier
            .padding(4.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(color)
            .border(
                when {
                    highlighted -> 10.dp
                    pulse || progress > 0f -> 6.dp
                    else -> 0.dp
                },
                if (highlighted) Color(0xFF00E676) else Color.Yellow,
                shape
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 96.sp)
            if (label != null) {
                Text(label, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            }
        }
        if (progress > 0f) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(190.dp),
                color = Color.Yellow,
                strokeWidth = 14.dp,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
        }
    }
}
