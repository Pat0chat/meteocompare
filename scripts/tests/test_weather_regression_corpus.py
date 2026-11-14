"""Independent structural checks for the immutable weather regression fixtures."""
import hashlib
import json
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'app/src/test/resources/weather_regression/v1'


def canonical_fixture_bytes(content: bytes) -> bytes:
    """Allow Windows git CRLF checkout without weakening golden SHA-256 checks."""
    normalized = content.replace(b'\r\n', b'\n')
    if b'\r' in normalized:
        raise ValueError('Unexpected bare carriage return in fixture')
    return normalized


class WeatherRegressionCorpusIntegrityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads((DATA / 'manifest.json').read_text(encoding='utf-8'))

    def test_all_fixture_files_have_expected_hash_and_unique_ids(self):
        for name, expected in self.manifest['files'].items():
            with self.subTest(name=name):
                content = canonical_fixture_bytes((DATA / name).read_bytes())
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

    def test_each_fixture_has_a_real_kotlin_consumer_and_exact_execution_proof(self):
        import importlib.util
        import re
        module_path = ROOT / 'scripts/report_test_results.py'
        spec = importlib.util.spec_from_file_location('corpus_report_wiring', module_path)
        module = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = module
        spec.loader.exec_module(module)
        sources = (
            ROOT / 'app/src/test/java/com/meteocompare/app/regression/WeatherRegressionCorpusTest.kt',
            ROOT / 'app/src/test/java/com/meteocompare/app/widget/WidgetRegressionCorpusTest.kt',
        )
        corpus_tests = '\n'.join(source.read_text(encoding='utf-8') for source in sources)
        expected = {name: meta['cases'] for name, meta in self.manifest['files'].items()}
        self.assertEqual(set(expected), set(module.CORPUS_CONSUMERS))
        self.assertEqual(len(module.CORPUS_CONSUMERS), 9)
        for name, number in expected.items():
            with self.subTest(fixture=name):
                self.assertEqual(len(re.findall(r'records\("' + re.escape(name) + r'"\)', corpus_tests)), 1)
                self.assertEqual(len(re.findall(r'proof\("' + re.escape(name) +
                                                r'",\s*(?:count|cases),\s*' + str(number) + r',',
                                                corpus_tests)), 1)
                klass, method = module.CORPUS_CONSUMERS[name]
                self.assertIn(f'fun {method}()', corpus_tests)
        gradle = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
        self.assertIn('outputs.upToDateWhen { false }', gradle)
        self.assertIn('outputs.doNotCacheIf(', gradle)
        for workflow in ('quality-checks.yml', 'release-fdroid.yml'):
            code = (ROOT / '.github/workflows' / workflow).read_text(encoding='utf-8')
            self.assertIn('--require-corpus both', code)

    def test_generator_is_deterministic_and_committed_fixtures_are_current(self):
        subprocess.run([sys.executable, 'scripts/generate_weather_regression_corpus.py', '--check'],
                       cwd=ROOT, check=True, capture_output=True, text=True)


if __name__ == '__main__':
    unittest.main()
