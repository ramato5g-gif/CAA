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

/**
 * Caratteristiche estratte da un frame. La conversione in coordinate schermo
 * la fa il modello di calibrazione (GazeModel), che impara da solo quanto
 * pesano occhi e testa per QUESTO utente.
 *
 * ex, ey = posizione dell'iride nell'occhio; hx, hy = posa della testa;
 * eyeDist = distanza interoculare / larghezza frame (serve per l'indicatore di distanza).
 */
data class GazeFeatures(
    val ex: Float, val ey: Float,
    val hx: Float, val hy: Float,
    val eyeDist: Float,
    val timestamp: Long,
)

/**
 * Analyzer CameraX basato su MediaPipe Face Landmarker (478 punti, iridi incluse).
 * Il frame viene ruotato e specchiato: destra/sinistra = destra/sinistra dell'utente.
 * [onFeatures] è chiamato sul thread dell'analyzer.
 * @throws Exception se il modello in assets manca o non si carica.
 */
class GazeAnalyzer(
    context: Context,
    private val onFeatures: (GazeFeatures?) -> Unit,
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
            .setRunningMode(RunningMode.VIDEO)
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
        if (frame == null) { onFeatures(null); return }

        var ts = SystemClock.uptimeMillis()
        if (ts <= lastTs) ts = lastTs + 1
        lastTs = ts

        val landmarks = try {
            landmarker.detectForVideo(BitmapImageBuilder(frame).build(), ts)
                .faceLandmarks().firstOrNull()
        } catch (e: Exception) {
            null
        }

        onFeatures(
            if (landmarks != null && landmarks.size >= 478)
                computeFeatures(landmarks, frame.width, frame.height)
            else null
        )
    }

    fun close() = landmarker.close()

    // Indici MediaPipe:
    //  occhio A: angoli 33/133, palpebre 159/145, iride 468 (centro) + 469-472 (bordo)
    //  occhio B: angoli 362/263, palpebre 386/374, iride 473 (centro) + 474-477 (bordo)
    //  punta del naso: 1
    private fun computeFeatures(lm: List<NormalizedLandmark>, w: Int, h: Int): GazeFeatures? {
        fun p(i: Int) = PointF(lm[i].x() * w, lm[i].y() * h)
        // Centro iride = media di centro + 4 punti del bordo (meno rumore del solo centro)
        fun irisCenter(from: Int): PointF {
            var x = 0f; var y = 0f
            for (i in from..from + 4) { x += lm[i].x() * w; y += lm[i].y() * h }
            return PointF(x / 5f, y / 5f)
        }

        val ratios = listOfNotNull(
            eyeRatio(irisCenter(468), p(33), p(133), p(159), p(145)),
            eyeRatio(irisCenter(473), p(362), p(263), p(386), p(374)),
        )
        if (ratios.isEmpty()) return null // entrambi gli occhi chiusi

        val a = p(33); val b = p(263); val nose = p(1)
        val eyeDist = hypot(a.x - b.x, a.y - b.y)
        if (eyeDist < 1f) return null

        return GazeFeatures(
            ex = ratios.map { it.x }.average().toFloat(),
            ey = ratios.map { it.y }.average().toFloat(),
            hx = (nose.x - (a.x + b.x) / 2f) / eyeDist,
            hy = (nose.y - (a.y + b.y) / 2f) / eyeDist,
            eyeDist = eyeDist / w,
            timestamp = System.currentTimeMillis(),
        )
    }

    /** x: 0..1 da bordo sinistro a destro dell'occhio; y: 0.5 + scostamento dalla linea degli angoli. */
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
