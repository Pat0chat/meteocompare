"""Construction du jeu de données journalier du banc d'essai.

Prévisions : reproduction de BootstrapBiasHistoryUseCase. Les séries horaires
`_previous_dayN` sont agrégées sur la journée civile locale, et une journée
n'est retenue que si toutes ses heures (23 à 25) sont présentes.

Observations : journée civile locale reconstruite à partir des METAR, avec un
relevé par heure (le plus proche de l'heure pleine). Tmax, Tmin et vent max
sont ainsi définis exactement comme côté prévision (maximum de valeurs
horaires). La pluie vient de GHCN-Daily, dont la fenêtre de cumul (souvent
06 h–06 h UTC) ne coïncide pas avec la journée civile : le décalage d'un jour
qui colle le mieux à la réanalyse est choisi par station et documenté.
"""
from __future__ import annotations

import math
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta, timezone as dt_timezone
from typing import Dict, List, Optional, Sequence
from zoneinfo import ZoneInfo

from .catalogue import BY_API_KEY, BY_NAME
from .sources import Fetcher, ghcn_daily, metar_hourly, previous_runs, reanalysis_daily

VARIABLES = ('tmax', 'tmin', 'wind', 'precip')
KNOT_TO_KMH = 1.852
MIN_OBSERVED_HOURS = 20


@dataclass
class CityData:
    icao: str
    name: str
    timezone: str
    # forecasts[lead][model][variable][date] = valeur
    forecasts: Dict[int, Dict[str, Dict[str, Dict[date, float]]]] = field(default_factory=dict)
    # observed / reanalysis[variable][date] = valeur
    observed: Dict[str, Dict[date, float]] = field(default_factory=dict)
    reanalysis: Dict[str, Dict[date, float]] = field(default_factory=dict)
    precip_shift_days: Optional[int] = None


def _finite(value) -> Optional[float]:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if math.isfinite(number) else None


def aggregate_previous_runs(hourly: Dict[str, list], models: Sequence[str], leads: Sequence[int],
                            start: date, end: date) -> Dict[int, Dict[str, Dict[str, Dict[date, float]]]]:
    times = hourly.get('time', [])
    dates = []
    for raw in times:
        try:
            dates.append(datetime.fromisoformat(raw).date())
        except (TypeError, ValueError):
            dates.append(None)
    expected = defaultdict(int)
    for day in dates:
        if day is not None and start <= day <= end:
            expected[day] += 1
    out: Dict[int, Dict[str, Dict[str, Dict[date, float]]]] = {}
    for lead in leads:
        out[lead] = {}
        for model in models:
            key = BY_NAME[model].api_key
            temp = hourly.get(f'temperature_2m_previous_day{lead}_{key}', [])
            rain = hourly.get(f'precipitation_previous_day{lead}_{key}', [])
            wind = hourly.get(f'wind_speed_10m_previous_day{lead}_{key}', [])
            acc = defaultdict(lambda: {'t': [], 'p': [], 'w': []})
            for index, day in enumerate(dates):
                if day is None or day not in expected:
                    continue
                t = _finite(temp[index]) if index < len(temp) else None
                p = _finite(rain[index]) if index < len(rain) else None
                w = _finite(wind[index]) if index < len(wind) else None
                if t is not None and -100.0 <= t <= 70.0:
                    acc[day]['t'].append(t)
                if p is not None and 0.0 <= p <= 5000.0:
                    acc[day]['p'].append(p)
                if w is not None and 0.0 <= w <= 500.0:
                    acc[day]['w'].append(w)
            series = {v: {} for v in VARIABLES}
            for day, values in acc.items():
                hours = expected[day]
                if not 23 <= hours <= 25:
                    continue
                if len(values['t']) == hours:
                    series['tmax'][day] = max(values['t'])
                    series['tmin'][day] = min(values['t'])
                if len(values['p']) == hours:
                    series['precip'][day] = sum(values['p'])
                if len(values['w']) == hours:
                    series['wind'][day] = max(values['w'])
            if any(series.values()):
                out[lead][model] = series
    return out


