package com.example.gazecaa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val KEY_SPEAK = "__speak"
private const val KEY_DEL = "__del"
private const val KEY_CLEAR = "__clear"

/**
 * Tabella CAA configurabile. Con [sentenceMode] i simboli si accodano in una frase
 * (barra in alto) che si legge con 🔊; anche i comandi della barra si scelgono con lo sguardo.
 */
@Composable
fun AacBoard(
    pointer: () -> Offset?,
    input: InputParams,
    items: List<AacSymbol>,
    cols: Int,
    sentenceMode: Boolean,
    say: (AacSymbol?, String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val sentence = remember { mutableStateListOf<AacSymbol>() }

    fun choose(key: String) {
        when (key) {
            KEY_SPEAK -> if (sentence.isNotEmpty()) say(null, sentence.joinToString(" ") { it.label })
            KEY_DEL -> if (sentence.isNotEmpty()) sentence.removeAt(sentence.size - 1)
            KEY_CLEAR -> sentence.clear()
            else -> items.firstOrNull { it.id == key }?.let {
                if (sentenceMode) { sentence += it; say(null, it.label) } else say(it, it.sdName)
            }
        }
    }

    val dwell = rememberDwellState(pointer, input, enabled, resetKey = Triple(items, cols, sentenceMode)) { choose(it) }
    val scan = input.select == SelectMode.SCAN
    fun tap(key: String) { if (scan) dwell.pressSwitch() else choose(key) }

    val cell: @Composable (String, String, String?, Color, String?, Modifier) -> Unit =
        { key, emoji, label, color, imagePath, mod -> BoardCell(dwell, key, emoji, label, color, imagePath, { tap(it) }, mod) }

    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (sentenceMode) {
            Row(Modifier.weight(0.45f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.weight(3f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(Color(0xFF263238))
                        .horizontalScroll(rememberScrollState()).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (sentence.isEmpty()) Text("Scegli i simboli per comporre una frase", color = Color(0xFF90A4AE))
                    sentence.forEach { s ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SymbolThumb(s, 48)
                            Text(s.label, fontSize = 14.sp, color = Color.White)
                        }
                    }
                }
                cell(KEY_SPEAK, "🔊", null, Color(0xFF2E7D32), null, Modifier.weight(1f).fillMaxHeight())
                cell(KEY_DEL, "⌫", null, Color(0xFF546E7A), null, Modifier.weight(1f).fillMaxHeight())
                cell(KEY_CLEAR, "✖", null, Color(0xFFC62828), null, Modifier.weight(1f).fillMaxHeight())
            }
        }
        val rows = (items.size + cols - 1) / cols
        for (r in 0 until rows) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (c in 0 until cols) {
                    val item = items.getOrNull(r * cols + c)
                    if (item == null) Spacer(Modifier.weight(1f))
                    else cell(item.id, item.emoji, item.label, item.color, item.imagePath, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun BoardCell(
    dwell: DwellState, key: String, emoji: String, label: String?, color: Color, imagePath: String?,
    onTap: (String) -> Unit, modifier: Modifier,
) {
    GazeCard(
        emoji = emoji, label = label, color = color, imagePath = imagePath,
        progress = { dwell.progressOf(key) },
        hovered = dwell.hovered == key,
        highlighted = dwell.justSelected == key,
        onClick = { onTap(key) },
        modifier = modifier.dwellTarget(dwell, key)
    )
}

/** Griglia di scelta dei simboli con tessera "+ Foto" e cestino sulle foto personali. */
@Composable
fun SymbolPicker(
    symbols: List<AacSymbol>,
    selected: List<String>,
    numbered: Boolean,
    onToggle: (AacSymbol) -> Unit,
    onAddPhoto: () -> Unit,
    onDelete: (AacSymbol) -> Unit,
    modifier: Modifier = Modifier,
    onVoice: ((AacSymbol) -> Unit)? = null,
    hasVoice: (String) -> Boolean = { false },
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "__add") {
            Box(
                Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF37474F)).clickable(onClick = onAddPhoto),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📷", fontSize = 36.sp)
                    Text("+ Foto", fontSize = 14.sp, color = Color.White)
                }
            }
        }
        items(symbols, key = { it.id }) { s ->
            val pos = selected.indexOf(s.id)
            Box(
                Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                    .background(if (pos >= 0) s.color else Color(0xFF263238))
                    .clickable { onToggle(s) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SymbolThumb(s, 48)
                    val prefix = if (pos < 0) "" else if (numbered) "${pos + 1}. " else "✓ "
                    Text(prefix + s.label, fontSize = 14.sp, color = Color.White, maxLines = 1)
                }
                if (onVoice != null) {
                    Text(
                        if (hasVoice(s.id)) "🔊" else "🎙", fontSize = 18.sp,
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp).clickable { onVoice(s) }
                    )
                }
                if (s.imagePath != null) {
                    Text(
                        "🗑", fontSize = 18.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clickable { onDelete(s) }
                    )
                }
            }
        }
    }
}

/** Scelta dei simboli (nell'ordine in cui si toccano) e del numero di colonne. */
@Composable
fun CaaSetupScreen(
    initialIds: List<String>,
    initialCols: Int,
    onAddPhoto: () -> Unit,
    onDelete: (AacSymbol) -> Unit,
    onVoice: (AacSymbol) -> Unit,
    hasVoice: (String) -> Boolean,
    onDone: (List<String>, Int) -> Unit,
) {
    var ids by remember { mutableStateOf(initialIds) }
    var cols by remember { mutableIntStateOf(initialCols) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Modifica tabella", null) {
            Button(enabled = ids.isNotEmpty(), onClick = { onDone(ids.filter { Symbols.byId(it) != null }, cols) }) { Text("✓ Fatto") }
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Colonne:  ")
            ChoiceRow(listOf(2, 3, 4, 5), cols, { "$it" }) { cols = it }
            Text(
                "   ${ids.size} simboli · ${(ids.size + cols - 1) / cols} righe  (con lo sguardo meglio non più di 6-8)",
                color = Color(0xFFB0BEC5)
            )
        }
        SymbolPicker(
            symbols = Symbols.caaAll,
            selected = ids,
            numbered = true,
            onToggle = { s -> ids = if (s.id in ids) ids - s.id else ids + s.id },
            onAddPhoto = onAddPhoto,
            onDelete = { s -> ids = ids - s.id; onDelete(s) },
            modifier = Modifier.fillMaxSize().padding(16.dp),
            onVoice = onVoice,
            hasVoice = hasVoice,
        )
    }
}
