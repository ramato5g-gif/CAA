package com.example.gazecaa

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/** Media mobile semplice su N campioni (filtro anti-jitter / saccadi). */
class MovingAverage(private val size: Int) {
    private val window = ArrayDeque<Float>()
    private var sum = 0f

    fun add(v: Float): Float {
        window.addLast(v); sum += v
        if (window.size > size) sum -= window.removeFirst()
        return sum / window.size
    }
}

/** Valori grezzi corrispondenti ai bordi dello schermo, ottenuti dalla calibrazione. */
data class Calibration(val rawAtLeft: Float, val rawAtRight: Float, val rawAtTop: Float, val rawAtBottom: Float)

/** Converte [RawGaze] in un punto normalizzato (0..1, 0..1) dello schermo, con smoothing. */
class GazeMapper(window: Int) {
    private var avgX = MovingAverage(window)
    private var avgY = MovingAverage(window)
    private var windowSize = window

    @Volatile var calibration: Calibration? = null

    /** SMOOTHING: cambia la finestra della media mobile (azzera la storia). */
    fun setWindow(n: Int) {
        if (n == windowSize) return
        windowSize = n; reset()
    }

    fun reset() {
        avgX = MovingAverage(windowSize); avgY = MovingAverage(windowSize)
    }

    fun map(raw: RawGaze): Offset {
        val c = calibration
        val nx = if (c != null) linear(raw.x, c.rawAtLeft, c.rawAtRight) else 0.5f + raw.x * GazeConfig.DEFAULT_GAIN_X
        val ny = if (c != null) linear(raw.y, c.rawAtTop, c.rawAtBottom) else 0.5f + raw.y * GazeConfig.DEFAULT_GAIN_Y
        return Offset(avgX.add(nx.coerceIn(0f, 1f)), avgY.add(ny.coerceIn(0f, 1f)))
    }

    private fun linear(v: Float, at0: Float, at1: Float): Float {
        val range = at1 - at0
        return if (abs(range) < 1e-4f) 0.5f else (v - at0) / range
    }
}
