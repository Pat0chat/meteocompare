# Audit réel du corpus v1.22.0 — couverture et preuve d'exécution

## Conclusion

Les **74 537 lignes de fixtures sont bien reliées à neuf méthodes Kotlin/JUnit** qui
appellent les calculs de production. Elles sont présentes dans neuf TSV versionnés,
avec oracle déterministe et sommes SHA-256. Les assertions se font **pour chaque
ligne**, avec un ID d'erreur reproductible.

Ce corpus n'est **pas** une couverture exhaustive de l'application. Les neuf
familles ne remplacent ni les nombreuses autres suites JVM, ni les AndroidTests,
ni la validation statistique sur observations. Un nombre de scénarios élevé ne
constitue pas un pourcentage de couverture de code.

## Inventaire vérifié sur cette candidate

| Corpus | Cas | Fonction ou comportement Kotlin effectivement sollicité |
|---|---:|---|
| `wmo.tsv` | 114 | `WeatherCondition.fromWmoCode` |
| `clouds.tsv` | 40 411 | `fromCloudCover`, `SolarSkyInterpretation.isVeiledSky`, `refine` |
| `daily.tsv` | 8 461 | `ForecastSeries.resolveDailyCondition` (zones et heures physiques) |
| `consensus.tsv` | 609 | `WeatherConditionConsensus.resolve`, poids des familles |
| `sunshine.tsv` | 2 880 | `estimateHourlySunshineHours`, nuit et couverture nuageuse |
| `sunshine_consensus.tsv` | 5 120 | `estimatedHourlySunshineFromModels`, temps horaire et consensus |
| `widget_layout.tsv` | 12 870 | `classifyWidgetLayoutForShape` |
| `widget_metrics.tsv` | 1 408 | `forecastMetricLayout`, `forecastFittingCardProfile` |
| `widget_refresh.tsv` | 2 664 | `isWidgetDispatchDue`, `nextWidgetRefreshTick` |
| **Total** | **74 537** | **Neuf méthodes JUnit qui itèrent sur les fixtures** |

Les classes `WeatherRegressionCorpusTest` et `WidgetRegressionCorpusTest` ont
également **deux tests d'invariants** sans TSV. Il ne faut donc pas confondre
**11 méthodes** avec **74 537 méthodes de test indépendantes**.

Dans la branche auditée, le projet contient **289 fichiers de production Kotlin**,
**179 fichiers de tests JVM** et **50 fichiers de tests instrumentés Android**.
Il s'agit de comptes de fichiers, **pas** d'un taux de couverture.

## Périmètres qu'il ne faut surtout pas prétendre couvrir par les TSV

