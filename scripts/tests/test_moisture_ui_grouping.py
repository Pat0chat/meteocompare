"""UI guardrails for moisture presentation in the expanded Home/summary, timeline and charts."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'app/src/main/java/com/meteocompare/app'


def source(relative):
    return (JAVA / relative).read_text(encoding='utf-8')


class MoisturePresentationTests(unittest.TestCase):
    def test_expanded_home_places_dewpoint_under_max_temperature_range(self):
        screen = source('ui/citylist/CityListScreen.kt')
        state = source('ui/citylist/CityListUiState.kt')
        viewmodel = source('ui/citylist/CityListViewModel.kt')
        self.assertIn('supporting = dewPointC?.let', screen)
        self.assertIn('supportingIcon = if (dewPointC != null) DewPointMetricIcon', screen)
        self.assertIn('currentDewPointC: Double? = null', state)
        self.assertIn('currentDewPointC = ForecastConsensus.continuous(', viewmodel)
        self.assertIn('series.hourly.timestamps.indexOf(hour)', viewmodel)

    def test_icons_are_distinct_from_rain_and_temperature(self):
        icons = source('ui/components/MoistureMetricIcons.kt')
        self.assertIn('HumidityGauge', icons)
        self.assertIn('DewCondensation', icons)
        today = source('ui/citydetail/CityDetailScreen.kt')
        compare = source('ui/localitycompare/LocalityComparisonOverview.kt')
        chrono = source('ui/citydetail/ChronoTimeline.kt')
        chart = source('ui/graphicview/GraphicForecastScreen.kt')
        for name in ('HumidityMetricIcon', 'DewPointMetricIcon'):
            for text in (today, compare, chrono, chart):
                self.assertIn(name, text)

    def test_column_cells_are_one_line_each(self):
        cols = source('ui/citydetail/SimplifiedTimeline.kt')
        self.assertIn('icon = HumidityMetricIcon', cols)
        self.assertIn('icon = DewPointMetricIcon', cols)
        self.assertNotIn('text = stringResource(R.string.metric_humidity)\n', cols)
        self.assertNotIn('text = stringResource(R.string.metric_dew_point)\n', cols)

    def test_chart_merges_without_mixing_units_or_extra_rows(self):
        chart = source('ui/graphicview/GraphicForecastScreen.kt')
        self.assertIn('values = renderData.dewPoints,', chart)
        self.assertIn('values = renderData.humidities,', chart)
        self.assertIn('domain = humidityDomain,', chart)
        self.assertIn('PlotDomain(0.0, 100.0', chart)
        self.assertIn('include(point.apparentTemperatureC)', chart)
        self.assertIn('include(point.dewPointC)', chart)
        self.assertNotIn('GraphicMoisturePlot(', chart)
        self.assertNotIn('val dewDomain =', chart)
        self.assertIn('"graphic_dew_point_tooltip_value"', chart)
        self.assertIn('"graphic_humidity_tooltip_value"', chart)
        self.assertIn('selected.humidityPercent?.let', chart)
        self.assertIn('selected.dewPointC?.let', chart)


if __name__ == '__main__':
    unittest.main()
