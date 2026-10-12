package com.example.gazecaa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Size
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private lateinit var settings: GazeSettings
    private lateinit var mapper: GazeMapper
    private var analyzer: GazeAnalyzer? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    @Volatile private var latestRaw: RawGaze? = null
    private var lastFaceTime = 0L
    private val gazeNorm = mutableStateOf<Offset?>(null) // 0..1
    private val faceVisible = mutableStateOf(false)
    private val errorMsg = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tts = TextToSpeech(this, this)
        settings = GazeSettings(this)
        mapper = GazeMapper(settings.smoothing).apply { calibration = settings.loadCalibration() }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var hasPerm by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                    )
                }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { hasPerm = it }
                LaunchedEffect(Unit) { if (!hasPerm) launcher.launch(Manifest.permission.CAMERA) }
                LaunchedEffect(hasPerm) { if (hasPerm) startCamera() }
                LaunchedEffect(settings.smoothing) { mapper.setWindow(settings.smoothing) }

                // Al primo avvio (nessuna calibrazione salvata) parte subito la calibrazione
                var calibrating by remember { mutableStateOf(mapper.calibration == null) }
                var showSettings by remember { mutableStateOf(false) }
                var rootSize by remember { mutableStateOf(IntSize.Zero) }

                Surface(Modifier.fillMaxSize(), color = Color.Black) {
                    Box(Modifier.fillMaxSize().onSizeChanged { rootSize = it }) {
                        val err = errorMsg.value
                        when {
                            err != null -> Text(err, Modifier.align(Alignment.Center).padding(32.dp), color = Color(0xFFFF8A80))

                            !hasPerm -> Text("Serve il permesso della fotocamera", Modifier.align(Alignment.Center))

                            calibrating -> CalibrationScreen(
                                rawProvider = { latestRaw },
                                onDone = { c ->
                                    if (c != null) {
                                        mapper.calibration = c
                                        settings.saveCalibration(c)
                                        mapper.reset()
                                    }
                                    calibrating = false
                                }
                            )

                            else -> Column(Modifier.fillMaxSize()) {
                                // Barra del caregiver (si usa col tocco)
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (faceVisible.value) "👁 Volto rilevato" else "⚠ Volto non rilevato",
                                        color = if (faceVisible.value) Color(0xFF81C784) else Color(0xFFFFB74D),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(onClick = { showSettings = true }) { Text("⚙ Impostazioni") }
                                    Button(onClick = { calibrating = true }) { Text("Calibra") }
                                }

                                val g = gazeNorm.value
                                AacGrid(
                                    gazePx = g?.let { Offset(it.x * rootSize.width, it.y * rootSize.height) },
                                    onSelect = { speak(it.label) },
                                    dwellMs = settings.dwellMs,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        if (showSettings) SettingsDialog(settings) { showSettings = false }
                    }
                }
            }
        }
    }

    private fun startCamera() {
        if (analyzer != null) return
        val gazeAnalyzer = try {
            GazeAnalyzer(this, headWeight = { settings.headWeight }) { raw ->
                runOnUiThread { onRawGaze(raw) }
            }
        } catch (e: Exception) {
            errorMsg.value = "Impossibile caricare il modello MediaPipe " +
                    "(${GazeConfig.MODEL_ASSET} in assets).\n${e.message}"
            return
        }
        analyzer = gazeAnalyzer

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(GazeConfig.ANALYSIS_WIDTH, GazeConfig.ANALYSIS_HEIGHT),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            ).build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(cameraExecutor, gazeAnalyzer)
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (e: Exception) {
                errorMsg.value = "Errore fotocamera: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** Main thread, a ogni frame analizzato. */
    private fun onRawGaze(raw: RawGaze?) {
        val now = System.currentTimeMillis()
        latestRaw = raw
        if (raw != null) {
            lastFaceTime = now
            faceVisible.value = true
            gazeNorm.value = mapper.map(raw)
        } else if (now - lastFaceTime > GazeConfig.FACE_LOST_TIMEOUT_MS) {
            faceVisible.value = false
            gazeNorm.value = null
            mapper.reset()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale.ITALIAN)
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            tts.setSpeechRate(0.9f) // 1.0 = velocità normale
        }
    }

    private fun speak(text: String) {
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aac-${System.nanoTime()}")
    }

    override fun onDestroy() {
        tts.stop(); tts.shutdown()
        cameraExecutor.execute { analyzer?.close() }
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
