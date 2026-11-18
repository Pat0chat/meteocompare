"""Corpus de parité entre le portage Python et le moteur Kotlin.

Les cas sont tirés d'un générateur pseudo-aléatoire à graine fixe (dont des
situations à deux camps, des profils de calibration partiels et des valeurs
bornées), puis calculés par le portage Python. ForecastEngineV3ParityTest
recalcule chaque cas avec le code Kotlin de l'application : si les deux
implémentations divergent, la CI échoue et le banc d'essai ne mesure plus
l'app.

    python3 -m scripts.backtest.parity          # régénère
    python3 -m scripts.backtest.parity --check  # vérifie (CI)
"""
from __future__ import annotations

import argparse
import random
from datetime import date, timedelta
from pathlib import Path
from typing import List, Optional

from . import engines, reliability
from .catalogue import MODELS

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'app/src/test/resources/engine_parity/v1'
SEED = 20261118


def _f(value: Optional[float]) -> str:
    return '_' if value is None else repr(float(value))


def _i(value: Optional[int]) -> str:
    return '_' if value is None else str(int(value))


def _random_profile(rng: random.Random, precipitation: bool) -> engines.CalibrationProfile:
    n = rng.choice([5, 12, 14, 15, 20, 26, 30, 31])
    wet_hits = rng.choice([0, 6, 14, 18]) if precipitation else 0
    return engines.CalibrationProfile(
        bias=round(rng.uniform(-3, 3), 3),
        score=rng.randint(0, 100),
        standard_deviation=round(rng.uniform(0, 4), 3) if rng.random() > 0.1 else 0.0,
        mean_absolute_error=round(rng.uniform(0, 4), 3),
        sample_size=n,
        observed_wet_days=rng.randint(0, n) if precipitation else None,
        forecast_wet_days=rng.randint(0, n) if precipitation else None,
        lead_day=rng.randint(1, 7),
        wet_hit_bias=round(rng.uniform(-4, 4), 3) if wet_hits else None,
        wet_hit_score=rng.randint(0, 100) if wet_hits else None,
        wet_hit_standard_deviation=round(rng.uniform(0, 5), 3) if wet_hits else None,
        wet_hit_mean_absolute_error=round(rng.uniform(0, 5), 3) if wet_hits else None,
        wet_hit_sample_size=wet_hits,
    )


def _profile_text(model: str, p: engines.CalibrationProfile) -> str:
    return ':'.join([model, _f(p.bias), str(p.score), _f(p.standard_deviation), _f(p.mean_absolute_error),
                     str(p.sample_size), _i(p.observed_wet_days), _i(p.forecast_wet_days), str(p.lead_day),
                     _f(p.wet_hit_bias), _i(p.wet_hit_score), _f(p.wet_hit_standard_deviation),
                     _f(p.wet_hit_mean_absolute_error), str(p.wet_hit_sample_size)])


def continuous_rows(rng: random.Random, count: int = 400) -> List[tuple]:
    names = [m.name for m in MODELS]
    rows = []
    for index in range(count):
        engine = engines.ENGINES[index % 4]
        kind = rng.choice(['temperature', 'wind', 'cloud', 'bimodal'])
        models = rng.sample(names, rng.randint(1, 10))
        if kind == 'bimodal':
            split = rng.randint(1, len(models) - 1) if len(models) > 1 else 1
            values = [rng.gauss(10, 0.6) if i < split else rng.gauss(14, 0.6) for i in range(len(models))]
            tight, wide, low, high = 0.5, 3.0, None, None
        elif kind == 'wind':
            values = [max(0.0, rng.gauss(18, 6)) for _ in models]
            tight, wide, low, high = 2.0, 12.0, 0.0, None
        elif kind == 'cloud':
            values = [rng.uniform(0, 100) for _ in models]
            tight, wide, low, high = 10.0, 50.0, 0.0, 100.0
        else:
            values = [rng.gauss(17, 1.8) for _ in models]
            tight, wide, low, high = 0.5, 3.0, None, None
        values = [round(v, 2) for v in values]
        profiles = {m: _random_profile(rng, False) for m in models if rng.random() < 0.7}
        entries = [engines.Entry(m, v) for m, v in zip(models, values)]
        result = engines.continuous(entries, engines.ContinuousOptions(
            engine=engine, calibration=profiles, tight=tight, wide=wide, min=low, max=high))
        rows.append((
            f'C{index:04d}', engine, _f(tight), _f(wide), _f(low), _f(high),
            ';'.join(f'{m}:{_f(v)}' for m, v in zip(models, values)),
            ';'.join(_profile_text(m, p) for m, p in profiles.items()) or '_',
            _f(result.central), _f(result.interval.low), _f(result.interval.high),
            result.effective_engine, 'true' if result.fallback else 'false',
            _i(result.engine_convergence_percent),
        ))
    return rows


