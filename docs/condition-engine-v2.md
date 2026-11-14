# MeteoCompare — moteur de conditions v2 (novembre 2026)

## Objectif et périmètre

Cette évolution traite quatre axes : (1) classification horaire dans un modèle,
(2) agrégation temporelle des journées et nuits, (3) consensus multi-modèles,
(4) validation et prévention des régressions. Elle remplace les règles de
classification dupliquées par un résultat explicable ; les interfaces publiques
historiques `WeatherCondition?` sont conservées en adaptation afin de limiter
les régressions d'intégration.

La présence d'un « ? » n'est pas à elle seule un bug : les données peuvent
réellement manquer, être insuffisantes, ou être contradictoires. L'objectif est
**d'éliminer les indéterminations évitables, pas de convertir les lacunes en
certitudes inventées**.

## Phase 1 — résolution horaire par modèle

- `ConditionDecisionEngine.hourly(hourly, index)` est la référence unique.
- Les WMO natifs valides sont prioritaires ; ils peuvent être affinés en ciel
  voilé selon la règle solaire déjà existante.
- Le repli utilise exclusivement les variables du **même modèle**.
- Quantités négatives, non finies, ou excessives sont rejetées ; la
  précipitation horaire est étiquetée « heure précédente ».
- Sans WMO, un cumul >0,1 mm et une température strictement >2 °C permettent une
  **estimation** de précipitation liquide ; près de 0 °C, la phase reste
  ambiguë entre précipitation liquide et neige. Ce seuil est un garde-fou
  d'ingénierie, pas un diagnostic thermodynamique ni une calibration.
- Pour la nébulosité, le total valide prime. Avec des couches partielles,
  une classification ne peut être rendue que si les bornes physiques (minimum
  observé et maximum possible) donnent **la même** classe. Aucune couche
  manquante n'est remplie par un zéro inventé.
- Pas de recopie silencieuse de l'heure précédente ni de mélange de modèles.

`ConditionDecision` contient : `condition`, `status`, `origin`, `coverage`,
`agreement`, `alternatives`, `reasons`, `notablePhenomenon`. Les statuts sont
`RELIABLE`, `ESTIMATED`, `INCOMPLETE`, `AMBIGUOUS`, `UNAVAILABLE`. `agreement`
n'est **pas** une probabilité calibrée d'exactitude.

## Phase 2 — périodes Day, Night et Full Day

- `ConditionDecisionEngine.period` résout les intervalles réels en `Instant`.
  Une journée compte réellement 23, 24 ou 25 heures selon le fuseau et la
  transition été/hiver ; les nuits traversent la date suivante.
- Les horodatages dupliqués ne comptent qu'une fois ; le WMO natif est
  prioritaire sur une reconstruction au même instant.
