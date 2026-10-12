package com.example.gazecaa

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun PhotoSourceDialog(onGallery: () -> Unit, onCamera: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi una foto") },
        text = { Text("Usa foto reali degli oggetti, delle persone e dei luoghi del bambino: sono più riconoscibili delle icone.") },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCamera) { Text("📷 Scatta") }
                Button(onClick = onGallery) { Text("🖼 Galleria") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSymbolDialog(imagePath: String, onSave: (label: String, phrase: String) -> Unit, onCancel: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    val bmp = rememberSymbolBitmap(imagePath)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Nuovo simbolo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bmp != null) {
                    Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.size(140.dp).clip(RoundedCornerShape(16.dp)))
                }
                OutlinedTextField(
                    value = label, onValueChange = { label = it }, singleLine = true,
                    label = { Text("Nome (es. succo, nonna, parco)") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phrase, onValueChange = { phrase = it }, singleLine = true,
                    label = { Text("Con articolo / frase (es. il succo, Voglio il succo)") }, modifier = Modifier.fillMaxWidth()
                )
                Text("Nelle sessioni ABA l'istruzione sarà \"Guarda ${phrase.ifBlank { label }}\"; nella tabella si pronuncia la frase.")
            }
        },
        confirmButton = {
            TextButton(enabled = label.isNotBlank(), onClick = { onSave(label.trim(), phrase.trim().ifBlank { label.trim() }) }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Annulla") } }
    )
}