| Sous-système | Couverture *par le corpus TSV* | Vérification nécessaire |
|---|---|---|
| Codes WMO, ciel clair/partiel/voilé/couvert, dominante quotidienne (#40) | **Oui, ciblée** | Ajouter les cas qui échappent aux grilles actuelles |
| Consensus des familles et soleil horaire | **Oui, ciblée** | Compléter la chaîne depuis le JSON de fournisseur |
| Dimensions, métriques et tick de widgets | **Oui, ciblée** | Tester le rendu Glance et les vrais launchers |
| Moteur numérique de confiance, calibration V3, biais local, PoP | **Non** | Tests métier distincts + fixtures d'observations |
| HTTP Open-Meteo, alignement DTO, échecs/réessais, mapper | **Non** | Fixtures JSON, tests de décodage et bout-en-bout |
| Cache prévisions, Room, migrations, DataStore | **Non** | Fixtures de bases et AndroidTests, tests d'invalidation |
| Today Summary, popup, chronologies, graphiques, navigation | **Non** | AndroidTests Compose (distincts du corpus) |
| Glance/RemoteViews, notifications et WorkManager | **Non**, hors fonctions pures énumérées ci-dessus | Tests instrumentés et de cycle de vie |
| Qualité scientifique sur météo réelle (biais, fiabilité) | **Non** | Archives historiques et observations indépendantes |

**Priorités de la prochaine extension** : fixtures JSON réalistes Open-Meteo
(UTC, DST, intervalle précédent), parcours complet DTO → mapper → forecast →
Today Summary, mélange d'heures et de modèles pour la confiance/probabilité,
caches corrompus/migrations, UI et accessibilité sur émulateur, puis validation
sur observations historiques. Ces périmètres ne peuvent être déclarés couverts
avant l'ajout de leurs tests effectifs.

## Preuve qu'une exécution a réellement eu lieu

À la fin de chaque test Kotlin consommant un TSV, `CorpusExecutionProof`
compare le nombre de lignes **effectivement lues** à un total exact indépendant
du manifest généré. Le reçu suivant est émis **seulement après le passage des
assertions** :

```text
METEOCOMPARE_CORPUS_V1 file=clouds.tsv cases=40411 elapsed_ms=458
```

`report_test_results.py` extrait ces reçus du `system-out` des rapports JUnit
XML. Une méthode verte **sans** reçu, un nombre incorrect, un reçu dupliqué,
un reçu sous une mauvaise classe ou un XML manquant **ne valide aucun scénario**.
Les workflows exigent les reçus complets en Debug **et** en Release :

```bash
python3 scripts/report_test_results.py --require-corpus both
```

Les tâches Gradle `Test` sont forcées à s'exécuter à chaque validation : ni
`UP-TO-DATE`, ni `FROM-CACHE` ne doivent réutiliser d'anciens rapports.
Cela ne force pas la recompilation du projet à chaque passage.

## Mesure locale et test de mutation

Le 13 novembre 2026, un lanceur Kotlin/JVM hors Gradle a invoqué **les onze
méthodes réelles des deux classes de tests** et les fonctions Kotlin métier
concernées. Pour isoler les calculs métier d'Android, seules quelques
dépendances périphériques ont été remplacées, et le trio de fonctions de soleil
provient sans modification du source de `LocalityComparison.kt`.

| Famille | Temps de calcul local mesuré (millisecondes) |
|---|---:|
| WMO | 76 |
| Nuages | 458 |
| Journées / DST | 1 309 |
| Consensus | 71 |
| Soleil horaire | 22 |
| Soleil multi-modèles | 123 |
| Widgets dimensions | 31 |
| Widgets métriques | 23 |
| Widgets rafraîchissement | 9 |
| **Lancement JVM total des tests** | **2 202 ms** |

Les temps ne constituent pas des garanties de performance : ils dépendent de la
machine/JVM et **excluent la compilation Gradle et le démarrage du runner**.
Leur faiblesse est néanmoins crédible : ces tests effectuent des opérations
en mémoire, sans réseau, base de données ni UI.

**Mutation négative** : la première ligne de `clouds.tsv` a été modifiée dans
une copie temporaire de `CLEAR` vers `OVERCAST`. Le programme Kotlin a quitté
avec le code `1` sur cette ligne, avec l'ID de scénario `cloud-0-_-_-_-_-_`.
Le corpus versionné n'a pas été modifié.

La suite Gradle complète n'a **pas** pu être exécutée dans ce conteneur : le
wrapper requiert Gradle 9.7.1 indisponible localement et le téléchargement
échoue faute de résolution DNS (`services.gradle.org`). La réussite de
GitHub Actions et de la suite Android reste à confirmer sur le runner réel.

## Commandes recommandées sous Windows

```powershell
python scripts/generate_weather_regression_corpus.py --check
.\gradlew.bat :app:testDebugUnitTest --tests "*WeatherRegressionCorpusTest" --tests "*WidgetRegressionCorpusTest" --info
python scripts/report_test_results.py --require-corpus debug --markdown corpus-execution-report.md --json corpus-execution-report.json

# Vérification release complète (ne compte pas les deux variantes comme 2× plus de scénarios uniques)
.\gradlew.bat :app:testDebugUnitTest :app:testReleaseUnitTest :app:connectedDebugAndroidTest
python scripts/report_test_results.py --require-corpus both
```
