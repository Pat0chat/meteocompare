# Stratégie de tests MeteoCompare

Cette base sépare volontairement les tests rapides JVM des tests Android instrumentés.

## Corpus météo et widgets : 74 537 scénarios déterministes

Une bibliothèque de **74 537 cas versionnés** teste les conditions WMO,
le voile et les couches nuageuses, la dominante quotidienne (#40), les consensus
multi-familles, les heures de soleil (#38) et le dimensionnement/refresh des
widgets (#39). Elle passe par `WeatherRegressionCorpusTest` et
`WidgetRegressionCorpusTest` ; les résultats attendus ne sont pas dérivés
du logiciel testé.

```powershell
python scripts/generate_weather_regression_corpus.py --check
python -m unittest scripts.tests.test_weather_regression_corpus -v
.\gradlew.bat :app:testDebugUnitTest :app:testReleaseUnitTest
```

Voir [documentation complète](docs/weather-regression-corpus.md), notamment
pour les limites de couverture et l'ajout de nouveaux scénarios.

**Windows / Python :** si `python` déclenche le raccourci Microsoft Store,
utiliser le lanceur installé (`py -3` ou `py -3.13`) :

```powershell
py -3 scripts/generate_weather_regression_corpus.py --check
py -3 -m unittest discover -s scripts/tests -p "test_*.py" -v
```

Les fichiers de référence sont conservés avec des fins de ligne LF via
`.gitattributes`. Les tests de SHA-256 et le mode `--check` normalisent uniquement
les fins de ligne Windows CRLF, sans régénérer ni modifier le corpus et sans
masquer une modification du contenu des scénarios.

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


## Diagnostic des notifications météo locales

Le résumé quotidien n'utilise plus `WorkManager.setInitialDelay()` comme
horloge. Une alarme RTC inexacte (`AlarmManager.setAndAllowWhileIdle`) réveille
l'application autour de l'heure choisie puis déclenche un work immédiat portant
le tag `meteocompare_notifications`. Les alertes divergence/révision restent
périodiques, avec un contrôle immédiat lors d'une modification explicite des
réglages.

Logs utiles :

```powershell
adb logcat -s MeteoCompare/Notif WM-WorkerWrapper WM-Processor WM-SystemJobService
adb shell dumpsys alarm | findstr /I "meteocompare WEATHER_NOTIFICATION_DAILY_SUMMARY"
adb shell dumpsys jobscheduler | findstr /I "meteocompare notifications"
```

Recette de non-régression du résumé quotidien :

1. choisir une ville et activer son résumé quotidien ;
2. régler l'heure 3 à 5 minutes dans le futur ;
3. revenir à l'écran d'accueil Android **sans rouvrir MeteoCompare** ;
4. vérifier dans logcat `Daily alarm scheduled for ...`, puis
   `Daily summary alarm fired ...`, `Starting notification cycle: DAILY_SUMMARY`
   et enfin `Posted daily|...` ;
5. modifier uniquement l'heure et refaire le test : aucune désactivation / réactivation
   ne doit être nécessaire ;
6. répéter après un reboot et après un changement de fuseau/heure système.

Test Doze recommandé :

```powershell
adb shell dumpsys deviceidle force-idle
# Laisser passer l'heure choisie, sans ouvrir l'application.
adb shell dumpsys deviceidle unforce
adb shell input keyevent KEYCODE_WAKEUP
```

`setAndAllowWhileIdle()` est volontairement **inexact** : Android ne doit jamais
notifier avant l'heure choisie, mais peut décaler l'alarme selon ses politiques
d'économie d'énergie. Le critère de régression essentiel est que la notification
ne dépende plus de la réouverture de l'application.

Pour les alertes :

- **Divergence des modèles** peut apparaître dès le premier forecast si la
  convergence passe sous le seuil métier ; un contrôle immédiat est lancé à
  l'activation puis le suivi continue périodiquement.
- **Révision des prévisions / À retenir** exige un snapshot historique proche
  de H−24 (tolérance ±8 h) avec au moins deux modèles comparables : son absence
  juste après un wipe / une première installation est donc attendue. En pratique,
  il faut laisser l'historique local se constituer puis disposer d'un nouveau
  forecast environ un jour plus tard avant de valider ce scénario.

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

## Cartes compactes / dépliées sur l'accueil

- **JUnit (sans émulateur)** : `./gradlew :app:testDebugUnitTest --tests 'com.meteocompare.app.ui.citylist.CityCardExpansionTest'`
  - zéro/une/deux localités, dernière favorite, nouvel ajout, rafraîchissement,
    ordre inchangé ou modifié, suppression, premier/deuxième appui.
- **Android Compose (émulateur/appareil)** :
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.meteocompare.app.ui.citylist.CityCardExpansionUiTest`
  - résumé météo avec température, pluie, vent et convergence ; carte complète
    préservée ; bascule exclusive ; navigation en deux temps ; ajout/suppression ;
    chargement et erreur.
- **Régression tablette** : `AdaptiveNavigationContentTest` et
  `CityListContentTest` vérifient le focus de la ville récemment ajoutée,
  l'état sélectionné et le volet détails.

À vérifier également manuellement sur un petit écran et en mode sombre :
largeurs très étroites, longues dénominations de villes, lecteur d'écran,
unités impériales et actions contextuelles après dépliage.

## Régression : plages quotidiennes et unités personnalisées

### Plages en mode DAILY

Les présentations **daily timeline (courbes)** et **daily columns (colonnes)**
appellent maintenant `dailyMetricRange()` : elles utilisent exactement les
mêmes bornes et conversions. Les plages sont disponibles pour les valeurs
**Tmax, Tmin, cumul et probabilité de pluie, nuages, pression, humidité, point
de rosée, vitesse du vent et rafales**, uniquement lorsque **deux modèles
au moins** fournissent des échantillons valides pour la variable concernée.
L’ensoleillement et les indices UV n’ont pas de dispersion multi-modèle
suffisante dans ce pipeline : aucune plage artificielle n’est calculée.

Le plot des Tmax quotidiens réserve un espace **au-dessus de la courbe** pour
la plage puis la valeur centrale ; le plot des Tmin réserve l’espace sous
la courbe. La colonne des légendes, le quadrillage et les pistes ont les
mêmes hauteurs en mode daily. Le mode hourly conserve ses tailles.

### Tests ajoutés / étendus

- `DailyTimelineRangesTest` : 10 variables, profils métrique, impérial, UK et
  personnalisé, nœuds, Fahrenheit, inHg, pouces, locale, modèles insuffisants,
  valeurs non finies et source météo inchangée.
- `SimplifiedTimelineTest` : plages calculées depuis les données fournies par
  plusieurs modèles, notamment pression, humidité, point de rosée et nuages
  avec une couverture horaire suffisante.
- `ChronoTimelineTemperatureTest` : positionnement des Tmax au-dessus de
  leur courbe et des Tmin en dessous, avec l'espace pour deux lignes.
- `UnitSystemDisplayTest` (Compose Android) : valeurs et plages dans les deux
  rendus daily lors de changements d'unités, formats de pression, pluie et
  vent, annulation du dialogue Custom sans modifier les écrans ni les
  préférences, réouverture après annulation.
- Tests historiques conservés : conversions d'unités, saisie et lecture
  DataStore, notifications et widgets.

Sur poste Android disposant de Gradle 9.7.1 et d’un SDK compatible :

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug
```

Une validation sans Android des composants Kotlin purs (JUnit + Kotlin/JVM)
a réussi dans l'environnement d'édition : **35 tests exécutés avec succès**.
La chaîne Gradle Android n'y était pas installée et son téléchargement était
bloqué ; **la suite Android (tests Compose et builder Android) reste à
exécuter** avant publication.


## Chronologie quotidienne — affichage facultatif et compact des plages

Le contrôle **Afficher les plages / Show ranges** est disponible uniquement en mode
*Daily*. Il est désactivé par défaut et son état est partagé entre les modes
*Columns* et *Timeline/Frise* tant que la ville est ouverte (restauré lors d'un
changement de configuration Android via `rememberSaveable`). Il ne modifie ni
le modèle météo ni le système d'unités. Les plages ne s'affichent que si deux
modèles au moins ont fourni des bornes valides.

Cas de non-régression :

- Basculer *Daily columns → Frise → Columns* avec l'option cochée, puis la
  désactiver ; aucun libellé de plage ne doit subsister.
- En *Hourly*, ne jamais voir l'option, ni des plages de type *daily*.
- Les températures de la frise conservent leurs plages hors des courbes ; les
  autres grandeurs sont sur la même ligne quand les deux textes entiers tiennent,
  sinon sur la ligne suivante, y compris à grande échelle de police.
- Vérifier à la fois les valeurs et les bornes en profils metric, imperial, UK
  et custom (°F, kn, inHg, inches), notamment après annulation du popup Custom.
- Comparer visuellement l'alignement des lignes et la lecture des jours sur
  un téléphone étroit et une tablette, avec et sans plages.

Tests JVM dédiés : `DailyRangePlacementTest`.
Tests Compose : `DailyRangeLayoutComposeTest`, `UnitSystemDisplayTest`.

### Raffinement de la présentation des plages (daily)

- Le contrôle est désormais un **switch Material 3** dont toute la zone est
  cliquable (rôle accessible `Switch`). Il se trouve **sous la visualisation**,
  quel que soit le choix Columns/Timeline. L'explication de convergence ne
  s'affiche désormais plus sous la chronologie : elle est dans un popup ouvert
  en touchant le score ou les icônes de divergence.
- En mode plage activée, la largeur de Columns est limitée à **136 dp**
  (112 dp sinon), la largeur d'un jour Timeline reste **148 dp** dans les deux
  états. La colonne fixe d'icônes/étiquettes de la frise fait 96 dp sur mobile.
- Les lignes de Columns utilisent désormais un rail d'icônes commun : les
  valeurs commencent immédiatement après l'icône, sans espace élastique ; les
  lignes sans icône ont le même alignement. Les valeurs de pluie/vent dans la
  frise utilisent aussi un alignement à gauche lorsqu'il y a une icône.
- Les plages sont en caractères secondaires discrets (9 sp), à droite si la
  largeur mesurée le permet, sinon sous la valeur, en une ligne.
- Contrôler à **fontScale 1.0 et 1.4** avec un écran 360 dp, puis sur tablette ;
  vérifier les unités longues, le français et l'allemand, l'orientation et
  les rangées sans plage disponible. Tester aussi une chronologie horaire.

Tests : `ChronoTimelineTemperatureTest`, `CityDetailTimelineNavigationTest`
ainsi que `DailyRangeLayoutComposeTest` (switch, placement, rail d'icônes).


### Raffinement de la chronologie et des actions Home (v1.23.0)

- Le toggle « Afficher les plages » conserve son rôle `Switch`, mais la commande est
  réduite et n'a plus de Surface/Card colorée. Vérifier la lisibilité et la zone
  de clic (y compris TalkBack) sur un petit téléphone, avec fontScale 1.0/1.4.
- Dans **Daily Columns**, les icônes partagent un rail étroit de 15 dp et les
  valeurs sont regroupées avec leurs plages, en typographie secondaire discrète,
  sans sacrifier les valeurs principales ; contrôler les longs libellés en
  français/allemand et en unités personnalisées.
- Dans **Chronology Timeline**, les icônes de pluie et de vent sont présentes
  uniquement dans la première colonne de titres, et pas dans les cellules de
  valeurs, en Daily **et** Hourly. Vérifier avec plages visibles/invisibles.
- Sur **Home**, « Comparer les villes » apparaît dans la même rangée de FAB que
  « Réorganiser » dès deux villes, et non plus dans l'AppBar. Les deux actions
  doivent être absentes lorsqu'une seule ville est enregistrée. Vérifier la
  navigation des deux boutons et le mode RTL.
- Tests Compose concernés : `DailyRangeLayoutComposeTest` et
  `CityListContentTest`. Lancer les tests complets Android :
  `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug`.


### Information convergence et heatmap Daily Columns (v1.23.0)

- La note d'information « Le score indique la convergence ; les icônes signalent
  les variables qui divergent » n'occupe plus de ligne sous la chronologie.
  Cliquer sur un score dans **Columns** (y compris une icône de divergence), sur
  une cellule de convergence dans **Timeline** ou sur le titre fixe
  « Convergence » ouvre une boîte d'information Material 3 avec cette note.
  Tester en **Daily** et **Hourly**, pour un score présent, absent, ou une
  convergence sans divergence explicite. Le bouton « Fermer » et le bouton
  Retour doivent fermer la boîte sans modifier les prévisions ni les plages.
- En **Daily Columns**, la bande heatmap des températures fait toujours
  **64 dp**, plages affichées ou non. Les plages min/max passent sur une seule
  ligne avec leur température ; en cas de manque de largeur, seule la plage
  secondaire peut être tronquée. Tester avec °C, °F et une police 1.4×.
  L'horaire conserve une heatmap de **46 dp**.
- Tests Compose : `DailyRangeLayoutComposeTest` (dialogue et fermeture, titre
  cliquable, Daily/Hourly, dimensions fixes, débordements). Contrôles rapides :
  `python3 -m unittest scripts.tests.test_chronology_convergence_info_heatmap_contract -v`.
  Comme pour les autres tests Compose, validation sur émulateur requise.
