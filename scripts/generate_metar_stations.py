#!/usr/bin/env python3
"""Generate the bundled METAR station list used to calibrate on observations.

Source: NOAA Aviation Weather Center station cache (public domain),
https://aviationweather.gov/data/cache/stations.cache.json.gz

The app picks the nearest station to a city from this list and then requests
its METAR history by ICAO identifier only: the city position never leaves
the device for this purpose.

Usage:
    python3 scripts/generate_metar_stations.py [path/to/stations.cache.json.gz]
"""
from __future__ import annotations

import gzip
import json
import sys
import urllib.request
from pathlib import Path

SOURCE_URL = 'https://aviationweather.gov/data/cache/stations.cache.json.gz'
OUTPUT = Path(__file__).resolve().parents[1] / 'app' / 'src' / 'main' / 'assets' / 'metar_stations.tsv'


def load(path: str | None) -> list:
    if path:
        raw = Path(path).read_bytes()
    else:
        request = urllib.request.Request(SOURCE_URL, headers={'User-Agent': 'MeteoCompare-station-list/1.0'})
        with urllib.request.urlopen(request, timeout=120) as response:
            raw = response.read()
    return json.loads(gzip.decompress(raw) if raw[:2] == b'\x1f\x8b' else raw)


def rows(stations: list) -> list:
    out = {}
    for station in stations:
        icao = (station.get('icaoId') or '').strip().upper()
        if len(icao) != 4 or not icao.isalnum() or 'METAR' not in (station.get('siteType') or []):
            continue
        lat, lon, elev = station.get('lat'), station.get('lon'), station.get('elev')
        if not all(isinstance(v, (int, float)) for v in (lat, lon, elev)):
            continue
        if not (-90 <= lat <= 90 and -180 <= lon <= 180 and -500 <= elev <= 6000):
            continue
        out[icao] = f'{icao}\t{lat:.4f}\t{lon:.4f}\t{round(elev)}'
    return [out[key] for key in sorted(out)]


def main(argv: list) -> int:
    lines = rows(load(argv[1] if len(argv) > 1 else None))
    if len(lines) < 1000:
        print(f'Only {len(lines)} stations: refusing to overwrite {OUTPUT}', file=sys.stderr)
        return 1
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    header = '# METAR stations (ICAO, latitude, longitude, elevation m). Source: NOAA AWC, public domain.\n'
    OUTPUT.write_text(header + '\n'.join(lines) + '\n', encoding='utf-8')
    print(f'{len(lines)} stations written to {OUTPUT}')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
