package com.example.gazecaa

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Parametri di input condivisi da tutte le schermate selezionabili. */
data class InputParams(
    val mode: InputMode,
    val select: SelectMode,
    val dwellMs: Int,
    val magnet: Boolean,
    val blinkClicks: Int,
    /** Pressioni di un sensore esterno (tastiera/switch Bluetooth: Spazio o Invio). */
    val switchClicks: Int = 0,
)

/** Chiamato a ogni scelta fatta col puntatore, con il centro dell'immagine (correzione deriva). */
val LocalSelectFeedback = staticCompositionLocalOf<(Offset) -> Unit> { {} }

/**
 * Stato della selezione. Il calcolo gira a ogni frame dello schermo FUORI dalla
 * composizione: le schede si ridisegnano (anello) ma non si ricompongono → niente scatti.
 */
class DwellState {
    internal val bounds = HashMap<String, Rect>()
    var hovered by mutableStateOf<String?>(null)
        internal set
    internal var progress by mutableFloatStateOf(0f)
    var justSelected by mutableStateOf<String?>(null)
        internal set
    internal var locked: String? = null
    internal var scanElapsed = 0f
    internal var pendingSwitch = false

    /** In modalità Scansione: un tocco su qualsiasi scheda sceglie quella illuminata. */
    fun pressSwitch() { pendingSwitch = true }

    /** Da leggere SOLO in fase di disegno (lambda). */
    fun progressOf(key: String): Float = if (hovered == key) progress else 0f
}

/**
 * @param pointer posizione del puntatore in pixel (coordinate root), letta a ogni frame
 * @param enabled false = nessuna selezione (es. durante il rinforzo o in modalità Touch)
 * @param resetKey cambiandolo si azzerano posizioni e progresso (es. nuova prova)
 */
@Composable
fun rememberDwellState(
    pointer: () -> Offset?,
    input: InputParams,
    enabled: Boolean,
    resetKey: Any? = Unit,
    onSelect: (String) -> Unit,
): DwellState {
    val state = remember(resetKey) { DwellState() }
    val callback by rememberUpdatedState(onSelect)
    val en by rememberUpdatedState(enabled && (input.mode != InputMode.TOUCH || input.select == SelectMode.SCAN))
    val inp by rememberUpdatedState(input)
    val feedback by rememberUpdatedState(LocalSelectFeedback.current)
    val density = LocalDensity.current
    val marginPx = with(density) { GazeConfig.HIT_MARGIN_DP.dp.toPx() }
    val magnetPx = with(density) { GazeConfig.MAGNET_MAX_DP.dp.toPx() }
    val scope = rememberCoroutineScope()

    fun select(key: String, fromPointer: Boolean) {
        state.justSelected = key
        state.locked = if (fromPointer) key else null
        state.progress = 0f
        state.scanElapsed = 0f
        if (fromPointer) state.bounds[key]?.let { feedback(it.center) }
        callback(key)
        scope.launch {
            delay(GazeConfig.COOLDOWN_MS)
            if (state.justSelected == key) state.justSelected = null
        }
    }

    // Ciclo a ogni frame dello schermo
    LaunchedEffect(state) {
        var last = 0L
        while (true) {
            var fire: String? = null
            var fromPointer = true
            withFrameNanos { now ->
                val dtMs = if (last == 0L) 0f else ((now - last) / 1_000_000f).coerceAtMost(100f)
                last = now
                if (!en) {
                    state.hovered = null; state.progress = 0f; state.locked = null; state.pendingSwitch = false
                    return@withFrameNanos
                }

                // SCANSIONE: le immagini si illuminano a turno (ordine di lettura)
                if (inp.select == SelectMode.SCAN) {
                    val keys = state.bounds.entries
                        .sortedWith(compareBy({ (it.value.top / 40f).toInt() }, { it.value.left }))
                        .map { it.key }
                    if (keys.isEmpty()) return@withFrameNanos
                    state.scanElapsed += dtMs
                    val cur = state.hovered
                    if (cur == null || cur !in keys) {
                        state.hovered = keys.first(); state.scanElapsed = 0f
                    } else if (state.scanElapsed >= inp.dwellMs) {
                        state.hovered = keys[(keys.indexOf(cur) + 1) % keys.size]; state.scanElapsed = 0f
                    }
                    state.progress = (state.scanElapsed / inp.dwellMs).coerceIn(0f, 1f)
                    if (state.pendingSwitch) {
                        state.pendingSwitch = false
                        fire = state.hovered; fromPointer = false
                    }
                    return@withFrameNanos
                }
                state.pendingSwitch = false

                val h = pick(pointer(), state.hovered, state.bounds, marginPx, if (inp.magnet) magnetPx else 0f)

                // Dopo una selezione: bloccato finché non si esce dall'immagine
                val lk = state.locked
                if (lk != null) {
                    if (h == lk) { state.hovered = h; return@withFrameNanos }
                    state.locked = null
                }

                if (inp.select == SelectMode.BLINK) {
                    state.hovered = h; state.progress = 0f
                    return@withFrameNanos
                }

                // DWELL con decadimento: uscite brevi non azzerano il progresso
                if (h != null && h == state.hovered) {
                    state.progress = (state.progress + dtMs / inp.dwellMs).coerceAtMost(1f)
                    if (state.progress >= 1f) fire = h
                } else {
                    state.progress -= dtMs / GazeConfig.DWELL_DECAY_MS
                    if (state.progress <= 0f) { state.progress = 0f; state.hovered = h }
                }
            }
            fire?.let { select(it, fromPointer) }
        }
    }

    // Ammiccamento lungo o sensore esterno
    val initialBlinks = remember(state) { input.blinkClicks }
    val initialSwitch = remember(state) { input.switchClicks }
    LaunchedEffect(state, input.blinkClicks, input.switchClicks) {
        if ((input.blinkClicks != initialBlinks || input.switchClicks != initialSwitch) && en) {
            when (input.select) {
                SelectMode.SCAN -> state.pendingSwitch = true
                SelectMode.BLINK -> {
                    val h = state.hovered
                    if (h != null && state.locked != h) select(h, true)
                }
                SelectMode.DWELL -> Unit
            }
        }
    }
    return state
}

