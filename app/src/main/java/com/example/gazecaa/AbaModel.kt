package com.example.gazecaa

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// ======================= SIMBOLI =======================

/** @param sdName nome con articolo, usato nelle istruzioni ("Guarda il cane"). */
data class AacSymbol(val id: String, val label: String, val emoji: String, val color: Color, val sdName: String = label)

object SymbolLibrary {
    /** Libreria per i programmi ABA. Aggiungi qui nuovi elementi. */
    val all = listOf(
        AacSymbol("cane", "cane", "🐶", Color(0xFF8D6E63), "il cane"),
        AacSymbol("gatto", "gatto", "🐱", Color(0xFFFF8F00), "il gatto"),
        AacSymbol("palla", "palla", "⚽", Color(0xFF2E7D32), "la palla"),
        AacSymbol("mela", "mela", "🍎", Color(0xFFC62828), "la mela"),
        AacSymbol("banana", "banana", "🍌", Color(0xFFF9A825), "la banana"),
        AacSymbol("macchina", "macchina", "🚗", Color(0xFF1565C0), "la macchina"),
        AacSymbol("libro", "libro", "📖", Color(0xFF6A1B9A), "il libro"),
        AacSymbol("scarpa", "scarpa", "👟", Color(0xFF00838F), "la scarpa"),
        AacSymbol("bicchiere", "bicchiere", "🥛", Color(0xFF546E7A), "il bicchiere"),
        AacSymbol("cucchiaio", "cucchiaio", "🥄", Color(0xFF455A64), "il cucchiaio"),
        AacSymbol("letto", "letto", "🛏️", Color(0xFF5D4037), "il letto"),
        AacSymbol("sole", "sole", "☀️", Color(0xFFEF6C00), "il sole"),
        AacSymbol("casa", "casa", "🏠", Color(0xFFAD1457), "la casa"),
        AacSymbol("fiore", "fiore", "🌸", Color(0xFFD81B60), "il fiore"),
        AacSymbol("pesce", "pesce", "🐟", Color(0xFF0277BD), "il pesce"),
        AacSymbol("uccello", "uccello", "🐦", Color(0xFF0097A7), "l'uccello"),
        AacSymbol("mucca", "mucca", "🐮", Color(0xFF6D4C41), "la mucca"),
        AacSymbol("treno", "treno", "🚂", Color(0xFF37474F), "il treno"),
    )

    /** Tabella "Comunica". */
    val caa = listOf(
        AacSymbol("caa_si", "Sì", "👍", Color(0xFF2E7D32)),
        AacSymbol("caa_no", "No", "👎", Color(0xFFC62828)),
        AacSymbol("caa_acqua", "Acqua", "💧", Color(0xFF1565C0)),
        AacSymbol("caa_aiuto", "Aiuto", "🆘", Color(0xFFEF6C00)),
    )

    fun byId(id: String): AacSymbol? = all.firstOrNull { it.id == id }
}

// ======================= PROGRAMMI E CONFIGURAZIONE =======================

enum class ProgramType(val title: String) {
    RECEPTIVE("Identificazione recettiva"),
    MATCHING("Abbinamento");

    fun instruction(s: AacSymbol): String = when (this) {
        RECEPTIVE -> "Guarda ${s.sdName}"
        MATCHING -> "Trova quello uguale"
    }
}

enum class PromptLevel(val title: String) {
    INDEPENDENT("Indipendente"),
    VISUAL("Visivo"),        // la scheda corretta pulsa
    POSITIONAL("Posizionale") // la scheda corretta è più grande
}

data class SessionConfig(
    val learner: String,
    val program: ProgramType,
    val targetIds: List<String>,
    val fieldSize: Int,
    val trials: Int,
    val prompt: PromptLevel,
    val timeoutSec: Int,
    val errorCorrection: Boolean,
    val reinforcement: Boolean,
    val showLabels: Boolean,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("learner", learner); put("program", program.name)
        put("targets", JSONArray(targetIds)); put("field", fieldSize); put("trials", trials)
        put("prompt", prompt.name); put("timeout", timeoutSec)
        put("err", errorCorrection); put("reinf", reinforcement); put("labels", showLabels)
    }

    companion object {
        val DEFAULT = SessionConfig(
            learner = "", program = ProgramType.RECEPTIVE,
            targetIds = listOf("cane", "gatto", "palla"),
            fieldSize = 2, trials = 10, prompt = PromptLevel.INDEPENDENT,
            timeoutSec = 15, errorCorrection = true, reinforcement = true, showLabels = false,
        )
    }
}

fun sessionConfigFromJson(o: JSONObject): SessionConfig {
    val d = SessionConfig.DEFAULT
    return SessionConfig(
        learner = o.optString("learner", ""),
        program = runCatching { ProgramType.valueOf(o.getString("program")) }.getOrDefault(d.program),
        targetIds = o.optJSONArray("targets")?.let { a -> List(a.length()) { a.getString(it) } } ?: d.targetIds,
        fieldSize = o.optInt("field", d.fieldSize),
        trials = o.optInt("trials", d.trials),
        prompt = runCatching { PromptLevel.valueOf(o.getString("prompt")) }.getOrDefault(d.prompt),
        timeoutSec = o.optInt("timeout", d.timeoutSec),
        errorCorrection = o.optBoolean("err", d.errorCorrection),
        reinforcement = o.optBoolean("reinf", d.reinforcement),
        showLabels = o.optBoolean("labels", d.showLabels),
    )
}

// ======================= PIANO DELLE PROVE =======================

data class PlannedTrial(val targetId: String, val fieldIds: List<String>)

