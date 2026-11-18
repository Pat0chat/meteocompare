"""Banc d'essai des moteurs de prévision MeteoCompare.

Rejoue, jour par jour et sans regarder l'avenir, ce que l'app aurait affiché
pour chaque ville du panel, puis le compare aux observations des stations.

    python3 -m scripts.backtest.run --start 2026-05-01 --end 2026-09-30

Le premier lancement télécharge les données (cache dans build/backtest-cache) ;
les suivants tournent hors ligne avec --offline.
"""
from __future__ import annotations

import argparse
import json
import math
from collections import defaultdict
from datetime import date, timedelta
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from . import engines, variants
from .catalogue import DEFAULT_SELECTION
from .dataset import CityData, load_city
from .panel import load_panel
from .reliability import Sample, build_profile, window_samples
from .sources import Fetcher, SourceError

WARMUP_DAYS = 37
CONTINUOUS = {
    # variable : (variable de biais app, serré, large, minimum, calibrée par l'app)
    'tmax': ('TEMPERATURE', 0.5, 3.0, None, True),
    'tmin': ('TEMPERATURE', 0.5, 3.0, None, False),
    'wind': ('WIND_SPEED', 2.0, 12.0, 0.0, True),
}
METHODS = (
    'MULTI', 'SCENARIOS', 'CALIB_REANALYSE', 'ADAPTIVE_REANALYSE',
    'CALIB_OBS', 'ADAPTIVE_OBS', 'DECROISSANT_OBS', 'DECROISSANT_POIDS_OBS',
    'MEDIANE_SIMPLE', 'ECMWF_SEUL', 'MEILLEUR_RECENT',
)
PRECIP_METHODS = ('MULTI', 'CALIB_REANALYSE', 'ADAPTIVE_REANALYSE', 'CALIB_OBS', 'ADAPTIVE_OBS',
                  'PROBA_RECALIBREE')
INTERVALS = ('MULTI', 'CALIB_REANALYSE', 'EMPIRIQUE')


class Metric:
    __slots__ = ('n', 'abs', 'sum', 'sq')

    def __init__(self):
        self.n = 0
        self.abs = 0.0
        self.sum = 0.0
        self.sq = 0.0

    def add(self, error: float) -> None:
        self.n += 1
        self.abs += abs(error)
        self.sum += error
        self.sq += error * error

    def as_dict(self) -> dict:
        if not self.n:
            return {'n': 0}
        return {'n': self.n, 'mae': self.abs / self.n, 'bias': self.sum / self.n,
                'rmse': math.sqrt(self.sq / self.n)}


def _profiles(history: Dict[str, Dict[date, Sample]], variable: str, lead: int,
              issue: date) -> Dict[str, engines.CalibrationProfile]:
    out = {}
    for model, samples in history.items():
        profile = build_profile(variable, lead, window_samples(samples, issue))
        if profile is not None:
            out[model] = profile
    return out


def _histories(city: CityData, lead: int, variable: str, reference: Dict[date, float]):
    out: Dict[str, Dict[date, Sample]] = {}
    for model, series in city.forecasts.get(lead, {}).items():
        out[model] = {d: Sample(d, f, reference[d]) for d, f in series.get(variable, {}).items() if d in reference}
    return out


