package com.example.gazecaa

/** Come l'utente muove il puntatore. */
enum class InputMode(val title: String) {
    EYES("Sguardo"),   // iride + testa, richiede calibrazione a 9 punti
    HEAD("Testa"),     // solo movimento della testa: più stabile e affidabile
    TOUCH("Touch"),    // nessuna fotocamera, si tocca lo schermo
}

/** Come si conferma la scelta. */
enum class SelectMode(val title: String) {
    DWELL("Sosta"),              // si resta sull'immagine per il tempo di sosta
    BLINK("Ammiccamento lungo"), // si chiudono gli occhi ~1 s sull'immagine
    SCAN("Scansione"),           // le immagini si illuminano a turno: tocco, sensore o ammiccamento per scegliere
}

enum class CursorSize(val title: String, val radiusDp: Float) {
    HIDDEN("Nascosto", 0f), SMALL("Piccolo", 10f), MEDIUM("Medio", 18f), LARGE("Grande", 30f),
}

/**
 * ============================================================
 *  PARAMETRI DEL PROTOTIPO
 *  - DEFAULT_*: valori iniziali, poi modificabili dall'app (⚙ Impostazioni)
 *  - gli altri: si cambiano qui e si ricompila
 * ============================================================
 */
object GazeConfig {

    // ---------- Regolabili dall'app ----------
    const val DEFAULT_DWELL_MS = 1200
    /** Stabilità 1..10: alto = puntatore più fermo, ma più "in ritardo". */
    const val DEFAULT_STABILITY = 4
    /** Sensibilità modalità Testa 1..10: alto = basta ruotare meno la testa. */
    const val DEFAULT_HEAD_SENS = 5

    // ---------- Fluidità ----------
    /** Filtro One Euro: quanto si "apre" nei movimenti veloci (alto = meno ritardo). */
    const val FILTER_BETA = 1.0f
    /** Interpolazione a 60 fps tra un frame della fotocamera e l'altro (s). Basso = più reattivo. */
    const val POINTER_TAU_S = 0.06f

    // ---------- Selezione ----------
    /** Isteresi (dp) attorno all'immagine già agganciata. */
    const val HIT_MARGIN_DP = 40f
    /** Magnetismo: il puntatore "aggancia" l'immagine più vicina entro questa distanza (dp). */
    const val MAGNET_MAX_DP = 180f
    /** Se il puntatore esce dall'immagine il progresso non si azzera: cala in questo tempo (ms). */
    const val DWELL_DECAY_MS = 700f
    /** Pausa dopo una selezione (ms); per ripetere bisogna uscire e rientrare. */
    const val COOLDOWN_MS = 1200L
    /** Ammiccamento volontario: occhi chiusi tra MIN e MAX ms (quelli naturali durano ~0,1-0,3 s). */
    const val BLINK_SELECT_MIN_MS = 500L
    const val BLINK_SELECT_MAX_MS = 2000L

    /** Correzione automatica della deriva: a ogni scelta il puntatore si riallinea di questa frazione. */
    const val DRIFT_ALPHA = 0.25f
    /** ...solo se il puntatore era entro questa distanza (frazione schermo) dal centro dell'immagine. */
    const val DRIFT_MAX_DIST = 0.25f

    // ---------- ABA ----------
    /** Criterio di acquisizione: % risposte corrette indipendenti... */
    const val MASTERY_PCT = 80
    /** ...in questo numero di sessioni consecutive. */
    const val MASTERY_SESSIONS = 2
    /** Riduzione automatica del prompt dopo N risposte corrette di fila. */
    const val FADE_AFTER_CORRECT = 2
    /** Controlli del terapista nascosti: restano visibili per X ms dopo la pressione lunga. */
    const val REVEAL_MS = 8000L

    // ---------- Tracciamento ----------
    const val FACE_LOST_TIMEOUT_MS = 600L
    const val PUPIL_VERTICAL_GAIN = 2.0f
    const val BLINK_RATIO = 0.12f
    const val RIDGE_LAMBDA = 0.005
    /** Modalità Testa: il movimento verticale della testa è minore di quello orizzontale. */
    const val HEAD_GAIN_Y_FACTOR = 1.4f

    // ---------- Distanza dal tablet ----------
    const val EYE_DIST_MIN = 0.10f   // sotto: troppo lontano (~80 cm)
    const val EYE_DIST_MAX = 0.28f   // sopra: troppo vicino (~30 cm)

    // ---------- Camera / modello ----------
    const val ANALYSIS_WIDTH = 640
    const val ANALYSIS_HEIGHT = 480
    const val MODEL_ASSET = "face_landmarker.task"
    /** Prova prima la GPU (molto più veloce); se il tablet non la supporta passa alla CPU. */
    const val TRY_GPU = true

    // ---------- Calibrazione ----------
    const val CALIB_POINT_MS = 1800L
    const val CALIB_SETTLE_MS = 700L
    const val RECENTER_MS = 1500L
}
