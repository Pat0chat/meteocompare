"""Static architecture guard: apparent temperature reuses the normal forecast and UI data paths.

These checks do NOT replace instrumentation / network tracing on an Android device.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'app/src/main/java/com/meteocompare/app'


def source(relative):
    return (JAVA / relative).read_text(encoding='utf-8')


class ApparentTemperatureIntegrationTests(unittest.TestCase):
    def test_requested_once_in_existing_batched_forecast_without_new_endpoint(self):
        api = source('data/remote/OpenMeteoApi.kt')
        # Pressure is requested in the same existing forecast batch since 1.19.2.
        # Do not assume precipitation follows the apparent-temperature field directly.
        self.assertEqual(api.count('"temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m,pressure_msl,precipitation'), 1)
        self.assertIn('const val APPARENT_TEMPERATURE = "apparent_temperature"',
                      source('data/remote/BatchedForecastSplitter.kt'))

    def test_timeline_and_today_summary_reuse_one_memoized_hourly_consensus(self):
        detail = source('ui/citydetail/CityDetailScreen.kt')
        self.assertIn('val baseHourlyTimeline = remember(forecast, presentationNow, engineCacheSignature)', detail)
        self.assertIn('currentHourlyApparentTemperature(baseHourlyTimeline, currentHour)', detail)
        self.assertIn('currentApparentTemp = currentApparentForSummary', detail)
        self.assertNotIn('currentApparentTemperature(', detail)

    def test_frise_and_chart_cache_plot_data(self):
        frise = source('ui/citydetail/ChronoTimeline.kt')
        chart = source('ui/graphicview/GraphicForecastScreen.kt')
        self.assertIn('remember(points, mode) { chronoApparentTemperatureSeries(points, mode) }', frise)
        self.assertIn('remember(centralValues, apparentValues, minValues, maxValues, mode)', frise)
        self.assertIn('val apparentDashEffect = remember(density)', frise)
        self.assertIn('val renderData = remember(state.points, state.solarByDate, zone)', chart)

    def test_apparent_is_not_added_to_global_convergence_or_new_network_work(self):
        builder = source('ui/citydetail/SimplifiedTimelineBuilder.kt')
        self.assertIn('apparentTemperatureC = apparent?.forecastValue?.central', builder)
        self.assertIn('allowCalibration = false', builder)
        self.assertNotIn('apparent.agreement.convergencePercent', builder)
        for ui in ['ui/citydetail/ChronoTimeline.kt', 'ui/citydetail/SimplifiedTimeline.kt']:
            self.assertNotIn('OpenMeteoApi', source(ui))
            self.assertNotIn('refreshIfStale', source(ui))


if __name__ == '__main__':
    unittest.main()
