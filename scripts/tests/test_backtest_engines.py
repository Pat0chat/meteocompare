"""Portage Python du moteur V3 : dérive face au Kotlin et comportements de référence."""
import re
import sys
import unittest
from datetime import date, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.backtest import catalogue, engines, parity, reliability  # noqa: E402

KOTLIN = ROOT / 'app/src/main/java/com/meteocompare/app'


def kotlin(relative: str) -> str:
    return (KOTLIN / relative).read_text(encoding='utf-8')


class CatalogueDriftTest(unittest.TestCase):
    """Le portage doit rester aligné sur le catalogue et les lignées de l'app."""

    def test_api_keys_and_horizons_match_weather_model(self):
        source = kotlin('domain/model/WeatherModel.kt')
        found = {}
        for match in re.finditer(r'\n    ([A-Z0-9_]+)\(\s*apiKey = "([^"]+)".*?maxForecastDays = (\d+).*?'
                                 r'coverage = Coverage\.(\w+)', source, re.S):
            found[match.group(1)] = (match.group(2), int(match.group(3)), match.group(4))
        expected = {m.name: (m.api_key, m.max_forecast_days, m.coverage) for m in catalogue.MODELS}
        self.assertEqual(expected, found)

    def test_lineages_match_forecast_consensus(self):
        source = kotlin('domain/usecase/ForecastConsensus.kt')
        block = source.split('fun groupFor(model: WeatherModel): Group = when (model) {', 1)[1].split('\n    }', 1)[0]
        mapping = {}
        for match in re.finditer(r'((?:WeatherModel\.\w+\s*,\s*)*WeatherModel\.\w+)\s*->\s*Group\.(\w+)', block):
            for name in re.findall(r'WeatherModel\.(\w+)', match.group(1)):
                mapping[name] = match.group(2)
        self.assertEqual({m.name: m.group for m in catalogue.MODELS}, mapping)

    def test_default_selection_matches_mvp_selection(self):
        source = kotlin('domain/model/WeatherModel.kt')
        block = source.split('val MVP_SELECTION: List<WeatherModel> = listOf(', 1)[1].split(')', 1)[0]
        self.assertEqual(catalogue.DEFAULT_SELECTION, tuple(re.findall(r'[A-Z0-9_]+', block)))

    def test_engine_constants_match_kotlin(self):
        source = kotlin('domain/usecase/ForecastEngineV3.kt')
        constants = dict(re.findall(r'const val (\w+) = ([\d.]+)', source))
        self.assertEqual(float(constants['MIN_CALIBRATION_SAMPLES']), engines.MIN_CALIBRATION_SAMPLES)
        self.assertEqual(float(constants['FULL_CALIBRATION_SAMPLES']), engines.FULL_CALIBRATION_SAMPLES)
        self.assertEqual(float(constants['MIN_CALIBRATION_COVERAGE']), engines.MIN_CALIBRATION_COVERAGE)
        self.assertEqual(float(constants['MIN_CALIBRATED_FAMILIES']), engines.MIN_CALIBRATED_FAMILIES)
        self.assertEqual(float(constants['MIN_DOMINANT_SCENARIO_SHARE']), engines.MIN_DOMINANT_SCENARIO_SHARE)


