#!/usr/bin/env python3
"""Independent Python/zoneinfo oracle for daily forecast evolution coverage.

The fixture checks daily precipitation periods (start,end] separately from
instantaneous temperature/wind stamps [start,end), including 23/25-hour days.
"""
import argparse
import hashlib
import json
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'app/src/test/resources/weather_regression/v3'
DATES = (
    ('UTC', '2026-01-01'), ('UTC', '2026-11-13'),
    ('Europe/Paris', '2026-03-29'), ('Europe/Paris', '2026-10-25'),
    ('Europe/Paris', '2026-11-13'),
    ('America/New_York', '2026-03-08'), ('America/New_York', '2026-11-01'),
    ('America/Los_Angeles', '2026-03-08'), ('America/Los_Angeles', '2026-11-01'),
    ('Asia/Kolkata', '2026-11-13'), ('Asia/Kathmandu', '2026-11-13'),
    ('Australia/Adelaide', '2026-04-05'), ('Australia/Adelaide', '2026-10-04'),
    ('Pacific/Auckland', '2026-04-05'), ('Pacific/Auckland', '2026-09-27'),
    ('Australia/Lord_Howe', '2026-04-05'), ('Australia/Lord_Howe', '2026-10-04'),
)
VARIABLES = ('TEMPERATURE', 'PRECIPITATION', 'WIND')
MUTATIONS = ('COMPLETE', 'REMOVE_FIRST', 'REMOVE_LAST', 'REMOVE_MIDDLE',
             'DUPLICATE_MIDDLE', 'INVALID_MIDDLE', 'OFFGRID_MIDDLE')
HEADER = 'id\tzone\tdate\tvariable\tmutation\texpected\thours\tstartUtc\tendUtc\n'


def generate():
    rows = []
    hours_stats = {}
    for zone_name, date_s in DATES:
        zone = ZoneInfo(zone_name)
        day = date.fromisoformat(date_s)
        next_day = day + timedelta(days=1)
        start = datetime(day.year, day.month, day.day, tzinfo=zone).astimezone(timezone.utc)
        end = datetime(next_day.year, next_day.month, next_day.day, tzinfo=zone).astimezone(timezone.utc)
        hours = (end - start).total_seconds() / 3600
        hours_stats[str(hours)] = hours_stats.get(str(hours), 0) + 1
        supported = hours in (23, 24, 25)
        for variable in VARIABLES:
            for mutation in MUTATIONS:
                expected = supported and (
                    mutation == 'COMPLETE' or
                    (mutation == 'REMOVE_FIRST' and variable == 'PRECIPITATION') or
                    (mutation == 'REMOVE_LAST' and variable != 'PRECIPITATION')
                )
                row = (
                    f'{zone_name.replace("/", "_")}-{date_s}-{variable}-{mutation}',
                    zone_name, date_s, variable, mutation, str(expected).lower(), str(hours),
                    start.isoformat().replace('+00:00', 'Z'),
                    end.isoformat().replace('+00:00', 'Z')
                )
                rows.append('\t'.join(row))
    data = (HEADER + '\n'.join(rows) + '\n').encode('utf-8')
    manifest = (json.dumps({
        'version': 1, 'dataset': 'civil_day_hourly_coverage', 'cases': len(rows),
        'sha256': hashlib.sha256(data).hexdigest(), 'zonesAndDates': len(DATES),
        'dayLengthsInHours': hours_stats,
        'coverage': 'Open-Meteo precipitation at H is [H-1,H); instantaneous H is [H,H+1).'
    }, indent=2) + '\n').encode('utf-8')
    return data, manifest, len(rows)


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--check', action='store_true')
    options = p.parse_args()
    body, manifest, cases = generate()
    if options.check:
        assert (OUT/'civil_day_coverage.tsv').read_bytes() == body
        assert (OUT/'civil_day_coverage_manifest.json').read_bytes() == manifest
        print(f'Civil-day coverage corpus: {cases} cases verified')
    else:
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT/'civil_day_coverage.tsv').write_bytes(body)
        (OUT/'civil_day_coverage_manifest.json').write_bytes(manifest)
        print(f'Civil-day coverage corpus: {cases} cases written')

if __name__ == '__main__': main()
