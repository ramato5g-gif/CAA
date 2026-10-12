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
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.hypot

/**
 * Caratteristiche di un frame.
 * ex, ey = iride nell'occhio (NaN a occhi chiusi); hx, hy = posa della testa;
 * eyeDist = distanza interoculare / larghezza frame.
 */
data class GazeFeatures(
    val ex: Float, val ey: Float,
    val hx: Float, val hy: Float,
    val eyeDist: Float,
    val eyesOpen: Boolean,
    val timestamp: Long,
)

/**
 * Analyzer CameraX + MediaPipe Face Landmarker in modalità LIVE_STREAM (asincrona):
 * la fotocamera non aspetta il modello, MediaPipe scarta da solo i frame in eccesso.
 * Usa la GPU se disponibile, altrimenti la CPU.
 *
 * Callback chiamati su un thread di MediaPipe: fare il post sul main thread.
 */
class GazeAnalyzer(
    context: Context,
    private val onFeatures: (GazeFeatures?) -> Unit,
    private val onStats: (fps: Int, gpu: Boolean) -> Unit,
) : ImageAnalysis.Analyzer {

    private val appContext = context.applicationContext
    private var landmarker: FaceLandmarker
    private var usingGpu = false
    @Volatile private var gpuFailed = false
    private var lastTs = 0L
    private var frames = 0
    private var fpsWindowStart = SystemClock.uptimeMillis()

    init {
        landmarker = if (GazeConfig.TRY_GPU) {
            try { create(Delegate.GPU).also { usingGpu = true } } catch (e: Throwable) { create(Delegate.CPU) }
        } else create(Delegate.CPU)
    }

    private fun create(delegate: Delegate): FaceLandmarker = FaceLandmarker.createFromOptions(
        appContext,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(GazeConfig.MODEL_ASSET).setDelegate(delegate).build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result: FaceLandmarkerResult, input -> handle(result, input.width, input.height) }
            .setErrorListener { _ -> if (usingGpu) gpuFailed = true; onFeatures(null) }
            .build()
    )

    override fun analyze(imageProxy: ImageProxy) {
        if (gpuFailed) { // la GPU ha dato errore: si passa alla CPU
            gpuFailed = false; usingGpu = false
            runCatching { landmarker.close() }
            landmarker = create(Delegate.CPU)
        }
        val frame: Bitmap = try {
            val src = imageProxy.toBitmap()
            val m = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                postScale(-1f, 1f) // specchio: destra = destra dell'utente
            }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, false)
        } catch (e: Exception) {
            imageProxy.close(); return
        }
        imageProxy.close()

        var ts = SystemClock.uptimeMillis()
        if (ts <= lastTs) ts = lastTs + 1
        lastTs = ts
        try {
            landmarker.detectAsync(BitmapImageBuilder(frame).build(), ts)
        } catch (e: Exception) {
            onFeatures(null)
        }
    }

    private fun handle(result: FaceLandmarkerResult, w: Int, h: Int) {
        frames++
        val now = SystemClock.uptimeMillis()
        if (now - fpsWindowStart >= 1000) {
            onStats((frames * 1000 / (now - fpsWindowStart)).toInt(), usingGpu)
            frames = 0; fpsWindowStart = now
        }
        val lm = result.faceLandmarks().firstOrNull()
        onFeatures(if (lm != null && lm.size >= 478) computeFeatures(lm, w, h) else null)
    }

    fun close() = runCatching { landmarker.close() }

    // Indici MediaPipe: occhio A 33/133 (palpebre 159/145, iride 468-472),
    // occhio B 362/263 (palpebre 386/374, iride 473-477), punta del naso 1.
    private fun computeFeatures(lm: List<NormalizedLandmark>, w: Int, h: Int): GazeFeatures? {
        fun p(i: Int) = PointF(lm[i].x() * w, lm[i].y() * h)
        fun irisCenter(from: Int): PointF {
            var x = 0f; var y = 0f
            for (i in from..from + 4) { x += lm[i].x() * w; y += lm[i].y() * h }
            return PointF(x / 5f, y / 5f)
        }
        val a = p(33); val b = p(263); val nose = p(1)
        val eyeDist = hypot(a.x - b.x, a.y - b.y)
        if (eyeDist < 1f) return null

        val ratios = listOfNotNull(
            eyeRatio(irisCenter(468), p(33), p(133), p(159), p(145)),
            eyeRatio(irisCenter(473), p(362), p(263), p(386), p(374)),
        )
        val open = ratios.isNotEmpty()
        return GazeFeatures(
            ex = if (open) ratios.map { it.x }.average().toFloat() else Float.NaN,
            ey = if (open) ratios.map { it.y }.average().toFloat() else Float.NaN,
            hx = (nose.x - (a.x + b.x) / 2f) / eyeDist,
            hy = (nose.y - (a.y + b.y) / 2f) / eyeDist,
            eyeDist = eyeDist / w,
            eyesOpen = open,
            timestamp = System.currentTimeMillis(),
        )
    }

    private fun eyeRatio(iris: PointF, c1: PointF, c2: PointF, top: PointF, bottom: PointF): PointF? {
        val left = minOf(c1.x, c2.x)
        val width = maxOf(c1.x, c2.x) - left
        if (width < 4f) return null
        if ((bottom.y - top.y) / width < GazeConfig.BLINK_RATIO) return null // occhio chiuso
        val cornerY = (c1.y + c2.y) / 2f
        return PointF((iris.x - left) / width, 0.5f + (iris.y - cornerY) / width * GazeConfig.PUPIL_VERTICAL_GAIN)
    }
}
