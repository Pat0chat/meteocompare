"""Portage des profils de calibration de l'application.

Source Kotlin :
- domain/model/ModelReliability.kt (ModelReliabilityCalculator.compute)
- domain/usecase/ForecastEngineContextProvider.kt (buildProfile)
- data/local/BiasSampleDao.kt (fenêtre [asOf - 30 j, asOf[ par échéance)
"""
from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import date, timedelta
from typing import Dict, List, Optional, Sequence

from .engines import ksum, CalibrationProfile, FULL_CALIBRATION_SAMPLES, kotlin_round

MIN_SAMPLES_FOR_BIAS = 14
WET_DAY_THRESHOLD_MM = 0.1

# (tolérance, échelle MAE, échelle biais, échelle écart-type)
SCALES = {
    'TEMPERATURE': (1.5, 2.4, 1.2, 3.0),
    'PRECIPITATION': (1.0, 3.0, 1.5, 4.0),
    'WIND_SPEED': (5.0, 8.0, 5.0, 10.0),
}


@dataclass(frozen=True)
class Sample:
    target_date: date
    forecast: float
    observation: float


@dataclass(frozen=True)
class Reliability:
    score: int
    mean_bias: float
    mean_absolute_error: float
    standard_deviation: float
    sample_size: int
    observed_wet_days: Optional[int] = None
    forecast_wet_days: Optional[int] = None


def _exponential_score(value: float, scale: float) -> float:
    return min(1.0, max(0.0, math.exp(-max(value, 0.0) / scale)))


def _sample_std(values: Sequence[float], mean: float) -> float:
    if len(values) <= 1:
        return 0.0
    return math.sqrt(ksum((v - mean) ** 2 for v in values) / (len(values) - 1))


def compute(variable: str, samples: Sequence[Sample], window_days: int = 30) -> Optional[Reliability]:
    by_date: Dict[date, Sample] = {}
    for sample in samples:
        if math.isfinite(sample.forecast) and math.isfinite(sample.observation):
            by_date[sample.target_date] = sample
    ordered = [by_date[d] for d in sorted(by_date)]
    if len(ordered) < MIN_SAMPLES_FOR_BIAS:
        return None
    errors = [s.forecast - s.observation for s in ordered]
    abs_errors = [abs(e) for e in errors]
    mean_bias = ksum(errors) / len(errors)
    mae = ksum(abs_errors) / len(abs_errors)
    std = _sample_std(errors, mean_bias)
    tolerance, mae_scale, bias_scale, spread_scale = SCALES[variable]
    within = ksum(1 for e in abs_errors if e <= tolerance) / len(errors)
    coverage = min(1.0, max(0.0, len(ordered) / window_days))
    score = (_exponential_score(mae, mae_scale) * 0.42
             + _exponential_score(abs(mean_bias), bias_scale) * 0.20
             + _exponential_score(std, spread_scale) * 0.16
             + within * 0.17
             + coverage * 0.05)
    score = int(min(100, max(0, kotlin_round(score * 100.0))))
    observed_wet = forecast_wet = None
    if variable == 'PRECIPITATION':
        observed_wet = ksum(1 for s in ordered if s.observation > WET_DAY_THRESHOLD_MM)
        forecast_wet = ksum(1 for s in ordered if s.forecast > WET_DAY_THRESHOLD_MM)
    return Reliability(score, mean_bias, mae, std, len(ordered), observed_wet, forecast_wet)


def build_profile(variable: str, lead_day: int, samples: Sequence[Sample]) -> Optional[CalibrationProfile]:
    reliability = compute(variable, samples, FULL_CALIBRATION_SAMPLES)
    if reliability is None:
        return None
    wet_hit = None
    wet_hit_count = 0
    if variable == 'PRECIPITATION':
        hits = [s for s in samples if s.forecast > WET_DAY_THRESHOLD_MM and s.observation > WET_DAY_THRESHOLD_MM]
        wet_hit_count = len(hits)
        wet_hit = compute('PRECIPITATION', hits, FULL_CALIBRATION_SAMPLES)
    return CalibrationProfile(
        bias=reliability.mean_bias,
        score=reliability.score,
        standard_deviation=reliability.standard_deviation,
        mean_absolute_error=reliability.mean_absolute_error,
        sample_size=reliability.sample_size,
        observed_wet_days=reliability.observed_wet_days,
        forecast_wet_days=reliability.forecast_wet_days,
        lead_day=lead_day,
        wet_hit_bias=wet_hit.mean_bias if wet_hit else None,
        wet_hit_score=wet_hit.score if wet_hit else None,
        wet_hit_standard_deviation=wet_hit.standard_deviation if wet_hit else None,
        wet_hit_mean_absolute_error=wet_hit.mean_absolute_error if wet_hit else None,
        wet_hit_sample_size=wet_hit_count,
    )


def window_samples(history: Dict[date, Sample], issue_date: date,
                   window_days: int = FULL_CALIBRATION_SAMPLES) -> List[Sample]:
    """Échantillons visibles le jour d'émission : cible dans [émission - 30 j, émission[."""
    start = issue_date - timedelta(days=window_days)
    return [s for d, s in history.items() if start <= d < issue_date]
