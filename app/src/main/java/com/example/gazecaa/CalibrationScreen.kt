package com.example.gazecaa

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class CalibrationResult(val model: GazeModel, val errorPct: Float)

/** Raccoglie i frame validi (senza duplicati) per [durationMs]. */
suspend fun collectSamples(provider: () -> GazeFeatures?, durationMs: Long, requireEyes: Boolean): List<GazeFeatures> {
    val out = mutableListOf<GazeFeatures>()
    var lastTs = 0L
    val end = System.currentTimeMillis() + durationMs
    while (System.currentTimeMillis() < end) {
        provider()?.let {
            if (it.timestamp != lastTs && (!requireEyes || it.eyesOpen)) { out += it; lastTs = it.timestamp }
        }
        delay(20)
    }
    return out
}

private fun median(v: List<Float>): Float = v.filter { !it.isNaN() }.sorted().let { if (it.isEmpty()) Float.NaN else it[it.size / 2] }

/** Mediana componente per componente: elimina ammiccamenti e sguardi fuori bersaglio. */
fun medianFeatures(s: List<GazeFeatures>) = GazeFeatures(
    median(s.map { it.ex }), median(s.map { it.ey }),
    median(s.map { it.hx }), median(s.map { it.hy }),
    median(s.map { it.eyeDist }), true, 0L
)

/** Calibrazione a 9 punti per la modalità Sguardo. */
@Composable
fun CalibrationScreen(featuresProvider: () -> GazeFeatures?, onDone: (CalibrationResult?) -> Unit) {
    val points = remember {
        listOf(0.1f, 0.5f, 0.9f).flatMap { y -> listOf(0.1f, 0.5f, 0.9f).map { x -> Offset(x, y) } }.shuffled()
    }
    var step by remember { mutableIntStateOf(0) }
    val radius = remember { Animatable(1f) }
    val done by rememberUpdatedState(onDone)

    LaunchedEffect(Unit) {
        val feats = mutableListOf<GazeFeatures>()
        val targets = mutableListOf<Offset>()
        for (i in points.indices) {
            step = i
            radius.snapTo(1f)
            launch { radius.animateTo(0.3f, tween(GazeConfig.CALIB_POINT_MS.toInt())) }
            delay(GazeConfig.CALIB_SETTLE_MS)
            val s = collectSamples(featuresProvider, GazeConfig.CALIB_POINT_MS - GazeConfig.CALIB_SETTLE_MS, requireEyes = true)
            if (s.size >= 5) { feats += medianFeatures(s); targets += points[i] }
        }
        if (feats.size < 6) { done(null); return@LaunchedEffect }
        val model = GazeModel.fit(feats, targets)
        done(CalibrationResult(model, model.rmsError(feats, targets) * 100f))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Text(
            "Segui la stella  ·  ${step + 1}/9", color = Color.White, fontSize = 24.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
        )
        Canvas(Modifier.fillMaxSize()) {
            val t = points[step]
            val c = Offset(t.x * size.width, t.y * size.height)
            drawCircle(Color(0xFFFFD54F), radius = 44.dp.toPx() * radius.value, center = c)
            drawCircle(Color.Black, radius = 5.dp.toPx(), center = c)
        }
    }
}

/**
 * Ricentratura: l'utente guarda il centro per 1,5 s. Restituisce le features mediane;
 * in modalità Sguardo correggono la deriva, in modalità Testa diventano la posizione neutra.
 */
@Composable
fun RecenterScreen(featuresProvider: () -> GazeFeatures?, requireEyes: Boolean, onDone: (GazeFeatures?) -> Unit) {
    val radius = remember { Animatable(1f) }
    val done by rememberUpdatedState(onDone)

    LaunchedEffect(Unit) {
        launch { radius.animateTo(0.3f, tween((GazeConfig.CALIB_SETTLE_MS + GazeConfig.RECENTER_MS).toInt())) }
        delay(GazeConfig.CALIB_SETTLE_MS)
        val s = collectSamples(featuresProvider, GazeConfig.RECENTER_MS, requireEyes)
        done(if (s.size < 5) null else medianFeatures(s))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Text(
            if (requireEyes) "Guarda la stella" else "Guarda la stella tenendo la testa dritta",
            color = Color.White, fontSize = 24.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
        )
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFFFFD54F), radius = 44.dp.toPx() * radius.value, center = center)
            drawCircle(Color.Black, radius = 5.dp.toPx(), center = center)
        }
    }
}

/**
 * Test di precisione: 5 bersagli, si misura la distanza mediana tra puntatore e bersaglio.
 * Restituisce l'errore medio in pixel (null se il puntatore non è stato rilevato).
 */
@Composable
fun AccuracyTestScreen(pointerPx: () -> Offset?, onDone: (Float?) -> Unit) {
    val points = remember {
        listOf(Offset(0.2f, 0.25f), Offset(0.8f, 0.25f), Offset(0.5f, 0.5f), Offset(0.2f, 0.75f), Offset(0.8f, 0.75f))
    }
    var step by remember { mutableIntStateOf(0) }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val currentSize by rememberUpdatedState(sizePx)
    val done by rememberUpdatedState(onDone)

    LaunchedEffect(Unit) {
        val errors = mutableListOf<Float>()
        for (i in points.indices) {
            step = i
            delay(900)
            val d = mutableListOf<Float>()
            val end = System.currentTimeMillis() + 1300
            while (System.currentTimeMillis() < end) {
                val p = pointerPx()
                val sz = currentSize
                if (p != null && sz.width > 0) {
                    val t = Offset(points[i].x * sz.width, points[i].y * sz.height)
                    d += (p - t).getDistance()
                }
                delay(30)
            }
            if (d.size >= 5) errors += d.sorted()[d.size / 2]
        }
        done(if (errors.size >= 3) errors.average().toFloat() else null)
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).onSizeChanged { sizePx = it }) {
        Text(
            "Test di precisione: guarda il bersaglio  ·  ${step + 1}/5", color = Color.White, fontSize = 22.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
        )
        Canvas(Modifier.fillMaxSize()) {
            val t = points[step]
            val c = Offset(t.x * size.width, t.y * size.height)
            drawCircle(Color(0xFF64B5F6), radius = 36.dp.toPx(), center = c, style = Stroke(4.dp.toPx()))
            drawCircle(Color(0xFF64B5F6), radius = 6.dp.toPx(), center = c)
        }
    }
}
