package com.example.gazecaa

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Report PDF (A4) dell'andamento: per genitori, supervisore BCBA, équipe. */
object PdfReport {
    private const val W = 595
    private const val H = 842
    private const val M = 40f

    fun create(ctx: Context, learner: String, program: ProgramType, sessions: List<SessionRecord>): File {
        val list = sessions.sortedBy { it.startedAt }
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var c: Canvas = page.canvas
        var y = 56f

        val title = Paint().apply { textSize = 20f; isFakeBoldText = true; isAntiAlias = true }
        val bold = Paint().apply { textSize = 11f; isFakeBoldText = true; isAntiAlias = true }
        val text = Paint().apply { textSize = 10.5f; isAntiAlias = true }
        val grey = Paint().apply { textSize = 9f; color = 0xFF666666.toInt(); isAntiAlias = true }

        fun newPageIfNeeded(space: Float) {
            if (y + space > H - M) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                c = page.canvas
                y = 56f
            }
        }

        c.drawText("Report ABA — Sguardo CAA", M, y, title); y += 24
        c.drawText("Utente: $learner     Programma: ${program.title}", M, y, text); y += 15
        if (list.isNotEmpty()) {
            c.drawText("Periodo: ${formatDate(list.first().startedAt)} – ${formatDate(list.last().startedAt)}   ·   Sessioni: ${list.size}", M, y, text)
            y += 15
        }
        c.drawText("Generato il ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date())}", M, y, grey); y += 20

        // Grafico per sessione
        c.drawText("% corrette indipendenti (verde) e corrette totali (blu) per sessione", M, y, bold); y += 8
        drawChart(c, M, y, W - 2 * M, 150f, list.map { it.pctIndependent }, list.map { it.pctCorrect })
        y += 175

        // Tabella per target
        c.drawText("Andamento per target", M, y, bold); y += 16
        val cols = floatArrayOf(M, M + 150, M + 230, M + 310, M + 390)
        listOf("Target", "Sessioni", "Ultima %", "Media %", "Stato").forEachIndexed { i, h -> c.drawText(h, cols[i], y, bold) }
        y += 14
        val targetIds = list.flatMap { s -> s.trials.map { it.targetId } }.distinct()
        for (id in targetIds) {
            newPageIfNeeded(16f)
            val vals = list.mapNotNull { it.independentPctFor(id) }
            val lastN = vals.takeLast(GazeConfig.MASTERY_SESSIONS)
            val mastered = lastN.size == GazeConfig.MASTERY_SESSIONS && lastN.all { it >= GazeConfig.MASTERY_PCT }
            val name = Symbols.byId(id)?.label ?: id
            c.drawText(name, cols[0], y, text)
            c.drawText("${vals.size}", cols[1], y, text)
            c.drawText("${vals.lastOrNull() ?: 0}%", cols[2], y, text)
            c.drawText("${if (vals.isEmpty()) 0 else vals.average().toInt()}%", cols[3], y, text)
            c.drawText(if (mastered) "Acquisito" else "In corso", cols[4], y, if (mastered) bold else text)
            y += 14
        }
        y += 8
        newPageIfNeeded(40f)
        c.drawText(
            "Criterio di acquisizione: ≥${GazeConfig.MASTERY_PCT}% di risposte corrette indipendenti in ${GazeConfig.MASTERY_SESSIONS} sessioni consecutive.",
            M, y, grey
        ); y += 22

        // Elenco sessioni con note
        newPageIfNeeded(30f)
        c.drawText("Sessioni", M, y, bold); y += 16
        for (s in list.asReversed()) {
            newPageIfNeeded(30f)
            c.drawText(
                "${formatDate(s.startedAt)}  ·  ${s.trials.size} prove  ·  campo ${s.config.fieldSize}  ·  ${s.config.inputMode.title}  ·  corrette ${s.pctCorrect}%  ·  indip. ${s.pctIndependent}%",
                M, y, text
            ); y += 13
            if (s.notes.isNotBlank()) {
                wrap(s.notes, 105).forEach { line ->
                    newPageIfNeeded(13f)
                    c.drawText("   Note: $line", M, y, grey); y += 12
                }
            }
            y += 4
        }

        doc.finishPage(page)
        val safe = learner.replace(Regex("[^A-Za-z0-9]"), "_").ifBlank { "utente" }
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.ITALY).format(Date())
        val out = File(File(ctx.cacheDir, "export").apply { mkdirs() }, "report_${safe}_$stamp.pdf")
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun drawChart(c: Canvas, x: Float, y: Float, w: Float, h: Float, a: List<Int>, b: List<Int>) {
        val axis = Paint().apply { color = 0xFFCCCCCC.toInt(); strokeWidth = 0.8f }
        val crit = Paint().apply { color = 0xFFE57373.toInt(); strokeWidth = 1f }
        val label = Paint().apply { textSize = 8f; color = 0xFF666666.toInt(); isAntiAlias = true }
        listOf(0, 50, GazeConfig.MASTERY_PCT, 100).forEach { v ->
            val yy = y + h - h * v / 100f
            c.drawLine(x + 22, yy, x + w, yy, if (v == GazeConfig.MASTERY_PCT) crit else axis)
            c.drawText("$v%", x, yy + 3, label)
        }
        fun line(vals: List<Int>, color: Int) {
            if (vals.isEmpty()) return
            val p = Paint().apply { this.color = color; strokeWidth = 2f; isAntiAlias = true }
            val x0 = x + 30
            val ww = w - 36
            val step = if (vals.size > 1) ww / (vals.size - 1) else 0f
            var px = 0f
            var py = 0f
            vals.forEachIndexed { i, v ->
                val cx = if (vals.size > 1) x0 + i * step else x0 + ww / 2
                val cy = y + h - h * v / 100f
                if (i > 0) c.drawLine(px, py, cx, cy, p)
                c.drawCircle(cx, cy, 2.5f, p)
                px = cx; py = cy
            }
        }
        line(b, 0xFF1E88E5.toInt())
        line(a, 0xFF43A047.toInt())
    }

    private fun wrap(s: String, max: Int): List<String> {
        val out = mutableListOf<String>()
        var cur = StringBuilder()
        for (word in s.replace('\n', ' ').split(' ')) {
            if (cur.length + word.length + 1 > max && cur.isNotEmpty()) { out += cur.toString(); cur = StringBuilder() }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(word)
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }
}
