package com.example.gazecaa

/**
 * ============================================================
 *  PARAMETRI DEL PROTOTIPO
 *  - DEFAULT_*: valori iniziali, poi modificabili dall'app (⚙ Impostazioni)
 *  - gli altri: si cambiano qui e si ricompila
 * ============================================================
 */
object GazeConfig {

    // ---------- Regolabili dall'app (questi sono i valori di partenza) ----------
    /** Tempo di sosta (ms) per attivare un pulsante. */
    const val DEFAULT_DWELL_MS = 1500
    /** Finestra media mobile (frame). Più alto = cursore più stabile ma più lento. */
    const val DEFAULT_SMOOTHING = 8
    /** Peso della testa nel segnale: 0 = solo occhi, 1 = molto head-tracking. */
    const val DEFAULT_HEAD_WEIGHT = 0.5f

    // ---------- Dwell ----------
    /** Isteresi (dp): margine oltre il bordo del pulsante agganciato prima di azzerare il timer. */
    const val HIT_MARGIN_DP = 40f
    /** Pausa (ms) dopo una selezione; poi serve uscire e rientrare per ripetere. */
    const val COOLDOWN_MS = 1200L

    // ---------- Tracciamento ----------
    /** Volto assente per più di X ms → cursore nascosto (gli ammiccamenti brevi sono ignorati). */
    const val FACE_LOST_TIMEOUT_MS = 600L
    /** Peso della posizione dell'iride. */
    const val PUPIL_WEIGHT = 1.0f
    /** Fattore di scala verticale dell'iride (il movimento verticale dell'occhio è piccolo). */
    const val PUPIL_VERTICAL_GAIN = 2.0f
    /** Inverti (1f ↔ -1f) se ruotando la TESTA il cursore va al contrario rispetto agli OCCHI. */
    const val HEAD_SIGN_X = 1f
    const val HEAD_SIGN_Y = 1f
    /** Distanza verticale tipica naso-occhi (in "distanze interoculari"), sottratta per centrare l'asse Y. */
    const val NOSE_BASELINE_Y = 0.6f
    /** Apertura occhio (altezza/larghezza) sotto cui il frame è un ammiccamento. */
    const val BLINK_RATIO = 0.12f

    /** Sensibilità SOLO senza calibrazione: schermo = 0.5 + raw * GAIN. */
    const val DEFAULT_GAIN_X = 3.0f
    const val DEFAULT_GAIN_Y = 3.0f

    // ---------- Camera / modello ----------
    const val ANALYSIS_WIDTH = 640
    const val ANALYSIS_HEIGHT = 480
    const val MODEL_ASSET = "face_landmarker.task"
    /** GPU più veloce ma non supportata da tutti i tablet: se l'app crasha, lascia false. */
    const val USE_GPU = false

    // ---------- Calibrazione ----------
    const val CALIB_POINT_MS = 2200L
    const val CALIB_SETTLE_MS = 800L
}
