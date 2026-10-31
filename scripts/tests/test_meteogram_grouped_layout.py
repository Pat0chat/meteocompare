"""Static guardrails for the grouped 1.19.3 Meteogram implementation.

These tests supplement Android/Compose tests; they do not replace an APK build.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'app/src/main/java/com/meteocompare/app/ui/meteogram/MeteogramScreen.kt'


class MeteogramGroupedLayoutTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding='utf-8')

    def test_single_plot_combines_every_metric_on_one_canvas(self):
        section = self.source.split('private fun MeteogramCombinedCard(', 1)[1].split(
            '/** The non-temperature lines', 1)[0]
        self.assertEqual(1, section.count('MeteogramPlot('))
        self.assertNotIn('CombinedPlotSection(', section)
        self.assertIn('TAG_METEOGRAM_COMBINED', section)
        for measurement in ('it.temperatureC', 'it.apparentC', 'it.dewC',
                            'it.humidityPercent?.toDouble()', 'it.pressureHpa',
                            'it.windKmh', 'it.gustKmh'):
            self.assertIn(measurement, section)
        self.assertIn('drawRainBars(', section)
        self.assertIn('drawCloudBars(', section)
        self.assertIn('drawDailyRangeMarkers(', section)

    def test_separate_presets_group_humidity_and_pressure(self):
        preset = self.source.split('internal val MeteogramPresets = ', 1)[1].split(')', 1)[0]
        self.assertIn('MeteogramKind.EVERYDAY', preset)
        self.assertIn('MeteogramKind.TEMPERATURE', preset)
        self.assertNotIn('MeteogramKind.HUMIDITY', preset)
        self.assertNotIn('MeteogramKind.PRESSURE', preset)
        section = self.source.split('private fun MeteogramChartCard(', 1)[1].split(
            'private fun MeteogramCombinedCard(', 1)[0]
        self.assertIn('pressureDomain, PressureColor', section)
        self.assertIn('humidityDomain, HumidityColor', section)
        self.assertIn('MeteogramKind.PRESSURE, height, pressureDomain', section)
        self.assertIn('MeteogramKind.HUMIDITY, height, humidityDomain', section)

    def test_cloud_bars_do_not_pretend_to_be_layers(self):
        bars = self.source.split('private fun DrawScope.drawCloudBars(', 1)[1].split(
            'private fun DrawScope.drawRainBars(', 1)[0]
        self.assertIn('One bar = total cloud cover', self.source)
        self.assertNotIn('CloudDetailColor', bars)
        self.assertNotIn('0.33f', bars)
        self.assertNotIn('0.66f', bars)

    def test_daily_plot_draws_visible_hourly_temperature_pillars(self):
        daily = self.source.split('private fun MeteogramDailyStrip(', 1)[1]
        self.assertIn('for (i in visible)', daily)
        self.assertIn('hours[i].temperatureC', daily)
        self.assertIn('drawLine(TemperatureColor', daily)
        self.assertIn('meteogramHourlyDailyDomain(hours, ranges)', daily)


if __name__ == '__main__':
    unittest.main()
