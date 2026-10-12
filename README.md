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
**Puntatore** (Home): Sguardo (calibrazione 9 punti), Testa (premi Ricentra), Touch.
**Conferma** (⚙): Sosta, Ammiccamento lungo, Scansione (le schede si illuminano a turno; si sceglie
toccando lo schermo, con un sensore/switch Bluetooth che invia Spazio o Invio, o con un ammiccamento lungo).
Magnetismo e correzione automatica della deriva a ogni scelta.

**Comunica**: tabella configurabile (✏), simboli con foto personali (📷), modalità "componi frase".

**Sessione ABA**: risposta Touch/Sguardo/Testa; identificazione recettiva o abbinamento; campo 1–4;
prompt visivo/posizionale con sfumatura automatica; correzione dell'errore; rinforzo con suono e
vibrazione; token board con pausa premio; pausa automatica se il volto esce dall'inquadratura.

**Dati**: riepilogo con note del terapista, storico, 📈 andamento per utente/target con criterio
di acquisizione (80% indipendenti in 2 sessioni consecutive, modificabile in GazeConfig), CSV.

**Protezione bambino** (⚙): controlli nascosti, si mostrano tenendo premuto ⚙ nell'angolo.
**📌 Blocca app**: fissaggio schermo, il bambino non può uscire dall'app.

**👤 Profili**: un profilo per bambino (impostazioni, calibrazione, tabella, configurazione ABA).
**🎙 Voci registrate**: su ogni simbolo (tabella o target ABA) si può registrare una voce familiare.
**📏 Test di precisione**: misura l'errore del puntatore in cm e suggerisce quante immagini per riga.
**📄 Report PDF** dall'Andamento; **💾 Backup** completo (.zip) esportabile e ripristinabile.

I dati e le foto restano solo sul tablet. Nel campo "Utente" usa iniziali o un codice.

## Consigli
Tablet su supporto fisso a 40–60 cm, luce frontale diffusa, niente controluce.
Se ruotando la testa il cursore va al contrario rispetto agli occhi: inverti `HEAD_SIGN_X/Y`.

⚠ Prototipo, non dispositivo medico certificato.
