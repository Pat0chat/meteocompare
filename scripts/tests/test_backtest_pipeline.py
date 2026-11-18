"""Banc d'essai : agrégation des données et variantes proposées (hors ligne)."""
import sys
import unittest
from datetime import date, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.backtest import dataset, variants  # noqa: E402


class DatasetTest(unittest.TestCase):
    def test_incomplete_forecast_day_is_dropped(self):
        times = [f'2026-07-0{d}T{h:02d}:00' for d in (1, 2) for h in range(24)]
        values = [float(i % 24) for i in range(48)]
        values[30] = None
        key = 'ecmwf_ifs'
        hourly = {'time': times,
                  f'temperature_2m_previous_day1_{key}': values,
                  f'precipitation_previous_day1_{key}': [0.1] * 48,
                  f'wind_speed_10m_previous_day1_{key}': [5.0] * 48}
        out = dataset.aggregate_previous_runs(hourly, ['ECMWF'], [1], date(2026, 7, 1), date(2026, 7, 2))
        series = out[1]['ECMWF']
        self.assertEqual({date(2026, 7, 1): 23.0}, series['tmax'])
        self.assertAlmostEqual(2.4, series['precip'][date(2026, 7, 2)])

    def test_metar_uses_local_day_and_one_report_per_hour(self):
        rows = []
        for hour in range(24):
            rows.append({'valid': f'2026-07-01 {hour:02d}:00', 'tmpc': '20', 'sknt': '10'})
            rows.append({'valid': f'2026-07-01 {hour:02d}:30', 'tmpc': '30', 'sknt': '10'})
        rows.append({'valid': '2026-06-30 22:00', 'tmpc': '20', 'sknt': '10'})
        rows.append({'valid': '2026-06-30 23:00', 'tmpc': '20', 'sknt': '10'})
        daily = dataset.metar_daily(rows, 'Europe/Paris')
        self.assertEqual(20.0, daily['tmax'][date(2026, 7, 1)])
        self.assertAlmostEqual(10 * dataset.KNOT_TO_KMH, daily['wind'][date(2026, 7, 1)])

    def test_ghcn_shift_is_chosen_against_the_reference(self):
        reference = {date(2026, 7, d): (5.0 if d % 3 == 0 else 0.0) for d in range(1, 29)}
        rows = {(day + timedelta(days=1)).isoformat(): {'PRCP': str(value)} for day, value in reference.items()}
        precipitation, shift = dataset.ghcn_precipitation(rows, reference)
        self.assertEqual(1, shift)
        self.assertEqual(5.0, precipitation[date(2026, 7, 3)])


class VariantTest(unittest.TestCase):
    def test_decay_state_never_uses_the_future(self):
        series = variants.DecaySeries([(date(2026, 7, d), 1.0) for d in range(1, 11)])
        self.assertIsNone(series.as_of(date(2026, 7, 1)))
        early = series.as_of(date(2026, 7, 2))
        late = series.as_of(date(2026, 7, 11))
        self.assertAlmostEqual(0.1, early.bias)
        self.assertGreater(late.bias, early.bias)
        self.assertLess(late.bias, 1.0)

    def test_empirical_interval_uses_error_quantiles(self):
        errors = [float(i) for i in range(-5, 6)]
        self.assertEqual((16.0, 24.0), variants.empirical_interval(20.0, errors))
        self.assertIsNone(variants.empirical_interval(20.0, errors[:5]))

    def test_recalibrated_probability_moves_towards_observed_frequency(self):
        history = [(0.6, False)] * 30
        self.assertLess(variants.recalibrated_probability(0.6, history), 0.2)
        self.assertEqual(0.6, variants.recalibrated_probability(0.6, []))


if __name__ == '__main__':
    unittest.main()
