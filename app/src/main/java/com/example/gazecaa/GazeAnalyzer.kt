package com.example.gazecaa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import kotlin.math.hypot

/** Segnale di sguardo "grezzo", centrato su 0 (non ancora mappato sullo schermo). */
data class RawGaze(val x: Float, val y: Float, val timestamp: Long)

/**
 * Analyzer CameraX basato su MediaPipe Face Landmarker (478 punti, iridi incluse).
 *
 * Il frame viene ruotato e SPECCHIATO, così destra/sinistra dell'immagine coincidono
 * con destra/sinistra dell'utente e dello schermo.
 *
 * [onGaze] viene chiamato sul thread dell'analyzer: fare il post sul main thread.
 * @throws Exception se il modello in assets manca o non si carica.
 */
class GazeAnalyzer(
    context: Context,
    private val headWeight: () -> Float,
    private val onGaze: (RawGaze?) -> Unit,
) : ImageAnalysis.Analyzer {

    private val landmarker: FaceLandmarker = FaceLandmarker.createFromOptions(
        context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(GazeConfig.MODEL_ASSET)
                    .setDelegate(if (GazeConfig.USE_GPU) Delegate.GPU else Delegate.CPU)
                    .build()
            )
            .setRunningMode(RunningMode.VIDEO) // sincrono, adatto al thread dell'analyzer
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()
    )

    private var lastTs = 0L

    override fun analyze(imageProxy: ImageProxy) {
        val frame: Bitmap? = try {
            val src = imageProxy.toBitmap()
            val m = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                postScale(-1f, 1f) // effetto specchio
            }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        } catch (e: Exception) {
            null
        } finally {
            imageProxy.close()
        }
        if (frame == null) { onGaze(null); return }

        // I timestamp in modalità VIDEO devono essere strettamente crescenti
        var ts = SystemClock.uptimeMillis()
        if (ts <= lastTs) ts = lastTs + 1
        lastTs = ts

        val landmarks = try {
            landmarker.detectForVideo(BitmapImageBuilder(frame).build(), ts)
                .faceLandmarks().firstOrNull()
        } catch (e: Exception) {
            null
        }

        onGaze(
            if (landmarks != null && landmarks.size >= 478)
                computeGaze(landmarks, frame.width, frame.height)
            else null
        )
    }

    fun close() = landmarker.close()

    // ------------------------------------------------------------------
    // Indici MediaPipe:
    //  occhio A: angoli 33/133, iride centro 468
    //  occhio B: angoli 362/263, iride centro 473
    //  palpebre: 159/145 (A), 386/374 (B) — solo per rilevare l'ammiccamento
    //  punta del naso: 1
    // ------------------------------------------------------------------
    private fun computeGaze(lm: List<NormalizedLandmark>, w: Int, h: Int): RawGaze? {
        fun p(i: Int) = PointF(lm[i].x() * w, lm[i].y() * h)

        val ratios = listOfNotNull(
            eyeRatio(p(468), p(33), p(133), p(159), p(145)),
            eyeRatio(p(473), p(362), p(263), p(386), p(374)),
        )
        if (ratios.isEmpty()) return null // entrambi gli occhi chiusi

        val px = ratios.map { it.x }.average().toFloat()
        val py = ratios.map { it.y }.average().toFloat()

        // Posa della testa approssimata: posizione del naso rispetto al centro degli occhi
        val a = p(33); val b = p(263); val nose = p(1)
        val eyeDist = hypot(a.x - b.x, a.y - b.y)
        if (eyeDist < 1f) return null
        val headX = (nose.x - (a.x + b.x) / 2f) / eyeDist
        val headY = (nose.y - (a.y + b.y) / 2f) / eyeDist - GazeConfig.NOSE_BASELINE_Y

        val hw = headWeight()
        val x = GazeConfig.PUPIL_WEIGHT * (px - 0.5f) + hw * GazeConfig.HEAD_SIGN_X * headX
        val y = GazeConfig.PUPIL_WEIGHT * (py - 0.5f) + hw * GazeConfig.HEAD_SIGN_Y * headY
        return RawGaze(x, y, System.currentTimeMillis())
    }

    /**
     * Posizione dell'iride nell'occhio:
     *  x = 0 (bordo sinistro) .. 1 (bordo destro)
     *  y = 0.5 + scostamento verticale dalla linea degli angoli (le palpebre si muovono
     *      con lo sguardo, gli angoli no → riferimento più stabile).
     */
    private fun eyeRatio(iris: PointF, c1: PointF, c2: PointF, top: PointF, bottom: PointF): PointF? {
        val left = minOf(c1.x, c2.x)
        val width = maxOf(c1.x, c2.x) - left
        if (width < 4f) return null
        if ((bottom.y - top.y) / width < GazeConfig.BLINK_RATIO) return null // ammiccamento

        val cornerY = (c1.y + c2.y) / 2f
        return PointF(
            (iris.x - left) / width,
            0.5f + (iris.y - cornerY) / width * GazeConfig.PUPIL_VERTICAL_GAIN
        )
    }
}
