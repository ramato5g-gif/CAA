package com.example.gazecaa

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Size
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import kotlin.math.abs
import kotlin.math.exp

private enum class Screen { HOME, CAA, CAA_EDIT, ABA_SETUP, ABA_SESSION, ABA_SUMMARY, HISTORY, PROGRESS }
private enum class Overlay { NONE, CALIBRATION, RECENTER, TEST }

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private lateinit var settings: GazeSettings
    private lateinit var estimator: GazeEstimator
    private lateinit var repo: SessionRepository
    private lateinit var profiles: ProfileStore
    private lateinit var voices: VoiceStore
    private var analyzer: GazeAnalyzer? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // --- Tracciamento (aggiornato dalla fotocamera, NON letto in composizione) ---
    @Volatile private var latestFeatures: GazeFeatures? = null
    @Volatile private var targetNorm: Offset? = null
    private var rootSize = IntSize.Zero
    private var lastFaceTime = 0L
    private var eyesClosedSince = 0L

    /** Puntatore interpolato a 60 fps, in pixel. Letto solo in disegno e nel ciclo di selezione. */
    private val pointerPx = mutableStateOf<Offset?>(null)

    // --- Stato UI (cambia raramente) ---
    private val dist = mutableStateOf(DistStatus.NO_FACE)
    private val fpsText = mutableStateOf("")
    private val blinkClicks = mutableIntStateOf(0)
    private val switchClicks = mutableIntStateOf(0)
    private val pinned = mutableStateOf(false)
    private val errorMsg = mutableStateOf<String?>(null)
    private var activeMode = InputMode.EYES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tts = TextToSpeech(this, this)
        profiles = ProfileStore(this)
        settings = GazeSettings(this, profiles.current().id)
        voices = VoiceStore(this)
        Symbols.init(this)
        estimator = GazeEstimator(settings.loadModel(), settings.loadNeutral()).apply {
            setStability(settings.stability); setHeadSensitivity(settings.headSens)
        }
        repo = SessionRepository(this)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } }
    }

    override fun onResume() {
        super.onResume()
        pinned.value = isPinned()
    }

    @Composable
    private fun App() {
        var hasPerm by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPerm = it }
        LaunchedEffect(Unit) { if (!hasPerm) launcher.launch(Manifest.permission.CAMERA) }
        LaunchedEffect(hasPerm) { if (hasPerm) startCamera() }

        var screen by remember { mutableStateOf(Screen.HOME) }
        var overlay by remember {
            mutableStateOf(if (settings.inputMode == InputMode.EYES && !estimator.calibrated) Overlay.CALIBRATION else Overlay.NONE)
        }
        var showSettings by remember { mutableStateOf(false) }
        var abaConfig by remember { mutableStateOf(settings.loadAbaConfig()) }
        var lastSession by remember { mutableStateOf<SessionRecord?>(null) }
        var sessionRun by remember { mutableIntStateOf(0) }

        // --- Foto personali ---
        var askPhotoSource by remember { mutableStateOf(false) }
        var newPhotoPath by remember { mutableStateOf<String?>(null) }
        var symbolToDelete by remember { mutableStateOf<AacSymbol?>(null) }

        // --- Voci, profili, backup, test ---
        var voiceFor by remember { mutableStateOf<AacSymbol?>(null) }
        var pendingVoice by remember { mutableStateOf<AacSymbol?>(null) }
        var voiceRev by remember { mutableIntStateOf(0) }
        var showProfiles by remember { mutableStateOf(false) }
        var showBackup by remember { mutableStateOf(false) }
        var testResultPx by remember { mutableStateOf<Float?>(null) }
        var showTestResult by remember { mutableStateOf(false) }
        val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) voiceFor = pendingVoice else toast("Serve il permesso del microfono per registrare")
            pendingVoice = null
        }
        val openVoice: (AacSymbol) -> Unit = { sym ->
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voiceFor = sym
            else { pendingVoice = sym; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
        }
        val rev = voiceRev
        val hasVoice: (String) -> Boolean = { id -> rev >= 0 && voices.has(id) }
        val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val n = runCatching { Backup.restore(this, uri) }.getOrDefault(0)
                if (n > 0) {
                    toast("Backup ripristinato ($n file). Riavvio…")
                    window.decorView.postDelayed({
                        startActivity(Intent.makeRestartActivityTask(componentName))
                        Runtime.getRuntime().exit(0)
                    }, 1200)
                } else toast("File di backup non valido")
            }
        }
        val captureUri = remember {
            val f = File(File(cacheDir, "photos").apply { mkdirs() }, "scatto.jpg")
            FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        }
        val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) newPhotoPath = importPhoto(uri) ?: run { toast("Impossibile leggere la foto"); null }
        }
        val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) newPhotoPath = importPhoto(captureUri) ?: run { toast("Impossibile leggere la foto"); null }
        }

        val mode = if (screen == Screen.ABA_SESSION) abaConfig.inputMode else settings.inputMode
        LaunchedEffect(mode) { activeMode = mode; estimator.mode = mode; estimator.reset() }
        LaunchedEffect(settings.stability) { estimator.setStability(settings.stability) }
        LaunchedEffect(settings.headSens) { estimator.setHeadSensitivity(settings.headSens) }

        // Ciclo a 60 fps: interpolazione morbida del puntatore tra i frame della fotocamera
        LaunchedEffect(Unit) {
            var last = 0L
            var cur: Offset? = null
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                    last = now
                    val t = targetNorm?.let { Offset(it.x * rootSize.width, it.y * rootSize.height) }
                    val c = cur
                    cur = when {
                        t == null -> null
                        c == null -> t
                        else -> c + (t - c) * (1f - exp(-dt / GazeConfig.POINTER_TAU_S))
                    }
                    pointerPx.value = cur
                }
            }
        }

        // Indietro di sistema: disattivato in sessione e, con la protezione bambino, nella tabella
        val backBlocked = screen == Screen.ABA_SESSION || (settings.childLock && screen == Screen.CAA)
        BackHandler(enabled = overlay == Overlay.NONE && screen != Screen.HOME) {
            if (!backBlocked) screen = Screen.HOME
        }

        val busy = overlay != Overlay.NONE
        val pointer: () -> Offset? = { pointerPx.value }
        val input = InputParams(
            mode, settings.selectMode, settings.dwellMs, settings.magnet,
            blinkClicks.intValue, switchClicks.intValue
        )
        val status: @Composable () -> Unit = { if (mode != InputMode.TOUCH) FaceStatus(dist.value) }
        val feedback: (Offset) -> Unit = remember { { c: Offset -> if (settings.autoDrift) correctDrift(c) } }

        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize().onSizeChanged { rootSize = it }) {
                CompositionLocalProvider(LocalSelectFeedback provides feedback) {
                    val err = errorMsg.value
                    when {
                        err != null -> Text(err, Modifier.align(Alignment.Center).padding(32.dp), color = Color(0xFFFF8A80))
                        !hasPerm -> Text("Serve il permesso della fotocamera", Modifier.align(Alignment.Center))
                        else -> when (screen) {
                            Screen.HOME -> HomeScreen(
                                status = status,
                                infoText = buildString {
                                    when (settings.inputMode) {
                                        InputMode.EYES -> append(calibrationLabel(settings.calibErrorPct))
                                        InputMode.HEAD -> append("Modalità Testa: premi Ricentra con la testa dritta verso il tablet")
                                        InputMode.TOUCH -> append("Modalità Touch")
                                    }
                                    append("  ·  Conferma: ${settings.selectMode.title}")
                                    if (settings.inputMode != InputMode.TOUCH && fpsText.value.isNotEmpty()) append("  ·  ${fpsText.value}")
                                },
                                inputMode = settings.inputMode,
                                onInputMode = { m ->
                                    settings.setInput(m)
                                    if (m == InputMode.EYES && !estimator.calibrated) overlay = Overlay.CALIBRATION
                                },
                                onCaa = { screen = Screen.CAA },
                                onAba = { screen = Screen.ABA_SETUP },
                                onHistory = { screen = Screen.HISTORY },
                                onEditCaa = { screen = Screen.CAA_EDIT },
                                onCalibrate = { overlay = Overlay.CALIBRATION },
                                onRecenter = { overlay = Overlay.RECENTER },
                                onSettings = { showSettings = true },
                                pinned = pinned.value,
                                onTogglePin = { togglePin() },
                                profileName = profiles.current().name,
                                onProfiles = { showProfiles = true },
                                onAccuracyTest = { overlay = Overlay.TEST },
                                onBackup = { showBackup = true },
                            )

                            Screen.CAA -> Column(Modifier.fillMaxSize()) {
                                ChildLockArea(settings.childLock) {
                                    TopBar("Comunica", { screen = Screen.HOME }, status)
                                }
                                AacBoard(
                                    pointer = pointer,
                                    input = input,
                                    items = settings.caaIds.mapNotNull { Symbols.byId(it) },
                                    cols = settings.caaCols,
                                    sentenceMode = settings.caaSentence,
                                    say = ::say,
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Screen.CAA_EDIT -> CaaSetupScreen(
                                initialIds = settings.caaIds,
                                initialCols = settings.caaCols,
                                onAddPhoto = { askPhotoSource = true },
                                onDelete = { symbolToDelete = it },
                                onVoice = openVoice,
                                hasVoice = hasVoice,
                                onDone = { ids, cols -> settings.setCaa(ids, cols); screen = Screen.HOME }
                            )

                            Screen.ABA_SETUP -> AbaSetupScreen(
                                initial = abaConfig,
                                defaultLearner = profiles.current().name,
                                onBack = { screen = Screen.HOME },
                                onVoice = openVoice,
                                hasVoice = hasVoice,
                                onAddPhoto = { askPhotoSource = true },
                                onDeleteSymbol = { symbolToDelete = it },
                                onStart = { cfg ->
                                    abaConfig = cfg
                                    settings.saveAbaConfig(cfg)
                                    sessionRun++
                                    screen = Screen.ABA_SESSION
                                    if (cfg.inputMode == InputMode.EYES && !estimator.calibrated) overlay = Overlay.CALIBRATION
                                }
                            )

                            Screen.ABA_SESSION -> key(sessionRun) {
                                AbaSessionScreen(
                                    config = abaConfig,
                                    pointer = pointer,
                                    input = input,
                                    paused = busy,
                                    faceLost = dist.value == DistStatus.NO_FACE,
                                    childLock = settings.childLock,
                                    say = ::say,
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
                                    onSaveNotes = { n ->
                                        val updated = s.copy(notes = n)
                                        if (updated.trials.isNotEmpty()) repo.save(updated)
                                        lastSession = updated
                                    },
                                    onExport = { exportCsv(listOf(lastSession ?: s)) },
                                    onRepeat = { sessionRun++; screen = Screen.ABA_SESSION },
                                    onHome = { screen = Screen.HOME }
                                )
                            }

                            Screen.HISTORY -> AbaHistoryScreen(
                                repo = repo,
                                onBack = { screen = Screen.HOME },
                                onExport = { exportCsv(it) },
                                onProgress = { screen = Screen.PROGRESS }
                            )

                            Screen.PROGRESS -> AbaProgressScreen(
                                sessions = remember { repo.all() },
                                onBack = { screen = Screen.HISTORY },
                                onReport = { learner, program, list ->
                                    runCatching { PdfReport.create(this, learner, program, list) }
                                        .onSuccess { shareFile(it, "application/pdf", "Report ABA") }
                                        .onFailure { toast("Report non riuscito: ${it.message}") }
                                }
                            )
                        }
                    }
                }

                // Cursore: legge la posizione solo in fase di disegno (nessuna ricomposizione)
                val radiusDp = settings.cursorSize.radiusDp
                val showCursor = (!busy || overlay == Overlay.TEST) && mode != InputMode.TOUCH && settings.selectMode != SelectMode.SCAN &&
                        radiusDp > 0f && (screen == Screen.CAA || screen == Screen.ABA_SESSION || screen == Screen.HOME)
                if (showCursor) {
                    Canvas(Modifier.fillMaxSize()) {
                        val p = pointerPx.value ?: return@Canvas
                        val r = radiusDp.dp.toPx()
                        drawCircle(Color.Yellow.copy(alpha = 0.55f), radius = r, center = p)
                        drawCircle(Color.Black, radius = r, center = p, style = Stroke(3.dp.toPx()))
                    }
                }

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

                    Overlay.RECENTER -> RecenterScreen({ latestFeatures }, requireEyes = mode == InputMode.EYES) { f ->
                        when {
                            f == null -> toast("Ricentratura non riuscita: volto non rilevato.")
                            mode == InputMode.HEAD -> {
                                val n = HeadNeutral(f.hx, f.hy)
                                estimator.neutral = n
                                settings.saveNeutral(n)
                            }
                            else -> {
                                val m = estimator.model
                                val p = m.predict(f)
                                m.offX += 0.5f - p.x
                                m.offY += 0.5f - p.y
                                settings.saveModel(m)
                            }
                        }
                        estimator.reset()
                        overlay = Overlay.NONE
                    }

                    Overlay.TEST -> AccuracyTestScreen({ pointerPx.value }) { err ->
                        testResultPx = err
                        showTestResult = true
                        overlay = Overlay.NONE
                    }

                    Overlay.NONE -> Unit
                }

                if (showSettings) SettingsDialog(settings) { showSettings = false }

                if (askPhotoSource) PhotoSourceDialog(
                    onGallery = {
                        askPhotoSource = false
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onCamera = { askPhotoSource = false; takePhoto.launch(captureUri) },
                    onDismiss = { askPhotoSource = false }
                )

                newPhotoPath?.let { path ->
                    NewSymbolDialog(
                        imagePath = path,
                        onSave = { label, phrase ->
                            Symbols.add(AacSymbol("u_${System.currentTimeMillis()}", label, "🖼️", Color(0xFF455A64), phrase, path))
                            newPhotoPath = null
                        },
                        onCancel = { File(path).delete(); newPhotoPath = null }
                    )
                }

                voiceFor?.let { sym ->
                    VoiceDialog(sym, voices) { voiceFor = null; voiceRev++ }
                }

                if (showProfiles) ProfileDialog(
                    store = profiles,
                    onSelect = { p -> profiles.currentId = p.id; showProfiles = false; recreate() },
                    onDismiss = { showProfiles = false }
                )

                if (showBackup) AlertDialog(
                    onDismissRequest = { showBackup = false },
                    title = { Text("Backup") },
                    text = { Text("Il backup contiene profili, impostazioni, sessioni, foto e voci. Salvalo su Drive o inviatelo per non perdere i dati o per passarli a un altro tablet. Il ripristino sostituisce i dati attuali.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showBackup = false
                            runCatching { Backup.create(this) }
                                .onSuccess { shareFile(it, "application/zip", "Backup Sguardo CAA") }
                                .onFailure { toast("Backup non riuscito: ${it.message}") }
                        }) { Text("💾 Esporta backup") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBackup = false; restorePicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
                            Text("Ripristina…")
                        }
                    }
                )

                if (showTestResult) {
                    val px = testResultPx
                    val dm = resources.displayMetrics
                    val cm = if (px == null) 0f else px / dm.xdpi * 2.54f
                    val screenCm = rootSize.width / dm.xdpi * 2.54f
                    val maxCols = if (cm <= 0f) 0 else (screenCm / (cm * 3f)).toInt().coerceIn(1, 5)
                    AlertDialog(
                        onDismissRequest = { showTestResult = false },
                        title = { Text("Risultato test di precisione") },
                        text = {
                            Text(
                                if (px == null) "Puntatore non rilevato: controlla posizione e luce, poi riprova."
                                else "Errore medio ≈ %.1f cm (%.0f px).\nImmagini consigliate: larghe almeno %.0f cm → al massimo %d per riga."
                                    .format(cm, px, cm * 3f, maxCols)
                            )
                        },
                        confirmButton = { TextButton(onClick = { showTestResult = false }) { Text("OK") } }
                    )
                }

                symbolToDelete?.let { s ->
                    AlertDialog(
                        onDismissRequest = { symbolToDelete = null },
                        title = { Text("Eliminare \"${s.label}\"?") },
                        text = { Text("La foto viene cancellata. Le sessioni già registrate restano.") },
                        confirmButton = { TextButton(onClick = { Symbols.remove(s.id); symbolToDelete = null }) { Text("Elimina") } },
                        dismissButton = { TextButton(onClick = { symbolToDelete = null }) { Text("Annulla") } }
                    )
                }
            }
        }
    }

    /**
     * Correzione automatica della deriva: a ogni scelta fatta col puntatore si assume che
     * l'utente guardasse il centro dell'immagine e si riallinea il modello di una frazione.
     */
    private fun correctDrift(centerPx: Offset) {
        val t = targetNorm ?: return
        if (rootSize.width == 0 || rootSize.height == 0) return
        val dx = centerPx.x / rootSize.width - t.x
        val dy = centerPx.y / rootSize.height - t.y
        if (abs(dx) > GazeConfig.DRIFT_MAX_DIST || abs(dy) > GazeConfig.DRIFT_MAX_DIST) return
        val a = GazeConfig.DRIFT_ALPHA
        when (activeMode) {
            InputMode.EYES -> {
                val m = estimator.model
                m.offX += a * dx; m.offY += a * dy
                settings.saveModel(m)
            }
            InputMode.HEAD -> {
                val g = estimator.headGain
                val n = estimator.neutral
                val nn = HeadNeutral(n.hx - a * dx / g, n.hy - a * dy / (g * GazeConfig.HEAD_GAIN_Y_FACTOR))
                estimator.neutral = nn
                settings.saveNeutral(nn)
            }
            InputMode.TOUCH -> Unit
        }
    }

    /** Sensore esterno (switch Bluetooth o tastiera): Spazio / Invio. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (event.repeatCount == 0) switchClicks.intValue++
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun isPinned(): Boolean =
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    /** Blocca l'app in primo piano (fissaggio schermo): il bambino non può uscire. */
    private fun togglePin() {
        try {
            if (isPinned()) stopLockTask() else startLockTask()
        } catch (e: Exception) {
            toast("Fissaggio non disponibile: ${e.message}")
        }
        window.decorView.postDelayed({ pinned.value = isPinned() }, 800)
    }

    /** Copia la foto nella memoria dell'app, ridotta a max 1024 px e raddrizzata. */
    private fun importPhoto(uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val raw = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val orientation = contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val deg = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val bmp = if (deg == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(deg) }, true)
        val dir = Symbols.imageDir ?: return null
        val out = File(dir, "img_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        out.absolutePath
    }.getOrNull()

    private fun startCamera() {
        if (analyzer != null) return
        val a = try {
            GazeAnalyzer(
                this,
                onFeatures = { f -> runOnUiThread { onFeatures(f) } },
                onStats = { fps, gpu -> runOnUiThread { fpsText.value = "$fps fps (${if (gpu) "GPU" else "CPU"})" } }
            )
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

    /** Main thread, a ogni risultato del modello. Lavoro minimo, nessuna ricomposizione. */
    private fun onFeatures(f: GazeFeatures?) {
        val now = System.currentTimeMillis()
        latestFeatures = f
        if (f != null) {
            lastFaceTime = now
            val d = distStatus(true, f.eyeDist)
            if (dist.value != d) dist.value = d

            // Ammiccamento lungo volontario = clic
            if (!f.eyesOpen) {
                if (eyesClosedSince == 0L) eyesClosedSince = now
            } else if (eyesClosedSince != 0L) {
                val closed = now - eyesClosedSince
                if (closed in GazeConfig.BLINK_SELECT_MIN_MS..GazeConfig.BLINK_SELECT_MAX_MS) blinkClicks.intValue++
                eyesClosedSince = 0L
            }

            estimator.estimate(f)?.let { targetNorm = it }
        } else if (now - lastFaceTime > GazeConfig.FACE_LOST_TIMEOUT_MS) {
            if (dist.value != DistStatus.NO_FACE) dist.value = DistStatus.NO_FACE
            targetNorm = null
            eyesClosedSince = 0L
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
            tts.setSpeechRate(0.9f)
        }
    }

    /** Voce registrata del simbolo se presente, altrimenti sintesi vocale. */
    private fun say(symbol: AacSymbol?, text: String) {
        if (symbol != null && voices.has(symbol.id)) {
            if (ttsReady) tts.stop()
            if (voices.play(symbol.id)) return
        }
        voices.stopPlayback()
        speak(text)
    }

    private fun shareFile(file: File, mime: String, title: String) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, title))
        } catch (e: Exception) {
            toast("Condivisione non riuscita: ${e.message}")
        }
    }

    private fun speak(text: String) {
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aac-${System.nanoTime()}")
    }

    override fun onDestroy() {
        tts.stop(); tts.shutdown()
        voices.stopPlayback(); voices.stopRecording()
        cameraExecutor.execute { analyzer?.close() }
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
