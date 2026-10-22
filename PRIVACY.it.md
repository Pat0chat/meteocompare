# Informativa sulla privacy — MeteoCompare

*Ultimo aggiornamento: 22 ottobre 2026*

**Langues / Languages / Sprachen / Idiomas / Lingue:** [Français](PRIVACY.md) · [English](PRIVACY.en.md) · [Deutsch](PRIVACY.de.md) · [Español](PRIVACY.es.md) · [Italiano](PRIVACY.it.md)

## In breve

**MeteoCompare non utilizza i tuoi dati per la profilazione, la pubblicità o l'analisi dell'utilizzo.** L'app non contiene SDK pubblicitari, strumenti di analisi, sistemi di segnalazione remota degli arresti anomali né account.

Preferiti, preferenze e cache dell'app vengono conservati localmente. Alcune informazioni necessarie alle funzioni meteo lasciano tuttavia il dispositivo: le ricerche delle città vengono inviate al servizio di geocodifica Open-Meteo e le coordinate dei luoghi consultati alle API meteo Open-Meteo. Per le allerte meteorologiche francesi può essere inviato al Worker pubblico MeteoCompare un codice di dipartimento. Quando apri esplicitamente il radar pioggia, le coordinate della località vengono utilizzate anche per caricare le immagini di RainViewer e le tessere cartografiche di OpenStreetMap.

---

## 1. Dati trattati da MeteoCompare

MeteoCompare non crea un archivio di utenti e non utilizza dati per profilazione, analisi del pubblico, pubblicità o tracciamento tra applicazioni.

In particolare:

- ❌ Nessun Google Analytics, Firebase Analytics o SDK equivalente
- ❌ Nessun Crashlytics o altro servizio di segnalazione remota dei crash
- ❌ Nessuna pubblicità né rete pubblicitaria
- ❌ Nessun identificatore pubblicitario (`AD_ID`)
- ❌ Nessun account MeteoCompare
- ❌ Nessun cookie di tracciamento o WebView usata per tracciare
- ❌ Nessuna identificazione intenzionale del dispositivo tramite fingerprinting
- ❌ Nessun tracciamento tra applicazioni

Le uniche trasmissioni di rete effettuate dall'app sono quelle necessarie alle funzioni descritte nelle sezioni 2, 3 e 4.

## 2. Open-Meteo

