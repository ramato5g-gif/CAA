package com.example.gazecaa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/** Tabella CAA 2x2 selezionabile con lo sguardo (o al tocco). */
@Composable
fun AacGrid(
    gazePx: Offset?,
    dwellMs: Int,
    onSelect: (AacSymbol) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    items: List<AacSymbol> = SymbolLibrary.caa,
) {
    val dwell = rememberDwellState(gazePx, dwellMs, enabled) { key ->
        items.firstOrNull { it.id == key }?.let(onSelect)
    }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        for (row in 0 until 2) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (col in 0 until 2) {
                    val item = items[row * 2 + col]
                    GazeCard(
                        emoji = item.emoji,
                        label = item.label,
                        color = item.color,
                        progress = dwell.progressOf(item.id),
                        highlighted = dwell.justSelected == item.id,
                        onClick = { onSelect(item) },
                        modifier = Modifier.weight(1f).fillMaxHeight().dwellTarget(dwell, item.id)
                    )
                }
            }
        }
    }
}
