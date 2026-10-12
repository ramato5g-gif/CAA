package com.example.gazecaa

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private enum class Phase { PRESENT, RESPONSE, REINFORCE, CORRECTION, ITI, BREAK }

private const val SKIP = "__skip__"
private val praises = listOf("Ottimo!", "Perfetto!", "Sì, giusto!", "Grande!", "Bel lavoro!")

// Tempi della prova (ms) — regolabili qui
private const val PRE_SD_MS = 600L        // pausa prima dell'istruzione
private const val REINFORCE_MS = 1800L    // durata del rinforzo
private const val CORRECTION_MS = 2500L   // durata della correzione dell'errore
private const val ITI_MS = 800L           // intervallo tra le prove

/**
 * Prove discrete (DTT): SD → risposta → rinforzo/correzione → intervallo.
 * Token board con pausa premio, sfumatura automatica del prompt, pausa automatica
 * se il volto esce dall'inquadratura, controlli nascondibili (protezione bambino).
 */
@Composable
fun AbaSessionScreen(
    config: SessionConfig,
    pointer: () -> Offset?,
    input: InputParams,
    paused: Boolean,
    faceLost: Boolean,
    childLock: Boolean,
    say: (AacSymbol?, String) -> Unit,
    onRecenter: () -> Unit,
    onFinish: (SessionRecord) -> Unit,
) {
    val plan = remember(config) { buildTrialPlan(config, Random(System.nanoTime())) }
    val startedAt = remember { System.currentTimeMillis() }
    val records = remember { mutableStateListOf<TrialRecord>() }
    var index by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(Phase.PRESENT) }
    var prompt by remember { mutableStateOf(config.prompt) }
    var fadeLevel by remember { mutableStateOf(config.prompt) }
    var streak by remember { mutableIntStateOf(0) }
    var response by remember { mutableStateOf<String?>(null) }
    var tokens by remember { mutableIntStateOf(0) }
    var breakLeft by remember { mutableIntStateOf(0) }
    var endBreak by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    // Il volto perso mette in pausa solo se si risponde con sguardo/testa
    val pausedEff = paused || (faceLost && config.inputMode != InputMode.TOUCH)
    val pausedNow by rememberUpdatedState(pausedEff)
    val finishCb by rememberUpdatedState(onFinish)
    val sayCb by rememberUpdatedState(say)
    val haptic = LocalHapticFeedback.current
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }

    fun finish() {
        if (finished) return
        finished = true
        finishCb(SessionRecord("s$startedAt", startedAt, System.currentTimeMillis(), config, records.toList()))
    }

    /** Sfumatura: dopo N corrette si scende di un livello di prompt, dopo un errore si sale. */
    fun updateFade(correct: Boolean) {
        val levels = PromptLevel.entries
        if (correct) {
            streak += 1
            if (streak >= GazeConfig.FADE_AFTER_CORRECT && fadeLevel.ordinal > 0) {
                fadeLevel = levels[fadeLevel.ordinal - 1]; streak = 0
            }
        } else {
            streak = 0
            if (fadeLevel.ordinal < levels.size - 1) fadeLevel = levels[fadeLevel.ordinal + 1]
        }
    }

    val trial = plan[index]
    val target = Symbols.byId(trial.targetId) ?: SymbolLibrary.all.first()
    val sd = config.program.instruction(target)

    LaunchedEffect(index) {
        response = null
        prompt = if (config.autoFade) fadeLevel else config.prompt
        phase = Phase.PRESENT
        delay(PRE_SD_MS)
        while (pausedNow) delay(100) // non si presenta la prova durante la pausa
        sayCb(target, sd) // voce registrata se presente, altrimenti sintesi vocale
        phase = Phase.RESPONSE

        var waited = 0L
        val timeout = config.timeoutSec * 1000L
        while (response == null && waited < timeout) {
            delay(100)
            if (!pausedNow) waited += 100
        }

        val r = response
        if (r != SKIP) {
            val correct = r == trial.targetId
            records += TrialRecord(index + 1, trial.targetId, trial.fieldIds, r, correct, prompt, waited, System.currentTimeMillis())
            if (config.autoFade) updateFade(correct)
            if (correct) {
                phase = Phase.REINFORCE
                if (config.sound) {
                    tone?.startTone(ToneGenerator.TONE_PROP_ACK, 250)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                if (config.reinforcement) sayCb(null, praises.random())
                if (config.tokens > 0) tokens += 1
                delay(REINFORCE_MS)

                // Token board completa → pausa premio
                if (config.tokens > 0 && tokens >= config.tokens) {
                    phase = Phase.BREAK
                    endBreak = false
                    sayCb(null, "Hai vinto il premio!")
                    var left = config.breakSec
                    while (left > 0 && !endBreak) {
                        breakLeft = left
                        delay(1000)
                        if (!pausedNow) left--
                    }
                    tokens = 0
                }
            } else {
                phase = Phase.CORRECTION
                if (config.errorCorrection) {
                    sayCb(null, if (config.program == ProgramType.RECEPTIVE) "Guarda, questo è ${target.sdName}" else "Guarda, è questo")
                    delay(CORRECTION_MS)
                } else delay(700)
            }
        }
        phase = Phase.ITI
        delay(ITI_MS)
        if (index + 1 >= plan.size) finish() else index += 1
    }

    val dwell = rememberDwellState(
        pointer, input,
        enabled = phase == Phase.RESPONSE && !pausedEff,
        resetKey = index
    ) { key -> if (phase == Phase.RESPONSE) response = key }
    val scan = input.select == SelectMode.SCAN

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Barra del terapista (nascosta con la protezione bambino: pressione lunga sull'ingranaggio)
            ChildLockArea(childLock) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Prova ${index + 1}/${plan.size}  ·  Corrette ${records.count { it.correct }}/${records.size}" +
                                if (prompt != PromptLevel.INDEPENDENT) "  ·  Prompt: ${prompt.title}" else "",
                        modifier = Modifier.weight(1f), color = Color(0xFFB0BEC5)
                    )
                    OutlinedButton(enabled = phase == Phase.RESPONSE, onClick = { prompt = PromptLevel.VISUAL }) { Text("💡 Prompt") }
                    if (config.inputMode != InputMode.TOUCH) OutlinedButton(onClick = onRecenter) { Text("⊕ Ricentra") }
                    OutlinedButton(enabled = phase == Phase.RESPONSE, onClick = { response = SKIP }) { Text("⏭ Salta") }
                    Button(onClick = { finish() }) { Text("■ Termina") }
                }
            }

            Text(
                sd, fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(4.dp)
            )

            // Token board
            if (config.tokens > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    for (i in 0 until config.tokens) {
                        Text(
                            "⭐", fontSize = 36.sp,
                            modifier = Modifier.padding(horizontal = 4.dp).alpha(if (i < tokens) 1f else 0.2f)
                        )
                    }
                }
            }

            if (pausedEff && phase == Phase.RESPONSE) {
                Text(
                    if (faceLost) "⏸ In pausa: volto non visibile" else "⏸ In pausa",
                    color = Color(0xFFFFB74D), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
            }

            // Abbinamento: campione in alto (non selezionabile)
            if (config.program == ProgramType.MATCHING) {
                Box(Modifier.weight(0.7f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GazeCard(
                        target.emoji, if (config.showLabels) target.label else null, target.color,
                        Modifier.fillMaxHeight().aspectRatio(1f), imagePath = target.imagePath
                    )
                }
            }

            // Campo: una riga orizzontale (il tracciamento è più preciso in orizzontale)
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                trial.fieldIds.forEach { id ->
                    val s = Symbols.byId(id) ?: return@forEach
                    val isTarget = id == trial.targetId
                    val w = if (isTarget && prompt == PromptLevel.POSITIONAL && phase == Phase.RESPONSE) 1.6f else 1f
                    val dim = phase == Phase.CORRECTION && !isTarget
                    GazeCard(
                        emoji = s.emoji,
                        label = if (config.showLabels) s.label else null,
                        color = s.color,
                        imagePath = s.imagePath,
                        progress = { dwell.progressOf(id) },
                        hovered = dwell.hovered == id && phase == Phase.RESPONSE,
                        highlighted = isTarget && (phase == Phase.REINFORCE || phase == Phase.CORRECTION),
                        pulse = isTarget && ((prompt == PromptLevel.VISUAL && phase == Phase.RESPONSE) || phase == Phase.CORRECTION),
                        onClick = {
                            if (phase == Phase.RESPONSE) { if (scan) dwell.pressSwitch() else response = id }
                        },
                        modifier = Modifier.weight(w).fillMaxHeight().alpha(if (dim) 0.25f else 1f).dwellTarget(dwell, id)
                    )
                }
            }
        }
        if (phase == Phase.REINFORCE && config.reinforcement) ReinforcerOverlay()
        if (phase == Phase.BREAK) BreakOverlay(breakLeft) { endBreak = true }
    }
}

@Composable
private fun ReinforcerOverlay() {
    val t = rememberInfiniteTransition(label = "rinforzo")
    val s by t.animateFloat(0.8f, 1.25f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "scala")
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Text("🌟🎉🌟", fontSize = 140.sp, modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s })
    }
}

@Composable
private fun BreakOverlay(secondsLeft: Int, onEnd: () -> Unit) {
    val t = rememberInfiniteTransition(label = "premio")
    val r by t.animateFloat(-12f, 12f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "rot")
    Box(Modifier.fillMaxSize().background(Color(0xFF1A237E)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎁", fontSize = 160.sp, modifier = Modifier.graphicsLayer { rotationZ = r })
            Text("Premio!", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("$secondsLeft s", fontSize = 28.sp, color = Color.White.copy(alpha = 0.7f))
            OutlinedButton(onClick = onEnd, modifier = Modifier.padding(top = 24.dp)) { Text("Riprendi ora") }
        }
    }
}
