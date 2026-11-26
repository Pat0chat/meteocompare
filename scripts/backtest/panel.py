"""Panel de villes du banc d'essai.

Chaque point est placé SUR la station d'observation (aéroport) : on mesure
ainsi la qualité des prévisions, sans mélanger l'écart de représentativité
entre un centre-ville et sa station la plus proche.

`python3 -m scripts.backtest.panel --refresh` reconstruit panel.json :
coordonnées METAR (aviationweather.gov) et station GHCN-Daily la plus proche
qui publie la pluie (moins de 25 km et 150 m de dénivelé).
"""
from __future__ import annotations

import argparse
import json
import math
import urllib.request
from datetime import date
from pathlib import Path

from .sources import USER_AGENT, Fetcher, ghcn_daily

PANEL_FILE = Path(__file__).with_name('panel.json')

# (ICAO, nom, fuseau) : France d'abord, puis tous les continents et climats.
STATIONS = (
    ('LFPO', 'Paris-Orly', 'Europe/Paris'),
    ('LFLL', 'Lyon', 'Europe/Paris'),
    ('LFML', 'Marseille', 'Europe/Paris'),
    ('LFRB', 'Brest', 'Europe/Paris'),
    ('LFBO', 'Toulouse', 'Europe/Paris'),
    ('LFST', 'Strasbourg', 'Europe/Paris'),
    ('LFMN', 'Nice', 'Europe/Paris'),
    ('LFLS', 'Grenoble', 'Europe/Paris'),
    ('EGLL', 'Londres', 'Europe/London'),
    ('EDDM', 'Munich', 'Europe/Berlin'),
    ('LEMD', 'Madrid', 'Europe/Madrid'),
    ('LIRF', 'Rome', 'Europe/Rome'),
    ('ENGM', 'Oslo', 'Europe/Oslo'),
    ('KJFK', 'New York', 'America/New_York'),
    ('KDEN', 'Denver', 'America/Denver'),
    ('CYUL', 'Montréal', 'America/Toronto'),
    ('RJTT', 'Tokyo', 'Asia/Tokyo'),
    ('YSSY', 'Sydney', 'Australia/Sydney'),
    ('FAOR', 'Johannesburg', 'Africa/Johannesburg'),
    ('SAEZ', 'Buenos Aires', 'America/Argentina/Buenos_Aires'),
)

GHCN_STATIONS_URL = 'https://www.ncei.noaa.gov/pub/data/ghcn/daily/ghcnd-stations.txt'


def load_panel() -> list:
    return json.loads(PANEL_FILE.read_text(encoding='utf-8'))


def _haversine_km(lat1, lon1, lat2, lon2) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 6371.0 * 2 * math.asin(math.sqrt(a))


def _ghcn_inventory(fetcher: Fetcher) -> list:
    body = fetcher.get(GHCN_STATIONS_URL, {})
    stations = []
    for line in body.decode('utf-8', 'replace').splitlines():
        try:
            stations.append((line[0:11], float(line[12:20]), float(line[21:30]), float(line[31:37])))
        except ValueError:
            continue
    return stations


def refresh(cache_dir: Path) -> list:
    fetcher = Fetcher(cache_dir)
    request = urllib.request.Request(
        'https://aviationweather.gov/api/data/stationinfo?format=json&ids=' + ','.join(s[0] for s in STATIONS),
        headers={'User-Agent': USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        info = {row['icaoId']: row for row in json.loads(response.read())}
    inventory = _ghcn_inventory(fetcher)
    # GHCN-Daily publie avec plusieurs semaines de retard : on vérifie la
    # présence de la pluie sur la période d'évaluation par défaut.
    probe_start, probe_end = date(2026, 5, 1), date(2026, 9, 30)
    panel = []
    for icao, name, timezone in STATIONS:
        meta = info[icao]
        lat, lon, elev = float(meta['lat']), float(meta['lon']), float(meta['elev'])
        candidates = sorted(
            (c for c in inventory if abs(c[1] - lat) < 0.5 and abs(c[2] - lon) < 0.7),
            key=lambda c: _haversine_km(lat, lon, c[1], c[2]))
        ghcn = None
        for station_id, slat, slon, selev in candidates[:8]:
            distance = _haversine_km(lat, lon, slat, slon)
            if distance > 25 or abs(selev - elev) > 150:
                continue
            rows = ghcn_daily(fetcher, station_id, probe_start, probe_end)
            if sum(1 for r in rows.values() if r.get('PRCP') not in (None, '')) >= 120:
                ghcn = {'id': station_id, 'distance_km': round(distance, 1), 'elevation_m': selev}
                break
        panel.append({'icao': icao, 'name': name, 'timezone': timezone, 'lat': lat, 'lon': lon,
                      'elevation_m': elev, 'ghcn': ghcn})
        print(f"{icao:5s} {name:14s} GHCN={ghcn['id'] if ghcn else '—'}")
    PANEL_FILE.write_text(json.dumps(panel, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    return panel


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--refresh', action='store_true')
    parser.add_argument('--cache-dir', default='build/backtest-cache')
    args = parser.parse_args()
    if args.refresh:
        refresh(Path(args.cache_dir))
    else:
        for row in load_panel():
            print(row['icao'], row['name'], (row['ghcn'] or {}).get('id', '—'))


if __name__ == '__main__':
    main()