MeteoCompare utilizza le API pubbliche di [Open-Meteo](https://open-meteo.com/) per cercare località, ottenere previsioni meteo, alcuni dati storici e dati marini.

### Informazioni inviate a Open-Meteo

A seconda dell'azione compiuta nell'app:

- **Ricerca di una città:** il testo digitato viene inviato all'API di geocodifica Open-Meteo.
- **Caricamento o aggiornamento di una località:** le coordinate del luogo consultato (latitudine e longitudine), le variabili meteorologiche richieste, i modelli selezionati e i parametri tecnici necessari vengono inviati alle API Open-Meteo.
- **Caricamenti automatici o widget:** le coordinate di una città preferita possono essere riutilizzate per aggiornarne i dati meteo senza dover reinserire ogni volta la città.

Come per ogni connessione Internet, Open-Meteo riceve anche l'indirizzo IP di origine e le informazioni tecniche HTTP necessarie a instradare la richiesta.

MeteoCompare non invia a Open-Meteo il tuo account Google, identificatore pubblicitario, identificatore Android specifico dell'app, nome, indirizzo e-mail o elenco completo dei preferiti.

### Conservazione da parte di Open-Meteo

L'attuale informativa di Open-Meteo indica che il servizio API gratuito può conservare i **log del server per 90 giorni** per diagnosi, manutenzione e prevenzione degli abusi. Tali log possono contenere informazioni sensibili presenti nelle richieste, come le **coordinate geografiche**. Open-Meteo dichiara che i log vengono eliminati dopo 90 giorni e non sono condivisi con terzi.

Questa conservazione è effettuata da Open-Meteo secondo i propri termini e la propria informativa sulla privacy: [open-meteo.com/en/terms](https://open-meteo.com/en/terms).

I dati ottenuti tramite Open-Meteo sono forniti con licenza **CC BY 4.0**. MeteoCompare li aggrega, confronta e trasforma prima di mostrarli. Attribuzione e licenza: [open-meteo.com/en/license](https://open-meteo.com/en/license).

## 3. Allerte Météo-France tramite il Worker MeteoCompare

Per le città francesi, l'app può interrogare il Worker pubblico `https://meteocompare.app/_mcx/vigilance` per mostrare le allerte ufficiali (Vigilance) di Météo-France.

L'app invia soltanto i seguenti parametri funzionali:

- il **codice del dipartimento** (ad esempio `91`);
- `coast=1` quando la località è identificata come costiera, per includere le allerte litoranee.

Il Worker gestisce sul server l'autenticazione di Météo-France e la cache. Nell'app non sono inclusi identificativi, password o token di Météo-France.

Come per ogni richiesta HTTPS, l'infrastruttura di rete che ospita o protegge il Worker riceve l'indirizzo IP di origine e i parametri della richiesta. MeteoCompare non usa intenzionalmente tali informazioni per profilare, tracciare o identificare gli utenti.

Le città fuori dalla Francia non generano chiamate a Vigilance. Quando rimuovi una città dai preferiti, il relativo stato delle allerte viene eliminato dalla memoria e la cache persistente corrispondente viene cancellata secondo il normale funzionamento dell'app.

## 4. Radar pioggia: RainViewer e OpenStreetMap

La vista radar è facoltativa e contatta questi servizi solo dopo che l'hai aperta esplicitamente dalla scheda di una città.

- **RainViewer** fornisce metadati e immagini delle osservazioni radar. Le coordinate (latitudine e longitudine) della località consultata sono incluse nella richiesta dell'immagine per centrare la mappa radar.
- **OpenStreetMap** fornisce soltanto le tessere della mappa di sfondo necessarie all'area visualizzata. Le tessere già consultate sono conservate in una cache HTTP locale per evitare di scaricarle inutilmente a ogni apertura. Questa cache rispetta le direttive HTTP del server e può essere svuotata da Android.

Come per ogni richiesta HTTPS, questi servizi ricevono anche l'indirizzo IP di origine e le informazioni tecniche HTTP necessarie alla trasmissione. MeteoCompare non invia loro account, identificatori pubblicitari o elenchi dei preferiti.

Le proiezioni a +15, +30, +45 e +60 minuti sono calcolate **localmente sul dispositivo** a partire dalle ultime immagini RainViewer: segmentazione delle aree piovose, stima del loro spostamento ed estrapolazione con incertezza crescente. Nessun dato futuro aggiuntivo viene richiesto a RainViewer per produrre questa previsione a brevissimo termine.

## 5. Archiviazione locale sul dispositivo

MeteoCompare conserva localmente i seguenti dati:

| Dati | Archiviazione locale | Finalità | Trasmissione in rete |
|---|---|---|---|
| Città preferite e relative coordinate | DataStore Preferences | Ripristinare la selezione delle località | Le coordinate della località vengono inviate a Open-Meteo quando si carica una previsione |
| Modelli meteorologici attivati | DataStore Preferences | Conservare la configurazione | I modelli richiesti fanno parte dei parametri delle richieste meteo |
| Tema, frequenza di aggiornamento, visualizzazione dei dettagli e altre preferenze | DataStore Preferences | Personalizzare l'app | No, salvo quando una preferenza modifica direttamente i parametri di una richiesta meteo |
| Lingua dell'app | SharedPreferences interne | Scegliere la lingua dell'interfaccia e delle ricerche | Il codice lingua può essere inviato alla geocodifica per localizzare i risultati |
| Cache delle previsioni e cronologia dei calcoli | Room SQLite | Avvio rapido, modalità offline, confronti | Il contenuto della cache non viene caricato come tale |
| Cache Vigilance | DataStore Preferences | Ridurre le richieste di rete e fornire un ripiego temporaneo | Non come cache; possono essere effettuate nuove richieste al Worker |
| Cache HTTP radar/mappe | `cacheDir` Android (OkHttp, massimo 64 MiB) | Riutilizzare risposte HTTP e tessere OSM secondo le intestazioni di cache | Non come cache; solo le richieste mancanti o scadute sono inviate a RainViewer / OpenStreetMap |
| Preferenze dei widget | Glance DataStore | Conservare città, colori e opzioni del widget | La città configurata può attivare le stesse richieste meteo dell'app |

«Archiviato localmente» significa che MeteoCompare non sincronizza questi database o file con un server MeteoCompare. Non significa che le informazioni contenute non siano mai utilizzate in una richiesta di rete: ad esempio, le coordinate di una località preferita devono essere inviate al servizio meteo per ottenerne le previsioni.

### Backup Android

Il backup automatico di Android è attivato in modo **selettivo**. Le regole di backup di MeteoCompare autorizzano soltanto:

- le preferenze generali dell'app;
- l'elenco delle città preferite;
- la preferenza della lingua.

Android può eseguire il backup o trasferire questi elementi sul tuo account o dispositivo in base alle impostazioni del sistema. MeteoCompare non ha accesso a tali backup.

I database Room della cache meteo, la cache HTTP in `cacheDir` e gli altri file non espressamente inclusi nelle regole di backup non fanno parte di questo backup selettivo dell'app.

La disinstallazione elimina i dati locali dell'app presenti sul dispositivo. Un backup Android già creato rimane gestito da Android e dalle impostazioni del tuo account.

## 6. Autorizzazioni Android

| Autorizzazione | Finalità |
|---|---|
| `INTERNET` | Richieste HTTPS a Open-Meteo, al Worker MeteoCompare, a RainViewer e OpenStreetMap |
| `ACCESS_NETWORK_STATE` | Rilevare l'assenza di rete e adattare interfaccia / richieste |
| `RECEIVE_BOOT_COMPLETED` | Ripianificare correttamente gli aggiornamenti periodici dei widget dopo il riavvio o l'aggiornamento dell'app |
| `POST_NOTIFICATIONS` | Mostrare notifiche meteo locali (riepilogo giornaliero, divergenza tra modelli, modifiche delle previsioni) solo se le attivi nelle Impostazioni. Sono calcolate sul dispositivo: nessun servizio push, identificatore o dato aggiuntivo viene trasmesso |
| `ACCESS_COARSE_LOCATION` | Funzione facoltativa «La mia posizione»: provider di rete/combinato con consenso esplicito |
| `ACCESS_FINE_LOCATION` | Autorizzazione facoltativa: se concessa, acquisizione GPS puntuale (in particolare per le posizioni simulate nell'emulatore). Mai utilizzata in background; la posizione esatta non viene memorizzata |

MeteoCompare **non richiede**:

- la posizione in background (`ACCESS_BACKGROUND_LOCATION`); la posizione precisa (`ACCESS_FINE_LOCATION`) è facoltativa e viene consultata solo dopo il consenso in primo piano;
- l'accesso a contatti, foto o file esterni;
- l'accesso al telefono o agli SMS;
- Bluetooth o NFC;
- l'identificatore pubblicitario.

Per impostazione predefinita, **scegli tu la città**. La funzione facoltativa «La mia posizione» ottiene una posizione approssimativa solo dopo il consenso, mentre un'Activity è visibile; consulta la sezione dedicata più avanti.

## 7. Sicurezza delle trasmissioni

I servizi di rete configurati da MeteoCompare utilizzano HTTPS. I dati che lasciano il dispositivo sono quindi crittografati durante il trasferimento tra l'app e i server contattati con i normali meccanismi TLS di Android.

## 8. Destinatari

L'app non è destinata specificamente ai minori di 13 anni. Non crea account, profili pubblicitari o cronologie sul server MeteoCompare associate all'identità degli utenti.

## 9. Modifiche alla presente informativa

Se un aggiornamento introduce una nuova raccolta di dati, un sistema di account, sincronizzazione cloud, analisi, pubblicità o un altro trasferimento significativo di dati, questa informativa sarà aggiornata prima della pubblicazione della versione interessata.

L'impegno per la serie 1.x rimane l'assenza di analisi, pubblicità, tracciamento e profilazione da parte di MeteoCompare.

## 10. Contatti

Per domande su questa informativa: [github.com/Pat0chat/MeteoCompare/issues](https://github.com/Pat0chat/MeteoCompare/issues)

## 11. Posizione approssimativa (v1.19.0, funzione facoltativa)

«La mia posizione» è disattivata per impostazione predefinita. Quando scegli questa opzione, MeteoCompare richiede insieme `ACCESS_COARSE_LOCATION` e `ACCESS_FINE_LOCATION`: su Android 12+ puoi scegliere «Precisa» o «Approssimativa». Se scegli «Approssimativa», MeteoCompare non interroga **mai** `GPS_PROVIDER`. La scelta della posizione precisa permette un'acquisizione GPS puntuale, particolarmente utile su alcuni emulatori. La proposta di concedere una precisione maggiore non viene ripetuta dopo il primo consenso: nelle preferenze non incluse nel backup viene ricordato solo un indicatore locale «proposta mostrata» (nessuna posizione). Puoi modificare la scelta nelle impostazioni Android. Non sono presenti né l'autorizzazione `ACCESS_BACKGROUND_LOCATION` né SDK di tracciamento. L'acquisizione avviene solo mentre un'Activity è visibile (attivazione, ritorno in primo piano secondo la politica di aggiornamento o richiesta esplicita).

La posizione grezza rimane solo in memoria durante la determinazione e viene poi ridotta localmente a una griglia di 0,05 gradi. Solo le coordinate di questa cella e la data/ora dell'aggiornamento sono archiviate in un DataStore privato `current_location`, che non viene sottoposto a backup né trasferito. Non viene registrata alcuna cronologia delle posizioni o dei percorsi. Per questa località non vengono salvate cronologie dell'evoluzione delle previsioni e la cache precedente viene ripulita quando la cella cambia o la funzione viene disattivata. Le richieste dei modelli meteo inviano la cella approssimativa a Open-Meteo, che vede anche il tuo indirizzo IP. Una posizione approssimativa resta un dato di localizzazione.

Le tessere radar consultate possono persistere temporaneamente nella cache HTTP comune fino alla rimozione. Non vengono sottoposte a backup né trasferite, ma restano dati geografici tecnici su disco.

I widget non attivano **mai** il GPS o la localizzazione quando Android li aggiorna in background. Usano l'ultima cella memorizzata, che può quindi essere obsoleta dopo uno spostamento. Per aggiornare la tua posizione, apri MeteoCompare oppure tocca l'azione ↻ su un widget «La mia posizione»: viene aperta un'Activity visibile che può richiedere le autorizzazioni di localizzazione in primo piano e permetterti di scegliere la precisione, senza tracciamento in background. Un'acquisizione riuscita viene riutilizzata per 30 minuti quando l'app torna automaticamente in primo piano; i tentativi troppo ravvicinati sono limitati. Rimuovere la scheda «La mia posizione» cancella le preferenze locali di localizzazione e la relativa cache.
