#!/usr/bin/env python3
"""Static widget contract checks. Run: python scripts/test_widget_manifest.py"""
from pathlib import Path
from xml.etree import ElementTree as ET
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'app/src/main/AndroidManifest.xml'
WIDGET_DIR = ROOT / 'app/src/main/java/com/meteocompare/app/widget'
RES_DIR = ROOT / 'app/src/main/res'
ANDROID = '{http://schemas.android.com/apk/res/android}'
EXPECTED = {
    'MeteoWeatherWidgetReceiver': 'meteocompare_widget_info_weather',
    'MeteoInsightWidgetReceiver': 'meteocompare_widget_info_insight',
}

class WidgetContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = ET.parse(MANIFEST).getroot().find('application')
        cls.receivers = {
            tag.get(ANDROID + 'name').split('.')[-1]: tag
            for tag in cls.app.findall('receiver')
            if tag.get(ANDROID + 'name', '').startswith('.widget.Meteo')
        }

    def test_exactly_two_resizable_widget_entries(self):
        self.assertEqual(set(EXPECTED), set(self.receivers))
        for receiver_name, resource in EXPECTED.items():
            info = self.receivers[receiver_name].find('meta-data')
            self.assertEqual(info.get(ANDROID + 'resource'), '@xml/' + resource)
            provider = ET.parse(RES_DIR / 'xml' / (resource + '.xml')).getroot()
            self.assertEqual(provider.get(ANDROID + 'resizeMode'), 'horizontal|vertical')
            self.assertEqual(provider.get(ANDROID + 'widgetFeatures'), 'reconfigurable')
            self.assertEqual(provider.get(ANDROID + 'configure'),
                             'com.meteocompare.app.widget.MeteoWidgetConfigActivity')


    def test_weather_widget_resources_are_size_neutral(self):
        provider = self.receivers['MeteoWeatherWidgetReceiver']
        self.assertEqual(provider.get(ANDROID + 'label'), '@string/widget_label_weather')
        weather_info = ET.parse(RES_DIR / 'xml' / 'meteocompare_widget_info_weather.xml').getroot()
        self.assertEqual(weather_info.get(ANDROID + 'previewLayout'), '@layout/widget_preview_weather')
        previews = {f.name for f in (RES_DIR / 'layout').glob('widget_preview*.xml')}
        self.assertEqual(previews, {'widget_preview_weather.xml', 'widget_preview_insight.xml'})
        # Previous size-specific identities must not accidentally return.
        for directory in (RES_DIR / 'xml', RES_DIR / 'layout'):
            self.assertFalse(any(re.search(r'\d+[x×]\d+', f.stem) for f in directory.iterdir()))

    def test_registry_matches_manifest(self):
        source = (WIDGET_DIR / 'MeteoWidgetReceiver.kt').read_text()
        entries = re.search(r'val All: List<.*?> = listOf\((.*?)\n    \)', source, re.S)
        self.assertIsNotNone(entries)
        self.assertEqual(set(EXPECTED), set(re.findall(r'(Meteo\w+Receiver(?:\d+x\d+)?)::class\.java',entries.group(1))))

    def test_widget_configuration_ime(self):
        activity = self.app.find("activity[@" + ANDROID + "name='.widget.MeteoWidgetConfigActivity']")
        self.assertIsNotNone(activity)
        self.assertEqual(activity.get(ANDROID + 'windowSoftInputMode'), 'adjustResize')
        form = (WIDGET_DIR / 'MeteoWidgetConfigActivity.kt').read_text()
        dialog = (WIDGET_DIR / 'WidgetColorWheelPicker.kt').read_text()
        helper = (WIDGET_DIR / 'WidgetKeyboardFocus.kt').read_text()
        self.assertRegex(form, r'(?s)showWidgetKeyboardOnFocus\(\).*?TAG_WIDGET_OPACITY_INPUT')
        self.assertRegex(dialog, r'(?s)showWidgetKeyboardOnFocus\(\).*?TAG_WIDGET_COLOR_HEX')
        self.assertIn('.imePadding()', form)
        self.assertIn('LocalSoftwareKeyboardController.current', helper)
        self.assertIn('onFocusChanged', helper)

    def test_picker_labels_localized(self):
        for locale in ('values','values-en','values-de','values-es','values-it'):
            strings = ET.parse(RES_DIR / locale / 'strings_widget.xml').getroot()
            values = {el.get('name'): (el.text or '') for el in strings.findall('string')}
            self.assertIn('widget_label_weather', values)
            self.assertIn('widget_label_insight', values)
            self.assertNotIn('×', values['widget_label_weather'])
            self.assertNotIn('×', values['widget_label_insight'])

if __name__ == '__main__':
    unittest.main(verbosity=2)