- Les conditions couvrent des *durées* exactes (y compris les fractions
  d'heure au lever/coucher), pas le nombre naïf de lignes.
- **Majorité irréversible** : une condition représentant strictement plus de
  la moitié de l'intervalle complet est dominante même si toutes les autres
  heures inconnues contredisaient cette condition.
- Sinon, une condition n'est proposée qu'avec une couverture représentative
  (`>=75 %` avec les deux bords et des trous internes bornés) et un consensus
  hiérarchique cohérent. Ces garde-fous sont des règles d'ingénierie explicites
  et à calibrer avec observations ; ils ne sont pas une garantie statistique.
- Si seule la famille sèche/humide est déterminable, mais aucun sous-type
  n'est justifié, la condition reste `INCOMPLETE` avec alternatives.
- Le WMO `daily` est **uniquement** un repli marqué `DAILY_WMO_MAXIMUM` pour
  la journée complète ; il ne décrit pas la dominante et n'est jamais injecté
  comme résultat jour/nuit. Un orage bref peut être conservé dans
  `notablePhenomenon` sans remplacer l'icône dominante.
- Les dates présentes dans les heures mais pas dans l'axe `daily` sont
  récupérées par la timeline, le comparateur et le tableau par modèle.

## Phase 3 — consensus multi-modèles

- Vote hiérarchique famille sèche/humide, famille de phénomène, sous-classe ;
  les familles apparentées ne multiplient pas artificiellement leur poids.
- Les WMO natifs de deux lignées indépendantes restent prioritaires.
  Une égalité peut être départagée seulement avec **deux autres familles
  indépendantes** présentant des indices dérivés, sans présenter ce mélange
  comme une probabilité calibrée.
- Lorsqu'il y a peu ou pas de WMO natifs, les entrées dérivées par modèle
  alimentent le vote. La combinaison de médianes température/précipitation/
  nuages provenant de modèles différents est **interdite** pour créer une
  condition synthétique. Un ancien test qui exigeait cette combinaison a été
  corrigé explicitement.
- `WeatherConditionConsensus.resolveDecision(...)` conserve le diagnostic
  d'accord et la couverture **entre modèles** (à distinguer de la couverture
  **temporelle** d'un jour).
- Les calculs de Home, chronologie et matrices par modèle utilisent le nouveau
  moteur ou ses adaptateurs. Les interfaces présentent `≠` quand une condition
  est véritablement ambiguë, `—` quand la donnée manque, et les libellés
  accessibles sont traduits en six langues. Les icônes estimées sont atténuées.
- Le nouveau diagnostic est disponible dans `HourlyForecastAggregate`,
  `SimplifiedTimelinePoint` et `DayConditionsRow`. Il ne faut pas interpréter
  la valeur `coverage` comme une probabilité de bon résultat.

## Phase 4 — preuve de non-régression et validation

Tests intégrés dans le dépôt :

- `ConditionDecisionEngineTest` : WMO prioritaire, pluie et phase ambiguë,
  valeurs invalides, couches nuageuses partielles, trou temporel malgré
  dominante certaine, météo sévère minoritaire, pluie/temps sec à égalité,
  dates horaires sans daily, données solaires absentes, nuits et DST 23/25 h.
- `ConditionEnginePropertyTest` : permutations horaires et modèles,
  doublons, borne de majorité, journée de 23/24/25 h, familles de modèles.
- `SimplifiedTimelineTest` / `WeatherConditionTest` : tests d'intégration
  ciblés pour journées dont seules les heures sont disponibles.
- Corpus préexistant : 8 461 scénarios de conditions quotidiennes dans
  `daily.tsv`. **Sept oracles `middle-gap-*`** ont été revus : la dominante
  est maintenant prouvée malgré cinq heures inconnues. Aucun autre oracle
  journalier n'a été modifié.

### Vérifications exécutées dans le présent environnement

- **90 tests Kotlin du noyau : 90 réussis** avec `kotlinc` et un relais JUnit
  minimal (dont les anciens tests de régression et les nouveaux tests).
- **8 461 scénarios journaliers du corpus rejoués : zéro divergence**.
- **216 tests Python du dépôt : réussis** ; générateur de corpus `--check`,
  ressources linguistiques et prévisualisations Compose contrôlés.

### Limites explicites

L'environnement ne contient pas la distribution **Gradle 9.7.1** et n'a pas
accès au serveur pour la télécharger. **L'APK, la compilation Kotlin Android
complète, les nouveaux tests d'intégration Android, le rendu Compose et les
widgets n'ont donc pas été validés à l'exécution.** Les 90 tests de noyau ne
valent pas compilation de l'application. Lancer dans Android Studio ou CI :

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -q
python3 scripts/generate_weather_regression_corpus.py --check
```

Aucune réponse Open-Meteo historique de Gif-sur-Yvette n'était fournie :
**la diminution réelle du nombre de « ? » sur cette ville et l'amélioration
statistique des prévisions ne sont pas mesurées**. Pour valider la qualité
scientifique, capturer des réponses réelles **à dates identiques** (avant/après),
rejouer tous les modèles et comparer les résultats à des observations
indépendantes sur plusieurs échéances.

### Points à approfondir pour une version ultérieure

- La précipitation accumulée est horodatée par sa **fin d'heure**, contrairement
  au caractère instantané du WMO. La provenance est conservée, mais un
  réalignement des bornes dans toutes les vues demanderait des tests de
  transition et une modification explicite de la convention d'affichage.
- `effectiveCloudCover` conserve, en absence de total, l'heuristique historique
  utilisant trois couches. Cette formule n'est pas une loi physique de
  chevauchement nuageux ; préférer à terme une méthode documentée/calibrée.
- Les seuils de pluie (0,1 mm) et de couverture représentative (75 %) sont
  **heuristiques**. Les évaluer empiriquement avant de prétendre à une
  optimisation scientifique de la précision.
- Pas d'interpolation arbitraire entre heures, pas de déduction d'orages depuis
  un cumul, pas de neige certaine depuis la seule température à 2 m.
