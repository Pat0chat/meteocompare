"""Portage Python fidèle du moteur de prévision V3 de l'application.

Source Kotlin :
- app/src/main/java/com/meteocompare/app/domain/usecase/ForecastConsensus.kt
- app/src/main/java/com/meteocompare/app/domain/usecase/ForecastEngineV3.kt

Ce module ne doit rien « améliorer » : il sert de référence pour mesurer le
comportement ACTUEL de l'app. Toute divergence avec le Kotlin est un bug,
détecté par le corpus de parité (voir parity.py et ForecastEngineV3ParityTest).
Les propositions d'amélioration vivent dans variants.py.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field, replace
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

from .catalogue import group_for

MULTI_CONSENSUS = 'MULTI_CONSENSUS'
CALIBRATION = 'CALIBRATION'
SCENARIOS = 'SCENARIOS'
ADAPTIVE = 'ADAPTIVE'
ENGINES = (MULTI_CONSENSUS, CALIBRATION, SCENARIOS, ADAPTIVE)

MIN_CALIBRATION_SAMPLES = 14
FULL_CALIBRATION_SAMPLES = 30
MIN_CALIBRATION_COVERAGE = 0.34
MIN_CALIBRATED_FAMILIES = 2
MIN_DOMINANT_SCENARIO_SHARE = 0.55
V3_EPS = 1e-9
CONSENSUS_EPS = 1e-12


def ksum(values: Iterable[float]) -> float:
    """Somme naïve de gauche à droite, comme Kotlin `sumOf`.

    Python 3.12+ compense les erreurs d'arrondi dans `sum()` : les résultats
    pourraient alors différer du Kotlin dans le dernier bit.
    """
    total = 0.0
    for value in values:
        total += value
    return total


def kotlin_round(value: float) -> int:
    """Équivalent de Kotlin `roundToInt` (Math.round : demi vers +∞)."""
    return math.floor(value + 0.5)


def is_finite(value: Optional[float]) -> bool:
    return value is not None and math.isfinite(value)


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


# ───────────────────────────── ForecastConsensus ─────────────────────────────

@dataclass(frozen=True)
class Entry:
    model: str
    value: float


@dataclass(frozen=True)
class WeightedEntry:
    model: str
    value: float
    weight: float


@dataclass(frozen=True)
class Stats:
    mean: float
    std_dev: float
    min: float
    max: float
    count: int
    total_weight: float


def _bounded_local_weight(raw: Optional[float]) -> float:
    weight = 1.0 if raw is None else raw
    return clamp(weight, 0.5, 1.5) if math.isfinite(weight) else 1.0


def family_balanced_weights(models: Iterable[str],
                            local_weights: Optional[Dict[str, float]] = None) -> Dict[str, float]:
    local_weights = local_weights or {}
    unique: List[str] = []
    for model in models:
        if model not in unique:
            unique.append(model)
    groups: Dict[str, List[str]] = {}
    for model in unique:
        groups.setdefault(group_for(model), []).append(model)
    out: Dict[str, float] = {}
    for siblings in groups.values():
        total = 0.0
        for model in siblings:
            total += _bounded_local_weight(local_weights.get(model))
        group_mass = clamp(total / len(siblings), 0.75, 1.25)
        for model in siblings:
            out[model] = _bounded_local_weight(local_weights.get(model)) / total * group_mass
    return out


def _usable(entries: Iterable[WeightedEntry]) -> List[WeightedEntry]:
    return [e for e in entries if math.isfinite(e.value) and math.isfinite(e.weight) and e.weight > 0.0]


def weighted_median(entries: Iterable[WeightedEntry]) -> Optional[float]:
    rows = sorted(_usable(entries), key=lambda e: e.value)
    if not rows:
        return None
    total = ksum(e.weight for e in rows)
    half = total / 2.0
    cumulative = 0.0
    for index, row in enumerate(rows):
        cumulative += row.weight
        if cumulative > half + CONSENSUS_EPS:
            return row.value
        if abs(cumulative - half) <= CONSENSUS_EPS and index + 1 < len(rows):
            return (row.value + rows[index + 1].value) / 2.0
    return rows[-1].value


def weighted_stats(entries: Iterable[WeightedEntry]) -> Optional[Stats]:
    rows = _usable(entries)
    if not rows:
        return None
    total = ksum(e.weight for e in rows)
    mean = ksum(e.value * e.weight for e in rows) / total
    variance = ksum(e.weight * (e.value - mean) * (e.value - mean) for e in rows) / total
    return Stats(mean, math.sqrt(variance), min(e.value for e in rows), max(e.value for e in rows),
                 len(rows), total)


def score_from_dispersion(std_dev: float, tight: float, wide: float) -> int:
    if not math.isfinite(std_dev):
        return 0
    if std_dev <= tight:
        return 100
    if std_dev >= wide:
        return 0
    return int(clamp(kotlin_round(100.0 * (1.0 - (std_dev - tight) / (wide - tight))), 0, 100))


# ─────────────────────────────── ForecastEngineV3 ─────────────────────────────

@dataclass(frozen=True)
class CalibrationProfile:
    bias: float
    score: int
    standard_deviation: float
    mean_absolute_error: float
    sample_size: int
    observed_wet_days: Optional[int] = None
    forecast_wet_days: Optional[int] = None
    lead_day: int = 1
    wet_hit_bias: Optional[float] = None
    wet_hit_score: Optional[int] = None
    wet_hit_standard_deviation: Optional[float] = None
    wet_hit_mean_absolute_error: Optional[float] = None
    wet_hit_sample_size: int = 0


@dataclass(frozen=True)
class Interval:
    low: Optional[float]
    high: Optional[float]


@dataclass(frozen=True)
class Scenario:
    share: float
    central: Optional[float]
    low: Optional[float]
    high: Optional[float]


@dataclass(frozen=True)
class ContinuousResult:
    central: Optional[float]
    stats: Optional[Stats]
    interval: Interval
    engine: str
    effective_engine: str
    rows: Tuple[WeightedEntry, ...] = ()
    fallback: bool = False
    fallback_reason: Optional[str] = None
    calibration_coverage: float = 0.0
    calibrated_family_count: int = 0
    calibration_strength: float = 0.0
    historical_score: Optional[float] = None
    scenario_count: int = 1
    dominant_share: Optional[float] = 1.0
    scenario_gap: Optional[float] = None
    scenarios: Tuple[Scenario, ...] = ()
    explanation: Optional[str] = None
    adaptive_trust: Optional[float] = None
    model_count: int = 0
    family_count: int = 0
    engine_convergence_percent: Optional[int] = None


@dataclass(frozen=True)
class ContinuousOptions:
    engine: str = MULTI_CONSENSUS
    local_weights: Dict[str, float] = field(default_factory=dict)
    calibration: Dict[str, CalibrationProfile] = field(default_factory=dict)
    tight: float = 0.5
    wide: float = 3.0
    min: Optional[float] = None
    max: Optional[float] = None


def _bound(value: Optional[float], low: Optional[float], high: Optional[float]) -> Optional[float]:
    if value is None or not math.isfinite(value):
        return value
    if low is not None and high is not None:
        return clamp(value, low, high)
    if low is not None:
        return max(low, value)
    if high is not None:
        return min(high, value)
    return value


def weighted_quantile(entries: Iterable[WeightedEntry], quantile: float) -> Optional[float]:
    rows = sorted(_usable(entries), key=lambda e: e.value)
    if not rows:
        return None
    target = clamp(quantile, 0.0, 1.0) * ksum(e.weight for e in rows)
    cumulative = 0.0
    for row in rows:
        cumulative += row.weight
        if cumulative + V3_EPS >= target:
            return row.value
    return rows[-1].value


def _weighted_mean(entries: Iterable[WeightedEntry]) -> Optional[float]:
    rows = _usable(entries)
    total = ksum(e.weight for e in rows)
    return ksum(e.value * e.weight for e in rows) / total if total > 0 else None


def spread_interval(rows: Sequence[WeightedEntry], center: float, std_dev: float,
                    extra_sigma: float = 0.0) -> Interval:
    q10 = weighted_quantile(rows, .1)
    q90 = weighted_quantile(rows, .9)
    sigma = math.sqrt(max(0.0, std_dev * std_dev + extra_sigma * extra_sigma))
    normal_low = center - 1.2816 * sigma
    normal_high = center + 1.2816 * sigma
    return Interval(
        low=min(q10, normal_low) if q10 is not None else normal_low,
        high=max(q90, normal_high) if q90 is not None else normal_high,
    )


@dataclass(frozen=True)
class _Balanced:
    rows: Tuple[WeightedEntry, ...]
    model_count: int
    family_count: int


@dataclass(frozen=True)
class _Robust:
    central: float
    stats: Stats
    interval: Interval
    rows: Tuple[WeightedEntry, ...]
    convergence: Optional[int]
    model_count: int
    family_count: int


def _distinct(values: Iterable) -> int:
    return len(dict.fromkeys(values))


def _balance(entries: Sequence[Entry], local_weights: Dict[str, float]) -> _Balanced:
    valid = [e for e in entries if math.isfinite(e.value)]
    weights = family_balanced_weights([e.model for e in valid], local_weights)
    rows = []
    for row in valid:
        weight = weights.get(row.model)
        if weight is None or weight <= 0.0:
            continue
        rows.append(WeightedEntry(row.model, row.value, weight))
    return _Balanced(tuple(rows), _distinct(e.model for e in valid),
                     _distinct(group_for(e.model) for e in valid))


def _robust_from_balanced(balanced: _Balanced, tight: float, wide: float) -> Optional[_Robust]:
    if not balanced.rows:
        return None
    median = weighted_median(balanced.rows)
    if median is None:
        return None
    deviations = [replace(r, value=abs(r.value - median)) for r in balanced.rows]
    mad = weighted_quantile(deviations, 0.5)
    mad = 0.0 if mad is None else mad
    robust_scale = max(tight * 0.35, max(mad * 1.4826, V3_EPS))
    huber_limit = 1.5 * robust_scale
    rows = []
    for row in balanced.rows:
        distance = abs(row.value - median)
        factor = 1.0 if distance <= huber_limit else huber_limit / max(distance, V3_EPS)
        rows.append(replace(row, weight=row.weight * factor))
    stats = weighted_stats(rows)
    if stats is None:
        return None
    central = _weighted_mean(rows)
    if central is None:
        return None
    return _Robust(
        central=central,
        stats=stats,
        interval=spread_interval(rows, central, stats.std_dev),
        rows=tuple(rows),
        convergence=score_from_dispersion(stats.std_dev, tight, wide) if balanced.family_count >= 2 else None,
        model_count=balanced.model_count,
        family_count=balanced.family_count,
    )


def _empty_result(engine: str) -> ContinuousResult:
    return ContinuousResult(central=None, stats=None, interval=Interval(None, None), engine=engine,
                            effective_engine=engine, scenario_count=0, dominant_share=None)


def calibration_strength(profile: CalibrationProfile) -> float:
    if profile.sample_size < MIN_CALIBRATION_SAMPLES:
        return 0.0
    return clamp(profile.sample_size / FULL_CALIBRATION_SAMPLES, 0.45, 1.0)


def _multi_consensus(entries: Sequence[Entry], options: ContinuousOptions) -> ContinuousResult:
    balanced = _balance(entries, options.local_weights)
    robust = _robust_from_balanced(balanced, options.tight, options.wide)
    if robust is None:
        return _empty_result(MULTI_CONSENSUS)
    return ContinuousResult(
        central=_bound(robust.central, options.min, options.max),
        stats=robust.stats,
        rows=robust.rows,
        interval=Interval(_bound(robust.interval.low, options.min, options.max),
                          _bound(robust.interval.high, options.min, options.max)),
        engine=MULTI_CONSENSUS,
        effective_engine=MULTI_CONSENSUS,
        explanation='ROBUST_FAMILY_BALANCED',
        model_count=robust.model_count,
        family_count=robust.family_count,
        engine_convergence_percent=robust.convergence,
    )


def _calibration_consensus(entries: Sequence[Entry], options: ContinuousOptions) -> ContinuousResult:
    usable = [e for e in entries if math.isfinite(e.value)]
    if not usable:
        return _empty_result(CALIBRATION)
    family_weights = family_balanced_weights([e.model for e in usable], options.local_weights)
    total_mass = ksum(family_weights.values())
    total_mass = total_mass if total_mass > 0 else 1.0
    calibrated_ids: List[str] = []
    calibrated_mass = 0.0
    weighted_score = 0.0
    weighted_score_mass = 0.0
    noise_sum = 0.0
    noise_mass = 0.0
    strength_sum = 0.0
    skills: Dict[str, float] = {}
    corrected: List[Entry] = []
    for row in usable:
        profile = options.calibration.get(row.model)
        if profile is None or not math.isfinite(profile.bias) or profile.sample_size < MIN_CALIBRATION_SAMPLES:
            corrected.append(row)
            continue
        strength = calibration_strength(profile)
        score = float(clamp(profile.score, 0, 100))
        skill = 0.85 + 0.3 * (score / 100.0)
        family_weight = family_weights.get(row.model, 0.0)
        std = profile.standard_deviation if is_finite(profile.standard_deviation) and profile.standard_deviation >= 0.0 else None
        mae = profile.mean_absolute_error if is_finite(profile.mean_absolute_error) and profile.mean_absolute_error >= 0.0 else None
        noise = max(0.0, std if std is not None else (mae if mae is not None else 0.0))
        calibrated_ids.append(row.model)
        calibrated_mass += family_weight
        weighted_score += score * family_weight
        weighted_score_mass += family_weight
        noise_sum += noise * family_weight
        noise_mass += family_weight
        strength_sum += strength * family_weight
        skills[row.model] = skill
        bounded = _bound(row.value - profile.bias * strength, options.min, options.max)
        corrected.append(replace(row, value=bounded if bounded is not None else row.value))
    coverage = clamp(calibrated_mass / total_mass, 0.0, 1.0)
    calibrated_families = _distinct(group_for(m) for m in calibrated_ids)
    average_strength = strength_sum / calibrated_mass if calibrated_mass > 0 else 0.0
    if calibrated_families < MIN_CALIBRATED_FAMILIES or coverage < MIN_CALIBRATION_COVERAGE:
        fallback = _multi_consensus(entries, replace(options, engine=MULTI_CONSENSUS))
        return replace(fallback, engine=CALIBRATION, fallback=True,
                       fallback_reason='INSUFFICIENT_CALIBRATION', calibration_coverage=coverage,
                       calibrated_family_count=calibrated_families, calibration_strength=average_strength)
    skill_weights = dict(options.local_weights)
    for row in corrected:
        if row.model in skills:
            skill_weights[row.model] = skill_weights.get(row.model, 1.0) * skills[row.model]
    robust = _robust_from_balanced(_balance(corrected, skill_weights), options.tight, options.wide)
    if robust is None:
        return _empty_result(CALIBRATION)
    residual_noise = noise_sum / noise_mass if noise_mass > 0 else 0.0
    extra_sigma = residual_noise * (0.2 + (1.0 - average_strength) * 0.25)
    interval = spread_interval(robust.rows, robust.central, robust.stats.std_dev, extra_sigma)
    return ContinuousResult(
        central=_bound(robust.central, options.min, options.max),
        stats=robust.stats,
        rows=robust.rows,
        interval=Interval(_bound(interval.low, options.min, options.max),
                          _bound(interval.high, options.min, options.max)),
        engine=CALIBRATION,
        effective_engine=CALIBRATION,
        calibration_coverage=coverage,
        calibrated_family_count=calibrated_families,
        calibration_strength=average_strength,
        historical_score=weighted_score / weighted_score_mass if weighted_score_mass > 0 else None,
        explanation='BIAS_CORRECTED_SKILL_WEIGHTED',
        model_count=robust.model_count,
        family_count=robust.family_count,
        engine_convergence_percent=robust.convergence,
    )


def _scenario_split(rows: Sequence[WeightedEntry], tight: float):
    ordered = sorted(_usable(rows), key=lambda e: e.value)
    if len(ordered) < 4:
        return None
    total = ksum(e.weight for e in ordered)
    center = weighted_median(ordered)
    if center is None:
        return None
    mad = weighted_quantile([replace(e, value=abs(e.value - center)) for e in ordered], .5)
    mad = 0.0 if mad is None else mad
    minimum_gap = max(tight * .9, mad * 1.25)
    best_index = -1
    best_score = -math.inf
    left_weight = 0.0
    best_gap = 0.0
    for index in range(len(ordered) - 1):
        left_weight += ordered[index].weight
        right_weight = total - left_weight
        gap = ordered[index + 1].value - ordered[index].value
        minority = min(left_weight, right_weight) / total
        if minority < .18 or gap < minimum_gap:
            continue
        score = gap * (.5 + minority)
        if score > best_score:
            best_score = score
            best_index = index
            best_gap = gap
    if best_index < 0:
        return None
    return ordered[:best_index + 1], ordered[best_index + 1:], best_gap


def _scenario_consensus(entries: Sequence[Entry], options: ContinuousOptions) -> ContinuousResult:
    balanced = _balance(entries, options.local_weights)
    base = _robust_from_balanced(balanced, options.tight, options.wide)
    if base is None:
        return _empty_result(SCENARIOS)
    split = _scenario_split(balanced.rows, options.tight)
    if split is None:
        fallback = _multi_consensus(entries, replace(options, engine=MULTI_CONSENSUS))
        return replace(fallback, engine=SCENARIOS, effective_engine=MULTI_CONSENSUS, fallback=True,
                       fallback_reason='SINGLE_SCENARIO', scenario_count=1, dominant_share=1.0,
                       explanation='SINGLE_SCENARIO')
    first, second, gap_value = split
    total_weight = ksum(e.weight for e in first) + ksum(e.weight for e in second)
    clusters = []
    for rows in (first, second):
        weight = ksum(e.weight for e in rows)
        stats = weighted_stats(rows)
        clusters.append({
            'rows': tuple(rows),
            'weight': weight,
            'share': weight / total_weight if total_weight > 0 else 0.0,
            'central': weighted_median(rows),
            'low': weighted_quantile(rows, .1),
            'high': weighted_quantile(rows, .9),
            'std': stats.std_dev if stats is not None else 0.0,
        })
    clusters.sort(key=lambda c: c['weight'], reverse=True)
    dominant = clusters[0]
    diagnostics = tuple(Scenario(c['share'], _bound(c['central'], options.min, options.max),
                                 _bound(c['low'], options.min, options.max),
                                 _bound(c['high'], options.min, options.max)) for c in clusters)
    if dominant['share'] + V3_EPS < MIN_DOMINANT_SCENARIO_SHARE:
        fallback = _multi_consensus(entries, replace(options, engine=MULTI_CONSENSUS))
        return replace(fallback, engine=SCENARIOS, effective_engine=MULTI_CONSENSUS, fallback=True,
                       fallback_reason='NO_DOMINANT_SCENARIO', scenario_count=2,
                       dominant_share=dominant['share'], scenario_gap=gap_value, scenarios=diagnostics,
                       explanation='BALANCED_SCENARIOS')
    central = dominant['central'] if dominant['central'] is not None else base.central
    interval = spread_interval(dominant['rows'], central, dominant['std'])
    return ContinuousResult(
        central=_bound(central, options.min, options.max),
        stats=weighted_stats(dominant['rows']),
        rows=dominant['rows'],
        interval=Interval(_bound(interval.low, options.min, options.max),
                          _bound(interval.high, options.min, options.max)),
        engine=SCENARIOS,
        effective_engine=SCENARIOS,
        scenario_count=2,
        dominant_share=dominant['share'],
        scenario_gap=gap_value,
        scenarios=diagnostics,
        explanation='DOMINANT_SCENARIO',
        model_count=balanced.model_count,
        family_count=balanced.family_count,
        engine_convergence_percent=int(clamp(kotlin_round(dominant['share'] * 100), 0, 100)),
    )


def _adaptive_consensus(entries: Sequence[Entry], options: ContinuousOptions) -> ContinuousResult:
    multi = _multi_consensus(entries, replace(options, engine=MULTI_CONSENSUS))
    calibration = _calibration_consensus(entries, replace(options, engine=CALIBRATION))
    scenarios = _scenario_consensus(entries, replace(options, engine=SCENARIOS))
    gap = scenarios.scenario_gap
    multi_std = multi.stats.std_dev if multi.stats is not None else 0.0
    strong_scenario = (
        scenarios.scenario_count > 1
        and scenarios.effective_engine == SCENARIOS
        and (scenarios.dominant_share if scenarios.dominant_share is not None else 0.0) >= MIN_DOMINANT_SCENARIO_SHARE
        and (scenarios.dominant_share if scenarios.dominant_share is not None else 1.0) <= 0.82
        and is_finite(gap)
        and gap >= max(options.tight * 1.1, multi_std * 0.5)
    )
    if strong_scenario:
        return replace(scenarios, engine=ADAPTIVE, effective_engine=SCENARIOS, explanation='ADAPTIVE_SCENARIO')
    calibration_ready = (
        not calibration.fallback
        and calibration.calibration_coverage >= 0.5
        and calibration.calibrated_family_count >= MIN_CALIBRATED_FAMILIES
        and (calibration.historical_score is None or calibration.historical_score >= 45.0)
    )
    if calibration_ready and calibration.central is not None and multi.central is not None:
        score = calibration.historical_score if calibration.historical_score is not None else 50.0
        trust = clamp(0.35 + calibration.calibration_coverage * 0.25 + calibration.calibration_strength * 0.2
                      + (score / 100.0) * 0.15, 0.5, 0.85)
        central = _bound(calibration.central * trust + multi.central * (1.0 - trust), options.min, options.max)
        sigma = max(calibration.stats.std_dev if calibration.stats is not None else 0.0, multi_std)
        interval_rows = calibration.rows if calibration.rows else multi.rows
        interval = spread_interval(interval_rows, central if central is not None else calibration.central, sigma)
        return replace(calibration, central=central,
                       interval=Interval(_bound(interval.low, options.min, options.max),
                                         _bound(interval.high, options.min, options.max)),
                       engine=ADAPTIVE, effective_engine=CALIBRATION, adaptive_trust=trust,
                       explanation='ADAPTIVE_CALIBRATION_BLEND')
    return replace(multi, engine=ADAPTIVE, effective_engine=MULTI_CONSENSUS, explanation='ADAPTIVE_ROBUST_FALLBACK')


def continuous(entries: Sequence[Entry], options: ContinuousOptions = ContinuousOptions()) -> ContinuousResult:
    if options.engine == CALIBRATION:
        return _calibration_consensus(entries, options)
    if options.engine == SCENARIOS:
        return _scenario_consensus(entries, options)
    if options.engine == ADAPTIVE:
        return _adaptive_consensus(entries, options)
    return _multi_consensus(entries, options)


# ─────────────────────────────────── Pluie ───────────────────────────────────

@dataclass(frozen=True)
class PrecipitationRow:
    model: str
    amount_mm: Optional[float] = None
    probability_percent: Optional[int] = None


@dataclass(frozen=True)
class PrecipitationResult:
    engine: str
    effective_engine: str
    probability_percent: Optional[int]
    conditional_amount_mm: Optional[float]
    central_amount_mm: Optional[float]
    expected_amount_mm: Optional[float]
    model_count: int
    wet_model_count: int
    occurrence_calibration_coverage: float
    fallback: bool


MAX_PRECIPITATION_MM = 5_000.0


def _physical_precipitation(value: Optional[float]) -> Optional[float]:
    return value if is_finite(value) and 0.0 <= value <= MAX_PRECIPITATION_MM else None


def _occurrence_adjustment(calibration: Dict[str, CalibrationProfile], models: Sequence[str],
                           local_weights: Dict[str, float]) -> Tuple[float, float, int]:
    ids = list(dict.fromkeys(models))
    if not ids:
        return 0.0, 0.0, 0
    balance = family_balanced_weights(ids, local_weights)
    total_mass = ksum(balance.values())
    total_mass = total_mass if total_mass > 0 else 1.0
    adjustment = 0.0
    adjustment_mass = 0.0
    calibrated_ids: List[str] = []
    for model in ids:
        profile = calibration.get(model)
        if profile is None:
            continue
        if (profile.sample_size < MIN_CALIBRATION_SAMPLES or profile.observed_wet_days is None
                or profile.forecast_wet_days is None):
            continue
        n = max(1, profile.sample_size)
        observed = profile.observed_wet_days / n
        forecast = profile.forecast_wet_days / n
        score = int(clamp(profile.score, 0, 100))
        quality = clamp(score / 100.0, 0.25, 1.0)
        strength = calibration_strength(profile)
        family_weight = balance.get(model, 0.0)
        weight = family_weight * quality * strength
        if weight <= 0.0:
            continue
        calibrated_ids.append(model)
        adjustment += (observed - forecast) * weight
        adjustment_mass += weight
    calibrated_mass = ksum(balance.get(m, 0.0) for m in calibrated_ids)
    delta = clamp(adjustment / adjustment_mass, -0.2, 0.2) if adjustment_mass > 0 else 0.0
    return (delta, clamp(calibrated_mass / total_mass, 0.0, 1.0),
            _distinct(group_for(m) for m in calibrated_ids))


def precipitation(rows: Sequence[PrecipitationRow], engine: str = MULTI_CONSENSUS, threshold: float = 0.1,
                  local_weights: Optional[Dict[str, float]] = None,
                  calibration: Optional[Dict[str, CalibrationProfile]] = None,
                  amount_tight: float = 1.0, amount_wide: float = 8.0) -> PrecipitationResult:
    local_weights = local_weights or {}
    calibration = calibration or {}
    usable = []
    for row in rows:
        amount = _physical_precipitation(row.amount_mm)
        probability = row.probability_percent if row.probability_percent is not None and 0 <= row.probability_percent <= 100 else None
        if amount is not None or probability is not None:
            usable.append(PrecipitationRow(row.model, amount, probability))
    if not usable:
        return PrecipitationResult(engine, engine, None, None, None, None, 0, 0, 0.0, False)
    occurrence_weights = family_balanced_weights([r.model for r in usable], local_weights)
    delta, coverage, family_count = _occurrence_adjustment(calibration, [r.model for r in usable], local_weights)
    can_calibrate = (engine in (CALIBRATION, ADAPTIVE) and coverage >= MIN_CALIBRATION_COVERAGE
                     and family_count >= MIN_CALIBRATED_FAMILIES)
    probability_sum = 0.0
    total_weight = 0.0
    for row in usable:
        weight = occurrence_weights.get(row.model)
        if weight is None or weight <= 0.0:
            continue
        if row.probability_percent is not None:
            probability = row.probability_percent / 100.0
        else:
            amount = row.amount_mm if row.amount_mm is not None else -math.inf
            probability = 1.0 if amount > threshold else 0.0
        if can_calibrate:
            probability = clamp(probability + delta * 0.65, 0.0, 1.0)
        probability_sum += weight * probability
        total_weight += weight
    probability_value = probability_sum / total_weight if total_weight > 0.0 else None
    wet_rows = [r for r in usable if (r.amount_mm if r.amount_mm is not None else -math.inf) > threshold]
    amount_calibration: Dict[str, CalibrationProfile] = {}
    for model, profile in calibration.items():
        if not is_finite(profile.wet_hit_bias) or profile.wet_hit_score is None:
            continue
        std = profile.wet_hit_standard_deviation
        if not (is_finite(std) and std >= 0.0) or not is_finite(profile.wet_hit_mean_absolute_error):
            continue
        if profile.wet_hit_sample_size < MIN_CALIBRATION_SAMPLES:
            continue
        amount_calibration[model] = CalibrationProfile(
            bias=profile.wet_hit_bias, score=profile.wet_hit_score, standard_deviation=std,
            mean_absolute_error=profile.wet_hit_mean_absolute_error,
            sample_size=profile.wet_hit_sample_size, lead_day=profile.lead_day)
    amount_entries = [Entry(r.model, r.amount_mm) for r in wet_rows if is_finite(r.amount_mm)]
    amount_result = continuous(amount_entries, ContinuousOptions(
        engine=engine, local_weights=local_weights, calibration=amount_calibration,
        tight=amount_tight, wide=amount_wide, min=threshold))
    conditional = amount_result.central
    amounts = [r.amount_mm for r in usable if is_finite(r.amount_mm)]
    if not amounts:
        central_amount = None
    elif probability_value is not None and probability_value >= 0.5 and conditional is not None:
        central_amount = conditional
    else:
        central_amount = 0.0
    if probability_value is not None and conditional is not None:
        expected = probability_value * conditional
    elif amounts and not wet_rows:
        expected = 0.0
    else:
        expected = None
    return PrecipitationResult(
        engine=engine,
        effective_engine=amount_result.effective_engine,
        probability_percent=(int(clamp(kotlin_round(probability_value * 100), 0, 100))
                             if probability_value is not None else None),
        conditional_amount_mm=conditional,
        central_amount_mm=central_amount,
        expected_amount_mm=expected,
        model_count=len(usable),
        wet_model_count=len(wet_rows),
        occurrence_calibration_coverage=coverage,
        fallback=amount_result.fallback,
    )
