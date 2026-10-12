# Sguardo CAA — prototipo

Comunicazione Aumentativa Alternativa con lo sguardo (Android, Kotlin + Compose).
Tracking: MediaPipe Face Landmarker (iridi) + posa della testa, media mobile, dwell time, TTS italiano.

## Ottenere l'APK senza Android Studio (GitHub Actions)
1. Su GitHub crea un repository nuovo (es. `sguardo-caa`).
2. Carica TUTTO il contenuto di questa cartella, inclusa la cartella nascosta `.github`.
   Se non riesci a caricare `.github`: tab **Actions → set up a workflow yourself**,
   chiamalo `build-apk.yml` e incolla il contenuto di `.github/workflows/build-apk.yml`.
3. Tab **Actions**: la build parte da sola (5–10 min). Deve diventare verde.
4. Dal tablet apri il repository → **Releases → latest → SguardoCAA.apk** → installa
   (consenti "installa app sconosciute" al browser).

Ogni modifica caricata genera un nuovo APK nella stessa release. La firma è fissa
(`app/debug.keystore`), quindi gli aggiornamenti si installano sopra la versione precedente.

## Build locale (Android Studio)
Scarica il modello e mettilo in `app/src/main/assets/face_landmarker.task`:
https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task
Poi apri la cartella in Android Studio e premi Run.

## Uso
- Al primo avvio parte la **calibrazione**: l'utente fissa i 4 punti gialli.
- **⚙ Impostazioni**: tempo di sosta, stabilità del cursore, uso della testa (salvati).
- I pulsanti funzionano anche al tocco (per il caregiver).
- Parametri avanzati: `app/src/main/java/com/example/gazecaa/GazeConfig.kt`.

## Consigli
Tablet su supporto fisso a 40–60 cm, luce frontale diffusa, niente controluce.
Se ruotando la testa il cursore va al contrario rispetto agli occhi: inverti `HEAD_SIGN_X/Y`.

⚠ Prototipo, non dispositivo medico certificato.
