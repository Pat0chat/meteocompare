# Banc d'essai des moteurs de prévision

Ce dossier rejoue, jour par jour, ce que MeteoCompare aurait affiché dans une
vingtaine de villes, puis le compare à ce que les stations météo ont
réellement mesuré. Il sert à deux choses :

1. **mesurer les moteurs actuels** (Multi-consensus, Calibration, Scénarios,
   Adaptatif) contre des observations réelles, et non contre une réanalyse ;
2. **évaluer des améliorations avant de les porter en Kotlin** (biais à
   décroissance, poids de fiabilité, fourchettes calibrées, probabilité de
   pluie recalibrée, calibration sur stations).

Rien ici n'est embarqué dans l'application.

## Lancer

```bash
# Premier lancement : télécharge les données (≈ 20 min, mises en cache dans build/backtest-cache)
python3 -m scripts.backtest.run

# Lancements suivants, sans réseau
python3 -m scripts.backtest.run --offline

# Sous-ensemble, autre période ou autres échéances
python3 -m scripts.backtest.run --stations LFPO,LFLL --start 2026-06-01 --end 2026-08-31 --leads 1,2,3
```

Le rapport est écrit dans `build/backtest/report.md`, les chiffres bruts dans
`build/backtest/results.json`.

## Ce qui est mesuré, et comment

| Étape | Source | Détail |
|---|---|---|
| Prévisions passées | Open-Meteo Previous Runs | Mêmes requêtes et même agrégation journalière que l'app (`BootstrapBiasHistoryUseCase`) : journée civile locale, toutes les heures présentes. Sélection de modèles par défaut de l'app. |
| Référence actuelle de l'app | Open-Meteo Archive | Réanalyse utilisée aujourd'hui pour les profils de calibration. |
| Vérité terrain : Tmax, Tmin, vent | METAR (archive IEM) | Un relevé par heure (le plus proche de l'heure pleine), journée civile locale, au moins 20 heures. Même définition que côté prévision : maximum ou minimum de valeurs horaires. |
| Vérité terrain : pluie | NOAA GHCN-Daily | Les METAR hors États-Unis ne transmettent pas la pluie. Le cumul GHCN couvre souvent 06 h–06 h UTC : le décalage d'un jour qui colle le mieux est choisi par station et indiqué dans le rapport. |

Chaque point du panel est placé **sur l'aéroport** : on mesure la qualité
des prévisions, sans y mêler l'écart entre un centre-ville et sa station.

**Pas de fuite d'information.** Pour une prévision à J+L de la date D, émise
le jour D − L, les profils de calibration n'utilisent que les dates cibles
de [D − L − 30 j, D − L[, exactement comme `BiasSampleDao`. Les variantes
proposées suivent la même règle.

## Fidélité au code de l'app

`engines.py` et `reliability.py` sont des portages ligne à ligne de
`ForecastConsensus.kt`, `ForecastEngineV3.kt` et `ModelReliability.kt`
(y compris l'arrondi `roundToInt` et l'addition naïve de `sumOf`).
Deux garde-fous tournent en CI :

- `scripts/tests/test_backtest.py` compare le catalogue, les lignées et les
  constantes du portage au code Kotlin ;
- `ForecastEngineV3ParityTest` rejoue 720 cas générés par
  `python3 -m scripts.backtest.parity` avec le moteur Kotlin et exige les
  mêmes résultats à 10⁻⁹ près.

Toute modification d'un moteur Kotlin doit donc s'accompagner du même
changement dans le portage, puis de `python3 -m scripts.backtest.parity`.

## Fichiers

| Fichier | Rôle |
|---|---|
| `catalogue.py` | Modèles, clés API, lignées, sélection par défaut |
| `engines.py` | Portage fidèle des quatre moteurs et du moteur pluie |
| `reliability.py` | Portage du score de fiabilité et des profils de calibration |
| `variants.py` | Améliorations candidates (jamais utilisées par l'app) |
| `sources.py` | Téléchargements avec cache disque |
| `dataset.py` | Agrégation journalière des prévisions et des observations |
| `panel.py`, `panel.json` | Stations du panel (METAR + GHCN) |
| `run.py`, `report.py` | Évaluation et rapport |
| `parity.py` | Corpus de parité Python ↔ Kotlin |

## Limites connues

- Une seule saison évaluée par défaut (mai–septembre 2026), 20 stations.
  GHCN-Daily publie avec plusieurs semaines de retard, ce qui borne la fin
  de la période.
- Les METAR de nombreux pays donnent la température au degré près.
- Le vent d'aéroport est mesuré en terrain dégagé, à 10 m : il est souvent
  plus fort qu'en ville. Calibrer sur ces stations rapproche la prévision du
  vent « standard », pas du vent ressenti dans une rue abritée.
- La pluie GHCN n'est pas cumulée sur la même fenêtre que la journée civile.
  Les comparaisons entre méthodes restent justes, car elles partagent ce
  défaut, mais les valeurs absolues sont bruitées.
- Le banc d'essai suppose la réanalyse disponible dès le lendemain. Dans
  l'app, elle arrive avec quelques jours de retard, ce qui retarde un peu
  la calibration.
