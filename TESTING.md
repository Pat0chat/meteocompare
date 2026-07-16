# Stratégie de tests MeteoCompare

Cette base sépare volontairement les tests rapides JVM des tests Android instrumentés.

## Pyramide de tests

### Tests JVM (`app/src/test`)

Ils couvrent les règles métier, les agrégations météo, le mapping réseau, les ViewModels, les widgets purs et les schedulers testables sans appareil. Ils ne doivent dépendre ni du réseau réel, ni de l'heure système non contrôlée, ni d'une base Android persistante.

### Tests instrumentés (`app/src/androidTest`)

Ils couvrent ce qui nécessite Android :

- navigation complète de `MainActivity` avec repositories Hilt factices ;
- états et interactions Compose des écrans principaux ;
- accessibilité et sémantique des composants météo ;
- configuration des widgets ;
- DAO Room avec base en mémoire ;
- persistance DataStore ;
- application de la locale persistée.

Les parcours d'application n'utilisent jamais Open-Meteo. `TestRepositoryModule` remplace les repositories de production par des fakes déterministes. Les dates de fixtures sont calculées relativement au jour d'exécution puis figées pour tout le processus de test.

## Commandes

Sous Windows :

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Validation complète :

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug
```

Sous macOS/Linux, remplacer `gradlew.bat` par `./gradlew`.

## Environnement instrumenté recommandé

- API 35 ou 36 ;
- image d'émulateur x86_64 ;
- animations désactivées par la configuration Gradle ;
- locale système quelconque : les assertions utilisent les ressources localisées ou des test tags ;
- réseau non requis pour la suite `androidTest`.

Avant une release, exécuter au minimum la suite sur une locale française et une locale anglaise, ainsi que sur un appareil physique si possible.

## Règles pour les nouveaux tests

1. Un test Compose appelle `setContent` une seule fois.
2. Les interactions ciblent un `testTag`, un rôle sémantique ou une ressource localisée, jamais une chaîne française arbitraire quand elle peut varier.
3. Les tests de ViewModel utilisent un dispatcher principal de test et des flows contrôlés.
4. Les tests instrumentés ne contactent aucun service externe.
5. Toute correction de bug ajoute un test de non-régression au niveau le plus bas possible.
6. Un test ne dépend jamais des données laissées par un autre test.

## Diagnostic en cas d'échec

Pour obtenir davantage de détails :

```powershell
.\gradlew.bat connectedDebugAndroidTest --stacktrace --info
```

Les rapports sont générés dans :

- `app/build/reports/tests/testDebugUnitTest/`
- `app/build/reports/androidTests/connected/`
- `app/build/reports/lint-results-debug.html`


## Diagnostic du rafraîchissement des widgets

Le travail périodique porte le tag `meteocompare_widget` et le nom unique
`meteocompare_widget_refresh`. Pour diagnostiquer un téléphone où le launcher
semble conserver une ancienne vue :

```powershell
adb logcat -s MeteoCompare/Widget WM-WorkerWrapper WM-Processor
adb shell dumpsys jobscheduler | findstr /I "meteocompare widget_refresh"
```

Test de reprise après veille profonde :

```powershell
adb shell dumpsys deviceidle force-idle
# Attendre, puis sortir du mode idle
adb shell dumpsys deviceidle unforce
adb shell input keyevent KEYCODE_WAKEUP
```

Points à vérifier :

- le travail unique reste `ENQUEUED` entre deux exécutions ;
- une exécution produit un nouveau rendu Glance pour chaque AppWidgetId vivant ;
- après reboot ou mise à jour de l'APK, le receiver de réparation reprogramme
  le périodique et demande un tick immédiat ;
- un `force-stop` manuel bloque volontairement tous les composants Android
  jusqu'à la prochaine ouverture de l'application : ce cas ne peut pas être
  contourné par WorkManager ou par un receiver.
