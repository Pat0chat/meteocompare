#!/usr/bin/env python3
"""Independent golden conversion corpus for the presentation-only WeatherUnits API.

The reference formulae below are deliberately not imported from Kotlin. Review any
change to a coefficient, rounding contract or expected symbol before regenerating.

    python3 scripts/generate_unit_conversion_corpus.py
    python3 scripts/generate_unit_conversion_corpus.py --check

All generation is deterministic, offline and independent of device locale/timezone.
"""
from __future__ import annotations

import argparse
import hashlib
import itertools
import json
from decimal import Decimal, ROUND_FLOOR, ROUND_HALF_UP
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'app/src/test/resources/unit_conversion/v1'

# Reference constants: international inch (25.4 mm), international mile (1.609344 km),
# international nautical mile (1.852 km), international foot (0.3048 m),
# conventional inHg (3386.389 Pa), and SI second (no conversion).
FACTOR = {
    'mph': Decimal('1.609344'), 'kn': Decimal('1.852'), 'm/s': Decimal('3.6'),
    'in': Decimal('25.4'), 'ft': Decimal('0.3048'), 'mi': Decimal('1.609344'),
    'inHg': Decimal('33.86389'),
}
LOCALES = ('en_US', 'fr_FR', 'de_DE', 'es_ES', 'it_IT', 'tr_TR')
UNITS = ('TEMPERATURE', 'TEMPERATURE_COMPACT', 'WIND_SPEED', 'PRECIPITATION',
         'PRECIPITATION_RATE', 'HEIGHT', 'DISTANCE', 'PRESSURE', 'PERCENT', 'SECONDS')
DIMENSIONS = (
    ('temperature', ('CELSIUS', 'FAHRENHEIT')),
    ('wind', ('KMH', 'MPH', 'KNOTS', 'MS')),
    ('precipitation', ('MM', 'INCHES')),
    ('pressure', ('HPA', 'INHG')),
    ('height', ('METRES', 'FEET')),
    ('distance', ('KM', 'MILES')),
)
DEFAULTS = ('CELSIUS', 'KMH', 'MM', 'HPA', 'METRES', 'KM')


def profiles():
    result = [('metric', 'METRIC', DEFAULTS),
              ('imperial', 'IMPERIAL', DEFAULTS),
              ('uk_mixed', 'UK_MIXED', DEFAULTS)]
    for idx, combination in enumerate(itertools.product(*(options for _, options in DIMENSIONS))):
        result.append((f'custom-{idx:03d}', 'CUSTOM', combination))
    assert len(result) == 131
    return result


def symbol(system: str, choices: tuple[str, ...], kind: str) -> str:
    temperature, wind, precipitation, pressure, height, distance = choices
    if system != 'CUSTOM':
        temperature = 'FAHRENHEIT' if system == 'IMPERIAL' else 'CELSIUS'
        wind = 'MPH' if system in ('IMPERIAL', 'UK_MIXED') else 'KMH'
        precipitation = 'INCHES' if system == 'IMPERIAL' else 'MM'
        pressure = 'INHG' if system == 'IMPERIAL' else 'HPA'
        height = 'FEET' if system == 'IMPERIAL' else 'METRES'
        distance = 'MILES' if system != 'METRIC' else 'KM'
    if kind == 'TEMPERATURE': return '°F' if temperature == 'FAHRENHEIT' else '°C'
    if kind == 'TEMPERATURE_COMPACT': return '°F' if temperature == 'FAHRENHEIT' else '°'
    if kind == 'WIND_SPEED': return {'KMH': 'km/h', 'MPH': 'mph', 'KNOTS': 'kn', 'MS': 'm/s'}[wind]
    if kind in ('PRECIPITATION', 'PRECIPITATION_RATE'):
        unit = 'in' if precipitation == 'INCHES' else 'mm'
        return unit + ('/h' if kind == 'PRECIPITATION_RATE' else '')
    if kind == 'PRESSURE': return 'inHg' if pressure == 'INHG' else 'hPa'
    if kind == 'HEIGHT': return 'ft' if height == 'FEET' else 'm'
    if kind == 'DISTANCE': return 'mi' if distance == 'MILES' else 'km'
    if kind == 'PERCENT': return '%'
    if kind == 'SECONDS': return 's'
    raise ValueError(kind)


