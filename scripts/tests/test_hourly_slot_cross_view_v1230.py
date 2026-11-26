"""Static architectural guards: executable hourly semantics live in JVM tests.

Never rewrite this contract to match an old convention without changing the
underlying forecast data and JVM tests accordingly.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'app/src/main/java/com/meteocompare/app'
TESTS = ROOT / 'app/src/test/java/com/meteocompare/app'


def source(relative):
    return (APP / relative).read_text(encoding='utf-8')


class HourlySlotCrossViewContract(unittest.TestCase):
    def test_gusts_are_ending_interval_across_all_hourly_views(self):
        sampler = source('domain/util/HourlySampling.kt')
        self.assertIn('fun gustForSlot(hourly: HourlyForecast, atIndex: Int)', sampler)
        self.assertIn('nextHourIndex(hourly, atIndex)?.let { hourly.windGusts10m.getOrNull(it) }', sampler)
        for relative in (
            'ui/citydetail/SimplifiedTimelineBuilder.kt',
            'ui/graphicview/GraphicForecastViewModel.kt',
            'ui/citydetail/CityDetailScreen.kt',
            'domain/util/WeatherScenarioBuilder.kt',
        ):
            with self.subTest(relative=relative):
                code = source(relative)
                self.assertIn('HourlySampling.gustForSlot(', code)
                self.assertNotIn('windGusts10m.getOrNull(', code)

    def test_rain_ends_at_h_plus_one_while_wmo_is_at_h(self):
        sampler = source('domain/util/HourlySampling.kt')
        self.assertIn('fun precipitationForSlot(', sampler)
        self.assertIn('start.plusSeconds(3_600L)', sampler)
        for relative in (
            'domain/util/ForecastAggregates.kt',
            'ui/citydetail/SimplifiedTimelineBuilder.kt',
            'ui/graphicview/GraphicForecastViewModel.kt',
            'domain/util/WeatherScenarioBuilder.kt',
            'domain/usecase/LocalityComparison.kt',
            'domain/usecase/ConfidenceCalculator.kt',
        ):
            with self.subTest(relative=relative):
                self.assertIn('HourlySampling.precipitationForSlot(', source(relative))
        self.assertIn('ConditionDecisionEngine.upcomingHour(',
                      source('ui/citydetail/SimplifiedTimelineBuilder.kt'))

    def test_solar_accumulations_use_forward_endpoint_and_keep_missing_unknown(self):
        solar = source('domain/usecase/LocalityComparison.kt')
        self.assertIn('val nextIndex = hourly.indexOfTimestamp(instant.plusSeconds(3600))', solar)
        self.assertIn('sunshineDuration.getOrNull(it)', solar)
        self.assertIn('directNormalIrradiance.getOrNull(it)', solar)
        self.assertIn('if (centralCloud != null && centralCloud >= 90)', solar)
        self.assertIn('if (solarFamilies.isEmpty()', solar)
        cloud_layers = source('ui/citydetail/CloudLayersTimeline.kt')
        self.assertIn('hour.plusSeconds(3600L)', cloud_layers)
        engine = source('domain/util/ConditionDecisionEngine.kt')
        self.assertIn('hourly.timestamps[index].plusSeconds(HOUR_SECONDS)', engine)
        self.assertIn('hourly.sunshineDuration.getOrNull(next)', engine)

    def test_five_widget_slots_have_six_source_endpoints(self):
        path = TESTS / 'widget/WidgetForecastSelectionTest.kt'
        test = path.read_text(encoding='utf-8')
        start = test.index('fun `hourly fusionne les modeles disponibles avec le consensus v2`()')
        end = test.index('    @Test', start)
        fixture = test[start:end]
        self.assertIn('val timestamps = (1L..6L)', fixture)
        self.assertIn('precipitationProbability = listOf(0, 10, 20, 30, 40, 50)', fixture)
        self.assertIn('listOf(5, 10, 15, 20, 25)', fixture)
        self.assertIn('widget five hours do not reuse preceding probability without sixth endpoint', test)


if __name__ == '__main__':
    unittest.main()