fun Modifier.dwellTarget(state: DwellState, key: String): Modifier =
    this.onGloballyPositioned { state.bounds[key] = it.boundsInRoot() }

/** Isteresi + magnetismo verso l'immagine più vicina. */
private fun pick(p: Offset?, current: String?, bounds: Map<String, Rect>, margin: Float, magnet: Float): String? {
    if (p == null) return null
    if (current != null && bounds[current]?.inflate(margin)?.contains(p) == true) return current
    bounds.entries.firstOrNull { it.value.contains(p) }?.let { return it.key }
    if (magnet <= 0f) return null
    val best = bounds.entries.minByOrNull { dist2(p, it.value) } ?: return null
    return if (dist2(p, best.value) <= magnet * magnet) best.key else null
}

private fun dist2(p: Offset, r: Rect): Float {
    val dx = maxOf(r.left - p.x, 0f, p.x - r.right)
    val dy = maxOf(r.top - p.y, 0f, p.y - r.bottom)
    return dx * dx + dy * dy
}

/**
 * Scheda selezionabile. L'anello di progresso è disegnato in fase di draw
 * (nessuna ricomposizione a ogni frame). La dimensione dell'emoji si adatta alla scheda.
 */
@Composable
fun GazeCard(
    emoji: String,
    label: String?,
    color: Color,
    modifier: Modifier = Modifier,
    progress: () -> Float = { 0f },
    hovered: Boolean = false,
    highlighted: Boolean = false,
    pulse: Boolean = false,
    imagePath: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    val photo = rememberSymbolBitmap(imagePath)
    val scale = if (pulse) {
        val t = rememberInfiniteTransition(label = "pulse")
        val s by t.animateFloat(1f, 1.08f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "s")
        s
    } else 1f
    val ringColor = Color.Yellow
    val ringTrack = Color.White.copy(alpha = 0.25f)

    BoxWithConstraints(
        modifier
            .padding(4.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(color)
            .border(
                when {
                    highlighted -> 10.dp
                    pulse -> 6.dp
                    hovered -> 4.dp
                    else -> 0.dp
                },
                if (highlighted) Color(0xFF00E676) else Color.Yellow,
                shape
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawWithContent {
                drawContent()
                val p = progress()
                if (p > 0f) {
                    val d = minOf(size.width, size.height) * 0.75f
                    val stroke = 14.dp.toPx()
                    val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                    drawArc(ringTrack, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
                    drawArc(ringColor, -90f, 360f * p, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val base = minOf(maxWidth.value, maxHeight.value)
        val emojiSp = (base * 0.42f).coerceIn(28f, 120f)
        val labelSp = (base * 0.13f).coerceIn(14f, 40f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (photo != null) {
                Image(
                    photo, contentDescription = label, contentScale = ContentScale.Crop,
                    modifier = Modifier.size((base * if (label != null) 0.62f else 0.8f).dp).clip(RoundedCornerShape(16.dp))
                )
            } else Text(emoji, fontSize = emojiSp.sp)
            if (label != null) {
                Text(label, fontSize = labelSp.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Carica (una volta) la foto di un simbolo personale. */
@Composable
fun rememberSymbolBitmap(path: String?): ImageBitmap? = remember(path) {
    path?.let { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() }
}

/** Miniatura di un simbolo (foto o emoji) per le griglie di scelta. */
@Composable
fun SymbolThumb(s: AacSymbol, sizeDp: Int) {
    val photo = rememberSymbolBitmap(s.imagePath)
    if (photo != null) {
        Image(
            photo, contentDescription = s.label, contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(10.dp))
        )
    } else Text(s.emoji, fontSize = (sizeDp * 0.8f).sp)
}