def evaluate(cities: List[CityData], leads, eval_start: date, eval_end: date) -> dict:
    metrics = defaultdict(Metric)              # (var, lead, method)
    city_metrics = defaultdict(Metric)         # (var, lead, method, icao)
    interval_stats = defaultdict(lambda: [0, 0, 0.0])  # (var, lead, kind) -> n, inside, width
    precip_records = []
    reference_gap = defaultdict(Metric)        # var -> réanalyse − observation
    days = [eval_start + timedelta(days=i) for i in range((eval_end - eval_start).days + 1)]

    for city in cities:
        for variable in ('tmax', 'tmin', 'wind', 'precip'):
            for day in days:
                rea = city.reanalysis.get(variable, {}).get(day)
                obs = city.observed.get(variable, {}).get(day)
                if rea is not None and obs is not None:
                    reference_gap[variable].add(rea - obs)

        for lead in leads:
            by_model = city.forecasts.get(lead, {})
            for variable, (bias_variable, tight, wide, minimum, app_calibrates) in CONTINUOUS.items():
                observed = city.observed.get(variable, {})
                hist_rea = _histories(city, lead, variable, city.reanalysis.get(variable, {}))
                hist_obs = _histories(city, lead, variable, observed)
                decay = {m: variants.DecaySeries([(d, s.forecast - s.observation) for d, s in h.items()])
                         for m, h in hist_obs.items()}
                past_errors: List[Tuple[date, float]] = []
                for day in days:
                    truth = observed.get(day)
                    entries = [engines.Entry(m, s[variable][day]) for m, s in by_model.items()
                               if day in s.get(variable, {})]
                    if truth is None or not entries:
                        continue
                    issue = day - timedelta(days=lead)
                    options = engines.ContinuousOptions(tight=tight, wide=wide, min=minimum)
                    prof_rea = _profiles(hist_rea, bias_variable, lead, issue) if app_calibrates else {}
                    prof_obs = _profiles(hist_obs, bias_variable, lead, issue)
                    results = {
                        'MULTI': engines.continuous(entries, options),
                        'SCENARIOS': engines.continuous(entries, engines.ContinuousOptions(
                            engine=engines.SCENARIOS, tight=tight, wide=wide, min=minimum)),
                        'CALIB_REANALYSE': engines.continuous(entries, engines.ContinuousOptions(
                            engine=engines.CALIBRATION, calibration=prof_rea, tight=tight, wide=wide, min=minimum)),
                        'ADAPTIVE_REANALYSE': engines.continuous(entries, engines.ContinuousOptions(
                            engine=engines.ADAPTIVE, calibration=prof_rea, tight=tight, wide=wide, min=minimum)),
                        'CALIB_OBS': engines.continuous(entries, engines.ContinuousOptions(
                            engine=engines.CALIBRATION, calibration=prof_obs, tight=tight, wide=wide, min=minimum)),
                        'ADAPTIVE_OBS': engines.continuous(entries, engines.ContinuousOptions(
                            engine=engines.ADAPTIVE, calibration=prof_obs, tight=tight, wide=wide, min=minimum)),
                    }
                    centrals = {name: result.central for name, result in results.items()}
                    states = {e.model: decay[e.model].as_of(issue) if e.model in decay else None
                              for e in entries}
                    centrals['DECROISSANT_OBS'] = variants.decayed_consensus(
                        entries, states, tight, wide, skill_weights=False, minimum=minimum)
                    centrals['DECROISSANT_POIDS_OBS'] = variants.decayed_consensus(
                        entries, states, tight, wide, skill_weights=True, minimum=minimum)
                    values = sorted(e.value for e in entries)
                    middle = len(values) // 2
                    centrals['MEDIANE_SIMPLE'] = (values[middle] if len(values) % 2
                                                  else (values[middle - 1] + values[middle]) / 2)
                    ecmwf = by_model.get('ECMWF', {}).get(variable, {}).get(day)
                    centrals['ECMWF_SEUL'] = ecmwf
                    centrals['MEILLEUR_RECENT'] = _best_recent(entries, hist_obs, issue) or centrals['MULTI']

                    for name, central in centrals.items():
                        if central is None:
                            continue
                        metrics[(variable, lead, name)].add(central - truth)
                        city_metrics[(variable, lead, name, city.icao)].add(central - truth)

                    # Fourchettes : celles de l'app et la fourchette empirique proposée.
                    for kind, interval in (('MULTI', results['MULTI'].interval),
                                           ('CALIB_REANALYSE', results['CALIB_REANALYSE'].interval)):
                        _record_interval(interval_stats, (variable, lead, kind), interval.low, interval.high, truth)
                    best = centrals['DECROISSANT_POIDS_OBS']
                    window = [e for d, e in past_errors if issue - timedelta(days=30) <= d < issue]
                    empirical = variants.empirical_interval(best, window) if best is not None else None
                    if empirical:
                        _record_interval(interval_stats, (variable, lead, 'EMPIRIQUE'), empirical[0],
                                         empirical[1], truth)
                    if best is not None:
                        past_errors.append((day, truth - best))

            _precipitation(city, lead, days, metrics, city_metrics, precip_records)

    _recalibrate_precipitation(precip_records, metrics, city_metrics)
    return {
        'metrics': {f'{v}|{l}|{m}': x.as_dict() for (v, l, m), x in metrics.items()},
        'city_metrics': {f'{v}|{l}|{m}|{c}': x.as_dict() for (v, l, m, c), x in city_metrics.items()},
        'intervals': {f'{v}|{l}|{k}': {'n': s[0], 'coverage': s[1] / s[0] if s[0] else None,
                                       'mean_width': s[2] / s[0] if s[0] else None}
                      for (v, l, k), s in interval_stats.items()},
        'reference_gap': {v: m.as_dict() for v, m in reference_gap.items()},
    }


def _record_interval(stats, key, low, high, truth) -> None:
    if low is None or high is None:
        return
    entry = stats[key]
    entry[0] += 1
    entry[1] += low <= truth <= high
    entry[2] += high - low


def _best_recent(entries, history, issue) -> Optional[float]:
    best_value, best_mae = None, math.inf
    for entry in entries:
        samples = window_samples(history.get(entry.model, {}), issue)
        if len(samples) < 14:
            continue
        mae = sum(abs(s.forecast - s.observation) for s in samples) / len(samples)
        if mae < best_mae:
            best_value, best_mae = entry.value, mae
    return best_value