class EngineBehaviourTest(unittest.TestCase):
    def test_sibling_models_share_one_vote(self):
        weights = engines.family_balanced_weights(['AROME_FRANCE_HD', 'AROME_FRANCE', 'GFS'])
        self.assertAlmostEqual(0.5, weights['AROME_FRANCE_HD'])
        self.assertAlmostEqual(0.5, weights['AROME_FRANCE'])
        self.assertAlmostEqual(1.0, weights['GFS'])

    def test_weighted_median_splits_exact_halves(self):
        rows = [engines.WeightedEntry('GFS', 10.0, 1.0), engines.WeightedEntry('ECMWF', 12.0, 1.0)]
        self.assertEqual(11.0, engines.weighted_median(rows))

    def test_kotlin_rounding_is_half_up(self):
        self.assertEqual(3, engines.kotlin_round(2.5))
        self.assertEqual(-2, engines.kotlin_round(-2.5))

    def test_huber_limits_an_outlier_without_removing_it(self):
        entries = [engines.Entry(m, v) for m, v in
                   (('GFS', 15.0), ('ECMWF', 15.2), ('UKMO_GLOBAL', 14.9), ('JMA_GSM', 15.1), ('GEM_GLOBAL', 25.0))]
        result = engines.continuous(entries)
        self.assertGreater(result.central, 15.05)
        self.assertLess(result.central, 15.6)

    def test_calibration_needs_two_calibrated_lineages(self):
        profile = engines.CalibrationProfile(bias=2.0, score=80, standard_deviation=1.0,
                                             mean_absolute_error=1.0, sample_size=30)
        entries = [engines.Entry('GFS', 20.0), engines.Entry('ECMWF', 20.0), engines.Entry('JMA_GSM', 20.0)]
        result = engines.continuous(entries, engines.ContinuousOptions(
            engine=engines.CALIBRATION, calibration={'GFS': profile}))
        self.assertTrue(result.fallback)
        self.assertEqual(engines.MULTI_CONSENSUS, result.effective_engine)
        self.assertEqual(20.0, result.central)

    def test_calibration_removes_full_bias_after_thirty_days(self):
        profile = engines.CalibrationProfile(bias=2.0, score=80, standard_deviation=0.5,
                                             mean_absolute_error=2.0, sample_size=30)
        entries = [engines.Entry('GFS', 20.0), engines.Entry('ECMWF', 20.0)]
        result = engines.continuous(entries, engines.ContinuousOptions(
            engine=engines.CALIBRATION, calibration={'GFS': profile, 'ECMWF': profile}))
        self.assertFalse(result.fallback)
        self.assertAlmostEqual(18.0, result.central)

    def test_scenarios_follow_a_clear_majority(self):
        entries = [engines.Entry(m, v) for m, v in
                   (('GFS', 10.0), ('ECMWF', 10.2), ('UKMO_GLOBAL', 10.1), ('JMA_GSM', 10.3),
                    ('GEM_GLOBAL', 14.0), ('ICON_GLOBAL', 14.2))]
        result = engines.continuous(entries, engines.ContinuousOptions(engine=engines.SCENARIOS))
        self.assertEqual(engines.SCENARIOS, result.effective_engine)
        self.assertLess(result.central, 10.4)

    def test_parity_corpus_is_up_to_date(self):
        for name, content in parity.outputs().items():
            committed = (parity.OUT / name).read_bytes().replace(b'\r\n', b'\n')
            self.assertEqual(content, committed, f'{name}: run python3 -m scripts.backtest.parity')


class ReliabilityTest(unittest.TestCase):
    def test_no_profile_before_fourteen_days(self):
        start = date(2026, 6, 1)
        samples = [reliability.Sample(start + timedelta(days=i), 20.0, 19.0) for i in range(13)]
        self.assertIsNone(reliability.compute('TEMPERATURE', samples))
        samples.append(reliability.Sample(start + timedelta(days=13), 20.0, 19.0))
        self.assertAlmostEqual(1.0, reliability.compute('TEMPERATURE', samples).mean_bias)

    def test_window_excludes_the_issue_day_and_older_than_thirty_days(self):
        issue = date(2026, 7, 31)
        history = {issue - timedelta(days=i): reliability.Sample(issue - timedelta(days=i), 1.0, 0.0)
                   for i in range(0, 40)}
        days = sorted(s.target_date for s in reliability.window_samples(history, issue))
        self.assertEqual(issue - timedelta(days=30), days[0])
        self.assertEqual(issue - timedelta(days=1), days[-1])


if __name__ == '__main__':
    unittest.main()
