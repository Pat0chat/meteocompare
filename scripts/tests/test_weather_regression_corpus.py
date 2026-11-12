"""Independent structural checks for the immutable weather regression fixtures."""
import hashlib
import json
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'app/src/test/resources/weather_regression/v1'


class WeatherRegressionCorpusIntegrityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads((DATA / 'manifest.json').read_text(encoding='utf-8'))

    def test_all_fixture_files_have_expected_hash_and_unique_ids(self):
        for name, expected in self.manifest['files'].items():
            with self.subTest(name=name):
                content = (DATA / name).read_bytes()
                self.assertEqual(hashlib.sha256(content).hexdigest(), expected['sha256'])
                lines = content.decode('utf-8').splitlines()
                self.assertEqual(len(lines) - 1, expected['cases'])
                columns = len(lines[0].split('\t'))
                ids = [line.split('\t')[0] for line in lines[1:]]
                self.assertEqual(len(ids), len(set(ids)))
                self.assertTrue(all(len(line.split('\t')) == columns for line in lines[1:]))

    def test_corpus_is_large_and_regressions_are_present(self):
        counts = {name: info['cases'] for name, info in self.manifest['files'].items()}
        self.assertGreaterEqual(sum(counts.values()), 74_000)
        self.assertGreaterEqual(counts['sunshine_consensus.tsv'], 5_000)
        self.assertGreaterEqual(counts['widget_layout.tsv'], 12_000)
        self.assertGreaterEqual(counts['widget_metrics.tsv'], 1_400)
        self.assertGreaterEqual(counts['widget_refresh.tsv'], 2_600)
        daily = (DATA / 'daily.tsv').read_text(encoding='utf-8')
        for marker in ('isolated-', 'wet-majority-', 'equal-', 'middle-gap-',
                       'sparse-daily-', 'sparse-nodaily-', 'cloud-fallback-',
                       'duplicate-native-', 'Europe/Paris\t2026-03-29',
                       'Europe/Paris\t2026-10-25', 'America/New_York'):
            with self.subTest(marker=marker):
                self.assertIn(marker, daily)
        clouds = (DATA / 'clouds.tsv').read_text(encoding='utf-8')
        self.assertIn('VEILED', clouds)
        self.assertIn('OVERCAST', clouds)
        sunshine = (DATA / 'sunshine.tsv').read_text(encoding='utf-8')
        self.assertIn('solar-native-', sunshine)
        self.assertIn('solar-dni-', sunshine)
        sunshine_consensus = (DATA / 'sunshine_consensus.tsv').read_text(encoding='utf-8')
        self.assertIn('NCEP_AIGFS', sunshine_consensus)
        self.assertIn('solar-consensus-', sunshine_consensus)
        widget = (DATA / 'widget_layout.tsv').read_text(encoding='utf-8')
        self.assertIn('widget-105-105', widget)
        self.assertIn('-PILL-', widget)
        refresh = (DATA / 'widget_refresh.tsv').read_text(encoding='utf-8')
        self.assertIn('refresh-9223372036854775807', refresh)

    def test_generator_is_deterministic_and_committed_fixtures_are_current(self):
        subprocess.run([sys.executable, 'scripts/generate_weather_regression_corpus.py', '--check'],
                       cwd=ROOT, check=True, capture_output=True, text=True)


if __name__ == '__main__':
    unittest.main()
