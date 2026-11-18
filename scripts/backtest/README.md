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

## Premiers résultats (mai–septembre 2026, 20 stations)

Rapport complet : [`results/2026-05-a-2026-09.md`](results/2026-05-a-2026-09.md).
Erreur absolue moyenne à J+1, écart relatif au Multi-consensus entre parenthèses :

| Méthode | Tmax | Tmin | Vent max | Pluie (Brier) |
|---|---:|---:|---:|---:|
| Multi-consensus (défaut de l'app) | 0,97 °C | 1,28 °C | 8,1 km/h | 0,111 |
| Calibration actuelle (réanalyse) | 1,05 (+9 %) | 1,28 (non calibrée) | 8,2 (+2 %) | 0,104 |
| Adaptatif actuel (réanalyse) | 1,05 (+8 %) | 1,29 (+1 %) | 8,2 (+2 %) | 0,104 |
| Scénarios | 1,01 (+4 %) | 1,29 (+1 %) | 8,2 (+1 %) | — |
| Calibration sur stations | 0,79 (−19 %) | 1,01 (−21 %) | 3,6 (−56 %) | 0,089 |
| Biais décroissant + poids, sur stations | 0,78 (−20 %) | 0,98 (−23 %) | 3,6 (−56 %) | — |
| Probabilité de pluie recalibrée | — | — | — | 0,071 (−36 %) |

Ce que ces chiffres disent :

1. **La calibration actuelle dégrade la Tmax à J+1** (+9 %) et n'aide qu'un
   peu à J+5–J+7. La cause est la référence : face aux stations, la
   réanalyse est trop froide de 0,5 °C en Tmax et sous-estime le vent max de
   8 km/h. Corriger les modèles vers elle ajoute ce biais.
2. **Calibrer sur les observations des stations** fait mieux que le
   Multi-consensus dans 20 villes sur 20 en Tmax et 18 sur 20 en Tmin
   (−19 % et −21 % d'erreur), et le biais tombe à presque zéro. Sur le vent, le gain de −56 % vient surtout de l'exposition
   des anémomètres d'aéroport (voir les limites).
3. **La méthode d'estimation compte peu à côté de la référence** : biais à
   décroissance et poids de fiabilité ne gagnent qu'un à deux points de plus
   que la moyenne sur 30 jours, mais sans seuil à 14 jours.
4. **Le moteur Adaptatif dilue la calibration** : même avec des profils sur
   stations, il ne gagne que 7 % en Tmax, contre 19 % pour la Calibration.
5. **Le moteur Scénarios n'améliore aucune variable journalière** : il est
   moins bon que le Multi-consensus dans 18 villes sur 20 en Tmax.
6. **La probabilité de pluie est mal calibrée** : la convertir en fréquence
   observée par classe réduit le score de Brier de 36 %.
7. **Les fourchettes de l'app sont trop étroites et le deviennent de plus en
   plus avec l'échéance** : 74 % des Tmax observées tombent dedans à J+1,
   56 % à J+7, et seulement 23 % pour le vent. La fourchette empirique tient
   73–75 % à toutes les échéances.

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
