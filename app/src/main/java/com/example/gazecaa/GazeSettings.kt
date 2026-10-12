package com.example.gazecaa

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** Impostazioni persistenti (SharedPreferences) osservabili da Compose. */
/** Impostazioni del profilo [profileId]. */
class GazeSettings(context: Context, profileId: String) {
    private val prefs = context.getSharedPreferences(ProfileStore.prefsNameFor(profileId), Context.MODE_PRIVATE)

    var inputMode by mutableStateOf(enumOr(prefs.getString("input", null), InputMode.EYES))
        private set
    var selectMode by mutableStateOf(enumOr(prefs.getString("select", null), SelectMode.DWELL))
        private set
    var dwellMs by mutableIntStateOf(prefs.getInt("dwell", GazeConfig.DEFAULT_DWELL_MS))
        private set
    var stability by mutableIntStateOf(prefs.getInt("stability2", GazeConfig.DEFAULT_STABILITY))
        private set
    var headSens by mutableIntStateOf(prefs.getInt("head_sens", GazeConfig.DEFAULT_HEAD_SENS))
        private set
    var cursorSize by mutableStateOf(enumOr(prefs.getString("cursor_size", null), CursorSize.MEDIUM))
        private set
    var magnet by mutableStateOf(prefs.getBoolean("magnet", true))
        private set
    var caaIds by mutableStateOf(prefs.getString("caa_ids", null)?.split(",")?.filter { it.isNotBlank() } ?: CaaLibrary.DEFAULT_IDS)
        private set
    var caaCols by mutableIntStateOf(prefs.getInt("caa_cols", 3))
        private set
    var autoDrift by mutableStateOf(prefs.getBoolean("auto_drift", true))
        private set
    var childLock by mutableStateOf(prefs.getBoolean("child_lock", false))
        private set
    var caaSentence by mutableStateOf(prefs.getBoolean("caa_sentence", false))
        private set
    /** Errore stimato dell'ultima calibrazione (% schermo); -1 = mai calibrato. */
    var calibErrorPct by mutableFloatStateOf(prefs.getFloat("calib_err", -1f))
        private set

    fun setInput(m: InputMode) { inputMode = m; prefs.edit().putString("input", m.name).apply() }

    fun update(select: SelectMode, dwell: Int, stab: Int, head: Int, cursor: CursorSize, mag: Boolean) {
        selectMode = select; dwellMs = dwell; stability = stab; headSens = head; cursorSize = cursor; magnet = mag
        prefs.edit()
            .putString("select", select.name).putInt("dwell", dwell).putInt("stability2", stab)
            .putInt("head_sens", head).putString("cursor_size", cursor.name).putBoolean("magnet", mag)
            .apply()
    }

    fun setFlags(drift: Boolean, lock: Boolean, sentence: Boolean) {
        autoDrift = drift; childLock = lock; caaSentence = sentence
        prefs.edit().putBoolean("auto_drift", drift).putBoolean("child_lock", lock).putBoolean("caa_sentence", sentence).apply()
    }

    fun setCaa(ids: List<String>, cols: Int) {
        caaIds = ids; caaCols = cols
        prefs.edit().putString("caa_ids", ids.joinToString(",")).putInt("caa_cols", cols).apply()
    }

    fun loadModel(): GazeModel? = GazeModel.deserialize(prefs.getString("model_v2", null))

    fun saveModel(m: GazeModel, errPct: Float? = null) {
        val e = prefs.edit().putString("model_v2", m.serialize())
        if (errPct != null) { e.putFloat("calib_err", errPct); calibErrorPct = errPct }
        e.apply()
    }

    fun loadNeutral(): HeadNeutral = HeadNeutral.deserialize(prefs.getString("head_neutral", null)) ?: HeadNeutral.DEFAULT
    fun saveNeutral(n: HeadNeutral) { prefs.edit().putString("head_neutral", n.serialize()).apply() }

    fun loadAbaConfig(): SessionConfig =
        prefs.getString("aba_config", null)
            ?.let { runCatching { sessionConfigFromJson(JSONObject(it)) }.getOrNull() }
            ?: SessionConfig.DEFAULT

    fun saveAbaConfig(c: SessionConfig) { prefs.edit().putString("aba_config", c.toJson().toString()).apply() }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, def: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: def
}