def precipitation_rows(rng: random.Random, count: int = 200) -> List[tuple]:
    names = [m.name for m in MODELS]
    rows = []
    for index in range(count):
        engine = engines.ENGINES[index % 4]
        models = rng.sample(names, rng.randint(1, 9))
        amounts = []
        for _ in models:
            draw = rng.random()
            amounts.append(None if draw < 0.08 else 0.0 if draw < 0.45 else round(rng.expovariate(0.3), 2))
        profiles = {m: _random_profile(rng, True) for m in models if rng.random() < 0.75}
        result = engines.precipitation([engines.PrecipitationRow(m, a) for m, a in zip(models, amounts)],
                                       engine=engine, threshold=0.1, calibration=profiles,
                                       amount_tight=1.0, amount_wide=8.0)
        rows.append((
            f'P{index:04d}', engine,
            ';'.join(f'{m}:{_f(a)}' for m, a in zip(models, amounts)),
            ';'.join(_profile_text(m, p) for m, p in profiles.items()) or '_',
            _i(result.probability_percent), _f(result.conditional_amount_mm), _f(result.central_amount_mm),
            _f(result.expected_amount_mm),
        ))
    return rows


def reliability_rows(rng: random.Random, count: int = 120) -> List[tuple]:
    rows = []
    start = date(2026, 6, 1)
    for index in range(count):
        variable = ('TEMPERATURE', 'PRECIPITATION', 'WIND_SPEED')[index % 3]
        n = rng.choice([10, 13, 14, 15, 22, 30])
        samples = []
        for day in range(n):
            if variable == 'PRECIPITATION':
                obs = 0.0 if rng.random() < 0.5 else round(rng.expovariate(0.25), 1)
                fc = max(0.0, round(obs + rng.gauss(0.3, 2.0), 1))
            elif variable == 'WIND_SPEED':
                obs = round(rng.uniform(5, 45), 1)
                fc = max(0.0, round(obs + rng.gauss(-4, 5), 1))
            else:
                obs = round(rng.uniform(5, 32), 1)
                fc = round(obs + rng.gauss(0.4, 1.6), 1)
            samples.append(reliability.Sample(start + timedelta(days=day), fc, obs))
        result = reliability.compute(variable, samples, 30)
        rows.append((
            f'R{index:04d}', variable,
            ';'.join(f'{s.target_date.isoformat()}:{_f(s.forecast)}:{_f(s.observation)}' for s in samples),
            _i(result.score if result else None), _f(result.mean_bias if result else None),
            _f(result.mean_absolute_error if result else None), _f(result.standard_deviation if result else None),
            _i(result.sample_size if result else None),
            _i(result.observed_wet_days if result else None), _i(result.forecast_wet_days if result else None),
        ))
    return rows


def _table(header: tuple, rows: List[tuple]) -> bytes:
    return ('\n'.join('\t'.join(row) for row in [header, *rows]) + '\n').encode('utf-8')


def outputs() -> dict:
    rng = random.Random(SEED)
    return {
        'continuous.tsv': _table(('id', 'engine', 'tight', 'wide', 'min', 'max', 'entries', 'profiles',
                                  'central', 'low', 'high', 'effective', 'fallback', 'convergence'),
                                 continuous_rows(rng)),
        'precipitation.tsv': _table(('id', 'engine', 'rows', 'profiles', 'probability', 'conditional',
                                     'central', 'expected'), precipitation_rows(rng)),
        'reliability.tsv': _table(('id', 'variable', 'samples', 'score', 'bias', 'mae', 'std', 'n',
                                   'observed_wet', 'forecast_wet'), reliability_rows(rng)),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    for name, content in outputs().items():
        destination = OUT / name
        if args.check:
            actual = destination.read_bytes().replace(b'\r\n', b'\n') if destination.exists() else None
            if actual != content:
                raise SystemExit(f'OUT OF DATE: {destination}. Run python3 -m scripts.backtest.parity')
        else:
            destination.write_bytes(content)
    print('engine parity corpus OK')


if __name__ == '__main__':
    main()
