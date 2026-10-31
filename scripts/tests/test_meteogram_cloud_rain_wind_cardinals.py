"""Regression checks for the clouds/rain versus wind separation (not a Compose build)."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/java/com/meteocompare/app/ui/meteogram/MeteogramScreen.kt"
RES = ROOT / "app/src/main/res"

class MeteorogramSeparationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.code = SRC.read_text(encoding="utf-8")

    def test_same_day_and_night_colors_in_all_graphs(self):
        frame = self.code.split('private fun DrawScope.drawChartFrame(', 1)[1].split(
            'private fun DrawScope.drawTimeGrid(', 1)[0]
        self.assertNotIn('everyday:', frame)
        self.assertIn('meteogramDayNightAlpha(hours[i].daylight)', frame)
        self.assertIn('drawMeteogramBackground(hours, first, last, offset, step)', frame)
        self.assertEqual(2, self.code.count('drawChartFrame(hours, zone, first, last, shift, hourStep)'))

    def test_cloud_rain_card_has_only_cloud_rain_and_pressure(self):
        card = self.code.split('private fun MeteogramChartCard(', 1)[1].split(
            'private fun MeteogramGraphFrame(', 1)[0]
        clouds = card.split('MeteogramKind.EVERYDAY -> {', 1)[1].split('MeteogramKind.TEMPERATURE ->', 1)[0]
        self.assertIn('drawCloudLayerBands(', clouds)
        self.assertIn('drawRainBars(', clouds)
        self.assertIn('pressureDomain, PressureColor', clouds)
        self.assertNotIn('windDomain, WindColor', clouds)
        self.assertNotIn('drawWindDirectionArrows(', clouds)
        self.assertIn('MeteogramCloudRainLegend(units)', card)
        self.assertIn('MeteogramValueAxis(kind, height, domain, units', card)
        selected = self.code.split('private fun MeteogramCardSelectedValues(',1)[1].split(
            'MeteogramKind.TEMPERATURE ->',1)[0]
        self.assertNotIn('Triple(WindColor', selected)

    def test_cloud_rain_legend_exposes_all_measurements(self):
        legend = self.code.split('private fun MeteogramCloudRainLegend(', 1)[1].split(
            'private fun MeteogramCombinedLegend(', 1)[0]
        for name in ('meteogram_cloud_picker', 'meteogram_cloud_low',
                     'meteogram_cloud_mid', 'meteogram_cloud_high',
                     'meteogram_rain', 'meteogram_pressure'):
            self.assertIn('R.string.' + name, legend)
        self.assertIn('emphasized = true', legend)
        self.assertNotIn('meteogram_wind', legend)

    def test_wind_cardinals_present_and_localized(self):
        wind = self.code.split('private fun DrawScope.drawWindDirectionArrows(', 1)[1].split(
            'private fun DrawScope.drawWindArrow(', 1)[0]
        self.assertIn('meteogramWindCardinal(direction, cardinalLanguage)', wind)
        self.assertIn('drawContext.canvas.nativeCanvas.drawText(label', wind)
        self.assertIn('drawWindArrow(x, arrowY, arrowSize, direction.toFloat())', wind)
        for locale in ('values', 'values-en', 'values-de', 'values-es', 'values-it'):
            resources = {item.attrib.get('name'): item.text for item in
                         ET.parse(RES / locale / 'strings.xml').getroot()}
            self.assertIn('meteogram_wind_origin', resources)
            self.assertNotIn('Wind', resources['meteogram_weather'] if locale=='values' else '')
        self.assertIn('listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")', self.code)
        self.assertIn('listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")', self.code)

if __name__ == '__main__':
    unittest.main()
