package com.example.gazecaa

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Impostazioni persistenti (SharedPreferences) osservabili da Compose. */
class GazeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("gaze_settings", Context.MODE_PRIVATE)

    var dwellMs by mutableIntStateOf(prefs.getInt("dwell", GazeConfig.DEFAULT_DWELL_MS))
        private set
    var smoothing by mutableIntStateOf(prefs.getInt("smoothing", GazeConfig.DEFAULT_SMOOTHING))
        private set
    var headWeight by mutableFloatStateOf(prefs.getFloat("head", GazeConfig.DEFAULT_HEAD_WEIGHT))
        private set

    fun update(dwell: Int, smooth: Int, head: Float) {
        dwellMs = dwell; smoothing = smooth; headWeight = head
        prefs.edit().putInt("dwell", dwell).putInt("smoothing", smooth).putFloat("head", head).apply()
    }

    fun loadCalibration(): Calibration? =
        if (!prefs.contains("cal_l")) null
        else Calibration(
            prefs.getFloat("cal_l", 0f), prefs.getFloat("cal_r", 0f),
            prefs.getFloat("cal_t", 0f), prefs.getFloat("cal_b", 0f)
        )

    fun saveCalibration(c: Calibration) {
        prefs.edit()
            .putFloat("cal_l", c.rawAtLeft).putFloat("cal_r", c.rawAtRight)
            .putFloat("cal_t", c.rawAtTop).putFloat("cal_b", c.rawAtBottom)
            .apply()
    }
}
