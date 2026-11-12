#!/usr/bin/env python3
"""Generate deterministic, independently specified MeteoCompare regression vectors.

Run --check in CI; changing a golden expectation should require reviewing this generator.
No external weather APIs, time-of-day dependence, or randomness are used.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'app/src/test/resources/weather_regression/v1'
DRY = {0: 'CLEAR', 1: 'MAINLY_CLEAR', 2: 'PARTLY_CLOUDY', 3: 'OVERCAST'}
WET = {51: 'DRIZZLE', 61: 'RAIN', 80: 'RAIN_SHOWERS', 95: 'THUNDERSTORM', 71: 'SNOW'}
DATES = (
    ('Europe/Paris', '2026-08-24'),
    ('Europe/Paris', '2026-03-29'),  # spring: 23h
    ('Europe/Paris', '2026-10-25'),  # autumn: 25h
    ('America/New_York', '2026-03-08'),
    ('America/New_York', '2026-11-01'),
    ('Asia/Tokyo', '2026-08-24'),
    ('UTC', '2026-08-24'),
)


def day_hours(zone: str, day: str) -> int:
    d = date.fromisoformat(day)
    start = datetime(d.year, d.month, d.day, tzinfo=ZoneInfo(zone)).astimezone(timezone.utc)
    nxt = d + timedelta(days=1)
    end = datetime(nxt.year, nxt.month, nxt.day, tzinfo=ZoneInfo(zone)).astimezone(timezone.utc)
    return int((end - start).total_seconds() / 3600)


def text(n: object) -> str:
    return '_' if n is None else str(n)


def table(header: tuple[str, ...], rows: list[tuple]) -> bytes:
    assert len(set(r[0] for r in rows)) == len(rows), 'Scenario IDs must be unique'
    assert all(len(row) == len(header) for row in rows)
    return ('\t'.join(header) + '\n' + ''.join('\t'.join(map(str, row)) + '\n' for row in rows)).encode('utf-8')


def cloud_sky(total: int) -> str:
    if total < 20: return 'CLEAR'
    if total < 45: return 'MAINLY_CLEAR'
    if total < 90: return 'PARTLY_CLOUDY'
    return 'OVERCAST'


def wmo_rows():
    reference = {0: 'CLEAR', 1: 'MAINLY_CLEAR', 2: 'PARTLY_CLOUDY', 3: 'OVERCAST'}
    for codes, family in (
        ((45, 48), 'FOG'), ((51, 53, 55), 'DRIZZLE'),
        ((56, 57, 66, 67), 'FREEZING_RAIN'), ((61, 63, 65), 'RAIN'),
        ((71, 73, 75, 77), 'SNOW'), ((80, 81, 82), 'RAIN_SHOWERS'),
        ((85, 86), 'SNOW_SHOWERS'), ((95, 96, 99), 'THUNDERSTORM')
    ):
        reference.update({code: family for code in codes})
    return [(f'wmo-{code}', code, reference.get(code, 'UNKNOWN')) for code in range(-3, 111)]


def cloud_rows():
    rows = []
    for total in range(101):
        for low in (None, 0, 25, 26, 90):
            for mid in (None, 0, 35, 36, 90):
                for high in (None, 79, 80, 100):
                    for sun, dni in ((None, None), (899, None), (900, None), (None, 160)):
                        veil = (total >= 90 and low is not None and low <= 25
                                and mid is not None and mid <= 35 and high is not None
                                and high >= 80 and (sun is not None and sun >= 900
                                or sun is None and dni is not None and dni >= 160))
                        basic = cloud_sky(total)
                        refined = 'VEILED' if veil and basic == 'OVERCAST' else basic
                        key = '-'.join(map(text, (total, low, mid, high, sun, dni)))
                        rows.append((f'cloud-{key}', total, text(low), text(mid), text(high),
                                     text(sun), text(dni), basic, str(veil).lower(), refined))
    # Boundary / invalid data: explicit negatives, invalid percentages, conflicting solar evidence.
    for idx, (low, mid, high, sun, dni) in enumerate((
        (5, 10, 95, -5, None), (5, 10, 95, 3601, None), (5, 10, 95, 0, 800),
        (5, 10, 95, None, 159), (5, 10, 95, None, 160),
        (-1, 10, 95, 1800, None), (5, 101, 95, 1800, None),
        (5, 10, 101, 1800, None), (25, 35, 80, 900, None),
        (26, 35, 80, 900, None), (25, 36, 80, 900, None)
    )):
        valid_layers = all(n is not None and 0 <= n <= 100 for n in (low, mid, high))
        veiled = (valid_layers and low <= 25 and mid <= 35 and high >= 80
                  and ((sun is not None and 900 <= sun <= 3600)
                       or (sun is None and dni is not None and dni >= 160)))
        rows.append((f'cloud-edge-{idx}', 98, text(low), text(mid), text(high),
                     text(sun), text(dni), 'OVERCAST', str(veiled).lower(),
                     'VEILED' if veiled else 'OVERCAST'))
    return rows


def daily_rows():
    rows = []
    def add(name, zone, day, codes, daily, expected, provenance='HOURLY_WMO', mode='ALL', cloud=65):
        rows.append((name, zone, day, mode, text(daily), ','.join(text(c) for c in codes),
                     text(cloud), text(expected), text(provenance)))

    for zone, day in DATES:
        length = day_hours(zone, day)
        assert length in (23, 24, 25)
        for dry, dry_name in DRY.items():
            for wet, _ in WET.items():
                # One isolated weather event at every possible hour, even on DST days.
                for h in range(length):
                    codes = [dry] * length
                    codes[h] = wet
                    add(f'isolated-{zone}-{day}-{dry}-{wet}-{h}', zone, day,
                        codes, wet, dry_name)
                # 5 dry hours cannot overturn a wet majority.
                for shift in range(length):
                    codes = [wet] * length
                    for offset in range(5):
                        codes[(shift + offset) % length] = dry
                    add(f'wet-majority-{zone}-{day}-{dry}-{wet}-{shift}', zone, day,
                        codes, wet, WET[wet])
                # Even-length day: exactly half wet cannot be called dominant rain.
                if length % 2 == 0:
                    for shift in range(length):
                        codes = [dry] * length
                        for offset in range(length // 2):
                            codes[(shift + offset) % length] = wet
                        add(f'equal-{zone}-{day}-{dry}-{wet}-{shift}', zone, day,
                            codes, wet, None, 'NONE')
        # Native WMO can be used when a cache only contains a few hourly samples.
        for count in (1, 3, 6, 8):
            for daily in (0, 3, 61, 95):
                add(f'sparse-daily-{zone}-{day}-{count}-{daily}', zone, day,
                    [0] * length, daily, DRY.get(daily, WET.get(daily)), 'DAILY_WMO',
                    mode=f'FIRST_{count}')
                add(f'sparse-nodaily-{zone}-{day}-{count}-{daily}', zone, day,
                    [0] * length, None, None, 'NONE', mode=f'FIRST_{count}')
        # Missing 5 middle hours: no fabricated daily rain from a severe native code.
        add(f'middle-gap-{zone}-{day}', zone, day, [0] * length,
            61, None, 'NONE', mode='GAP_5')
        add(f'duplicate-native-{zone}-{day}', zone, day,
            [0] * (length - 1) + [61], 61, 'CLEAR', mode='DUP_NATIVE')
        for cloud in (0, 19, 20, 44, 45, 89, 90, 98, 100):
            add(f'cloud-fallback-{zone}-{day}-{cloud}', zone, day,
                [None] * length, 61, cloud_sky(cloud), 'DERIVED_VARIABLES', cloud=cloud)

    return rows



def consensus_rows():
    """Pairwise competing models, including same-family vote duplication."""
    rows = []
    conditions = ('CLEAR', 'MAINLY_CLEAR', 'PARTLY_CLOUDY', 'VEILED', 'OVERCAST',
                  'FOG', 'DRIZZLE', 'RAIN', 'RAIN_SHOWERS', 'SNOW',
                  'SNOW_SHOWERS', 'FREEZING_RAIN', 'THUNDERSTORM')
    models = ('GFS', 'ECMWF', 'ICON_GLOBAL')  # separate independent families
    for winner in conditions:
        for outlier in conditions:
            if winner == outlier:
                continue
            for position in range(3):
                readings = [winner, winner, winner]
                readings[position] = outlier
                rows.append((f'consensus-majority-{winner}-{outlier}-{position}',
                             ','.join(models), ','.join(readings), '_', winner))
    # Fair 50:50 across unrelated model families: never force a wet winner.
    for dry in ('CLEAR', 'MAINLY_CLEAR', 'PARTLY_CLOUDY', 'VEILED', 'OVERCAST', 'FOG'):
        for wet in ('DRIZZLE', 'RAIN', 'RAIN_SHOWERS', 'SNOW',
                    'FREEZING_RAIN', 'THUNDERSTORM'):
            for left, right in ((dry, wet), (wet, dry)):
                rows.append((f'consensus-tie-{dry}-{wet}-{left}',
                             'GFS,ECMWF', f'{left},{right}', '_', '_'))
            rows.append((f'consensus-four-tie-{dry}-{wet}',
                         'GFS,ECMWF,ICON_GLOBAL,ARPEGE_EUROPE',
                         f'{dry},{dry},{wet},{wet}', '_', '_'))
    # Two closely related NOAA models still account for just one lineage.
    for dry in ('CLEAR', 'PARTLY_CLOUDY', 'OVERCAST'):
        for wet in ('DRIZZLE', 'RAIN', 'SNOW', 'THUNDERSTORM'):
            rows.append((f'consensus-relatives-tie-{dry}-{wet}',
                         'GFS,NCEP_AIGFS,ECMWF', f'{dry},{dry},{wet}', '_', '_'))
            rows.append((f'consensus-relatives-wet-{dry}-{wet}',
                         'GFS,NCEP_AIGFS,ECMWF,ICON_GLOBAL',
                         f'{dry},{dry},{wet},{wet}', '_', wet))
    # Rain remains rain regardless of cloud cover in the aggregate.
    for cloud in (0, 19, 20, 44, 45, 89, 90, 95, 100):
        rows.append((f'consensus-rain-with-{cloud}-cloud',
                     'GFS,ECMWF,ICON_GLOBAL', 'RAIN,RAIN,CLEAR', cloud, 'RAIN'))
    return rows


def solar_rows():
    # Hours relative to 2026-08-24 UTC; synthetic sunrise 06:00, sunset 18:00.
    rows = []
    for hour in range(24):
        overlap = 1.0 if 6 <= hour < 18 else 0.0
        for cloud in (0, 19, 20, 44, 45, 89, 90, 95, 98, 100):
            # Explicit WMO sunshine is preserved even under an overcast sky.
            for sec in (0, 899, 900, 1800, 2640, 3600):
                rows.append((f'solar-native-{hour}-{cloud}-{sec}', hour, cloud, '_', '_', '_',
                             sec, '_', f'{overlap * sec / 3600.0:.9f}'))
            # Uncertainty in overcast conditions is NOT a 0-minute measurement.
            expected = None if cloud >= 90 else overlap * (100 - cloud) / 100.0
            rows.append((f'solar-cloud-{hour}-{cloud}', hour, cloud, '_', '_', '_',
                         '_', '_', text(f'{expected:.9f}' if expected is not None else None)))
            # DNI is a smooth proxy, never a measured number of sun seconds under overcast.
            for dni in (0, 120, 160, 370, 500):
                val = None if cloud >= 90 else overlap * max(0.0, min(1.0, (dni - 120) / 250))
                rows.append((f'solar-dni-{hour}-{cloud}-{dni}', hour, cloud, '_', '_', '_',
                             '_', dni, text(f'{val:.9f}' if val is not None else None)))
    return rows


# Widget geometry and refresh policy are also part of the regression contract.
# These tables encode independent boundary specifications; they do not query Kotlin.
WIDGET_WIDTHS = (0, 60, 89, 99, 100, 104, 105, 106, 150, 179, 199,
                 209, 210, 211, 219, 220, 221, 259, 260, 319, 320, 321,
                 379, 380, 420, 520)
WIDGET_HEIGHTS = (0, 40, 44, 45, 49, 60, 67, 68, 71, 72, 83, 84, 101,
                  102, 104, 105, 106, 119, 120, 121, 141, 142, 149, 150,
                  151, 152, 164, 165, 166, 184, 185, 230, 300)
WIDGET_STYLES = ('ROUNDED', 'SQUARE', 'SOFT', 'PILL', 'CUSTOM')
METRIC_NAMES = ('CLOUD', 'RAIN', 'SUNSHINE', 'CONVERGENCE')
PROFILES = ('DENSE', 'COMPACT', 'COMFORTABLE', 'EXPANDED')
FIXED_HEIGHTS = (36, 50, 64, 76)


def reference_widget_layout(width: int, height: int) -> str:
    # Contract: tiny below 105x105, height >=120 unlocks tall layouts.
    if width < 105 and height < 105: return 'TINY'
    if height >= 120 and width >= 220: return 'EXTRA_LARGE'
    if height >= 120 and width >= 100: return 'COMPACT_TALL'
    if width >= 380: return 'WIDE'
    if width >= 320: return 'LARGE'
    if width >= 210: return 'MEDIUM'
    return 'SMALL'


def widget_rows():
    result = []
    for width in WIDGET_WIDTHS:
        for height in WIDGET_HEIGHTS:
            for style in WIDGET_STYLES:
                for h_inset, v_inset in ((0, 0), (14, 7), (35, 14)):
                    raw = reference_widget_layout(width, height)
                    if style == 'PILL':
                        usable_w, usable_h = max(0, width - 2 * h_inset), max(0, height - 2 * v_inset)
                        if usable_w < 118 or usable_h < 45: expected = 'TINY'
                        elif height >= 120 and usable_w < 240: expected = 'COMPACT_TALL'
                        elif height < 120 and usable_w < 185: expected = 'SMALL'
                        else: expected = raw
                    else:
                        expected = raw
                    result.append((f'widget-{width}-{height}-{style}-{h_inset}-{v_inset}',
                                   width, height, style, h_inset, v_inset, expected))
    return result


def widget_metric_rows():
    rows = []
    heights = (0, 20, 35, 36, 40, 45, 49, 50, 56, 60, 63, 64,
               69, 70, 72, 76, 83, 90, 102, 140, 180, 260)
    for height in heights:
        for profile_index, profile in enumerate(PROFILES):
            for mask in range(16):
                metrics = [name for i, name in enumerate(METRIC_NAMES) if mask & (1 << i)]
                budget = max(0, height - FIXED_HEIGHTS[profile_index])
                rows_available = min(4, max(0, int(budget / 7)))
                priority = ('RAIN', 'CLOUD', 'CONVERGENCE', 'SUNSHINE')
                kept = set(name for name in priority if name in metrics)
                if len(metrics) > rows_available:
                    kept = set([name for name in priority if name in metrics][:rows_available])
                visible = [name for name in metrics if name in kept]
                sp = 5.5 if not visible else min(10.0, max(5.5, budget / len(visible) / 1.28))
                icon = min(11.1, sp + 1.1)
                requested_rows = len(metrics) * 7
                fitted = 'DENSE'
                for idx in range(profile_index, -1, -1):
                    if height >= FIXED_HEIGHTS[idx] + requested_rows:
                        fitted = PROFILES[idx]
                        break
                rows.append((f'metrics-{height}-{profile}-{mask}', height, profile, mask,
                             ','.join(visible) or '_', f'{sp:.6f}', f'{icon:.6f}', fitted))
    return rows


def refresh_rows():
    interval = 15 * 60 * 1000
    values = sorted(set((0, 1, -1, -interval, 2**63 - 1, 2**63 - 2,
                         *(bucket * interval + step
                           for bucket in (0, 1, 2, 4, 8, 24, 48)
                           for step in (-1, 0, 1, interval//2, interval - 1)))))
    rows = []
    for last in (None, *values):
        for now in values:
            for force in (False, True):
                due = (force or last is None or last <= 0 or now < last
                       or now // interval > last // interval)
                tick = now if last is None or now > last else min(2**63 - 1, last + 1)
                rows.append((f'refresh-{text(last)}-{now}-{int(force)}', text(last), now,
                             str(force).lower(), str(due).lower(), tick))
    return rows


def sunshine_consensus_rows():
    """Oracle for model-coverage bias (#38 / 44 minutes under overcast skies).

    Inputs are fully specified, even when solar information is absent. Families
    are an independently documented lineage map, not engine outputs.
    """
    import itertools
    sets = (
        ('GFS', 'ECMWF', 'ICON_GLOBAL'),
        ('GFS', 'NCEP_AIGFS', 'ECMWF'),
        ('GFS', 'ECMWF', 'ICON_GLOBAL', 'ARPEGE_EUROPE'),
    )
    family = {'GFS': 'NOAA', 'NCEP_AIGFS': 'NOAA', 'ECMWF': 'ECMWF',
              'ICON_GLOBAL': 'ICON', 'ARPEGE_EUROPE': 'ARPEGE'}
    values = (None, 0, 900, 2640)
    rows = []
    for group in sets:
        for clouds in itertools.product((20, 95), repeat=len(group)):
            for seconds in itertools.product(values, repeat=len(group)):
                cloud_families = set(map(family.__getitem__, group))
                explicit = {}
                for model, cloud, value in zip(group, clouds, seconds):
                    if cloud >= 90 and value is not None:
                        explicit.setdefault(family[model], []).append(value / 3600)
                if not explicit or (len(cloud_families) > 1
                                    and 2 * len(explicit) <= len(cloud_families)):
                    expected = None
                else:
                    medians = []
                    for samples in explicit.values():
                        samples = sorted(samples)
                        midpoint = len(samples)//2
                        medians.append(samples[midpoint] if len(samples)%2
                                       else (samples[midpoint-1]+samples[midpoint])/2)
                    medians.sort()
                    midpoint = len(medians)//2
                    expected = (medians[midpoint] if len(medians)%2
                                else (medians[midpoint-1]+medians[midpoint])/2)
                key = '-'.join(group) + '/' + ','.join(map(str, clouds)) + '/' + ','.join(map(text, seconds))
                rows.append((f'solar-consensus-{key}', ','.join(group),
                             ','.join(map(str, clouds)), ','.join(map(text, seconds)),
                             text(f'{expected:.9f}' if expected is not None else None)))
    return rows


def outputs() -> dict[str, bytes]:
    cases = {
        'wmo.tsv': table(('id', 'code', 'expected'), wmo_rows()),
        'clouds.tsv': table(('id', 'total', 'low', 'mid', 'high', 'sun_seconds', 'dni',
                             'expected_base', 'expected_veil', 'expected_refined'), cloud_rows()),
        'daily.tsv': table(('id', 'timezone', 'date', 'mode', 'daily_wmo', 'hourly_wmo',
                            'cloud_cover', 'expected', 'provenance'), daily_rows()),
        'consensus.tsv': table(('id', 'models', 'conditions', 'cloud', 'expected'), consensus_rows()),
        'sunshine.tsv': table(('id', 'utc_hour', 'cloud', 'low', 'mid', 'high',
                               'sun_seconds', 'dni', 'expected_hours'), solar_rows()),
        'widget_layout.tsv': table(('id', 'width_dp', 'height_dp', 'style', 'horizontal_inset_dp',
                                    'vertical_inset_dp', 'expected_layout'), widget_rows()),
        'widget_metrics.tsv': table(('id', 'height_dp', 'profile', 'bitmask', 'visible',
                                     'text_sp', 'icon_sp', 'fitted_profile'), widget_metric_rows()),
        'widget_refresh.tsv': table(('id', 'last_ms', 'now_ms', 'force', 'expected_due',
                                     'expected_tick'), refresh_rows()),
        'sunshine_consensus.tsv': table(('id', 'models', 'clouds', 'seconds', 'expected_hours'),
                                        sunshine_consensus_rows()),
    }
    manifest = {
        'schema_version': 1,
        'oracle': 'independent hand-specified domain contracts; NEVER generated from application outputs',
        'clock': 'fixed UTC timestamps; no network or wall-clock dependency',
        'files': {name: {'cases': content.count(b'\n') - 1,
                         'sha256': hashlib.sha256(content).hexdigest()}
                  for name, content in cases.items()},
    }
    cases['manifest.json'] = (json.dumps(manifest, indent=2, ensure_ascii=False, sort_keys=True) + '\n').encode()
    return cases


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true', help='Check committed golden files without modifying them')
    args = parser.parse_args()
    generated = outputs()
    OUT.mkdir(parents=True, exist_ok=True)
    for name, content in generated.items():
        destination = OUT / name
        if args.check:
            if not destination.exists() or destination.read_bytes() != content:
                raise SystemExit(f'OUT OF DATE: {destination}. Run scripts/generate_weather_regression_corpus.py')
        else:
            destination.write_bytes(content)
    print('weather regression corpus:', ', '.join(f'{k}={v["cases"]}' for k, v in json.loads(generated['manifest.json'])['files'].items()), 'OK')


if __name__ == '__main__':
    main()