/**
 * Target bilanciati a blocchi (ognuno compare lo stesso numero di volte), mai lo stesso
 * target due volte di fila tra un blocco e l'altro, posizione del corretto mai uguale
 * per più di 2 prove consecutive. Distrattori presi prima dagli altri target, poi dalla libreria.
 */
fun buildTrialPlan(cfg: SessionConfig, rnd: Random): List<PlannedTrial> {
    val targets = cfg.targetIds.ifEmpty { listOf(SymbolLibrary.all.first().id) }
    val order = mutableListOf<String>()
    while (order.size < cfg.trials) {
        val block = targets.shuffled(rnd).toMutableList()
        if (order.isNotEmpty() && block.size > 1 && block.first() == order.last()) block.add(block.removeAt(0))
        order += block
    }
    val others = SymbolLibrary.all.map { it.id } - targets.toSet()
    val result = mutableListOf<PlannedTrial>()
    var lastPos = -1
    var samePos = 0
    for (t in order.take(cfg.trials)) {
        val pool = ((targets - t).shuffled(rnd) + others.shuffled(rnd)).distinct()
        var field = (pool.take(cfg.fieldSize - 1) + t).shuffled(rnd)
        var tries = 0
        while (field.size > 1 && field.indexOf(t) == lastPos && samePos >= 1 && tries < 10) {
            field = field.shuffled(rnd); tries++
        }
        val pos = field.indexOf(t)
        samePos = if (pos == lastPos) samePos + 1 else 0
        lastPos = pos
        result += PlannedTrial(t, field)
    }
    return result
}

// ======================= DATI =======================

/** @param responseId null = nessuna risposta entro il tempo massimo. */
data class TrialRecord(
    val index: Int,
    val targetId: String,
    val fieldIds: List<String>,
    val responseId: String?,
    val correct: Boolean,
    val prompt: PromptLevel,
    val latencyMs: Long,
    val timestamp: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("i", index); put("target", targetId); put("field", JSONArray(fieldIds))
        put("response", responseId ?: JSONObject.NULL); put("correct", correct)
        put("prompt", prompt.name); put("latency", latencyMs); put("ts", timestamp)
    }
}

fun trialFromJson(o: JSONObject) = TrialRecord(
    index = o.getInt("i"),
    targetId = o.getString("target"),
    fieldIds = o.getJSONArray("field").let { a -> List(a.length()) { a.getString(it) } },
    responseId = if (o.isNull("response")) null else o.getString("response"),
    correct = o.getBoolean("correct"),
    prompt = runCatching { PromptLevel.valueOf(o.getString("prompt")) }.getOrDefault(PromptLevel.INDEPENDENT),
    latencyMs = o.getLong("latency"),
    timestamp = o.getLong("ts"),
)

data class SessionRecord(
    val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val config: SessionConfig,
    val trials: List<TrialRecord>,
) {
    val pctCorrect: Int get() = if (trials.isEmpty()) 0 else trials.count { it.correct } * 100 / trials.size
    val pctIndependent: Int
        get() = if (trials.isEmpty()) 0
        else trials.count { it.correct && it.prompt == PromptLevel.INDEPENDENT } * 100 / trials.size
    val avgLatencySec: Float
        get() = trials.filter { it.responseId != null }.map { it.latencyMs }.let { l ->
            if (l.isEmpty()) 0f else l.average().toFloat() / 1000f
        }

    /** (target, corrette, totali) */
    fun perTarget(): List<Triple<String, Int, Int>> =
        trials.groupBy { it.targetId }.map { (id, l) -> Triple(id, l.count { it.correct }, l.size) }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("start", startedAt); put("end", endedAt)
        put("config", config.toJson())
        put("trials", JSONArray().apply { trials.forEach { put(it.toJson()) } })
    }
}

fun sessionFromJson(o: JSONObject) = SessionRecord(
    id = o.getString("id"),
    startedAt = o.getLong("start"),
    endedAt = o.getLong("end"),
    config = sessionConfigFromJson(o.getJSONObject("config")),
    trials = o.getJSONArray("trials").let { a -> List(a.length()) { trialFromJson(a.getJSONObject(it)) } },
)

/** Archivio locale: un file JSON per sessione nella memoria privata dell'app. */
class SessionRepository(context: Context) {
    private val dir = File(context.filesDir, "aba_sessions").apply { mkdirs() }

    fun save(s: SessionRecord) = File(dir, "${s.id}.json").writeText(s.toJson().toString())

    fun all(): List<SessionRecord> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.extension == "json" }
            .mapNotNull { f -> runCatching { sessionFromJson(JSONObject(f.readText())) }.getOrNull() }
            .sortedByDescending { it.startedAt }

    fun delete(id: String) { File(dir, "$id.json").delete() }
}

private val dateFmt get() = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALY)
fun formatDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(ms))

/** CSV separato da ';' (si apre direttamente in Excel italiano), una riga per prova. */
fun sessionsToCsv(sessions: List<SessionRecord>): String = buildString {
    append('\uFEFF') // BOM UTF-8 per Excel
    appendLine("sessione;data_ora;utente;programma;campo;prova;target;immagini;risposta;esito;prompt;latenza_s")
    for (s in sessions.sortedBy { it.startedAt }) {
        val learner = s.config.learner.replace(';', ',')
        for (t in s.trials) {
            appendLine(
                listOf(
                    s.id, dateFmt.format(Date(t.timestamp)), learner, s.config.program.title,
                    s.config.fieldSize, t.index, t.targetId, t.fieldIds.joinToString("|"),
                    t.responseId ?: "nessuna risposta", if (t.correct) "corretta" else "errata",
                    t.prompt.title, String.format(Locale.ITALY, "%.1f", t.latencyMs / 1000f)
                ).joinToString(";")
            )
        }
    }
}
