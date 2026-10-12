package com.example.gazecaa

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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

/**
 * Calibrazione a 4 punti: l'utente fissa un punto in ogni angolo.
 * Dai valori raccolti si ricava il valore grezzo corrispondente ai bordi dello schermo.
 * Restituisce null se il volto non è stato rilevato.
 */
@Composable
fun CalibrationScreen(rawProvider: () -> RawGaze?, onDone: (Calibration?) -> Unit) {
    val m = 0.15f // margine dei punti dai bordi (frazione schermo)
    val targets = remember { listOf(Offset(m, m), Offset(1 - m, m), Offset(m, 1 - m), Offset(1 - m, 1 - m)) }
    var step by remember { mutableIntStateOf(0) }
    val done by rememberUpdatedState(onDone)

    LaunchedEffect(Unit) {
        val samples = mutableListOf<Offset>()
        for (i in targets.indices) {
            step = i
            delay(GazeConfig.CALIB_SETTLE_MS) // tempo per spostare lo sguardo
            var sx = 0f; var sy = 0f; var n = 0
            val end = System.currentTimeMillis() + GazeConfig.CALIB_POINT_MS - GazeConfig.CALIB_SETTLE_MS
            while (System.currentTimeMillis() < end) {
                rawProvider()?.let { sx += it.x; sy += it.y; n++ }
                delay(33)
            }
            if (n < 5) { done(null); return@LaunchedEffect }
            samples += Offset(sx / n, sy / n)
        }
        // samples: 0=TL 1=TR 2=BL 3=BR → estrapolazione lineare ai bordi 0 e 1
        val left = (samples[0].x + samples[2].x) / 2
        val right = (samples[1].x + samples[3].x) / 2
        val top = (samples[0].y + samples[1].y) / 2
        val bottom = (samples[2].y + samples[3].y) / 2
        val kx = (right - left) / (1 - 2 * m)
        val ky = (bottom - top) / (1 - 2 * m)
        done(Calibration(left - m * kx, right + m * kx, top - m * ky, bottom + m * ky))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Text(
            "Fissa il punto giallo (${step + 1}/4)",
            color = Color.White, fontSize = 28.sp,
            modifier = Modifier.align(Alignment.Center)
        )
        Canvas(Modifier.fillMaxSize()) {
            val t = targets[step]
            val c = Offset(t.x * size.width, t.y * size.height)
            drawCircle(Color.Yellow, radius = 22.dp.toPx(), center = c)
            drawCircle(Color.Black, radius = 6.dp.toPx(), center = c)
        }
    }
}
