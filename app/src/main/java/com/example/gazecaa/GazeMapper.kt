package com.example.gazecaa

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Modello di calibrazione: regressione lineare (ridge) per ogni asse
 *   schermo = b0 + b1·ex + b2·ey + b3·hx + b4·hy  (+ offset di ricentratura)
 * appresa sui 9 punti di calibrazione. Pesi di occhi e testa imparati per l'utente.
 */
class GazeModel(
    val bx: FloatArray,
    val by: FloatArray,
    var offX: Float = 0f,
    var offY: Float = 0f,
) {
    fun predict(f: GazeFeatures): Offset {
        val v = vec(f)
        return Offset(dot(bx, v) + offX, dot(by, v) + offY)
    }

    /** Errore quadratico medio (frazione dello schermo) sui punti indicati. */
    fun rmsError(fs: List<GazeFeatures>, targets: List<Offset>): Float {
        if (fs.isEmpty()) return 1f
        var s = 0f
        fs.forEachIndexed { i, f ->
            val p = predict(f); val dx = p.x - targets[i].x; val dy = p.y - targets[i].y
            s += dx * dx + dy * dy
        }
        return sqrt(s / fs.size)
    }

    fun serialize(): String = (bx.toList() + by.toList() + listOf(offX, offY)).joinToString(",")

    companion object {
        const val N = 5
        fun vec(f: GazeFeatures) = floatArrayOf(1f, f.ex, f.ey, f.hx, f.hy)
        private fun dot(a: FloatArray, b: FloatArray): Float { var s = 0f; for (i in a.indices) s += a[i] * b[i]; return s }

        /** Modello generico usato prima della calibrazione. */
        fun default() = GazeModel(
            floatArrayOf(-1.0f, 3f, 0f, 1.5f, 0f),
            floatArrayOf(-1.9f, 0f, 3f, 0f, 1.5f),
        )

        fun deserialize(s: String?): GazeModel? {
            val p = s?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: return null
            if (p.size != 2 * N + 2) return null
            return GazeModel(
                p.subList(0, N).toFloatArray(), p.subList(N, 2 * N).toFloatArray(),
                p[2 * N], p[2 * N + 1]
            )
        }

        fun fit(fs: List<GazeFeatures>, targets: List<Offset>): GazeModel {
            val x = fs.map { vec(it) }
            return GazeModel(ridge(x, targets.map { it.x }), ridge(x, targets.map { it.y }))
        }

        /** Risolve (XᵀX + λI)β = Xᵀy con eliminazione di Gauss-Jordan. */
        private fun ridge(x: List<FloatArray>, y: List<Float>): FloatArray {
            val a = Array(N) { DoubleArray(N + 1) }
            for (r in x.indices) {
                val row = x[r]
                for (i in 0 until N) {
                    for (j in 0 until N) a[i][j] += row[i].toDouble() * row[j]
                    a[i][N] += row[i].toDouble() * y[r]
                }
            }
            for (i in 1 until N) a[i][i] += GazeConfig.RIDGE_LAMBDA // l'intercetta non si regolarizza
            for (c in 0 until N) {
                var p = c
                for (r in c + 1 until N) if (abs(a[r][c]) > abs(a[p][c])) p = r
                val tmp = a[c]; a[c] = a[p]; a[p] = tmp
                val pv = a[c][c]
                if (abs(pv) < 1e-12) continue
                for (k in c..N) a[c][k] /= pv
                for (r in 0 until N) if (r != c) {
                    val f = a[r][c]
                    if (f != 0.0) for (k in c..N) a[r][k] -= f * a[c][k]
                }
            }
            return FloatArray(N) { a[it][N].toFloat() }
        }
    }
}

/**
 * Filtro One Euro: molto stabile quando lo sguardo è fermo (fissazione),
 * reattivo quando si muove (saccade). Migliore della media mobile semplice.
 */
class OneEuroFilter(var minCutoff: Float, private val beta: Float, private val dCutoff: Float = 1f) {
    private var xPrev = Float.NaN
    private var dxPrev = 0f
    private var tPrev = 0L

    fun filter(x: Float, tMs: Long): Float {
        if (xPrev.isNaN()) { xPrev = x; tPrev = tMs; return x }
        val dt = (tMs - tPrev).coerceAtLeast(1L) / 1000f
        tPrev = tMs
        val dx = (x - xPrev) / dt
        val aD = alpha(dCutoff, dt)
        dxPrev = aD * dx + (1 - aD) * dxPrev
        val a = alpha(minCutoff + beta * abs(dxPrev), dt)
        xPrev = a * x + (1 - a) * xPrev
        return xPrev
    }

    fun reset() { xPrev = Float.NaN; dxPrev = 0f }

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }
}

/** Features → punto schermo normalizzato (0..1) filtrato. */
class GazeEstimator(saved: GazeModel?) {
    @Volatile var model: GazeModel = saved ?: GazeModel.default()
    var calibrated: Boolean = saved != null

    private val fx = OneEuroFilter(0.4f, GazeConfig.FILTER_BETA)
    private val fy = OneEuroFilter(0.4f, GazeConfig.FILTER_BETA)

    /** SMOOTHING: stabilità 1 (reattivo, 2 Hz) .. 10 (molto stabile, 0.2 Hz). */
    fun setStability(s: Int) {
        val c = 2f / s.coerceIn(1, 10)
        fx.minCutoff = c; fy.minCutoff = c
    }

    fun estimate(f: GazeFeatures): Offset {
        val p = model.predict(f)
        return Offset(
            fx.filter(p.x.coerceIn(-0.1f, 1.1f), f.timestamp).coerceIn(0f, 1f),
            fy.filter(p.y.coerceIn(-0.1f, 1.1f), f.timestamp).coerceIn(0f, 1f),
        )
    }

    fun reset() { fx.reset(); fy.reset() }
}
