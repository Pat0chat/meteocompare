"""Pre-release architecture guards for humidity/dew-point propagation (not a build substitute)."""
from pathlib import Path
import unittest

JAVA = Path(__file__).resolve().parents[2] / 'app/src/main/java/com/meteocompare/app'


def code(path):
    return (JAVA / path).read_text(encoding='utf-8')


class MoistureIntegrationTests(unittest.TestCase):
    def test_one_batched_request_and_suffix_safe_split(self):
        api = code('data/remote/OpenMeteoApi.kt')
        splitter = code('data/remote/BatchedForecastSplitter.kt')
        self.assertIn('relative_humidity_2m,dew_point_2m', api)
        self.assertIn('get.ints(HourlyVar.RELATIVE_HUMIDITY_2M)', splitter)
        self.assertIn('get.doubles(HourlyVar.DEW_POINT_2M)', splitter)
        self.assertNotIn('getHumidityForecast', api)

    def test_cache_compatibility_physical_guards_and_alignment(self):
        dto = code('data/remote/dto/ForecastResponseDto.kt')
        hourly = code('domain/model/ForecastSeries.kt')
        mapper = code('data/mapper/ForecastMapper.kt')
        for name in ['relativeHumidity2m', 'dewPoint2m']:
            self.assertIn('val ' + name + ': List', dto)
            self.assertIn('val ' + name + ': List', hourly)
            self.assertIn('alignNonNullTimes', mapper)
        self.assertIn('ForecastPhysicalLimits::percentage', mapper)
        self.assertIn('ForecastPhysicalLimits::dewPoint', mapper)
        self.assertIn('emptyList()', hourly)

    def test_local_daily_aggregation_uses_calendar_day_and_half_coverage(self):
        daily = code('domain/util/ForecastMoisture.kt')
        self.assertIn('date.atStartOfDay(zone).toInstant()', daily)
        self.assertIn('date.plusDays(1).atStartOfDay(zone).toInstant()', daily)
        self.assertIn('ChronoUnit.HOURS.between(begin, end)', daily)
        self.assertIn('seen.add(time)', daily)
        self.assertIn('it.size.toDouble() >= required', daily)

    def test_today_chart_model_breakdown_and_chronologies(self):
        summary = code('ui/citydetail/CityDetailScreen.kt')
        chart = code('ui/graphicview/GraphicForecastScreen.kt')
        model = code('ui/graphicview/GraphicForecastViewModel.kt')
        chrono = code('ui/citydetail/ChronoTimeline.kt')
        columns = code('ui/citydetail/SimplifiedTimeline.kt')
        for k in ['today_summary_humidity', 'today_summary_dew_point']:
            self.assertIn(k, summary)
        for k in ['GraphicMoisturePlot', 'TAG_GRAPHIC_HUMIDITY_PLOT', 'TAG_GRAPHIC_DEW_POINT_PLOT']:
            self.assertIn(k, chart)
        self.assertIn('val dewPointC: Double?', model)
        self.assertIn('val humidityPercent: Int?', model)
        self.assertEqual(chrono.count('ChronoMoistureLane(points = points'), 2)
        self.assertIn('point.humidityPercent', columns)
        self.assertIn('point.dewPointC', columns)

    def test_both_city_comparison_modes_and_units(self):
        comparison = code('domain/usecase/LocalityComparison.kt')
        hourly = code('ui/localitycompare/LocalityHourlyChart.kt')
        daily = code('ui/localitycompare/LocalityComparisonOverview.kt')
        self.assertIn('dailyMoisture(date, zone)', comparison)
        self.assertIn('relativeHumidity2m.getOrNull(i)', comparison)
        self.assertIn('dewPoint2m.getOrNull(i)', comparison)
        self.assertIn('HUMIDITY(R.string.metric_humidity)', hourly)
        self.assertIn('DEW_POINT(R.string.metric_dew_point)', hourly)
        self.assertIn('DEW_POINT -> units.temperature(it)', hourly)
        self.assertIn('CompareChartMetric.DEW_POINT -> units.temp', daily)


if __name__ == '__main__':
    unittest.main()
