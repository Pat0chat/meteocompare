# Stratégie de tests MeteoCompare

Cette base sépare volontairement les tests rapides JVM des tests Android instrumentés.

## Pyramide de tests

### Tests JVM (`app/src/test`)

Ils couvrent les règles métier, les agrégations météo, le mapping réseau, les ViewModels, les widgets purs et les schedulers testables sans appareil. Le moteur d’évolution y vérifie notamment la sélection des snapshots locaux ~24/~48/~72 h, l’intersection des modèles comparables, les révisions, la détection des tendances et l’absence d’impact d’une erreur d’historisation sur la prévision principale. Ils ne doivent dépendre ni du réseau réel, ni de l'heure système non contrôlée, ni d'une base Android persistante.

### Tests instrumentés (`app/src/androidTest`)

Ils couvrent ce qui nécessite Android :

- navigation complète de `MainActivity` avec repositories Hilt factices ;
- états et interactions Compose des écrans principaux ;
- accessibilité et sémantique des composants météo ;
- configuration des widgets ;
- DAO Room avec base en mémoire, dont l’invariant « un snapshot cohérent par ville et tranche de 3 h » ;
- persistance DataStore ;
- application de la locale persistée ;
- repli/dépli des cartes d’analyse, dont « Évolution de la prévision », sans perdre leur résumé compact.

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

## Recette de stabilisation R9

Avant toute release construite à partir de l'audit R9 :

```powershell
.\gradlew.bat clean testDebugUnitTest testReleaseUnitTest lintDebug assembleDebug assembleRelease
.\gradlew.bat connectedDebugAndroidTest
```

Points manuels ciblés à vérifier en plus de la suite automatisée :

1. Ajouter une ville côtière et une ville intérieure : la pastille bleue du menu ne doit apparaître que si le mode Mer/côte est disponible ; l'icône 🌊 près du nom ne doit apparaître qu'après activation.
2. Passer hors ligne avec un ancien cache marin : l'état connu peut rester visible, puis doit être revalidé au retour réseau.
3. Changer successivement les quatre moteurs dans Settings : Home, Détails et widgets doivent changer de centrale sans modifier les badges de convergence brute.
4. Ouvrir Engine Comparison : les quatre courbes utilisent les mêmes données brutes ; le changement de moteur actif ne doit pas refetcher la météo.
5. Dans Évolution des prévisions, vérifier que les seuils dessinés correspondent aux seuils métier : T 0,5/1,0 °C ; pluie 1/2 mm ; vent 3/5 km/h.


## JDK 25 et MockK

Les tests JVM utilisent MockK 1.14.11. Son agent déclare Byte Buddy 1.18.2 transitivement ; MeteoCompare force `byte-buddy` et `byte-buddy-agent` en 1.18.9 dans le classpath **test** afin d'utiliser le chemin d'injection compatible JDK 25 et d'éviter l'appel terminalement déprécié à `sun.misc.Unsafe::objectFieldOffset`.

Pour vérifier la résolution réellement utilisée :

```powershell
.\gradlew.bat :app:dependencyInsight --dependency byte-buddy --configuration testReleaseRuntimeClasspath
.\gradlew.bat :app:dependencyInsight --dependency byte-buddy-agent --configuration testReleaseRuntimeClasspath
```

La version sélectionnée doit être `1.18.9`.


## R10 — consensus hybride des conditions météo

Le libellé de condition ne doit plus sur-représenter « Couvert » lorsque les modèles secs sont répartis entre `CLEAR`, `MAINLY_CLEAR`, `PARTLY_CLOUDY` et `OVERCAST`.

Tests automatiques ciblés :

- `WeatherConditionConsensusTest` : regroupement du ciel sec, conservation du vote brut pour la convergence, priorité aux phénomènes significatifs et tie-break prudent sec/pluie ;
- `WeatherConditionTest` : seuils de nébulosité `<20 / <45 / <85 / ≥85 %` ;
- `ForecastAggregatesTest` : propagation du consensus hiérarchique dans le mini-forecast widget 12 h ;
- `WeatherScenarioBuilderTest` : un ciel sec à 82 % reste `VARIABLE_SKY`, pas `OVERCAST`.

Recette manuelle recommandée : choisir une ville où plusieurs modèles oscillent entre codes WMO 1/2/3, comparer le pourcentage de nébulosité et l'icône affichée sur Home, Détails et widgets. Entre 45 et 84 % de nébulosité centrale, un ciel sec doit rester « Partiellement nuageux » ; pluie/neige/brouillard/orage doivent continuer à primer lorsqu'ils gagnent le vote catégoriel familial.


## R11 — consensus hiérarchique généralisé des conditions météo

R11 remplace le regroupement ponctuel `DRY_SKY` de R10 par un arbre sémantique complet. Le test doit vérifier la décision à chaque niveau, pas seulement le ciel sec.

Arbre de référence :

```text
ROOT
├─ NON_PRECIPITATION
│  ├─ SKY → CLEAR / MAINLY_CLEAR / PARTLY_CLOUDY / OVERCAST
│  └─ FOG
└─ PRECIPITATION
   ├─ LIQUID → DRIZZLE / RAIN_SHOWERS / RAIN
   ├─ FROZEN → SNOW_SHOWERS / SNOW
   ├─ FREEZING_RAIN
   └─ THUNDERSTORM
```

Tests ciblés dans `WeatherConditionConsensusTest` :

- fragmentation du ciel sec conservée comme cas de régression ;
- `DRIZZLE + RAIN_SHOWERS + RAIN` consolidés avant comparaison à `NON_PRECIPITATION` ;
- `SNOW + SNOW_SHOWERS` consolidés avant comparaison à la pluie liquide ;
- `SKY + FOG` consolidés au niveau `NON_PRECIPITATION` ;
- un orage minoritaire ne gagne pas uniquement grâce à son rang de sévérité si la branche `LIQUID` est plus soutenue ;
- égalité `PRECIPITATION / NON_PRECIPITATION` toujours prudente vers la précipitation ;
- toutes les conditions connues sauf `UNKNOWN` sont couvertes par exactement une feuille de l'arbre ;
- la convergence retournée reste le vote brut exact des modèles.

Recette manuelle : vérifier sur Home, Détails, widgets et Engine Comparison qu'une majorité sémantique ne se fragmente plus entre sous-types voisins, tout en contrôlant que le pourcentage de convergence reste inchangé par rapport aux codes WMO bruts.
