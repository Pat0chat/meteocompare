"""Widget scaling safeguards for issue #39 (Nova Launcher)."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WIDGET = ROOT / 'app/src/main/java/com/meteocompare/app/widget'
RES = ROOT / 'app/src/main/res'


class NovaWidgetScalingContracts(unittest.TestCase):
    def test_timeline_has_no_vertical_title_or_reserved_header(self):
        source = (WIDGET / 'MeteoWidget.kt').read_text(encoding='utf-8')
        panel = source.split('private fun ForecastTimelinePanel(', 1)[1].split('private fun ColumnScope.CombinedConfidenceBands(', 1)[0]
        self.assertNotIn('showHeader', panel)
        self.assertNotIn('widget_forecast_hours', panel)
        self.assertNotIn('widget_forecast_days', panel)
        self.assertNotIn('sourceModel', panel)
        self.assertIn('availablePanelWidthDp', panel)
        self.assertIn('availableCardWidthDp = estimatedCardWidthDp', panel)
        self.assertIn('GlanceModifier.fillMaxWidth().height(availableCardHeightDp.dp)', panel)
        self.assertEqual(2, source.count('hasConfidencePanel = data.confidenceStrips.isNotEmpty()'))

    def test_glyph_sizing_respects_both_width_and_height(self):
        source = (WIDGET / 'WidgetForecastCardLayout.kt').read_text(encoding='utf-8')
        self.assertIn('internal fun forecastTimelineGlyphSizeDp(', source)
        self.assertIn('availableCardWidthDp', source)
        self.assertIn('availableCardHeightDp', source)
        self.assertIn('widthLimit', source)
        self.assertIn('inlineForecastCardWidthDp(', source)
        widget = (WIDGET / 'MeteoWidget.kt').read_text(encoding='utf-8')
        self.assertIn('heightProfile = forecastCardHeightProfile(', widget)
        self.assertIn('val glyphSize = forecastTimelineGlyphSizeDp(', widget)

    def test_unused_widget_timeline_titles_removed_in_all_locales(self):
        for resource in RES.glob('values*/strings_widget.xml'):
            content = resource.read_text(encoding='utf-8')
            with self.subTest(resource=resource):
                self.assertNotIn('name="widget_forecast_hours"', content)
                self.assertNotIn('name="widget_forecast_days"', content)
                self.assertNotIn('name="widget_forecast_generic"', content)
                self.assertIn('name="widget_forecast_confidence"', content)

if __name__ == '__main__':
    unittest.main()
