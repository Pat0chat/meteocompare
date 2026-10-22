# Datenschutzerklärung — MeteoCompare

*Letzte Aktualisierung: 22. Oktober 2026*

**Langues / Languages / Sprachen / Idiomas / Lingue:** [Français](PRIVACY.md) · [English](PRIVACY.en.md) · [Deutsch](PRIVACY.de.md) · [Español](PRIVACY.es.md) · [Italiano](PRIVACY.it.md)

## Kurzfassung

**MeteoCompare verwendet Ihre Daten weder für Profilbildung noch für Werbung oder Analysen.** Die App enthält weder Werbe-SDKs noch Analysedienste, Absturzberichte an externe Dienste oder ein Kontosystem.

Favoriten, Einstellungen und App-Zwischenspeicher werden lokal gespeichert. Einige für die Wetterfunktionen erforderliche Informationen verlassen jedoch das Gerät: Bei einer Ortssuche wird der Suchbegriff an den Geokodierungsdienst von Open-Meteo gesendet; die Koordinaten betrachteter Orte werden an die Wetter-APIs von Open-Meteo übertragen. Für französische Wetterwarnungen kann ein Département-Code an den öffentlichen MeteoCompare-Worker gesendet werden. Wenn Sie das Regenradar ausdrücklich öffnen, werden die Ortskoordinaten außerdem zum Laden von RainViewer-Bildern und OpenStreetMap-Kartenkacheln verwendet.

---

## 1. Von MeteoCompare verarbeitete Daten

MeteoCompare legt keine Nutzerdatenbank an und verwendet keine Daten zur Profilbildung, Reichweitenmessung, Werbung oder appübergreifenden Nachverfolgung.

Im Einzelnen:

- ❌ Kein Google Analytics, Firebase Analytics oder vergleichbares SDK
- ❌ Kein Crashlytics oder anderer externer Dienst zur Absturzberichterstattung
- ❌ Keine Werbung und kein Werbenetzwerk
- ❌ Keine Werbe-ID (`AD_ID`)
- ❌ Kein MeteoCompare-Konto
- ❌ Keine Tracking-Cookies oder Tracking-WebViews
- ❌ Kein absichtliches Geräte-Fingerprinting
- ❌ Kein appübergreifendes Tracking

Die einzigen Netzwerkübertragungen der App sind diejenigen, die für die in den Abschnitten 2, 3 und 4 beschriebenen Funktionen erforderlich sind.

## 2. Open-Meteo