def metar_daily(rows: List[dict], tz_name: str) -> Dict[str, Dict[date, float]]:
    zone = ZoneInfo(tz_name)
    # Un relevé par heure locale : le plus proche de l'heure pleine.
    best: Dict[datetime, tuple] = {}
    for row in rows:
        try:
            utc = datetime.strptime(row['valid'], '%Y-%m-%d %H:%M').replace(tzinfo=dt_timezone.utc)
        except (TypeError, ValueError):
            continue
        local = utc.astimezone(zone)
        hour = local.replace(minute=0, second=0, microsecond=0)
        offset = local.minute if local.minute <= 30 else 60 - local.minute
        if local.minute > 30:
            hour = hour + timedelta(hours=1)
        temp = _finite(row.get('tmpc'))
        knots = _finite(row.get('sknt'))
        if temp is not None and not -70.0 <= temp <= 60.0:
            temp = None
        if knots is not None and not 0.0 <= knots <= 150.0:
            knots = None
        current = best.get(hour)
        if current is None or offset < current[0]:
            best[hour] = (offset, temp, knots)
    by_day = defaultdict(lambda: {'t': [], 'w': []})
    for hour, (_, temp, knots) in best.items():
        if temp is not None:
            by_day[hour.date()]['t'].append(temp)
        if knots is not None:
            by_day[hour.date()]['w'].append(knots * KNOT_TO_KMH)
    out = {'tmax': {}, 'tmin': {}, 'wind': {}}
    for day, values in by_day.items():
        if len(values['t']) >= MIN_OBSERVED_HOURS:
            out['tmax'][day] = max(values['t'])
            out['tmin'][day] = min(values['t'])
        if len(values['w']) >= MIN_OBSERVED_HOURS:
            out['wind'][day] = max(values['w'])
    return out


def _wet_agreement(obs: Dict[date, float], reference: Dict[date, float], shift: int) -> float:
    hits = total = 0
    for day, value in reference.items():
        observed = obs.get(day + timedelta(days=shift))
        if observed is None:
            continue
        total += 1
        hits += (observed > 1.0) == (value > 1.0)
    return hits / total if total else 0.0


def ghcn_precipitation(rows: Dict[str, dict], reanalysis: Dict[date, float]):
    raw = {}
    for key, row in rows.items():
        value = _finite(row.get('PRCP'))
        if value is not None and 0.0 <= value <= 1000.0:
            raw[date.fromisoformat(key)] = value
    if not raw:
        return {}, None
    shift = max((0, 1), key=lambda s: _wet_agreement(raw, reanalysis, s))
    return {day - timedelta(days=shift): value for day, value in raw.items()}, shift


def load_city(fetcher: Fetcher, station: dict, models: Sequence[str], leads: Sequence[int],
              start: date, end: date) -> CityData:
    city = CityData(station['icao'], station['name'], station['timezone'])
    keys = [BY_NAME[m].api_key for m in models]
    hourly = previous_runs(fetcher, station['lat'], station['lon'], station['timezone'], keys, leads, start, end)
    city.forecasts = aggregate_previous_runs(hourly, models, leads, start, end)
    daily = reanalysis_daily(fetcher, station['lat'], station['lon'], station['timezone'], start, end)
    mapping = {'tmax': 'temperature_2m_max', 'tmin': 'temperature_2m_min',
               'precip': 'precipitation_sum', 'wind': 'wind_speed_10m_max'}
    times = [date.fromisoformat(t) for t in daily.get('time', [])]
    for variable, key in mapping.items():
        city.reanalysis[variable] = {d: v for d, v in zip(times, daily.get(key, [])) if _finite(v) is not None}
    city.observed = metar_daily(metar_hourly(fetcher, station['icao'], start, end), station['timezone'])
    city.observed['precip'] = {}
    if station.get('ghcn'):
        rows = ghcn_daily(fetcher, station['ghcn']['id'], start - timedelta(days=1), end + timedelta(days=1))
        city.observed['precip'], city.precip_shift_days = ghcn_precipitation(rows, city.reanalysis['precip'])
    return city


def api_key_to_name(key: str) -> str:
    return BY_API_KEY[key].name
