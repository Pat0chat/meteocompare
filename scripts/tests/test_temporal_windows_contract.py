"""Pre-release guardrails for temporal origin and independently reviewed fixtures."""
import csv
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'app/src/main/java/com/meteocompare/app'
FIXTURE = ROOT / 'app/src/test/resources/weather_regression/v3'


class TemporalWindowsContractTest(unittest.TestCase):
    def test_independent_corpus_is_reproducible(self):
        subprocess.run([sys.executable, str(ROOT/'scripts/generate_temporal_windows_corpus.py'), '--check'],
                       check=True, capture_output=True, text=True)
        body = (FIXTURE/'temporal_windows.tsv').read_bytes()
        manifest = json.loads((FIXTURE/'manifest.json').read_text(encoding="utf-8"))
        self.assertEqual(hashlib.sha256(body).hexdigest(), manifest['sha256'])
        rows = list(csv.DictReader(body.decode().splitlines(), delimiter='\t'))
        self.assertEqual(300, len(rows))
        self.assertTrue(any(x['zone']=='Asia/Kolkata' and ':30:00Z' in x['hourUtc'] for x in rows))
        self.assertTrue(any(x['zone']=='Europe/Paris' and x['date']=='2026-10-25' for x in rows))

    def test_native_and_rain_windows_are_separate_in_daily_aggregation(self):
        engine = (SOURCE/'domain/util/ConditionDecisionEngine.kt').read_text(encoding="utf-8")
        self.assertIn('val timestamp = series.hourly.timestamps[index]', engine)
        self.assertNotIn('timestamp = series.hourly.timestamps[i].truncatedTo', engine)
        self.assertIn('requireNotNull(decision.timeWindow)', engine)
        self.assertIn('maxWithOrNull', engine)
        self.assertIn('decision.origin == ConditionOrigin.HOURLY_WMO', engine)

    def test_solar_maxima_use_intersection_with_preceding_precipitation_hour(self):
        daily = (SOURCE/'domain/util/ForecastSeriesDaily.kt').read_text(encoding="utf-8")
        self.assertIn('timestamp.isAfter(start)', daily)
        self.assertIn('timestamp.minusSeconds(3600L).isBefore(end)', daily)
        self.assertNotIn('instant.truncatedTo(ChronoUnit.HOURS)', daily)

    def test_future_twelve_hour_rain_is_aligned_with_both_ends(self):
        sampler = (SOURCE/'domain/util/HourlySampling.kt').read_text(encoding='utf-8')
        self.assertIn('start.plusSeconds(3_600L)', sampler)
        for filename in ('ForecastAggregates.kt', 'WeatherScenarioBuilder.kt'):
            text = (SOURCE/'domain/util'/filename).read_text(encoding='utf-8')
            self.assertIn('exactIndex(target)', text)
            self.assertIn('HourlySampling.precipitationForSlot(series.hourly, index)', text)
            self.assertIn('ConditionDecisionEngine.upcomingHour(series.hourly, index)', text)
            self.assertNotIn('ConditionDecisionEngine.hourly(series.hourly, index)', text)
        self.assertIn('ConditionWindowKind.HOURLY',
                      (SOURCE/'domain/util/ForecastAggregates.kt').read_text(encoding='utf-8'))

    def test_bootstrap_uses_unambiguous_instants_and_next_midnight(self):
        api = (SOURCE/'data/remote/PreviousRunsApi.kt').read_text(encoding="utf-8")
        bootstrap = (SOURCE/'domain/usecase/BootstrapBiasHistoryUseCase.kt').read_text(encoding="utf-8")
        self.assertIn('@Query("timeformat") timeFormat: String = "unixtime"', api)
        self.assertIn('endDate.plusDays(1)', bootstrap)
        self.assertIn('instant.minusSeconds(3_600L).atZone(zone).toLocalDate()', bootstrap)
        self.assertIn('expectedHours != civilDayHours', bootstrap)
        self.assertIn('expectedPrecipHours == civilDayHours', bootstrap)


if __name__ == '__main__':
    unittest.main()