MeteoCompare nutzt die öffentlichen APIs von [Open-Meteo](https://open-meteo.com/) für die Ortssuche, Wettervorhersagen, bestimmte historische Daten und Meeresdaten.

### An Open-Meteo übermittelte Informationen

Je nach Aktion in der App:

- **Ortssuche:** Der eingegebene Suchtext wird an die Geokodierungs-API von Open-Meteo gesendet.
- **Laden oder Aktualisieren eines Ortes:** Die Koordinaten des betrachteten Ortes (Breitengrad und Längengrad), die angeforderten Wettervariablen, die ausgewählten Modelle sowie die erforderlichen technischen Anfrageparameter werden an die Open-Meteo-APIs gesendet.
- **Automatische Aktualisierung oder Widgets:** Die Koordinaten einer favorisierten Stadt können zur Aktualisierung der Wetterdaten erneut verwendet werden, ohne dass Sie den Ort jedes Mal neu eingeben müssen.

Wie bei jeder Internetverbindung erhält Open-Meteo außerdem die Quell-IP-Adresse und die technischen HTTP-Informationen, die für die Weiterleitung der Anfrage erforderlich sind.

MeteoCompare sendet keine Google-Konten, Werbekennungen, app-spezifischen Android-Kennungen, Namen, E-Mail-Adressen oder vollständigen Favoritenlisten an Open-Meteo.

### Speicherung durch Open-Meteo

Laut der derzeitigen Richtlinie von Open-Meteo kann der kostenlose API-Dienst **Serverprotokolle 90 Tage lang** zu Diagnose-, Wartungs- und Missbrauchspräventionszwecken aufbewahren. Diese Protokolle können sensible Angaben aus Anfragen enthalten, beispielsweise **geografische Koordinaten**. Open-Meteo erklärt, dass die Protokolle nach 90 Tagen gelöscht und nicht an Dritte weitergegeben werden.

Diese Speicherung erfolgt durch Open-Meteo gemäß den eigenen Bedingungen und Datenschutzbestimmungen: [open-meteo.com/en/terms](https://open-meteo.com/en/terms).

Die über Open-Meteo bezogenen Daten stehen unter der Lizenz **CC BY 4.0**. MeteoCompare führt sie zusammen, vergleicht und verarbeitet sie vor der Anzeige. Quellenangabe und Lizenz: [open-meteo.com/en/license](https://open-meteo.com/en/license).

## 3. Météo-France-Wetterwarnungen über den MeteoCompare-Worker

Für französische Städte kann die App den öffentlichen Worker `https://meteocompare.app/_mcx/vigilance` abfragen, um die offiziellen Wetterwarnungen („Vigilance“) von Météo-France anzuzeigen.

Die App übermittelt ausschließlich folgende funktionsbezogene Parameter:

- den **Département-Code** (beispielsweise `91`);
- `coast=1`, wenn der Ort als Küstenort erkannt wird, um Küstenwarnungen einzubeziehen.

Der Worker übernimmt serverseitig die Authentifizierung bei Météo-France und die Zwischenspeicherung. In der App sind keine Météo-France-Benutzernamen, Passwörter oder Tokens enthalten.

Wie bei jeder HTTPS-Anfrage erhält die Netzwerkinfrastruktur, die den Worker hostet oder schützt, die Quell-IP-Adresse und die Anfrageparameter. MeteoCompare nutzt diese Angaben nicht absichtlich, um Nutzerprofile zu erstellen, Nutzer zu verfolgen oder zu identifizieren.

Orte außerhalb Frankreichs lösen keine Vigilance-Abfrage aus. Wird eine Stadt aus den Favoriten entfernt, wird ihr Warnstatus aus dem Arbeitsspeicher gelöscht und der entsprechende dauerhafte Cache im Rahmen des App-Betriebs bereinigt.

## 4. Regenradar: RainViewer und OpenStreetMap

Die Radaransicht ist optional. Diese Dienste werden erst kontaktiert, wenn Sie die Ansicht ausdrücklich auf der Detailseite einer Stadt öffnen.

- **RainViewer** liefert Metadaten und Bilder von Radarbeobachtungen. Die Koordinaten (Breiten- und Längengrad) des betrachteten Ortes sind in der Bildanfrage enthalten, um die Radarkarte zu zentrieren.
- **OpenStreetMap** liefert nur die für den angezeigten Bereich benötigten Kartenkacheln des Hintergrunds. Bereits betrachtete Kacheln werden in einem lokalen HTTP-Cache aufbewahrt, damit sie nicht bei jedem Öffnen erneut heruntergeladen werden müssen. Dieser Cache beachtet die HTTP-Cache-Vorgaben des Servers und kann von Android gelöscht werden.

Wie bei jeder HTTPS-Anfrage erhalten auch diese Dienste die Quell-IP-Adresse und die technischen HTTP-Informationen, die für die Übermittlung erforderlich sind. MeteoCompare sendet ihnen weder Kontodaten noch Werbekennungen oder Favoritenlisten.

Die Projektionen für +15, +30, +45 und +60 Minuten werden **lokal auf dem Gerät** aus den neuesten RainViewer-Bildern berechnet: Erkennung von Regengebieten, Schätzung ihrer Bewegung und Extrapolation mit zunehmender Unsicherheit. Zur Erstellung dieser Kurzfristprognose werden keine zusätzlichen zukünftigen Daten von RainViewer angefordert.

## 5. Lokale Speicherung auf dem Gerät

MeteoCompare speichert folgende Daten lokal:

| Daten | Lokaler Speicher | Zweck | Netzwerkübertragung |
|---|---|---|---|
| Favorisierte Städte und ihre Koordinaten | DataStore Preferences | Ausgewählte Orte wiederherstellen | Die Koordinaten des Ortes werden beim Laden einer Vorhersage an Open-Meteo gesendet |
| Aktivierte Wettermodelle | DataStore Preferences | Einstellungen beibehalten | Angeforderte Modelle sind Teil der Parameter von Wetteranfragen |
| Design, Aktualisierungsintervall, Detailansicht und weitere Einstellungen | DataStore Preferences | App anpassen | Nein, außer wenn eine Einstellung unmittelbar die Parameter einer Wetteranfrage verändert |
| App-Sprache | Interne SharedPreferences | Sprache der Benutzeroberfläche und der Suche wählen | Der Sprachcode kann zur Lokalisierung von Ergebnissen an die Geokodierung übermittelt werden |
| Vorhersage-Cache und Berechnungshistorie | Room SQLite | Schneller Start, Offline-Modus, Vergleiche | Cache-Inhalte werden nicht als solche hochgeladen |
| Vigilance-Cache | DataStore Preferences | Netzwerkanfragen verringern und eine kurze Ersatzanzeige ermöglichen | Nicht als Cache; neue Anfragen an den Worker sind möglich |
| HTTP-Cache für Radar/Karten | Android-`cacheDir` (OkHttp, maximal 64 MiB) | HTTP-Antworten und OSM-Kacheln gemäß Cache-Headern wiederverwenden | Nicht als Cache; nur fehlende oder abgelaufene Anfragen gehen an RainViewer / OpenStreetMap |
| Widget-Einstellungen | Glance DataStore | Stadt, Farben und Widget-Optionen speichern | Die eingestellte Stadt kann dieselben Wetteranfragen wie die App auslösen |

„Lokal gespeichert“ bedeutet, dass MeteoCompare diese Datenbanken und Dateien nicht mit einem MeteoCompare-Server synchronisiert. Das heißt nicht, dass darin enthaltene Informationen nie in einer Netzwerkanfrage verwendet werden: Um eine Vorhersage abzurufen, müssen beispielsweise die Koordinaten eines favorisierten Ortes an den Wetterdienst gesendet werden.

### Android-Sicherung

Die automatische Android-Sicherung ist **selektiv** aktiviert. Die Sicherungsregeln von MeteoCompare erlauben ausschließlich:

- allgemeine App-Einstellungen;
- die Liste der favorisierten Städte;
- die Spracheinstellung.

Je nach Systemeinstellungen kann Android diese Informationen in Ihrem Konto oder auf einem anderen Gerät sichern bzw. übertragen. MeteoCompare hat keinen Zugriff auf diese Sicherungen.

Die Room-Datenbanken des Wetter-Caches, der HTTP-Cache im `cacheDir` und andere Dateien, die nicht ausdrücklich in den Sicherungsregeln genannt sind, gehören nicht zu dieser selektiven App-Sicherung.

Beim Deinstallieren werden die lokalen App-Daten auf dem Gerät entfernt. Bereits erstellte Android-Sicherungen unterliegen weiterhin Android und Ihren Kontoeinstellungen.

## 6. Android-Berechtigungen

| Berechtigung | Zweck |
|---|---|
| `INTERNET` | HTTPS-Anfragen an Open-Meteo, den MeteoCompare-Worker, RainViewer und OpenStreetMap |
| `ACCESS_NETWORK_STATE` | Fehlende Netzwerkverbindung erkennen und Oberfläche / Anfragen anpassen |
| `RECEIVE_BOOT_COMPLETED` | Regelmäßige Widget-Aktualisierungen nach einem Neustart oder App-Update korrekt neu planen |
| `POST_NOTIFICATIONS` | Lokale Wetterbenachrichtigungen (Tagesübersicht, Modellabweichungen, Vorhersageänderungen) nur anzeigen, wenn Sie diese in den Einstellungen aktivieren. Sie werden auf dem Gerät berechnet: Es gibt keinen Push-Dienst; keine Kennung und keine zusätzlichen Daten werden übertragen |
| `ACCESS_COARSE_LOCATION` | Optionale Funktion „Mein Standort“: Netzwerk-/kombinierter Standortanbieter nach ausdrücklicher Zustimmung |
| `ACCESS_FINE_LOCATION` | Optionale Berechtigung: bei Zustimmung einmalige GPS-Ermittlung (insbesondere für im Emulator eingespeiste Positionen). Nie im Hintergrund verwendet; die genaue Position wird nicht gespeichert |

MeteoCompare **fordert nicht an**:

- Hintergrundstandort (`ACCESS_BACKGROUND_LOCATION`); genauer Standort (`ACCESS_FINE_LOCATION`) ist optional und wird nur nach Zustimmung im Vordergrund verwendet;
- Zugriff auf Kontakte, Fotos oder externe Dateien;
- Zugriff auf Telefonfunktionen oder SMS;
- Bluetooth oder NFC;
- die Werbekennung.

Standardmäßig **wählen Sie die Stadt selbst aus**. Die optionale Funktion „Mein Standort“ ermittelt nur nach Zustimmung eine ungefähre Position, während eine Activity sichtbar ist; siehe den gesonderten Abschnitt unten.

## 7. Sicherheit der Übertragung

Die von MeteoCompare konfigurierten Netzwerkdienste verwenden HTTPS. Daten, die das Gerät verlassen, werden daher bei der Übertragung zwischen der App und den kontaktierten Servern mit den üblichen TLS-Mechanismen von Android verschlüsselt.

## 8. Zielgruppe

Die App richtet sich nicht speziell an Kinder unter 13 Jahren. Sie erstellt weder Konten noch Werbeprofile oder eine serverseitige MeteoCompare-Historie, die mit der Identität eines Nutzers verknüpft ist.

## 9. Änderungen an dieser Erklärung

Wenn ein Update eine neue Datenerhebung, ein Kontosystem, Cloud-Synchronisierung, Analysen, Werbung oder eine andere wesentliche Datenübertragung hinzufügt, wird diese Erklärung vor der Veröffentlichung der betreffenden Version aktualisiert.

Für die Versionsreihe 1.x gilt weiterhin die Verpflichtung, auf Analysen, Werbung, Tracking und Profilbildung durch MeteoCompare zu verzichten.

## 10. Kontakt

Bei Fragen zu dieser Erklärung: [github.com/Pat0chat/MeteoCompare/issues](https://github.com/Pat0chat/MeteoCompare/issues)

## 11. Ungefährer Standort (v1.19.0, optionale Funktion)

„Mein Standort“ ist standardmäßig deaktiviert. Wenn Sie diese Option wählen, fordert MeteoCompare `ACCESS_COARSE_LOCATION` und `ACCESS_FINE_LOCATION` gemeinsam an: Unter Android 12+ können Sie „Genau“ oder „Ungefähr“ wählen. Bei „Ungefähr“ fragt MeteoCompare **niemals** `GPS_PROVIDER` ab. Die Freigabe des genauen Standorts ermöglicht eine einmalige GPS-Ermittlung, was besonders bei manchen Emulatoren hilfreich ist. Das Angebot, nachträglich genaueren Zugriff zu gewähren, wird nach der ersten Zustimmung nicht wiederholt: In nicht gesicherten Einstellungen wird nur ein lokales Kennzeichen „Angebot angezeigt“ gespeichert (keine Position). Die Auswahl kann in den Android-Einstellungen geändert werden. Es gibt weder die Berechtigung `ACCESS_BACKGROUND_LOCATION` noch ein Tracking-SDK. Eine Ermittlung findet nur statt, solange eine Activity sichtbar ist (Aktivierung, Rückkehr in den Vordergrund gemäß Aktualitätsregeln oder ausdrückliche Aktualisierung).

Die Rohposition bleibt während der Ermittlung ausschließlich im Arbeitsspeicher und wird anschließend lokal auf ein Raster von 0,05 Grad reduziert. Nur die Koordinaten dieser Rasterzelle und der Aktualisierungszeitpunkt werden in einem privaten DataStore `current_location` gespeichert, der weder gesichert noch übertragen wird. Es wird kein Positions- oder Bewegungsverlauf aufgezeichnet. Für diesen Ort werden keine Historien der Vorhersageentwicklung gespeichert; der bisherige Cache wird beim Wechsel der Rasterzelle oder Deaktivieren bereinigt. Wettermodell-Anfragen übermitteln die ungefähre Zelle an Open-Meteo, das auch Ihre IP-Adresse sieht. Auch eine ungefähre Position ist ein Standortdatum.

Abgerufene Radarkacheln können bis zur Verdrängung vorübergehend im gemeinsamen HTTP-Cache verbleiben. Sie werden weder gesichert noch übertragen, bleiben jedoch technische geografische Daten auf dem Datenträger.

Widgets lösen **niemals** GPS- oder Standortabfragen aus, wenn Android sie im Hintergrund aktualisiert. Sie verwenden die zuletzt gespeicherte Rasterzelle, die nach einer Ortsänderung veraltet sein kann. Um Ihren Standort zu aktualisieren, öffnen Sie MeteoCompare oder tippen Sie bei einem Widget „Mein Standort“ auf ↻: Daraufhin wird eine sichtbare Activity geöffnet, die Standortberechtigungen im Vordergrund anfordern und die Wahl der Genauigkeit ermöglichen kann, ohne Hintergrundverfolgung. Eine erfolgreiche Ermittlung wird bei automatischer Rückkehr in den Vordergrund 30 Minuten lang wiederverwendet; zu häufige Versuche werden begrenzt. Beim Entfernen der Karte „Mein Standort“ werden die lokalen Standorteinstellungen und der zugehörige Cache gelöscht.
