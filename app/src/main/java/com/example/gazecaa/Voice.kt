package com.example.gazecaa

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import java.io.File

/** Voci registrate (es. la voce della mamma o del terapista), una per simbolo. */
class VoiceStore(private val context: Context) {
    private val dir = File(context.filesDir, "voices").apply { mkdirs() }
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null

    private fun file(id: String) = File(dir, "$id.m4a")
    fun has(id: String) = file(id).exists()

    fun startRecording(id: String): Boolean {
        stopPlayback()
        return try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96000)
            r.setOutputFile(file(id).absolutePath)
            r.prepare()
            r.start()
            recorder = r
            true
        } catch (e: Exception) {
            recorder?.release(); recorder = null
            false
        }
    }

    fun stopRecording() {
        val r = recorder ?: return
        runCatching { r.stop() }
        r.release()
        recorder = null
    }

    fun play(id: String): Boolean {
        if (!has(id)) return false
        stopPlayback()
        return try {
            val p = MediaPlayer()
            p.setDataSource(file(id).absolutePath)
            p.setOnCompletionListener { mp -> mp.release(); if (player === mp) player = null }
            p.prepare()
            p.start()
            player = p
            true
        } catch (e: Exception) {
            false
        }
    }

    fun stopPlayback() {
        val p = player ?: return
        player = null
        runCatching { p.stop() }
        runCatching { p.release() }
    }

    fun delete(id: String) { file(id).delete() }
}

@Composable
fun VoiceDialog(symbol: AacSymbol, voices: VoiceStore, onDismiss: () -> Unit) {
    var recording by remember { mutableStateOf(false) }
    var has by remember { mutableStateOf(voices.has(symbol.id)) }
    var error by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { voices.stopRecording(); voices.stopPlayback() } }

    AlertDialog(
        onDismissRequest = { if (!recording) onDismiss() },
        title = { Text("Voce per \"${symbol.label}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SymbolThumb(symbol, 72)
                Text("Registra la frase da sentire al posto della sintesi vocale: nella tabella (es. \"Voglio il succo\") o come istruzione ABA (es. \"Guarda il succo\").")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!recording) {
                        Button(onClick = { error = !voices.startRecording(symbol.id); recording = !error }) { Text("⏺ Registra") }
                    } else {
                        Button(onClick = { voices.stopRecording(); recording = false; has = voices.has(symbol.id) }) { Text("⏹ Ferma") }
                    }
                    OutlinedButton(enabled = has && !recording, onClick = { voices.play(symbol.id) }) { Text("▶ Ascolta") }
                    OutlinedButton(enabled = has && !recording, onClick = { voices.delete(symbol.id); has = false }) { Text("🗑") }
                }
                if (recording) Text("● Registrazione in corso…")
                if (error) Text("Impossibile registrare: controlla il permesso del microfono.")
            }
        },
        confirmButton = { TextButton(enabled = !recording, onClick = onDismiss) { Text("Chiudi") } }
    )
}
