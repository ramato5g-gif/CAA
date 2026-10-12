package com.example.gazecaa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Size
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private enum class Screen { HOME, CAA, ABA_SETUP, ABA_SESSION, ABA_SUMMARY, HISTORY }
private enum class Overlay { NONE, CALIBRATION, RECENTER }

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private lateinit var settings: GazeSettings
    private lateinit var estimator: GazeEstimator
    private lateinit var repo: SessionRepository
    private var analyzer: GazeAnalyzer? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    @Volatile private var latestFeatures: GazeFeatures? = null
    private var lastFaceTime = 0L
    private val gazeNorm = mutableStateOf<Offset?>(null) // 0..1
    private val faceVisible = mutableStateOf(false)
    private val eyeDist = mutableFloatStateOf(0f)
    private val errorMsg = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tts = TextToSpeech(this, this)
        settings = GazeSettings(this)
        estimator = GazeEstimator(settings.loadModel()).apply { setStability(settings.stability) }
        repo = SessionRepository(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { App() }
        }
    }

    @Composable
    private fun App() {
        var hasPerm by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
            )
        }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPerm = it }
        LaunchedEffect(Unit) { if (!hasPerm) launcher.launch(Manifest.permission.CAMERA) }
        LaunchedEffect(hasPerm) { if (hasPerm) startCamera() }
        LaunchedEffect(settings.stability) { estimator.setStability(settings.stability) }

        var screen by remember { mutableStateOf(Screen.HOME) }
        // Al primo avvio parte subito la calibrazione
        var overlay by remember { mutableStateOf(if (estimator.calibrated) Overlay.NONE else Overlay.CALIBRATION) }
        var showSettings by remember { mutableStateOf(false) }
        var abaConfig by remember { mutableStateOf(settings.loadAbaConfig()) }
        var lastSession by remember { mutableStateOf<SessionRecord?>(null) }
        var sessionRun by remember { mutableStateOf(0) } // cambia per riavviare una sessione
        var rootSize by remember { mutableStateOf(IntSize.Zero) }

        BackHandler(enabled = overlay == Overlay.NONE && screen != Screen.HOME && screen != Screen.ABA_SESSION) {
            screen = Screen.HOME
        }

        val busy = overlay != Overlay.NONE
        val gazePx = gazeNorm.value?.let { Offset(it.x * rootSize.width, it.y * rootSize.height) }
        val status: @Composable () -> Unit = { FaceStatus(faceVisible.value, eyeDist.floatValue) }

        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize().onSizeChanged { rootSize = it }) {
                val err = errorMsg.value
                when {
                    err != null -> Text(err, Modifier.align(Alignment.Center).padding(32.dp), color = Color(0xFFFF8A80))
                    !hasPerm -> Text("Serve il permesso della fotocamera", Modifier.align(Alignment.Center))
                    else -> when (screen) {
                        Screen.HOME -> HomeScreen(
                            status = status,
                            calibText = calibrationLabel(settings.calibErrorPct),
                            onCaa = { screen = Screen.CAA },
                            onAba = { screen = Screen.ABA_SETUP },
                            onHistory = { screen = Screen.HISTORY },
                            onCalibrate = { overlay = Overlay.CALIBRATION },
                            onRecenter = { overlay = Overlay.RECENTER },
                            onSettings = { showSettings = true },
                        )

                        Screen.CAA -> Column(Modifier.fillMaxSize()) {
                            TopBar("Comunica", { screen = Screen.HOME }, status)
                            AacGrid(
                                gazePx = gazePx,
                                dwellMs = settings.dwellMs,
                                onSelect = { speak(it.label) },
                                enabled = !busy,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Screen.ABA_SETUP -> AbaSetupScreen(
                            initial = abaConfig,
                            onBack = { screen = Screen.HOME },
                            onStart = { cfg ->
                                abaConfig = cfg
                                settings.saveAbaConfig(cfg)
                                sessionRun++
                                screen = Screen.ABA_SESSION
                            }
                        )

                        Screen.ABA_SESSION -> androidx.compose.runtime.key(sessionRun) {
                            AbaSessionScreen(
                                config = abaConfig,
                                gazePx = gazePx,
                                dwellMs = settings.dwellMs,
                                paused = busy,
                                speak = ::speak,
                                onRecenter = { overlay = Overlay.RECENTER },
                                onFinish = { s ->
                                    if (s.trials.isNotEmpty()) repo.save(s)
                                    lastSession = s
                                    screen = Screen.ABA_SUMMARY
                                }
                            )
                        }

                        Screen.ABA_SUMMARY -> lastSession?.let { s ->
                            AbaSummaryScreen(
                                session = s,
                                onExport = { exportCsv(listOf(s)) },
                                onRepeat = { sessionRun++; screen = Screen.ABA_SESSION },
                                onHome = { screen = Screen.HOME }
                            )
                        }

                        Screen.HISTORY -> AbaHistoryScreen(
                            repo = repo,
                            onBack = { screen = Screen.HOME },
                            onExport = { exportCsv(it) }
                        )
                    }
                }

                // Cursore dello sguardo
                if (settings.showCursor && !busy && gazePx != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        val r = 18.dp.toPx()
                        drawCircle(Color.Yellow.copy(alpha = 0.6f), radius = r, center = gazePx)
                        drawCircle(Color.Black, radius = r, center = gazePx, style = Stroke(3.dp.toPx()))
                    }
                }

                // Calibrazione / ricentratura sopra a tutto
                when (overlay) {
                    Overlay.CALIBRATION -> CalibrationScreen({ latestFeatures }) { res ->
                        if (res != null) {
                            estimator.model = res.model
                            estimator.calibrated = true
                            estimator.reset()
                            settings.saveModel(res.model, res.errorPct)
                        } else toast("Calibrazione non riuscita: volto non rilevato. Riprova.")
                        overlay = Overlay.NONE
                    }

                    Overlay.RECENTER -> RecenterScreen({ latestFeatures }, estimator.model) { delta ->
                        if (delta != null) {
                            estimator.model.offX += delta.x
                            estimator.model.offY += delta.y
                            estimator.reset()
                            settings.saveModel(estimator.model)
                        } else toast("Ricentratura non riuscita: volto non rilevato.")
                        overlay = Overlay.NONE
                    }

                    Overlay.NONE -> Unit
                }

                if (showSettings) SettingsDialog(settings) { showSettings = false }
            }
        }
    }

    private fun startCamera() {
        if (analyzer != null) return
        val a = try {
            GazeAnalyzer(this) { f -> runOnUiThread { onFeatures(f) } }
        } catch (e: Exception) {
            errorMsg.value = "Impossibile caricare il modello MediaPipe (${GazeConfig.MODEL_ASSET}).\n${e.message}"
            return
        }
        analyzer = a

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
                analysis.setAnalyzer(cameraExecutor, a)
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (e: Exception) {
                errorMsg.value = "Errore fotocamera: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** Main thread, a ogni frame analizzato. */
    private fun onFeatures(f: GazeFeatures?) {
        val now = System.currentTimeMillis()
        latestFeatures = f
        if (f != null) {
            lastFaceTime = now
            faceVisible.value = true
            eyeDist.floatValue = f.eyeDist
            gazeNorm.value = estimator.estimate(f)
        } else if (now - lastFaceTime > GazeConfig.FACE_LOST_TIMEOUT_MS) {
            faceVisible.value = false
            gazeNorm.value = null
            estimator.reset()
        }
    }

    /** Salva il CSV in cache e lo condivide (email, Drive, WhatsApp...). */
    private fun exportCsv(sessions: List<SessionRecord>) {
        try {
            val dir = File(cacheDir, "export").apply { mkdirs() }
            val name = "dati_aba_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.ITALY).format(Date()) + ".csv"
            val file = File(dir, name).apply { writeText(sessionsToCsv(sessions)) }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Esporta dati ABA"))
        } catch (e: Exception) {
            toast("Esportazione non riuscita: ${e.message}")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

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
