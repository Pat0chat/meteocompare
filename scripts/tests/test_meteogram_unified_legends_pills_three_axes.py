"""UI source invariants; complements, but cannot replace, Compose device tests."""
from pathlib import Path
import unittest

SOURCE = (Path(__file__).resolve().parents[2]
          / 'app/src/main/java/com/meteocompare/app/ui/meteogram/MeteogramScreen.kt')


class MeteogramUnifiedDesignTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.code = SOURCE.read_text(encoding='utf-8')

    def test_all_legends_have_a_single_renderer(self):
        code = self.code
        segment = code.split('private fun MeteogramCloudRainLegend(', 1)[1].split(
            'private fun MeteogramCombinedLegend(', 1)[0]
        self.assertEqual(1, segment.count('MeteogramLegend(listOf('))
        self.assertEqual(5, segment.count('LegendEntry('))
        key = code.split('private fun MeteogramKey(', 1)[1].split(
            'private fun meteogramAxisColor(', 1)[0]
        self.assertIn('MaterialTheme.typography.labelSmall', key)
        self.assertIn('FontWeight.Medium', key)
        self.assertIn('MeteogramLegendMarker(entry.color, entry.line)', key)
        self.assertNotIn('entry.emphasized', code)
        self.assertEqual(3, sum(1 for tag in ('TAG_METEOGRAM_CLOUD_LEGEND',
                                             'TAG_METEOGRAM_RAIN_LEGEND',
                                             'TAG_METEOGRAM_PRESSURE_LEGEND')
                                if 'tag = ' + tag in segment))

    def test_everyday_has_three_right_side_axes_with_correct_cloud_lanes(self):
        code = self.code
        card = code.split('private fun MeteogramChartCard(', 1)[1].split(
            'private fun MeteogramGraphFrame(', 1)[0]
        self.assertIn('MeteogramValueAxis(kind, height, domain, units,', card)
        self.assertIn('MeteogramKind.PRESSURE, height, pressureDomain, units', card)
        self.assertIn('MeteogramCloudValueAxis(height)', card)
        axis = code.split('private fun MeteogramCloudValueAxis(', 1)[1].split(
            'private fun MeteogramValueAxis(', 1)[0]
        self.assertIn('TAG_METEOGRAM_CLOUD_AXIS', axis)
        self.assertIn('cloudFraction = WeatherCloudFraction', axis)
        self.assertIn('cloudLayers = true', axis)
        self.assertIn('"%"', axis)
        ruler = code.split('private fun MeteogramAxisRuler(', 1)[1].split(
            'private fun MeteogramTimeAxis(', 1)[0]
        self.assertIn('height * (cloudFraction / 3f)', ruler)
        self.assertIn('height * (1f - cloudFraction)', ruler)

    def test_selected_pills_equal_height_and_three_column_layout(self):
        code = self.code
        layout = code.split('private fun MeteogramSelectedMetricContent(', 1)[1].split(
            'private fun MeteogramValuePill(', 1)[0]
        self.assertIn('metrics.chunked(3)', layout)
        self.assertIn('Box(Modifier.weight(1f))', layout)
        self.assertIn('Spacer(Modifier.weight(1f))', layout)
        self.assertIn('TAG_METEOGRAM_SELECTED_GRID', layout)
        self.assertIn('MeteogramValuePill(label, value, color, Modifier.fillMaxWidth())', layout)
        pill = code.split('private fun MeteogramValuePill(', 1)[1]
        self.assertIn('modifier = modifier.height(76.dp)', pill)
        self.assertIn('maxLines = 2', pill)
        self.assertIn('maxLines = 1', pill)
        self.assertEqual(2, code.count('MeteogramSelectedMetricContent(') - 1)


if __name__ == '__main__':
    unittest.main()
