"""Guard the separated vertical domains of the Cloud / Rain / Pressure chart."""
from pathlib import Path
import re
import unittest

SRC = (Path(__file__).resolve().parents[2]
       / 'app/src/main/java/com/meteocompare/app/ui/meteogram/MeteogramScreen.kt')


class MeteogramWeatherZonesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SRC.read_text(encoding='utf-8')

    def test_plot_lanes_are_disjoint_and_all_three_cloud_layers_fit_upstairs(self):
        src = self.source
        cloud = float(re.search(r'WeatherCloudFraction = ([0-9.]+)f', src).group(1))
        lower = float(re.search(r'WeatherLowerFraction = ([0-9.]+)f', src).group(1))
        self.assertTrue(0.45 <= cloud < lower <= 0.70)
        self.assertGreater(lower - cloud, 0.015)
        bands = src.split('private fun DrawScope.drawCloudLayerBands(', 1)[1].split(
            'private fun DrawScope.drawWeatherLaneDivider(', 1)[0]
        self.assertIn('val laneHeight = totalHeight / 3f', bands)
        self.assertIn('CloudHighColor, CloudMidColor, CloudLowColor', bands)

    def test_weather_only_uses_separate_upper_and_lower_regions(self):
        card = self.source.split('private fun MeteogramChartCard(', 1)[1].split(
            'private fun MeteogramGraphFrame(', 1)[0]
        weather = card.split('MeteogramKind.EVERYDAY -> {', 1)[1].split(
            'MeteogramKind.TEMPERATURE ->', 1)[0]
        self.assertIn('topFraction = WeatherCloudFraction', weather)
        self.assertIn('drawWeatherLaneDivider()', weather)
        self.assertEqual(2, weather.count('plotTopFraction = WeatherLowerFraction'))
        self.assertIn('drawRainBars(', weather)
        self.assertIn('pressureDomain, PressureColor,', weather)
        self.assertLess(weather.index('drawCloudLayerBands('), weather.index('drawRainBars('))
        self.assertLess(weather.index('drawRainBars('), weather.index('drawMetricCurve('))
        # The combined view remains a single mixed plot with original transforms.
        combined = self.source.split('private fun MeteogramCombinedCard(', 1)[1].split(
            'private fun MeteogramCloudLayersHint(', 1)[0]
        self.assertNotIn('WeatherLowerFraction', combined)
        self.assertNotIn('drawWeatherLaneDivider()', combined)

    def test_scale_ticks_follow_vertical_lanes(self):
        card = self.source.split('private fun MeteogramChartCard(', 1)[1].split(
            'private fun MeteogramGraphFrame(', 1)[0]
        self.assertIn('plotTopFraction = if (kind == MeteogramKind.EVERYDAY)', card)
        self.assertIn('plotTopFraction = WeatherLowerFraction)', card)
        cloud_axis = self.source.split('private fun MeteogramCloudValueAxis(', 1)[1].split(
            'private fun MeteogramValueAxis(', 1)[0]
        self.assertIn('cloudFraction = WeatherCloudFraction', cloud_axis)
        axis = self.source.split('private fun MeteogramAxisRuler(', 1)[1].split(
            'private fun MeteogramTimeAxis(', 1)[0]
        self.assertIn('height * tickTopFraction', axis)
        self.assertIn('height * (1f - tickTopFraction)', axis)

    def test_other_plots_keep_original_geometry(self):
        rain = self.source.split('private fun DrawScope.drawRainBars(', 1)[1].split(
            'private fun DrawScope.drawMetricCurve(', 1)[0]
        self.assertIn('plotTopFraction: Float = 0f', rain)
        self.assertIn('size.height * 0.92f', rain)
        metric = self.source.split('private fun DrawScope.drawMetricCurve(', 1)[1].split(
            'private fun DrawScope.drawDayOverDayBands(', 1)[0]
        self.assertIn('plotTopFraction: Float = 0f', metric)
        self.assertIn('size.height * plotTopFraction', metric)


if __name__ == '__main__':
    unittest.main()
