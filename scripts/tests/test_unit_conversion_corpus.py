"""Integrity, independent reference anchors and CI execution contracts for units."""
from __future__ import annotations

import csv
import hashlib
import importlib.util
import json
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'app/src/test/resources/unit_conversion/v1'


class UnitConversionCorpusTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads((DATA / 'manifest.json').read_text(encoding='utf-8'))

    def rows(self, filename):
        with (DATA / filename).open(encoding='utf-8', newline='') as fh:
            return list(csv.DictReader(fh, delimiter='\t'))

    def test_all_reference_files_have_reviewed_hashes_exact_counts_and_unique_ids(self):
        expected = self.manifest['files']
        self.assertEqual(set(expected), {'profiles.tsv', 'conversions.tsv', 'display.tsv', 'missing.tsv'})
        self.assertEqual(self.manifest['reference_profiles'], 131)
        for filename, meta in expected.items():
            with self.subTest(file=filename):
                raw = (DATA / filename).read_bytes().replace(b'\r\n', b'\n')
                self.assertEqual(hashlib.sha256(raw).hexdigest(), meta['sha256'])
                rows = self.rows(filename)
                self.assertEqual(len(rows), meta['cases'])
                self.assertEqual(len({item['id'] for item in rows}), len(rows))
                self.assertTrue(all(None not in item for item in rows))
        self.assertGreaterEqual(expected['conversions.tsv']['cases'], 150_000)
        self.assertGreaterEqual(expected['display.tsv']['cases'], 450_000)
        self.assertGreaterEqual(expected['missing.tsv']['cases'], 5_000)

    def test_every_custom_combination_exists_once_plus_presets(self):
        import itertools
        profiles = self.rows('profiles.tsv')
        combos = [tuple(p[field] for field in ('temperature', 'wind', 'precipitation',
                                               'pressure', 'height', 'distance'))
                  for p in profiles if p['system'] == 'CUSTOM']
        self.assertEqual(len(combos), 128)
        all_possible = set(itertools.product(('CELSIUS', 'FAHRENHEIT'),
                                             ('KMH', 'MPH', 'KNOTS', 'MS'),
                                             ('MM', 'INCHES'), ('HPA', 'INHG'),
                                             ('METRES', 'FEET'), ('KM', 'MILES')))
        self.assertEqual(set(combos), all_possible)
        self.assertEqual({p['system'] for p in profiles if p['system'] != 'CUSTOM'},
                         {'METRIC', 'IMPERIAL', 'UK_MIXED'})

    def test_independent_physical_reference_anchors_and_temperature_differences(self):
        # These are not computed by Kotlin nor imported from WeatherUnits.
        anchors = {
            ('imperial', 'TEMPERATURE', '0', 'false'): (32.0, '°F'),
            ('imperial', 'TEMPERATURE', '100', 'false'): (212.0, '°F'),
            ('imperial', 'TEMPERATURE', '0', 'true'): (0.0, '°F'),
            ('imperial', 'TEMPERATURE', '20', 'true'): (36.0, '°F'),
            ('imperial', 'WIND_SPEED', '16.09344', 'false'): (10.0, 'mph'),
            ('uk_mixed', 'DISTANCE', '16.09344', 'false'): (10.0, 'mi'),
            ('imperial', 'PRECIPITATION', '25.4', 'false'): (1.0, 'in'),
            ('imperial', 'HEIGHT', '3.048', 'false'): (10.0, 'ft'),
            ('imperial', 'PRESSURE', '1013.25', 'false'): (1013.25 / 33.86389, 'inHg'),
        }
        found = {}
        with (DATA / 'conversions.tsv').open(encoding='utf-8', newline='') as fh:
            for row in csv.DictReader(fh, delimiter='\t'):
                key = (row['profile'], row['unit'], row['metric'], row['delta'])
                if key in anchors:
                    found[key] = row
        self.assertEqual(set(found), set(anchors))
        for key, (value, label) in anchors.items():
            with self.subTest(key=key):
                self.assertAlmostEqual(float(found[key]['expected']), value, places=8)
                self.assertEqual(found[key]['symbol'], label)

    def test_full_display_contract_includes_locales_precisions_and_subresolution_rain(self):
        locales, digits, units = set(), set(), set()
        have_trace, have_fahrenheit, have_knots = False, False, False
        for row in self.rows('display.tsv'):
            locales.add(row['locale'])
            digits.add(int(row['digits']))
            units.add(row['unit'])
            have_trace |= row['expected_value'].startswith('<')
            have_fahrenheit |= '°F' in row['expected_format']
            have_knots |= row['expected_format'].endswith(' kn')
            self.assertTrue(row['expected_format'].startswith(row['expected_value']))
            self.assertNotEqual(row['expected_format'], row['expected_value'])
        self.assertEqual(locales, {'en_US', 'fr_FR', 'de_DE', 'es_ES', 'it_IT', 'tr_TR'})
        self.assertEqual(digits, {0, 1, 2, 6})
        self.assertEqual(units, {'TEMPERATURE', 'TEMPERATURE_COMPACT', 'WIND_SPEED',
                                 'PRECIPITATION', 'PRECIPITATION_RATE', 'HEIGHT',
                                 'DISTANCE', 'PRESSURE', 'PERCENT', 'SECONDS'})
        self.assertTrue(have_trace and have_fahrenheit and have_knots)

    def test_corpus_is_wired_to_real_jvm_execution_not_only_python_checks(self):
        spec = importlib.util.spec_from_file_location('corpus_report_unit', ROOT / 'scripts/report_test_results.py')
        mod = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = mod
        spec.loader.exec_module(mod)
        actual = {name: meta['cases'] for name, meta in self.manifest['files'].items()}
        self.assertEqual(mod.read_unit_corpus_manifest(DATA / 'manifest.json'), actual)
        self.assertEqual(set(mod.UNIT_CORPUS_CONSUMERS), set(actual))
        kotlin = (ROOT / 'app/src/test/java/com/meteocompare/app/core/units/UnitConversionCorpusTest.kt').read_text(encoding='utf-8')
        for filename, (klass, method) in mod.UNIT_CORPUS_CONSUMERS.items():
            with self.subTest(file=filename):
                self.assertEqual(klass, 'UnitConversionCorpusTest')
                self.assertIn(f'fun {method}()', kotlin)
                self.assertIn(f'proof("{filename}"', kotlin)
                self.assertIn(f'"{filename}" to {actual[filename]:_}', kotlin)
        for workflow in ('quality-checks.yml', 'release-fdroid.yml'):
            data = (ROOT / '.github/workflows' / workflow).read_text(encoding='utf-8')
            self.assertIn('generate_unit_conversion_corpus.py --check', data)
            self.assertIn('--require-corpus both', data)
        self.assertIn('generate_unit_conversion_corpus.py --check', (ROOT / 'scripts/verify.sh').read_text(encoding='utf-8'))

        # A checked-in TSV and a passing Python generator do not by themselves
        # exercise the conversion engine. Guard the JVM consumer's direct calls
        # to the production WeatherUnits methods and the expected-value checks.
        self.assertIn('import com.meteocompare.app.domain.model.*', kotlin)
        self.assertIn('row.getValue("expected").toDouble()', kotlin)
        self.assertIn('units.convert(metric, kind, delta)', kotlin)
        self.assertIn('assertEquals("$id converted numeric value", expected, actual, tolerance)', kotlin)
        self.assertIn('row.getValue("symbol"), units.label(kind)', kotlin)
        self.assertIn('units.value(metric, kind, digits, locale, delta)', kotlin)
        self.assertIn('units.format(metric, kind, digits, locale, delta)', kotlin)
        self.assertIn('assertEquals("$id numeric text"', kotlin)
        self.assertIn('assertEquals("$id value and unit"', kotlin)
        self.assertIn('units.value(value, kind, locale = locale)', kotlin)
        self.assertIn('units.format(value, kind, locale = locale)', kotlin)
        self.assertIn('to WeatherUnits(', kotlin)

    def test_checked_in_vectors_are_exactly_reproducible(self):
        subprocess.run([sys.executable, 'scripts/generate_unit_conversion_corpus.py', '--check'],
                       cwd=ROOT, check=True, capture_output=True, text=True)


if __name__ == '__main__':
    unittest.main()