def reference_convert(value: Decimal, kind: str, label: str, delta: bool) -> Decimal:
    if kind.startswith('TEMPERATURE'):
        return value * Decimal(9) / Decimal(5) + (Decimal(0) if delta else Decimal(32)) if label == '°F' else value
    conversion = {'mph': 'mph', 'kn': 'kn', 'm/s': 'm/s', 'in': 'in', 'in/h': 'in',
                  'ft': 'ft', 'mi': 'mi', 'inHg': 'inHg'}.get(label)
    return value / FACTOR[conversion] if conversion else value


def requested_precision(kind: str, label: str, digits: int) -> int:
    if kind in ('PRECIPITATION', 'PRECIPITATION_RATE'):
        return max(2 if label.startswith('in') else 1, digits)
    if kind == 'PRESSURE' and label == 'inHg': return max(2, digits)
    return digits


def format_number(value: Decimal, digits: int, locale: str) -> str:
    digits = min(6, max(0, digits))
    # Decimal(str(float(...))) is the decimal representation seen by Java Formatter,
    # not the binary expansion or an already rounded display value.
    converted = Decimal(str(float(value)))
    zero_threshold = Decimal(5) / (Decimal(10) ** (digits + 1))
    if abs(converted) < zero_threshold: converted = Decimal(0)
    scale = Decimal(1).scaleb(-digits)
    if digits == 0:
        integer = (converted + Decimal('0.5')).to_integral_value(rounding=ROUND_FLOOR)
        formatted = str(integer)
    else:
        rounded = converted.quantize(scale, rounding=ROUND_HALF_UP)
        formatted = f'{rounded:.{digits}f}'
    return formatted.replace('.', ',') if locale != 'en_US' else formatted


def format_value(canonical: Decimal, kind: str, label: str, digits: int, delta: bool, locale: str) -> str:
    precision = requested_precision(kind, label, digits)
    converted = reference_convert(canonical, kind, label, delta)
    threshold = Decimal(1).scaleb(-precision)
    # Positive sub-resolution precipitation is never silently rendered as dry.
    if kind in ('PRECIPITATION', 'PRECIPITATION_RATE') and not delta \
            and canonical > 0 and converted < threshold / 2:
        return '<' + format_number(threshold, precision, locale)
    return format_number(converted, precision, locale)


def output_bytes(header: str, records) -> bytes:
    lines = [header]
    for record in records:
        lines.append('\t'.join(map(str, record)))
    return ('\n'.join(lines) + '\n').encode('utf-8')


def numeric_values():
    # Standards, zero crossings, signed increments, precipitation trace boundaries,
    # physically plausible and deliberately out-of-range measurements.
    exact = (
        '-273.15', '-200', '-100', '-50', '-40', '-25.4', '-20', '-18.52', '-16.09344',
        '-10', '-3.048', '-2', '-1.852', '-1.609344', '-1', '-0.5', '-0.25',
        '-0.1', '-0.051', '-0.05', '-0.049', '-0.01', '-0.001', '-0.000001',
        '0', '0.000001', '0.001', '0.01', '0.019', '0.049', '0.05', '0.051',
        '0.1', '0.25', '0.4', '0.5', '1', '1.609344', '1.852', '2', '3.048',
        '5', '10', '12.34', '16.09344', '18.52', '20', '25.4', '36', '40', '50',
        '75', '100', '200', '212', '273.15', '300', '500', '1000', '1013.25',
        '1024', '2400', '3600', '10000', '100000', '1000000',
    )
    # Additional independent, deterministic reference points; not pseudo-random
    # data derived from application logic. Positive and negative on varied scales.
    generated = [str(Decimal(i * 137 - 5329) / Decimal(37)) for i in range(60)]
    return tuple(dict.fromkeys(exact + tuple(generated)))


