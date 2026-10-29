# App Android del telecomando

Questo repository pubblica la pagina del telefono (`index.html`, su GitHub Pages) e compila anche l'**app Android** del
telecomando. Ogni volta che cambia `index.html` o la cartella `app-android/`, GitHub compila l'app e la mette nelle
**Releases**. L'ultima versione si scarica sempre da:

    https://github.com/<utente>/<repository>/releases/latest/download/telecomando-daggerheart.apk

Nello schermo del GM, riquadro Telecomandi: **📲 Scarica l'app** mostra il QR di questo indirizzo; il **codice del tavolo**
(predefinito `DAGGERHEARTJOJO`, si cambia con ✏️) è quello che i giocatori scrivono nell'app per collegarsi. In alternativa
c'è ancora **▦ QR del tavolo**. Poi ognuno sceglie il suo personaggio scrivendo il **codice del personaggio**:
A.R.C.H.I.E. `0001`, Rūamoko Mycena `0200`, Tzafriel `0030`, Conraua Anuri `4000` (pulsante 🔢 accanto a ogni PG per cambiarlo;
cambiandolo, il telefono che aveva quel PG deve scrivere il codice nuovo).

## Cosa fa in più l'app rispetto alla pagina
- resta collegata al tavolo anche con il telefono in tasca (notifica fissa "… è al tavolo", con **Lascia il tavolo**);
- i messaggi privati del GM arrivano come **notifiche** quando l'app non è davanti;
- vibrazione, schermo sempre acceso, icona tra le app;
- ricorda il tavolo e il personaggio: basta toccare l'icona. Dal menu ☰: **Cambia personaggio**, **Cambia tavolo** e **Resta collegato in tasca**
  (toglie il risparmio batteria per l'app, così Android non la addormenta);
- senza rete apre la copia della pagina contenuta nell'app.

## Installazione (giocatori)
1. Inquadra il QR **📲 Scarica l'app** del GM e scarica `telecomando-daggerheart.apk`.
2. Aprilo; la prima volta Android chiede di consentire "Installa app sconosciute" al browser: consenti e installa.
3. Apri l'app, scrivi il **codice del tavolo** che ti dice il GM (maiuscole e spazi non contano) e tocca **Entra al tavolo**
   (oppure inquadra il QR del tavolo), poi tocca il tuo personaggio e scrivi il suo codice.
4. Consenti le notifiche quando l'app lo chiede.

Nell'app: **Gioco** (PF, Stress, Speranza, Armatura, abilità, joystick), **Scheda** (✏️ Modifica: Evasione, Competenza,
soglie e massimi), **Carte** (equipaggiamento e Volta: usa, sposta, modifica, crea, togli), **Zaino** (oro e oggetti),
**Mappa**. In **Scheda** si riscrivono anche classe e livello, attacchi e tratti.

**Senza collegamento** (il GM non ha attivato i telecomandi, oppure il telefono non ha rete) l'app funziona lo stesso per la
scheda: PF, Stress, Speranza, Armatura, massimi, testi, carte, zaino e oro si cambiano e restano salvati sul telefono, anche
chiudendo l'app; arrivano al GM appena si collega. Joystick, abilità e riflettore invece aspettano il GM. Serve aver ricevuto
la scheda almeno una volta (la prima volta il GM deve aver attivato i telecomandi).

Gli aggiornamenti si installano sopra la versione vecchia (stessa procedura), se l'app è firmata con la chiave del GM (sotto).

## Prima configurazione (GM, una volta sola)
1. Carica in questo repository: `index.html`, `.nojekyll`, `APP-ANDROID.md`, la cartella `app-android/` e il file
   `.github/workflows/app-android.yml` (dal sito di GitHub: **Add file → Create new file**, scrivi come nome
   `.github/workflows/app-android.yml` e incolla il contenuto).
2. **Settings → Secrets and variables → Actions → New repository secret**, due volte:
   - `DH_KEYSTORE_B64`: il contenuto del file `keystore_b64.txt` (la chiave di firma dell'app);
   - `DH_KEYSTORE_PASSWORD`: la password della chiave.
   Senza questi due segreti l'app si compila lo stesso, ma con una chiave di prova che cambia ogni volta: per aggiornare
   bisognerebbe disinstallare la versione vecchia. **Conserva la chiave**: se la perdi, i telefoni dovranno reinstallare l'app.
3. **Actions → App Android → Run workflow** (oppure basta caricare un `index.html` nuovo). Dopo qualche minuto l'app è nelle Releases.

## Note
- Dal codice del tavolo nascono la stanza sul broker e la chiave che firma i comandi (PBKDF2): il codice non passa mai in rete,
  ma chi lo conosce può entrare al tavolo. Se finisce in mani sbagliate, cambialo (✏️ o ♻ Nuovo tavolo).
- I codici dei personaggi non passano in rete: il GM pubblica solo un'impronta, che il telefono controlla anche senza
  collegamento, e il GM ricontrolla quando il telefono prende il PG. Con 4 cifre è una serratura contro sbagli e dispetti,
  non una cassaforte.
- L'app accetta solo il link del tavolo `https://…#t=…&k=…` e apre nel browser ogni altro sito.
- Il link `https://legeo996.github.io/Daggerheart-telecomando/…` è dichiarato nell'app: in Android, *Impostazioni → App →
  Telecomando DH → Apri per impostazione predefinita → Link supportati* fa aprire quei link direttamente nell'app.
  Se pubblichi la pagina a un altro indirizzo, cambialo in `app-android/app/src/main/AndroidManifest.xml`.
- Dal 2027 Android chiederà che anche le app installate fuori dallo store siano registrate da uno sviluppatore verificato.
  Per un gruppo di amici basta l'account gratuito "a distribuzione limitata" della Android Developer Console (fino a 20 dispositivi).
