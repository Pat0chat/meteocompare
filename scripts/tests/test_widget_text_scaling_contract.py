import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
WIDGET = ROOT / 'app/src/main/java/com/meteocompare/app/widget'


class WidgetTextScalingContractTest(unittest.TestCase):
    def test_all_glance_text_size_paths_use_central_scale(self):
        for name in ('MeteoWidget.kt', 'MeteoValueWidgets.kt', 'WidgetCurrentLocationAction.kt'):
            source = (WIDGET / name).read_text(encoding='utf-8')
            self.assertEqual(source.count('fontSize ='), source.count('.widgetScaled()'), name)

    def test_all_bitmap_forecast_modes_receive_text_scale(self):
        widget = (WIDGET / 'MeteoWidget.kt').read_text(encoding='utf-8')
        self.assertEqual(widget.count('textScale = bitmapTextScale'), 3)
        self.assertIn('bitmapTextScale', widget)
        for name in ('WidgetMiniForecastRenderer.kt',
                     'WidgetHeatmapForecastRenderer.kt',
                     'WidgetHeatmapTrendForecastRenderer.kt'):
            content = (WIDGET / name).read_text(encoding='utf-8')
            self.assertIn('textScale: Float = 1f', content, name)
            self.assertIn('textScale.coerceIn(0.85f, 1.4f)', content, name)

    def test_preferences_and_both_widgets_use_same_instance_setting(self):
        for name in ('MeteoWidget.kt', 'MeteoValueWidgets.kt'):
            content = (WIDGET / name).read_text(encoding='utf-8')
            self.assertIn('LocalWidgetTextScale provides widgetTextScale(', content)
            self.assertIn('WidgetPreferences.AutoTextSizeKey', content)
            self.assertIn('WidgetPreferences.TextSizePctKey', content)
        config = (WIDGET / 'MeteoWidgetConfigActivity.kt').read_text(encoding='utf-8')
        self.assertIn('TAG_WIDGET_TEXT_SIZE', config)
        self.assertIn('TAG_WIDGET_TEXT_SIZE_AUTO', config)

if __name__ == '__main__':
    unittest.main()
