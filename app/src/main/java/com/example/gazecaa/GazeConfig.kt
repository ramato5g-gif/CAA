package com.example.gazecaa

/**
 * ============================================================
 *  PARAMETRI DEL PROTOTIPO
 *  - DEFAULT_*: valori iniziali, poi modificabili dall'app (⚙ Impostazioni)
 *  - gli altri: si cambiano qui e si ricompila
 * ============================================================
 */
object GazeConfig {

    // ---------- Regolabili dall'app ----------
    /** Tempo di sosta (ms) per attivare un'immagine. */
    const val DEFAULT_DWELL_MS = 1500
    /** Stabilità del cursore 1..10 (filtro One Euro): alto = più fermo ma più lento. */
    const val DEFAULT_STABILITY = 5

    // ---------- Filtro One Euro ----------
    /** Quanto il filtro "si apre" durante i movimenti veloci (saccadi). Più alto = meno ritardo. */
    const val FILTER_BETA = 0.4f

    // ---------- Dwell ----------
    /** Isteresi (dp): margine oltre il bordo del pulsante agganciato prima di azzerare il timer. */
    const val HIT_MARGIN_DP = 40f
    /** Pausa (ms) dopo una selezione; poi serve uscire e rientrare per ripetere. */
    const val COOLDOWN_MS = 1200L

    // ---------- Tracciamento ----------
    const val FACE_LOST_TIMEOUT_MS = 600L
    /** Scala dello spostamento verticale dell'iride (movimento piccolo). */
    const val PUPIL_VERTICAL_GAIN = 2.0f
    /** Apertura occhio (altezza/larghezza) sotto cui il frame è un ammiccamento. */
    const val BLINK_RATIO = 0.12f
    /** Regolarizzazione della regressione di calibrazione (più alto = modello più "prudente"). */
    const val RIDGE_LAMBDA = 0.005

    // ---------- Distanza dal tablet (distanza tra gli angoli esterni degli occhi / larghezza frame) ----------
    const val EYE_DIST_MIN = 0.10f   // sotto: troppo lontano (~80 cm)
    const val EYE_DIST_MAX = 0.28f   // sopra: troppo vicino (~30 cm)

    // ---------- Camera / modello ----------
    const val ANALYSIS_WIDTH = 640
    const val ANALYSIS_HEIGHT = 480
    const val MODEL_ASSET = "face_landmarker.task"
    /** GPU più veloce ma non supportata da tutti i tablet: se l'app crasha, lascia false. */
    const val USE_GPU = false

    // ---------- Calibrazione ----------
    const val CALIB_POINT_MS = 1800L
    const val CALIB_SETTLE_MS = 700L
    const val RECENTER_MS = 1500L
}
