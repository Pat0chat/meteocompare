"""Téléchargement des données du banc d'essai, avec cache disque.

Sources (toutes gratuites et sans clé) :
- Open-Meteo Previous Runs : ce que chaque modèle prévoyait 1 à 7 jours avant
  (exactement l'API utilisée par l'app pour son historique de biais) ;
- Open-Meteo Archive : la réanalyse que l'app utilise aujourd'hui comme
  référence de calibration ;
- Iowa Environmental Mesonet (IEM) : archive mondiale des METAR, relevés
  horaires de température et de vent des aéroports ;
- NOAA NCEI GHCN-Daily : cumuls de pluie journaliers des stations
  synoptiques (les METAR hors États-Unis ne transmettent pas la pluie).

Chaque réponse est mise en cache sous un nom dérivé de l'URL : relancer le
banc d'essai ne refait aucune requête.
"""
from __future__ import annotations

import csv
import hashlib
import io
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, timedelta
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence

USER_AGENT = 'MeteoCompare-backtest/1.0 (+https://github.com/Pat0chat/meteocompare)'


class SourceError(RuntimeError):
    pass


class Fetcher:
    def __init__(self, cache_dir: Path, offline: bool = False, retries: int = 5, pause_s: float = 0.4):
        self.cache_dir = Path(cache_dir)
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.offline = offline
        self.retries = retries
        self.pause_s = pause_s
        self.network_calls = 0

    def get(self, url: str, params: Dict[str, object]) -> bytes:
        query = urllib.parse.urlencode([(k, v) for k, v in params.items() if v is not None], doseq=True)
        full = f'{url}?{query}'
        path = self.cache_dir / (hashlib.sha256(full.encode()).hexdigest()[:32] + '.cache')
        if path.exists():
            return path.read_bytes()
        if self.offline:
            raise SourceError(f'Absent du cache (mode hors ligne) : {full}')
        last_error: Optional[Exception] = None
        for attempt in range(self.retries):
            try:
                request = urllib.request.Request(full, headers={'User-Agent': USER_AGENT})
                with urllib.request.urlopen(request, timeout=120) as response:
                    body = response.read()
                self.network_calls += 1
                self._raise_for_api_error(body, full)
                path.write_bytes(body)
                time.sleep(self.pause_s)
                return body
            except SourceError:
                raise
            except (urllib.error.URLError, TimeoutError, ConnectionError, OSError) as error:
                last_error = error
                time.sleep(min(30.0, 2.0 ** attempt))
        raise SourceError(f'Échec après {self.retries} tentatives : {full} ({last_error})')

    @staticmethod
    def _raise_for_api_error(body: bytes, url: str) -> None:
        if body[:1] != b'{':
            return
        try:
            payload = json.loads(body)
        except ValueError:
            return
        if isinstance(payload, dict) and payload.get('error'):
            raise SourceError(f"Erreur API ({payload.get('reason')}) : {url}")


def _chunks(start: date, end: date, days: int) -> Iterable[tuple]:
    cursor = start
    while cursor <= end:
        chunk_end = min(end, cursor + timedelta(days=days - 1))
        yield cursor, chunk_end
        cursor = chunk_end + timedelta(days=1)


def previous_runs(fetcher: Fetcher, lat: float, lon: float, timezone: str, model_keys: Sequence[str],
                  leads: Sequence[int], start: date, end: date, chunk_days: int = 31) -> Dict[str, list]:
    """Séries horaires `<variable>_previous_day<lead>_<modèle>` fusionnées sur la période."""
    hourly_vars = ','.join(f'{var}_previous_day{lead}' for lead in leads
                           for var in ('temperature_2m', 'precipitation', 'wind_speed_10m'))
    chunks = []
    for chunk_start, chunk_end in _chunks(start, end, chunk_days):
        body = fetcher.get('https://previous-runs-api.open-meteo.com/v1/forecast', {
            'latitude': f'{lat:.4f}', 'longitude': f'{lon:.4f}', 'models': ','.join(model_keys),
            'hourly': hourly_vars, 'timezone': timezone,
            'start_date': chunk_start.isoformat(), 'end_date': chunk_end.isoformat(),
            'wind_speed_unit': 'kmh', 'temperature_unit': 'celsius', 'precipitation_unit': 'mm',
        })
        chunks.append(json.loads(body).get('hourly') or {})
    keys = sorted({key for chunk in chunks for key in chunk})
    merged: Dict[str, list] = {key: [] for key in keys}
    for chunk in chunks:
        size = len(chunk.get('time', []))
        for key in keys:
            merged[key].extend(chunk.get(key, [None] * size))
    return merged


def reanalysis_daily(fetcher: Fetcher, lat: float, lon: float, timezone: str,
                     start: date, end: date) -> Dict[str, list]:
    body = fetcher.get('https://archive-api.open-meteo.com/v1/archive', {
        'latitude': f'{lat:.4f}', 'longitude': f'{lon:.4f}',
        'daily': 'temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max',
        'timezone': timezone, 'start_date': start.isoformat(), 'end_date': end.isoformat(),
        'wind_speed_unit': 'kmh',
    })
    return json.loads(body).get('daily') or {}


def metar_hourly(fetcher: Fetcher, icao: str, start: date, end: date) -> List[dict]:
    """Relevés METAR de routine en UTC : température (°C) et vent moyen (nœuds)."""
    last = end + timedelta(days=1)
    body = fetcher.get('https://mesonet.agron.iastate.edu/cgi-bin/request/asos.py', {
        'station': icao, 'data': ['tmpc', 'sknt'],
        'year1': start.year, 'month1': start.month, 'day1': start.day,
        'year2': last.year, 'month2': last.month, 'day2': last.day,
        'tz': 'Etc/UTC', 'format': 'onlycomma', 'latlon': 'no', 'missing': 'M',
        'trace': 'T', 'report_type': 3,
    })
    rows = []
    for row in csv.DictReader(io.StringIO(body.decode('utf-8', 'replace'))):
        rows.append({'valid': row.get('valid'), 'tmpc': row.get('tmpc'), 'sknt': row.get('sknt')})
    return rows


def ghcn_daily(fetcher: Fetcher, station: str, start: date, end: date) -> Dict[str, dict]:
    body = fetcher.get('https://www.ncei.noaa.gov/access/services/data/v1', {
        'dataset': 'daily-summaries', 'stations': station, 'startDate': start.isoformat(),
        'endDate': end.isoformat(), 'dataTypes': 'PRCP,TMAX,TMIN', 'format': 'csv', 'units': 'metric',
    })
    out = {}
    for row in csv.DictReader(io.StringIO(body.decode('utf-8', 'replace'))):
        out[row['DATE']] = {k: row.get(k) for k in ('PRCP', 'TMAX', 'TMIN')}
    return out
