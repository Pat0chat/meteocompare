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
            'private fun MeteogramCloudLayersHint(', 1)[0]
        self.assertEqual(1, section.count('MeteogramPlot('))
        self.assertNotIn('CombinedPlotSection(', section)
        self.assertIn('TAG_METEOGRAM_COMBINED', section)
        for measurement in ('it.temperatureC', 'it.apparentC', 'it.dewC',
                            'it.humidityPercent?.toDouble()', 'it.pressureHpa',
                            'it.windKmh', 'it.gustKmh'):
            self.assertIn(measurement, section)
        self.assertIn('drawRainBars(', section)
        self.assertIn('drawCloudLayerBands(', section)
        self.assertNotIn('drawDailyRangeMarkers(', section)

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

    def test_real_cloud_layers_are_kept_separately(self):
        bars = self.source.split('private fun DrawScope.drawCloudLayerBands(', 1)[1].split(
            'private fun DrawScope.drawRainBars(', 1)[0]
        for field in ('cloudLowPercent', 'cloudMidPercent', 'cloudHighPercent'):
            self.assertIn(field, bars)
        for color in ('CloudLowColor', 'CloudMidColor', 'CloudHighColor'):
            self.assertIn(color, bars)
        self.assertNotIn('cloudPercent', bars)
        self.assertIn('fill.cubicTo(', bars)
        self.assertIn('drawPath(fill,', bars)
        self.assertNotIn('drawRoundRect(', bars)

    def test_rectangular_time_axis_compact_rulers_and_selectable_axis(self):
        time_axis = self.source.split('private fun MeteogramTimeAxis(', 1)[1].split(
            'private fun MeteogramPlot(', 1)[0]
        self.assertNotIn('.clip(RoundedCornerShape(', time_axis)
        self.assertIn('.border(1.dp, MaterialTheme.colorScheme.outlineVariant', time_axis)
        self.assertIn('paint.measureText(label)', time_axis)
        self.assertIn('markerPaint.measureText(symbol)', time_axis)
        combined = self.source.split('private fun MeteogramCombinedCard(', 1)[1].split(
            'private fun MeteogramCloudLayersHint(', 1)[0]
        self.assertLess(combined.index('MeteogramScalePicker('), combined.index('MeteogramPlot('))
        self.assertIn('MeteogramCombinedSelectedAxis(', combined)
        self.assertNotIn('MeteogramCombinedScaleLegend(', combined)
        self.assertIn('private val AxisWidth = 54.dp', self.source)
        selection = self.source.split('private fun MeteogramScalePicker(', 1)[1].split(
            'private fun cardHeight(', 1)[0]
        for kind in ('TEMPERATURE', 'CLOUDS', 'RAIN', 'WIND', 'HUMIDITY', 'PRESSURE'):
            self.assertIn('MeteogramAxisChoice.' + kind, selection)
        self.assertIn('cloudLayers = choice == MeteogramAxisChoice.CLOUDS', selection)
        self.assertIn('contentDescription = choice.name.lowercase()', selection)
        self.assertNotIn('MeteogramScaleTicks(', self.source)

    def test_total_cloud_overlay_and_legend_removed(self):
        cloud_plot = self.source.split('private fun DrawScope.drawCloudLayerBands(', 1)[1].split(
            'private fun DrawScope.drawRainBars(', 1)[0]
        self.assertNotIn('drawPath(contour,', cloud_plot)
        self.assertNotIn('cloudPercent', cloud_plot)
        self.assertNotIn('R.string.meteogram_cloud_total', self.source)

    def test_removed_daily_panel_and_fixed_domains(self):
        preset = self.source.split('internal val MeteogramPresets = ', 1)[1].split(')', 1)[0]
        self.assertNotIn('DAILY', preset)
        self.assertNotIn('private fun MeteogramDailyStrip(', self.source)
        self.assertNotIn('drawDailyRangeMarkers(', self.source)
        for metric in ('temperatureDomain', 'rainDomain', 'windDomain', 'humidityDomain', 'pressureDomain'):
            self.assertIn('val ' + metric + ' = remember(hours)', self.source)



if __name__ == '__main__':
    unittest.main()
