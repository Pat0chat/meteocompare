"""Regression checks for full-bleed time ruler, selected value chips, solar markers.

Static assertions complement (not replace) instrumented Compose rendering checks.
"""
from pathlib import Path
import unittest

SRC = (Path(__file__).resolve().parents[2] / 'app/src/main/java/com/meteocompare/app/ui/meteogram/MeteogramScreen.kt')


class MeteogramTimelineSolarTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.src = SRC.read_text(encoding='utf-8')

    def test_timeline_backing_spans_both_plot_and_axis_headers(self):
        self.assertEqual(1, self.src.count('val timeStrip = MaterialTheme.colorScheme.surfaceContainerHighest'))
        self.assertEqual(1, self.src.count('Row(Modifier.fillMaxWidth().padding(top = 4.dp).drawBehind {'))
        self.assertEqual(1, self.src.count('drawRect(timeStrip.copy(alpha = 0.75f), size = Size(size.width, bottom))'))
        self.assertEqual(1, self.src.count('drawLine(timeOutline.copy(alpha = 0.65f)'))
        self.assertEqual(2, self.src.count('MeteogramGraphFrame {'))
        self.assertNotIn('Row(Modifier.padding(start = 8.dp, end = 5.dp, top = 4.dp))', self.src)

    def test_solar_markers_label_the_actual_city_local_time_and_overlay_data(self):
        ruler = self.src.split('private fun MeteogramTimeAxis(', 1)[1].split('private fun MeteogramPlot(', 1)[0]
        plot = self.src.split('private fun MeteogramPlot(', 1)[1].split('private fun DrawScope.drawChartFrame(', 1)[0]
        markers = self.src.split('private fun DrawScope.drawMarkerLines(', 1)[1].split('private fun DrawScope.drawCurrentTimeLine(', 1)[0]
        self.assertIn('marker.instant.atZone(zone).format(solarClockFormatter)', ruler)
        self.assertIn('WeatherTimeFormat.clock(locale, clockPreference)', ruler)
        self.assertIn('drawRoundRect(accent.copy(alpha = 0.94f)', ruler)
        self.assertIn('lastBadgeRight', ruler)
        self.assertIn('width > size.width', ruler)
        self.assertLess(plot.index('drawing(range.first, range.last, offset, step)'), plot.index('drawMarkerLines(hours, markers, offset, step)'))
        self.assertIn('strokeWidth = 2.7.dp.toPx()', markers)
        self.assertIn('drawCircle(color, radius = 3.6.dp.toPx()', markers)

    def test_per_card_values_instead_of_min_max_range_legend(self):
        card = self.src.split('private fun MeteogramChartCard(', 1)[1].split('private fun MeteogramCombinedCard(', 1)[0]
        self.assertIn('MeteogramCardSelectedValues(kind, selected, zone, timeFormatter, units)', card)
        self.assertNotIn('MeteogramScaleSummary(', card)
        self.assertNotIn('meteogramSelectedCaption(', card)
        self.assertNotIn('private fun MeteogramScaleSummary(', self.src)
        self.assertNotIn('private fun MeteogramScaleChip(', self.src)
        selected = self.src.split('private fun MeteogramCardSelectedValues(', 1)[1].split('private fun MeteogramSelectedValues(', 1)[0]
        self.assertIn('TAG_METEOGRAM_CARD_SELECTED_PREFIX', selected)
        self.assertIn('MeteogramValuePill(label, value, color)', selected)
        self.assertIn('hour.instant.atZone(zone).format(clockFormatter)', selected)
        self.assertIn('color.copy(alpha = 0.09f)', self.src)
        self.assertIn('private val TimeHeight = 49.dp', self.src)


if __name__ == '__main__':
    unittest.main()