def generate() -> dict[str, bytes]:
    result = {}
    ps = profiles()
    result['profiles.tsv'] = output_bytes('id\tsystem\ttemperature\twind\tprecipitation\tpressure\theight\tdistance',
        ((pid, system, *choices) for pid, system, choices in ps))

    values = numeric_values()
    def conversions():
        for profile_id, system, choices in ps:
            for unit in UNITS:
                unit_symbol = symbol(system, choices, unit)
                for idx, num in enumerate(values):
                    for delta in (('false', 'true') if unit.startswith('TEMPERATURE') else ('false',)):
                        converted = reference_convert(Decimal(num), unit, unit_symbol, delta == 'true')
                        yield (f'{profile_id}-{unit}-{idx:03d}-{delta}', profile_id, unit, num,
                               delta, format(converted, 'f'), unit_symbol)
    result['conversions.tsv'] = output_bytes('id\tprofile\tunit\tmetric\tdelta\texpected\tsymbol', conversions())

    # Full Cartesian product: every profile x every unit x every supported
    # locale x every precision x twelve independent values. This prevents
    # hidden interactions between language, rounding and unit selections.
    display_input = ('-40', '-3.048', '-0.049', '0', '0.00000001', '0.05',
                     '0.4', '1.852', '16.09344', '18.52', '25.4', '1013.25')
    digits_cycle = (0, 1, 2, 6)
    def displays():
        for profile_id, system, choices in ps:
            for unit in UNITS:
                unit_symbol = symbol(system, choices, unit)
                for locale in LOCALES:
                    for digits in digits_cycle:
                        for idx, num in enumerate(display_input):
                            # Temperature deltas receive an independent offset-free
                            # reference in addition to absolute readings.
                            for delta in ((False, True) if unit.startswith('TEMPERATURE')
                                          else (False,)):
                                dec = Decimal(num)
                                expected_value = format_value(dec, unit, unit_symbol, digits, delta, locale)
                                suffix = ('' if unit in ('TEMPERATURE_COMPACT', 'PERCENT') else ' ') + unit_symbol
                                yield (f'view-{profile_id}-{unit}-{locale}-{digits}-{idx:02d}-{int(delta)}',
                                       profile_id, unit, locale, digits, str(delta).lower(),
                                       num, expected_value, expected_value + suffix)
    result['display.tsv'] = output_bytes(
        'id\tprofile\tunit\tlocale\tdigits\tdelta\tmetric\texpected_value\texpected_format', displays())

    def missing():
        for profile_id, _, _ in ps:
            for unit in UNITS:
                for kind in ('null', 'nan', '+infinity', '-infinity'):
                    yield (f'missing-{profile_id}-{unit}-{kind}', profile_id, unit, kind, '—')
    result['missing.tsv'] = output_bytes('id\tprofile\tunit\tmetric\texpected', missing())
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Fail if corpus/manifest differs from reviewed fixtures')
    args = parser.parse_args()
    files = generate()
    manifest = {
        'revision': 1,
        'oracle': 'independent Decimal rational conversion reference; reviewed rounding/locale contracts',
        'reference_profiles': 131,
        'files': {name: {'cases': data.count(b'\n') - 1, 'sha256': hashlib.sha256(data).hexdigest()}
                  for name, data in sorted(files.items())},
    }
    files['manifest.json'] = (json.dumps(manifest, indent=2, ensure_ascii=False, sort_keys=True) + '\n').encode()
    if args.check:
        missing = [name for name, content in files.items() if not (OUT / name).is_file() or
                   (OUT / name).read_bytes().replace(b'\r\n', b'\n') != content]
        if missing:
            parser.error('Unit corpus is missing or stale: ' + ', '.join(missing))
        print('Unit conversion golden corpus verified:', {n: v['cases'] for n, v in manifest['files'].items()})
    else:
        OUT.mkdir(parents=True, exist_ok=True)
        for name, content in files.items():
            (OUT / name).write_bytes(content)
        print('Generated unit conversion golden corpus:', {n: v['cases'] for n, v in manifest['files'].items()})


if __name__ == '__main__':
    main()
