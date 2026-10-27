"""Static release guardrails; these do not replace Android/Compose execution."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'app/src/main/java/com/meteocompare/app'


class Release1193Guards(unittest.TestCase):
    def test_release_is_bumped_consistently(self):
        for locale in ('fr-FR', 'en-US', 'de-DE', 'es-ES', 'it-IT'):
            note = ROOT / f'fastlane/metadata/android/{locale}/changelogs/51.txt'
            self.assertTrue(note.exists(), f'Missing changelog: {locale}')
            self.assertTrue(note.read_text(encoding='utf-8').strip())

    def test_humidity_icon_is_percent_gauge_and_not_rain_drop(self):
        icons = (APP / 'ui/components/MoistureMetricIcons.kt').read_text(encoding="utf-8")
        humidity = icons.split('val HumidityMetricIcon:', 1)[1].split('val DewPointMetricIcon:', 1)[0]
        self.assertIn('name = "HumidityGauge"', humidity)
        self.assertIn('Percent sign', humidity)
        self.assertIn('lineTo(15.75f, 8.1f)', humidity)
        self.assertNotIn('cubicTo(', icons)
        self.assertIn('curveTo(', icons)

    def test_precipitation_axis_preserves_icon_and_units(self):
        source = (APP / 'ui/graphicview/GraphicForecastScreen.kt').read_text(encoding="utf-8")
        self.assertIn('private val RainAxisWidth = 50.dp', source)
        self.assertIn('compactHeader = true', source)
        self.assertIn('TAG_GRAPHIC_RAIN_AXIS_HEADER', source)
        self.assertIn('TAG_GRAPHIC_RAIN_AXIS_UNIT', source)
        self.assertIn('Modifier.width(GraphicAxisWidth - RainAxisWidth)', source)
        self.assertIn('text = units.label(unit)', source)

    def test_moisture_does_not_trigger_new_dedicated_forecast_request(self):
        api = (APP / 'data/remote/OpenMeteoApi.kt').read_text(encoding="utf-8")
        repo = (APP / 'data/repository/ForecastRepositoryImpl.kt').read_text(encoding="utf-8")
        self.assertIn('relative_humidity_2m,dew_point_2m', api)
        self.assertNotIn('getHumidityForecast', api)
        self.assertIn('coalescedFetchAndCache(', repo)
        self.assertIn('maxCacheAgeMs', repo)


if __name__ == '__main__':
    unittest.main()
