package com.example.gazecaa

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

data class Profile(val id: String, val name: String)

/**
 * Profili utente: ogni bambino ha le sue impostazioni, calibrazione, tabella CAA
 * e configurazione ABA. Il profilo "default" usa le impostazioni delle versioni precedenti.
 */
class ProfileStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    fun all(): List<Profile> {
        val list = runCatching {
            val a = JSONArray(prefs.getString("list", "[]"))
            List(a.length()) { i -> a.getJSONObject(i).let { Profile(it.getString("id"), it.getString("name")) } }
        }.getOrDefault(emptyList())
        return list.ifEmpty { listOf(Profile("default", "Utente 1")) }
    }

    var currentId: String
        get() = prefs.getString("current", "default") ?: "default"
        set(v) { prefs.edit().putString("current", v).commit() }

    fun current(): Profile = all().let { l -> l.firstOrNull { it.id == currentId } ?: l.first() }

    fun add(name: String): Profile {
        val p = Profile("p${System.currentTimeMillis()}", name)
        save(all() + p)
        return p
    }

    fun rename(id: String, name: String) = save(all().map { if (it.id == id) it.copy(name = name) else it })

    fun delete(id: String) {
        val rest = all().filterNot { it.id == id }
        if (rest.isEmpty()) return
        save(rest)
        context.deleteSharedPreferences(prefsNameFor(id))
    }

    private fun save(list: List<Profile>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name)) }
        prefs.edit().putString("list", a.toString()).commit()
    }

    companion object {
        fun prefsNameFor(id: String) = if (id == "default") "gaze_settings" else "gaze_settings_$id"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDialog(store: ProfileStore, onSelect: (Profile) -> Unit, onDismiss: () -> Unit) {
    var profiles by remember { mutableStateOf(store.all()) }
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Profile?>(null) }
    var renameText by remember { mutableStateOf("") }
    val currentId = store.current().id

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profili utente") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ogni profilo ha impostazioni, calibrazione, tabella e sessioni proprie. Usa iniziali o un codice.")
                profiles.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (p.id == currentId) Color(0xFF1B5E20) else Color(0xFF263238)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(p.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (p.id != currentId) Button(onClick = { onSelect(p) }) { Text("Usa") }
                        OutlinedButton(onClick = { renaming = p; renameText = p.name }) { Text("✏") }
                        if (p.id != currentId && profiles.size > 1) {
                            OutlinedButton(onClick = { store.delete(p.id); profiles = store.all() }) { Text("🗑") }
                        }
                    }
                }
                renaming?.let { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(renameText, { renameText = it }, singleLine = true, label = { Text("Nuovo nome") }, modifier = Modifier.weight(1f))
                        TextButton(enabled = renameText.isNotBlank(), onClick = {
                            store.rename(r.id, renameText.trim()); profiles = store.all(); renaming = null
                        }) { Text("OK") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("Nuovo profilo") }, modifier = Modifier.weight(1f))
                    TextButton(enabled = newName.isNotBlank(), onClick = {
                        store.add(newName.trim()); profiles = store.all(); newName = ""
                    }) { Text("Aggiungi") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )
}