def _precipitation(city: CityData, lead: int, days, metrics, city_metrics, records) -> None:
    observed = city.observed.get('precip', {})
    if not observed:
        return
    by_model = city.forecasts.get(lead, {})
    hist_rea = _histories(city, lead, 'precip', city.reanalysis.get('precip', {}))
    hist_obs = _histories(city, lead, 'precip', observed)
    for day in days:
        truth = observed.get(day)
        rows = [engines.PrecipitationRow(m, s['precip'][day]) for m, s in by_model.items()
                if day in s.get('precip', {})]
        if truth is None or not rows:
            continue
        issue = day - timedelta(days=lead)
        wet = truth > 0.1
        profiles = {'REANALYSE': _profiles(hist_rea, 'PRECIPITATION', lead, issue),
                    'OBS': _profiles(hist_obs, 'PRECIPITATION', lead, issue)}
        results = {
            'MULTI': engines.precipitation(rows, engines.MULTI_CONSENSUS),
            'CALIB_REANALYSE': engines.precipitation(rows, engines.CALIBRATION, calibration=profiles['REANALYSE']),
            'ADAPTIVE_REANALYSE': engines.precipitation(rows, engines.ADAPTIVE, calibration=profiles['REANALYSE']),
            'CALIB_OBS': engines.precipitation(rows, engines.CALIBRATION, calibration=profiles['OBS']),
            'ADAPTIVE_OBS': engines.precipitation(rows, engines.ADAPTIVE, calibration=profiles['OBS']),
        }
        for name, result in results.items():
            _record_precip(metrics, city_metrics, city.icao, lead, name, result.probability_percent,
                           result.central_amount_mm, truth, wet)
        multi = results['MULTI']
        if multi.probability_percent is not None:
            records.append({'city': city.icao, 'lead': lead, 'day': day, 'issue': issue,
                            'p': multi.probability_percent / 100.0, 'wet': wet,
                            'amount': multi.central_amount_mm, 'truth': truth})


def _record_precip(metrics, city_metrics, icao, lead, name, probability, amount, truth, wet) -> None:
    if probability is not None:
        p = probability / 100.0
        metrics[('precip_brier', lead, name)].add((p - wet) ** 2)
        city_metrics[('precip_brier', lead, name, icao)].add((p - wet) ** 2)
    if amount is not None:
        metrics[('precip_mm', lead, name)].add(amount - truth)
        city_metrics[('precip_mm', lead, name, icao)].add(amount - truth)


def _recalibrate_precipitation(records, metrics, city_metrics, window_days: int = 45) -> None:
    by_lead = defaultdict(list)
    for record in records:
        by_lead[record['lead']].append(record)
    for lead, items in by_lead.items():
        items.sort(key=lambda r: r['day'])
        for record in items:
            start = record['issue'] - timedelta(days=window_days)
            history = [(r['p'], r['wet']) for r in items if start <= r['day'] < record['issue']]
            p = variants.recalibrated_probability(record['p'], history)
            metrics[('precip_brier', lead, 'PROBA_RECALIBREE')].add((p - record['wet']) ** 2)
            city_metrics[('precip_brier', lead, 'PROBA_RECALIBREE', record['city'])].add((p - record['wet']) ** 2)
            if record['amount'] is not None:
                metrics[('precip_mm', lead, 'PROBA_RECALIBREE')].add(record['amount'] - record['truth'])


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--start', type=date.fromisoformat, default=date(2026, 5, 1))
    parser.add_argument('--end', type=date.fromisoformat, default=date(2026, 9, 30))
    parser.add_argument('--leads', default='1,3,5,7')
    parser.add_argument('--stations', default='', help='Liste ICAO séparée par des virgules (défaut : tout le panel)')
    parser.add_argument('--cache-dir', default='build/backtest-cache')
    parser.add_argument('--offline', action='store_true', help='Utiliser uniquement le cache')
    parser.add_argument('--json', default='build/backtest/results.json')
    parser.add_argument('--report', default='build/backtest/report.md')
    args = parser.parse_args()

    leads = tuple(int(x) for x in args.leads.split(','))
    wanted = {s.strip().upper() for s in args.stations.split(',') if s.strip()}
    fetcher = Fetcher(Path(args.cache_dir), offline=args.offline)
    cities = []
    for station in load_panel():
        if wanted and station['icao'] not in wanted:
            continue
        try:
            cities.append(load_city(fetcher, station, DEFAULT_SELECTION, leads,
                                    args.start - timedelta(days=WARMUP_DAYS), args.end))
        except SourceError as error:
            print(f"{station['icao']} ignorée : {error}")
    results = evaluate(cities, leads, args.start, args.end)
    results['config'] = {'start': args.start.isoformat(), 'end': args.end.isoformat(), 'leads': leads,
                         'models': DEFAULT_SELECTION, 'cities': [c.icao for c in cities],
                         'precip_shift_days': {c.icao: c.precip_shift_days for c in cities}}
    Path(args.json).parent.mkdir(parents=True, exist_ok=True)
    Path(args.json).write_text(json.dumps(results, indent=1, default=str), encoding='utf-8')
    from .report import render
    report = render(results)
    Path(args.report).parent.mkdir(parents=True, exist_ok=True)
    Path(args.report).write_text(report, encoding='utf-8')
    print(report)


if __name__ == '__main__':
    main()
