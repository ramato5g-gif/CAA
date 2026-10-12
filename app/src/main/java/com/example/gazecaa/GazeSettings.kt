package com.example.gazecaa

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** Impostazioni persistenti (SharedPreferences) osservabili da Compose. */
class GazeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("gaze_settings", Context.MODE_PRIVATE)

    var dwellMs by mutableIntStateOf(prefs.getInt("dwell", GazeConfig.DEFAULT_DWELL_MS))
        private set
    var stability by mutableIntStateOf(prefs.getInt("stability", GazeConfig.DEFAULT_STABILITY))
        private set
    var showCursor by mutableStateOf(prefs.getBoolean("cursor", true))
        private set
    /** Errore stimato dell'ultima calibrazione (% schermo); -1 = mai calibrato. */
    var calibErrorPct by mutableFloatStateOf(prefs.getFloat("calib_err", -1f))
        private set

    fun update(dwell: Int, stab: Int, cursor: Boolean) {
        dwellMs = dwell; stability = stab; showCursor = cursor
        prefs.edit().putInt("dwell", dwell).putInt("stability", stab).putBoolean("cursor", cursor).apply()
    }

    fun loadModel(): GazeModel? = GazeModel.deserialize(prefs.getString("model_v2", null))

    fun saveModel(m: GazeModel, errPct: Float? = null) {
        val e = prefs.edit().putString("model_v2", m.serialize())
        if (errPct != null) { e.putFloat("calib_err", errPct); calibErrorPct = errPct }
        e.apply()
    }

    fun loadAbaConfig(): SessionConfig =
        prefs.getString("aba_config", null)
            ?.let { runCatching { sessionConfigFromJson(JSONObject(it)) }.getOrNull() }
            ?: SessionConfig.DEFAULT

    fun saveAbaConfig(c: SessionConfig) {
        prefs.edit().putString("aba_config", c.toJson().toString()).apply()
    }
}
