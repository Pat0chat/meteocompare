"""Variantes proposées, évaluées à côté du comportement actuel de l'app.

Rien ici n'est utilisé par l'application : ce sont des candidats à mesurer
avant tout portage en Kotlin.

- Biais à décroissance exponentielle : `b ← (1 − α)·b + α·erreur`, démarré à
  0, donc naturellement prudent tant qu'il y a peu de jours (le poids des
  données vaut 1 − (1 − α)^n). Aucun seuil brutal à 14 jours.
- Poids de fiabilité : proportionnels à 1 / erreur quadratique (après
  correction du biais), ramenés vers 1 tant que l'historique est court,
  bornés entre 0,5 et 2, puis équilibrés par lignée.
- Fourchette empirique : centrale + quantiles 10 % / 90 % des erreurs passées
  de cette même centrale, à cette échéance, dans cette ville.
- Probabilité de pluie recalibrée : la part de modèles « humides » est
  convertie en fréquence observée, par classes, sur l'historique récent de
  tout le panel (table qui pourrait être livrée avec l'app).
"""
from __future__ import annotations

import bisect
from dataclasses import dataclass
from datetime import date
from typing import Dict, List, Optional, Sequence, Tuple

from . import engines
from .engines import ksum
from .catalogue import group_for

ALPHA = 0.1


@dataclass(frozen=True)
class DecayState:
    bias: float
    mse: float
    data_weight: float


class DecaySeries:
    """États successifs du biais décroissant d'un modèle, indexés par date cible."""

    def __init__(self, samples: Sequence[Tuple[date, float]], alpha: float = ALPHA):
        self.dates: List[date] = []
        self.states: List[DecayState] = []
        bias = 0.0
        mse = 0.0
        retained = 1.0
        for target, error in sorted(samples):
            residual = error - bias
            mse = (1 - alpha) * mse + alpha * residual * residual
            bias = (1 - alpha) * bias + alpha * error
            retained *= (1 - alpha)
            self.dates.append(target)
            self.states.append(DecayState(bias, mse, 1.0 - retained))

    def as_of(self, issue_date: date) -> Optional[DecayState]:
        """Dernier état dont la date cible est strictement antérieure à l'émission."""
        index = bisect.bisect_left(self.dates, issue_date) - 1
        return self.states[index] if index >= 0 else None


def _balanced_custom(entries: Sequence[engines.Entry], weights: Dict[str, float]) -> engines._Balanced:
    valid = [e for e in entries if engines.is_finite(e.value)]
    groups: Dict[str, List[engines.Entry]] = {}
    for entry in valid:
        groups.setdefault(group_for(entry.model), []).append(entry)
    rows = []
    for members in groups.values():
        raw = [weights.get(m.model, 1.0) for m in members]
        mass = engines.clamp(ksum(raw) / len(raw), 0.5, 2.0)
        total = ksum(raw)
        for member, weight in zip(members, raw):
            rows.append(engines.WeightedEntry(member.model, member.value, weight / total * mass))
    return engines._Balanced(tuple(rows), len({e.model for e in valid}), len(groups))


def decayed_consensus(entries: Sequence[engines.Entry], states: Dict[str, Optional[DecayState]],
                      tight: float, wide: float, skill_weights: bool,
                      minimum: Optional[float] = None) -> Optional[float]:
    corrected = []
    weights: Dict[str, float] = {}
    inverse_mse = {}
    for entry in entries:
        state = states.get(entry.model)
        value = entry.value - (state.bias if state else 0.0)
        if minimum is not None:
            value = max(minimum, value)
        corrected.append(engines.Entry(entry.model, value))
        if state and state.mse > 1e-6:
            inverse_mse[entry.model] = (1.0 / state.mse, state.data_weight)
    if skill_weights and len(inverse_mse) >= 2:
        reference = sorted(v for v, _ in inverse_mse.values())[len(inverse_mse) // 2]
        for model, (value, data_weight) in inverse_mse.items():
            raw = value / reference
            weights[model] = engines.clamp(1.0 + (raw - 1.0) * data_weight, 0.5, 2.0)
    balanced = _balanced_custom(corrected, weights) if skill_weights else engines._balance(corrected, {})
    robust = engines._robust_from_balanced(balanced, tight, wide)
    if robust is None:
        return None
    return robust.central if minimum is None else max(minimum, robust.central)


def empirical_interval(central: float, past_errors: Sequence[float],
                       minimum_samples: int = 10) -> Optional[Tuple[float, float]]:
    """Fourchette 80 % : centrale + quantiles 10 % et 90 % des erreurs (obs − centrale)."""
    if len(past_errors) < minimum_samples:
        return None
    ordered = sorted(past_errors)

    def quantile(q: float) -> float:
        position = q * (len(ordered) - 1)
        low = int(position)
        high = min(low + 1, len(ordered) - 1)
        return ordered[low] + (ordered[high] - ordered[low]) * (position - low)

    return central + quantile(0.1), central + quantile(0.9)


PROBABILITY_BINS = (0.0, 0.05, 0.25, 0.5, 0.75, 0.95, 1.000001)


def probability_bin(probability: float) -> int:
    return max(0, bisect.bisect_right(PROBABILITY_BINS, probability) - 1)


def recalibrated_probability(probability: float, history: Sequence[Tuple[float, bool]],
                             prior_weight: float = 4.0) -> float:
    """Fréquence observée dans la classe de `probability`, tirée vers la valeur brute si peu de cas."""
    target = probability_bin(probability)
    wet = 0
    count = 0
    for past_probability, observed_wet in history:
        if probability_bin(past_probability) == target:
            count += 1
            wet += observed_wet
    return (wet + prior_weight * probability) / (count + prior_weight)
