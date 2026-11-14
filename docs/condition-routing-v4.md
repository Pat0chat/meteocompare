# MeteoCompare — Carte de raccordement du moteur de conditions (V4)

## Contrat

1. Un `ConditionDecision` porte **la condition** et le **diagnostic** (`RELIABLE`, `ESTIMATED`, `AMBIGUOUS`, `INCOMPLETE`, `UNAVAILABLE`). Aucun écran de prévision ne doit recalculer le pictogramme à partir de médianes croisées ni transformer `null` en beau temps.
2. Chaque modèle horaire passe par `ConditionDecisionEngine.hourly` (WMO natif prioritaire, sinon variables cohérentes du modèle). Le repli n'invente pas de pluie dominante si la probabilité d'occurrence native du modèle est strictement inférieure à 50 %.
3. Chaque période complète, jour et nuit passe par `ConditionDecisionEngine.period`, appelé pour tous les modèles par `ForecastConditionResolver.periodConsensus`. La condition multi-modèles est arbitrée par `WeatherConditionConsensus.resolveDecision`.
4. La condition « actuelle » et la première case Home/widget à **la même échéance UTC** viennent de `ForecastAggregates`. `ConfidenceCalculator.currentWeatherDecision` est un simple délégateur.
5. Chaque surface conserve le diagnostic : dans Compose via `ConditionStatusIcon` (tap et détail), dans Glance/Bitmap/notifications via marqueur et libellé accessible/localisé lorsque possible.

## Matrice du code de production

| Surface | Producteur | Rendu / état | Vérification |
| --- | --- | --- | --- |
| Accueil, carte réduite | `CityListViewModel` → `ForecastAggregates.next24h` | `CityListUiState.Loaded.currentConditionDecision` → `ConditionStatusIcon` | `test_condition_wiring_v4_contract.py` |
| Accueil, carte étendue / bande 24 h | même agrégateur | `MiniForecastStrip.conditionDiagnostics` | même test |
| Détail ville, condition actuelle | `ConfidenceCalculator.currentWeatherDecision` → `ForecastAggregates` | `CityDetailUiState` → `TodaySummaryCard` | même test |
| Chronologie horaire simplifiée | `ConditionDecisionEngine.hourly` par modèle, `WeatherConditionConsensus` | `SimplifiedTimelinePoint.conditionDiagnostic` → `ConditionStatusIcon` | test de raccordement + tests Kotlin du moteur |
| Chronologie jour/nuit | `ForecastConditionResolver.periodConsensus` par période solaire | `dayConditionDiagnostic` / `nightConditionDiagnostic` | tests Kotlin DST, périodes et statuts |
| Chronologie Daily journée entière | `ForecastConditionResolver.periodConsensus(FULL_DAY)` | `conditionDiagnostic` dans le point | test de raccordement + tests `ForecastConditionResolverTest` |
| Chart View | `GraphicForecastViewModel` → `buildSimplifiedTimeline` | légende + icônes diagnostiquées | test de raccordement + tests Compose existants |
| Tableaux horaires par modèle | `ConditionDecisionEngine.hourly` | `HourlyWeatherByModelTable` → `ConditionStatusIcon` | test de raccordement |
| Tableaux Daily par modèle | `ConditionDecisionEngine.period` via calculateur | `WeatherByModelTable` → `ConditionStatusIcon` | tests Kotlin du moteur + test de raccordement |
| Comparaison des moteurs | `EngineComparisonBuilder` → `ForecastConditionResolver.periodConsensus` | `EngineComparisonValues.conditionDecision` → `EngineConditionTableRow` | test de raccordement + tests résolveur |
| Widgets actuels (Glance) | `WidgetData` → `ForecastAggregates` / calculateur | `MeteoWidget.WeatherGlyph`, `MeteoValueWidgets.ValueWeatherGlyph` | test de raccordement |
| Widgets 5 heures / 5 jours | `WidgetData` → `buildSimplifiedTimeline` | `WidgetForecastItem.conditionDecision` → `WeatherGlyph` | test de raccordement |
| Widgets mini-prévision, heatmap, heatmap tendance | `WidgetData` → `ForecastAggregates.next12h` | trois renderers bitmap avec `conditionDiagnostics` | test de raccordement |
| Notifications journalières | `WeatherNotificationEvaluator` → `EngineComparisonBuilder` | `WeatherNotification.DailySummary.conditionDecision`, libellé + icône marquée | test de raccordement |
| Scénarios météo représentatifs | `WeatherScenarioBuilder` → `resolveHourlyCondition` → `ConditionDecisionEngine.hourly` | illustration de scénario, pas consensus heure | test de raccordement |
| Explication de la confiance | `ConfidenceExplanationViewModel` → `ConfidenceCalculator.currentWeatherCondition` | couleur d'ambiance, pas de pictogramme météo de prévision | analyse statique |

## Exceptions voulues

- Les icônes de la page **Comment ça marche** sont des exemples fixes et non des prévisions du moteur.
- L'icône du **scénario météo représentatif** sur l'accueil exprime le type du scénario, pas une condition météo centrale à une heure. Le scénario emploie néanmoins les conditions horaires du moteur.
- Les illustrations de légende sont purement décoratives.
- Les écrans sans état météorologique (réglages, cartes de géolocalisation, contributions) n'ont pas de `ConditionDecision` à afficher.
- Les widgets/notifications ne peuvent pas ouvrir le dialogue Compose depuis leur pictogramme; ils présentent un marqueur et, pour les sorties accessibles, un libellé.

## Ce qui reste à valider dans Android

La matrice et les tests statiques démontrent la **présence des connexions dans les sources**, pas le rendu final sur terminal. Obligatoire avant publication : compilation de `:app`, tests instrumentés Compose/Glance et de notifications, puis contrôle manuel des contenus et des tailles du widget sur plusieurs launchers. Un audit d'égalité des icônes doit comparer des instants UTC et des configurations de moteur identiques (les vues ne partent pas toutes nécessairement du même ancrage horaire).
