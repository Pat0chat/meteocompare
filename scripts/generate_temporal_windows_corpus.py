#!/usr/bin/env python3
"""Independent UTC interval expectations for hourly evidence around civil-day edges.

V1 historical weather corpus is immutable; new physical-time cases live in v3.
Use --check in CI to assert committed fixtures are reproducible.
"""
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo
from pathlib import Path
import argparse
import csv
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'app/src/test/resources/weather_regression/v3'
DATES = (
    ('Europe/Paris', '2026-03-29'),
    ('Europe/Paris', '2026-10-25'),
    ('America/New_York', '2026-03-08'),
    ('America/New_York', '2026-11-01'),
    ('Asia/Kolkata', '2026-11-13'),
    ('UTC', '2026-11-13'),
)
HOUR = timedelta(hours=1)
COLUMNS = ('id','zone','date','kind','hourUtc','startUtc','endUtc','dayStartUtc','dayEndUtc','overlapSeconds')


def vectors():
    for zone_name, day_str in DATES:
        zone = ZoneInfo(zone_name)
        day = date.fromisoformat(day_str)
        day_start = datetime(day.year,day.month,day.day,tzinfo=zone).astimezone(timezone.utc)
        next_day = day + timedelta(days=1)
        day_end = datetime(next_day.year,next_day.month,next_day.day,tzinfo=zone).astimezone(timezone.utc)
        hours = int((day_end - day_start) / HOUR)
        assert hours in (23, 24, 25)
        for index in range(hours+1):
            stamped_hour = day_start + index * HOUR
            for kind in ('WMO','RAIN'):
                physical_start = stamped_hour if kind == 'WMO' else stamped_hour - HOUR
                physical_end = physical_start + HOUR
                overlap = max(0, int((min(day_end, physical_end) - max(day_start, physical_start)).total_seconds()))
                yield dict(zip(COLUMNS, (
                    f'{zone_name.replace("/","_")}-{day_str}-{kind}-{index:02d}', zone_name, day_str, kind,
                    stamped_hour.isoformat().replace('+00:00','Z'),
                    physical_start.isoformat().replace('+00:00','Z'),
                    physical_end.isoformat().replace('+00:00','Z'),
                    day_start.isoformat().replace('+00:00','Z'),
                    day_end.isoformat().replace('+00:00','Z'),
                    overlap
                )))


def generate():
    rows = list(vectors())
    header = '\t'.join(COLUMNS) + '\n'
    body = ''.join('\t'.join(str(row[column]) for column in COLUMNS) + '\n' for row in rows)
    data = (header + body).encode('utf-8')
    manifest = {
        'version': 3,
        'dataset': 'physical_hourly_precipitation_windows',
        'cases': len(rows),
        'sha256': hashlib.sha256(data).hexdigest(),
        'coverage': 'WMO H to H+1 representative vs rainfall H-1 to H; DST 23/25, midnight, Asia/Kolkata'
    }
    return data, (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args()
    data, manifest=generate()
    if args.check:
        assert (OUT/'temporal_windows.tsv').read_bytes() == data, 'Temporal windows corpus differs'
        assert (OUT/'manifest.json').read_bytes() == manifest, 'Temporal corpus manifest differs'
    else:
        OUT.mkdir(parents=True,exist_ok=True)
        (OUT/'temporal_windows.tsv').write_bytes(data)
        (OUT/'manifest.json').write_bytes(manifest)
    print(f'Temporal UTC corpus: {len(data.splitlines())-1} scenarios verified' if args.check else f'Temporal UTC corpus generated: {len(data.splitlines())-1} scenarios')

if __name__=='__main__': main()
