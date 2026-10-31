"""Regressions: compact color-coded rulers, localized axis abbreviations, chart heights.

These static checks supplement (and cannot replace) Android/Compose device rendering.
"""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SCREEN = ROOT / 'app/src/main/java/com/meteocompare/app/ui/meteogram/MeteogramScreen.kt'
RES = ROOT / 'app/src/main/res'


class MeteogramAxisTintLocaleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SCREEN.read_text(encoding='utf-8')

    def test_compact_axes_recover_metric_tint_and_transparency(self):
        ruler = self.source.split('private fun MeteogramAxisRuler(', 1)[1].split(
            'private fun MeteogramTimeAxis(', 1)[0]
        self.assertIn('.background(color.copy(alpha = 0.105f), rulerShape)', ruler)
        self.assertIn('.border(1.dp, color.copy(alpha = 0.38f), rulerShape)', ruler)
        self.assertIn('fontWeight = FontWeight.Bold, color = color', ruler)
        # Readable foreground for numbers, not solely low-contrast category tint.
        self.assertIn('color = MaterialTheme.colorScheme.onSurface', ruler)
        self.assertIn('private val AxisWidth = 54.dp', self.source)

    def test_both_axis_entry_points_use_the_same_localization(self):
        chosen = self.source.split('private fun MeteogramCombinedSelectedAxis(', 1)[1].split(
            'private fun cardHeight(', 1)[0]
        default = self.source.split('private fun MeteogramValueAxis(', 1)[1].split(
            'private fun MeteogramAxisRuler(', 1)[0]
        self.assertIn('meteogramAxisShortLabel(choice)', chosen)
        self.assertIn('meteogramAxisShortLabel(MeteogramAxisChoice.WIND)', default)
        self.assertNotRegex(chosen, r'"V"|"HR"')

    def test_short_labels_are_consistent_in_five_locales(self):
        expected = {
            'values': ('V', 'N', 'Pl', 'HR'),
            'values-en': ('W', 'C', 'R', 'RH'),
            'values-de': ('Wi', 'Wo', 'R', 'rF'),
            'values-es': ('V', 'N', 'Ll', 'HR'),
            'values-it': ('V', 'N', 'Pi', 'UR'),
        }
        keys = ['wind', 'clouds', 'rain', 'humidity', 'temperature', 'pressure']
        for folder, (wind, cloud, rain, humidity) in expected.items():
            with self.subTest(locale=folder):
                tags = {entry.attrib['name']: (entry.text or '') for entry in
                        ET.parse(RES / folder / 'strings.xml').getroot()}
                for key in keys:
                    self.assertIn(f'meteogram_axis_short_{key}', tags)
                self.assertEqual(wind, tags['meteogram_axis_short_wind'])
                self.assertEqual(cloud, tags['meteogram_axis_short_clouds'])
                self.assertEqual(rain, tags['meteogram_axis_short_rain'])
                self.assertEqual(humidity, tags['meteogram_axis_short_humidity'])
                self.assertEqual('T°', tags['meteogram_axis_short_temperature'])

    def test_heights_are_gently_increased_without_changing_time_axis(self):
        desired = {'Weather': 196, 'Temperature': 184, 'Humidity': 148,
                   'Pressure': 148, 'Wind': 172, 'Combined': 372, 'Time': 48}
        for kind, height in desired.items():
            with self.subTest(kind=kind):
                self.assertRegex(self.source,
                    rf'private val {kind}Height = {height}\.dp')


if __name__ == '__main__':
    unittest.main()
