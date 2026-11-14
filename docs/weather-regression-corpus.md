# Corpus de non-régression météorologique — v1

Ce corpus **reproductible et versionné** constitue une base commune de vérification
pour les corrections des issues #38, #39 et #40, la v1.22.0 et les évolutions à venir.
Il complète (et **ne remplace pas**) les tests unitaires, d'intégration Android,
les validations sur appareil et les comparaisons avec des observations réelles.

## Ce qui est réellement couvert

Les fixtures sont dans `app/src/test/resources/weather_regression/v1/`.
Leur taille est vérifiée exactement dans chaque méthode Kotlin, et un reçu d’exécution horodaté en temps monotone est imprimé après les assertions :

| Famille | Fichier | Cas | Contrat couvert |
|---|---|---:|---|
| Conditions WMO | `wmo.tsv` | 114 | Codes reconnus/inconnus, sévérité séparée de la dominante |
| Nuages et voile | `clouds.tsv` | 40 411 | Seuils 20/45/90 %, 3 couches, preuve solaire, pluie/brouillard prioritaires, valeurs invalides |
| Jours et fuseaux | `daily.tsv` | 8 461 | Pluie isolée aux heures possibles, pluie majoritaire, égalités, lacunes, doublons, codes journaliers, jours de 23/24/25 h |
| Consensus météo | `consensus.tsv` | 609 | Familles indépendantes, égalités sec/pluie, sous-types et précipitations prioritaires |
| Soleil horaire | `sunshine.tsv` | 2 880 | Durées fournisseur, DNI, couverture totale, nuits, valeurs inconnues |
| Soleil multi-modèles | `sunshine_consensus.tsv` | 5 120 | Majorité des familles, sources manquantes, dérivés NOAA, ciel très couvert, médianes |
| Géométrie widgets | `widget_layout.tsv` | 12 870 | Seuils de taille, marges, forme pilule, dispositions Nova/Android |
| Indicateurs widgets | `widget_metrics.tsv` | 1 408 | Ordre nuages/pluie/soleil/convergence, hauteur disponible, profil adapté, icônes et typographie |
| Rafraîchissement widgets | `widget_refresh.tsv` | 2 664 | Frontières des blocs de 15 min, rafraîchissement forcé, reculs d'horloge, tick monotone |
| **Total** | | **74 537** | |

**Un cas signifie une combinaison d'entrées avec un résultat attendu**, et non
un test JUnit différent. Plusieurs milliers de cas passent par une même méthode
JUnit pour limiter le temps de démarrage et garder un message d'échec identifiable
par ID (`cloud-...`, `isolated-...`, `solar-consensus-...`, etc.).

## Exécuter avant une PR ou une release

Sur Windows :

```powershell
python scripts/generate_weather_regression_corpus.py --check
python -m unittest scripts.tests.test_weather_regression_corpus -v
.\gradlew.bat :app:testDebugUnitTest :app:testReleaseUnitTest
.\gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest  # émulateur/appareil requis
```

Sur Linux/macOS : remplacer `gradlew.bat` par `./gradlew`.

**CI :** les contrôles des fixtures s'exécutent dans les jobs Python de
`.github/workflows/quality-checks.yml` et de
`.github/workflows/release-fdroid.yml`. Les tests Kotlin consomment
les TSV dans les tâches `testDebugUnitTest` / `testReleaseUnitTest` :
- `WeatherRegressionCorpusTest` (météo et soleil) ;
- `WidgetRegressionCorpusTest` (géométrie, métriques, cadence).

Le rapport CI **exige un reçu d’exécution portant le nombre exact de lignes** : une méthode verte seule ne suffit plus. Voir `docs/WEATHER_CORPUS_COVERAGE_AUDIT_1_22_0.md` pour la matrice des portions d’application effectivement couvertes.

La validation Android et les tests instrumentés dépendent d'un SDK, d'un
émulateur et d'une distribution Gradle installés. **Ne jamais considérer
un succès Python/Kotlin local comme preuve de la réussite des AndroidTests.**

