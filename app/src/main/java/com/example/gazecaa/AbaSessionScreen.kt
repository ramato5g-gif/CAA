package com.example.gazecaa

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private enum class Phase { PRESENT, RESPONSE, REINFORCE, CORRECTION, ITI }

private const val SKIP = "__skip__"
private val praises = listOf("Ottimo!", "Perfetto!", "Sì, giusto!", "Grande!", "Bel lavoro!")

// Tempi della prova (ms) — regolabili qui
private const val PRE_SD_MS = 600L        // pausa prima dell'istruzione
private const val REINFORCE_MS = 1800L    // durata del rinforzo
private const val CORRECTION_MS = 2500L   // durata della correzione dell'errore
private const val ITI_MS = 800L           // intervallo tra le prove

/**
 * Prove discrete (DTT): istruzione (SD) → risposta con lo sguardo → rinforzo o
 * correzione → intervallo → prova successiva. Ogni prova viene registrata.
 *
 * @param paused true durante la ricentratura: nessuna selezione e timer fermo.
 */
@Composable
fun AbaSessionScreen(
    config: SessionConfig,
    gazePx: Offset?,
    dwellMs: Int,
    paused: Boolean,
    speak: (String) -> Unit,
    onRecenter: () -> Unit,
    onFinish: (SessionRecord) -> Unit,
) {
    val plan = remember(config) { buildTrialPlan(config, Random(System.nanoTime())) }
    val startedAt = remember { System.currentTimeMillis() }
    val records = remember { mutableStateListOf<TrialRecord>() }
    var index by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(Phase.PRESENT) }
    var prompt by remember { mutableStateOf(config.prompt) }
    var response by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }
    val pausedNow by rememberUpdatedState(paused)
    val finishCb by rememberUpdatedState(onFinish)
    val speakCb by rememberUpdatedState(speak)

    fun finish() {
        if (finished) return
        finished = true
        finishCb(SessionRecord("s$startedAt", startedAt, System.currentTimeMillis(), config, records.toList()))
    }

    val trial = plan[index]
    val target = SymbolLibrary.byId(trial.targetId) ?: SymbolLibrary.all.first()
    val sd = config.program.instruction(target)

    LaunchedEffect(index) {
        response = null
        prompt = config.prompt
        phase = Phase.PRESENT
        delay(PRE_SD_MS)
        speakCb(sd)
        phase = Phase.RESPONSE

        // Attesa della risposta; il tempo non scorre durante la pausa
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
            if (correct) {
                phase = Phase.REINFORCE
                if (config.reinforcement) speakCb(praises.random())
                delay(REINFORCE_MS)
            } else {
                phase = Phase.CORRECTION
                if (config.errorCorrection) {
                    speakCb(if (config.program == ProgramType.RECEPTIVE) "Guarda, questo è ${target.sdName}" else "Guarda, è questo")
                    delay(CORRECTION_MS)
                } else delay(700)
            }
        }
        phase = Phase.ITI
        delay(ITI_MS)
        if (index + 1 >= plan.size) finish() else index += 1
    }

    val dwell = rememberDwellState(
        gazePx, dwellMs,
        enabled = phase == Phase.RESPONSE && !paused,
        resetKey = index
    ) { key -> if (phase == Phase.RESPONSE) response = key }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Barra del terapista
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
                OutlinedButton(onClick = onRecenter) { Text("⊕ Ricentra") }
                OutlinedButton(enabled = phase == Phase.RESPONSE, onClick = { response = SKIP }) { Text("⏭ Salta") }
                Button(onClick = { finish() }) { Text("■ Termina") }
            }

            Text(
                sd, fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            )

            // Abbinamento: campione in alto (non selezionabile)
            if (config.program == ProgramType.MATCHING) {
                Box(Modifier.weight(0.7f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GazeCard(
                        target.emoji, if (config.showLabels) target.label else null, target.color, 0f,
                        Modifier.fillMaxHeight().aspectRatio(1f)
                    )
                }
            }

            // Campo: una riga orizzontale (il tracciamento è più preciso in orizzontale)
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                trial.fieldIds.forEach { id ->
                    val s = SymbolLibrary.byId(id) ?: return@forEach
                    val isTarget = id == trial.targetId
                    val w = if (isTarget && prompt == PromptLevel.POSITIONAL && phase == Phase.RESPONSE) 1.6f else 1f
                    val dim = phase == Phase.CORRECTION && !isTarget
                    GazeCard(
                        emoji = s.emoji,
                        label = if (config.showLabels) s.label else null,
                        color = s.color,
                        progress = dwell.progressOf(id),
                        highlighted = isTarget && (phase == Phase.REINFORCE || phase == Phase.CORRECTION),
                        pulse = isTarget && ((prompt == PromptLevel.VISUAL && phase == Phase.RESPONSE) || phase == Phase.CORRECTION),
                        onClick = { if (phase == Phase.RESPONSE) response = id }, // risposta al tocco del terapista
                        modifier = Modifier.weight(w).fillMaxHeight().alpha(if (dim) 0.25f else 1f).dwellTarget(dwell, id)
                    )
                }
            }
        }
        if (phase == Phase.REINFORCE && config.reinforcement) ReinforcerOverlay()
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
