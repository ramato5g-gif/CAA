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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class CalibrationResult(val model: GazeModel, val errorPct: Float)

/** Raccoglie i frame validi (senza duplicati) per [durationMs]. */
suspend fun collectSamples(provider: () -> GazeFeatures?, durationMs: Long): List<GazeFeatures> {
    val out = mutableListOf<GazeFeatures>()
    var lastTs = 0L
    val end = System.currentTimeMillis() + durationMs
    while (System.currentTimeMillis() < end) {
        provider()?.let { if (it.timestamp != lastTs) { out += it; lastTs = it.timestamp } }
        delay(20)
    }
    return out
}

private fun median(v: List<Float>): Float = v.sorted()[v.size / 2]

/** Mediana componente per componente: elimina ammiccamenti e sguardi fuori bersaglio. */
fun medianFeatures(s: List<GazeFeatures>) = GazeFeatures(
    median(s.map { it.ex }), median(s.map { it.ey }),
    median(s.map { it.hx }), median(s.map { it.hy }),
    median(s.map { it.eyeDist }), 0L
)

/**
 * Calibrazione a 9 punti (griglia 3x3, ordine casuale). Il bersaglio si restringe
 * per mantenere l'attenzione (utile con i bambini). Restituisce null se il volto
 * non è stato visto in almeno 6 punti.
 */
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
            val s = collectSamples(featuresProvider, GazeConfig.CALIB_POINT_MS - GazeConfig.CALIB_SETTLE_MS)
            if (s.size >= 5) { feats += medianFeatures(s); targets += points[i] }
        }
        if (feats.size < 6) { done(null); return@LaunchedEffect }
        val model = GazeModel.fit(feats, targets)
        done(CalibrationResult(model, model.rmsError(feats, targets) * 100f))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Text(
            "Segui la stella  ·  ${step + 1}/9",
            color = Color.White, fontSize = 24.sp,
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
 * Ricentratura rapida (1 punto al centro): corregge la deriva se l'utente
 * si è spostato, senza rifare tutta la calibrazione. Restituisce la correzione.
 */
@Composable
fun RecenterScreen(featuresProvider: () -> GazeFeatures?, model: GazeModel, onDone: (Offset?) -> Unit) {
    val radius = remember { Animatable(1f) }
    val done by rememberUpdatedState(onDone)

    LaunchedEffect(Unit) {
        launch { radius.animateTo(0.3f, tween((GazeConfig.CALIB_SETTLE_MS + GazeConfig.RECENTER_MS).toInt())) }
        delay(GazeConfig.CALIB_SETTLE_MS)
        val s = collectSamples(featuresProvider, GazeConfig.RECENTER_MS)
        if (s.size < 5) { done(null); return@LaunchedEffect }
        val p = model.predict(medianFeatures(s))
        done(Offset(0.5f - p.x, 0.5f - p.y))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Text(
            "Guarda la stella",
            color = Color.White, fontSize = 24.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
        )
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFFFFD54F), radius = 44.dp.toPx() * radius.value, center = center)
            drawCircle(Color.Black, radius = 5.dp.toPx(), center = center)
        }
    }
}
