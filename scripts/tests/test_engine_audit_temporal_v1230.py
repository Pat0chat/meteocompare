"""Release guardrails for the verified meteorological time conventions."""
import csv
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'app/src/main/java/com/meteocompare/app'
FIXTURES = ROOT / 'app/src/test/resources/weather_regression/v3'


class EngineAuditTemporalTest(unittest.TestCase):
    def test_civil_day_corpus_is_reproducible_and_has_dst_and_fractional_offsets(self):
        subprocess.run([sys.executable, str(ROOT / 'scripts/generate_civil_day_coverage_corpus.py'), '--check'],
                       check=True, capture_output=True, text=True)
        contents = (FIXTURES / 'civil_day_coverage.tsv').read_bytes()
        manifest = json.loads((FIXTURES / 'civil_day_coverage_manifest.json').read_text(encoding="utf-8"))
        self.assertEqual(357, manifest['cases'])
        self.assertEqual(hashlib.sha256(contents).hexdigest(), manifest['sha256'])
        records = list(csv.DictReader(contents.decode().splitlines(), delimiter='\t'))
        self.assertEqual(357, len(records))
        self.assertEqual({'23.0', '24.0', '25.0', '23.5', '24.5'},
                         set(manifest['dayLengthsInHours']))
        self.assertTrue(any(r['zone'] == 'Asia/Kathmandu' for r in records))
        self.assertTrue(any(r['zone'] == 'Australia/Lord_Howe' for r in records))

    def test_evolution_snapshot_uses_physical_hour_coverage_not_raw_counts(self):
        helper = (APP / 'domain/util/ForecastEvolutionHourlyCoverage.kt').read_text(encoding="utf-8")
        recorder = (APP / 'data/repository/ForecastEvolutionRecorder.kt').read_text(encoding="utf-8")
        self.assertIn('start.plusSeconds(3_600L)', helper)
        self.assertIn('first.plusSeconds(offset * 3_600L) in validInstants', helper)
        self.assertIn('ForecastPhysicalLimits.precipitation(', helper)
        self.assertIn('ForecastPhysicalLimits.temperature(', helper)
        self.assertIn('ForecastPhysicalLimits.wind(', helper)
        self.assertIn('series.hasCompleteCivilDayHourlyCoverage(', recorder)
        self.assertNotIn('indices.size !in COMPLETE_CIVIL_DAY_HOURS', recorder)

    def test_scenario_12h_rejects_corrupt_extremes_at_entry(self):
        path = (APP / 'domain/util/WeatherScenarioBuilder.kt').read_text(encoding="utf-8")
        for expected in ('ForecastPhysicalLimits.temperature(',
                         'ForecastPhysicalLimits.precipitation(',
                         'ForecastPhysicalLimits.percentage(',
                         'ForecastPhysicalLimits.gust('):
            self.assertIn(expected, path)
        self.assertIn('HourlySampling.precipitationForSlot(series.hourly, index)', path)
        self.assertIn('HourlySampling.gustForSlot(series.hourly, index)', path)
        self.assertIn('series.hourly.exactIndex(target)', path)

    def test_daily_moisture_and_pressure_deduplicate_after_metric_validation(self):
        moisture = (APP / 'domain/util/ForecastMoisture.kt').read_text(encoding="utf-8")
        atmosphere = (APP / 'domain/util/DailyAtmosphericMetrics.kt').read_text(encoding="utf-8")
        self.assertIn('humidity.putIfAbsent(time, it)', moisture)
        self.assertIn('dewPoint.putIfAbsent(time, it)', moisture)
        self.assertIn('ForecastPhysicalLimits::percentage', moisture)
        self.assertIn('ForecastPhysicalLimits::dewPoint', moisture)
        self.assertIn('feelsLike.putIfAbsent(timestamp, it)', atmosphere)
        self.assertIn('pressure.putIfAbsent(timestamp, it)', atmosphere)
        self.assertIn('ForecastPhysicalLimits.pressure(', atmosphere)
        for name in ('ForecastMoistureTest.kt', 'DailyAtmosphericMetricsTest.kt'):
            test = (ROOT / 'app/src/test/java/com/meteocompare/app/domain/util' / name).read_text(encoding="utf-8")
            self.assertTrue('Duplicate' in test)

    def test_frozen_status_corpus_models_preceding_hour_not_next_hour(self):
        test = (ROOT / 'app/src/test/java/com/meteocompare/app/domain/util/ConditionStatusCorpusTest.kt').read_text(encoding="utf-8")
        self.assertIn('List(n + 1)', test)
        self.assertIn('it in 1..k', test)
        self.assertIn('if (it < k) wmo else null', test)
        self.assertNotIn('if (it < k && wmo == 61)', test)

    def test_all_engine_variants_have_adversarial_jvm_suite(self):
        test = (ROOT / 'app/src/test/java/com/meteocompare/app/domain/usecase/ForecastEngineInvariantMatrixTest.kt').read_text(encoding="utf-8")
        self.assertIn('ForecastEngine.entries.forEach', test)
        self.assertIn('Double.NaN', test)
        self.assertIn('Double.POSITIVE_INFINITY', test)
        self.assertIn('Double.NEGATIVE_INFINITY', test)
        self.assertIn('entries.reversed()', test)


if __name__ == '__main__':
    unittest.main()
