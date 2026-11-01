"""Guards for complete labels and metric/imperial units in the all-in-one legend.

Static source verification is complementary to device-side Compose tests.
"""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SCREEN = ROOT / "app/src/main/java/com/meteocompare/app/ui/meteogram/MeteogramScreen.kt"


class MeteogramCombinedLegendUnitsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        source = SCREEN.read_text(encoding="utf-8")
        cls.legend = source.split('private fun MeteogramCombinedLegend(units: WeatherUnits)', 1)[1].split(
            'private fun MeteogramLegend(', 1)[0]

    def test_temperature_feels_and_dew_have_localized_labels_and_temperature_units(self):
        for name, key in (
            ('TemperatureColor', 'meteogram_temperature'),
            ('ApparentColor', 'apparent_temperature_widget_prefix'),
            ('DewColor', 'metric_dew_point_short'),
        ):
            with self.subTest(metric=name):
                entry = next((part for part in self.legend.split('LegendEntry(')
                              if part.lstrip().startswith(name + ',')), None)
                self.assertIsNotNone(entry, f'{name} missing from combined legend')
                self.assertIn(f'R.string.{key}', entry.split('LegendEntry(', 1)[0])
                self.assertIn('(${units.temperatureUnit})', entry)

    def test_humidity_has_its_label_and_percent_unit(self):
        entry = next((part for part in self.legend.split('LegendEntry(')
                      if part.lstrip().startswith('HumidityColor,')), None)
        self.assertIsNotNone(entry)
        self.assertIn('R.string.meteogram_humidity', entry)
        self.assertIn(' (%)', entry)
        self.assertNotIn('HumidityColor, "%"', entry)

    def test_pressure_has_its_label_and_selects_hpa_or_inhg(self):
        entry = next((part for part in self.legend.split('LegendEntry(')
                      if part.lstrip().startswith('PressureColor,')), None)
        self.assertIsNotNone(entry)
        self.assertIn('R.string.meteogram_pressure', entry)
        self.assertIn('units.imperial', entry)
        self.assertIn('"inHg"', entry)
        self.assertIn('"hPa"', entry)

    def test_all_requested_labels_exist_in_all_five_locales(self):
        for folder in ('values', 'values-en', 'values-de', 'values-es', 'values-it'):
            with self.subTest(locale=folder):
                resources = {
                    item.attrib['name']: (item.text or '').strip()
                    for item in ET.parse(ROOT / f'app/src/main/res/{folder}/strings.xml').getroot()
                    if 'name' in item.attrib
                }
                for key in ('meteogram_temperature', 'apparent_temperature_widget_prefix',
                            'metric_dew_point_short', 'meteogram_humidity', 'meteogram_pressure'):
                    self.assertTrue(resources.get(key), f'{folder} missing {key}')


if __name__ == '__main__':
    unittest.main()