## Principes de qualité

1. **Oracle indépendant :** les attentes sont écrites à partir d'un contrat
   métier explicite, dans `scripts/generate_weather_regression_corpus.py`,
   et jamais obtenues en enregistrant la sortie de MeteoCompare.
2. **Immuabilité :** SHA-256, comptage et unicité des IDs dans `manifest.json`.
   La commande `--check` échoue si un fichier est différent.
3. **Sans réseau ni horloge réelle :** zones IANA et dates fixes ; mêmes
   résultats sur Windows, Linux et en CI.
4. **Pas de falsification des données :** une absence de soleil, nuages ou WMO
   reste inconnue ; le pourcentage de nébulosité n'est pas corrigé pour
   faire correspondre artificiellement un pictogramme.
5. **Critères météo séparés :** prévision dominante, épisode ponctuel,
   probabilité de pluie et ensoleillement sont des métriques différentes.
6. **Familles indépendantes :** GFS et NCEP_AIGFS partagent la même lignée
   dans les cas de consensus ; une source apparentée n'est pas une voix
   indépendante supplémentaire.
7. **Relecture des changements :** une modification d'oracle doit documenter
   la règle métier justifiant l'évolution, notamment près des seuils.

## Ajouter des scénarios

- Reproduire le bug avec un test ciblé et **un résultat attendu justifié**.
- Étendre le générateur pour ajouter les variantes utiles (frontières,
  null, ordre inverse, changement d'heure, sources différentes).
- Exécuter `python scripts/generate_weather_regression_corpus.py` puis `--check`.
- Inspecter le diff du générateur, les lignes TSV nouvelles et les SHA.
- Faire passer les tests JVM, puis les tests Android sur émulateur.
- Éviter les fichiers géants de réponses météo réelles avec des
  identifiants de localisation, des clés API ou des données personnelles.

**Révision documentée du contrat (moteur conditions phase 2, novembre 2026) :**
les sept cas `middle-gap-*` ont changé de `NONE` à `HOURLY_WMO/CLEAR`.
Dans les jours de 23/24/25 heures, les 5 heures manquantes ne peuvent
plus renverser la majorité des 18/19/20 heures réellement `CLEAR`.
Il s'agit d'une preuve arithmétique et non d'une interpolation ; le
WMO `daily` = `RAIN` demeure le phénomène le plus sévère, pas dominant.
Tous les autres oracles existants sont inchangés.

**Versionnement :** ne pas modifier un scénario v1 pour masquer une régression.
Si la définition métier change intentionnellement, expliquer et réviser le
contrat, ou créer `v2/` pour une rupture majeure.

## Couverture manquante / limites

Ce corpus est **large mais non exhaustif au sens mathématique**. Il ne peut pas
prouver qu'il n'existe aucune combinaison menant à une régression ou aucun
biais de prévision. En particulier, restent nécessaires :

- tests des moteurs numériques V3 et de leur calibration, ViewModels et
  adaptateurs API, déjà présents dans les suites habituelles ;
- intégration Open-Meteo (décodage, alignement de l'heure précédente),
  cache et migrations Room ;
- tests Android Compose pour Today Summary, chronologies, météogramme,
  Chart view, notifications et widgets Glance ;
- essais réels de redimensionnement Nova et autres launchers, polices
  augmentées, langues FR/EN/DE/ES/IT ;
- validation empirique des seuils et du biais statistique sur des observations
  historiques indépendantes, avec MAE/Brier score/fiabilité et incertitude.

Les jeux de cas déterministes protègent des **régressions de contrat** ; ils
ne remplacent pas les données d'observation nécessaires pour évaluer la
qualité scientifique des prévisions.

## Résultats dans GitHub Actions

Les comptages de méthodes unitaires, AndroidTests et scénarios du corpus sont publiés automatiquement dans le **rapport consolidé** de chaque workflow. Voir [Rapport CI](ci-test-report.md) pour les détails et la distinction scénarios/méthodes.
