"""Regression checks for Glance sizing, bounded bitmaps and host layouts."""
from pathlib import Path
from xml.etree import ElementTree as ET
import unittest

ROOT = Path(__file__).resolve().parents[2]
WIDGET = ROOT / 'app/src/main/java/com/meteocompare/app/widget'
RES = ROOT / 'app/src/main/res/xml'
ANDROID = '{http://schemas.android.com/apk/res/android}'

class ResponsiveLauncherWidgetContractTest(unittest.TestCase):
    def test_bitmap_weather_widget_uses_exact_host_size_and_insight_stays_bounded(self):
        weather = (WIDGET / 'MeteoWidget.kt').read_text(encoding='utf-8')
        self.assertIn('override val sizeMode = SizeMode.Exact', weather)
        self.assertNotIn('SizeMode.Responsive(', weather)
        insight = (WIDGET / 'MeteoValueWidgets.kt').read_text(encoding='utf-8')
        self.assertIn('SizeMode.Responsive(', insight)
        self.assertIn('responsiveGlanceSizes(insightWidgetResponsivePresets)', insight)
        catalog = (WIDGET / 'WidgetResponsiveSizes.kt').read_text(encoding='utf-8')
        self.assertNotIn('internal val weatherWidgetResponsivePresets =', catalog)
        self.assertIn('internal val insightWidgetResponsivePresets =', catalog)

    def test_two_row_widgets_do_not_inherit_short_header_or_half_empty_body(self):
        widget = (WIDGET / 'MeteoWidget.kt').read_text(encoding='utf-8')
        self.assertIn('val size = LocalSize.current', widget)
        self.assertIn('size.width.value', widget)
        self.assertIn('size.height.value', widget)
        widget = (WIDGET / 'MeteoWidget.kt').read_text(encoding='utf-8')
        large = widget.split('private fun ExtraLargeLayout(', 1)[1].split('private fun TwelveHourForecastStrip(', 1)[0]
        self.assertIn('COMPACT_FORECAST_HEADER_HEIGHT_DP', large)
        narrow = widget.split('private fun CompactTallLayout(', 1)[1].split('private fun ColumnScope.CompactConfidenceSummary(', 1)[0]
        self.assertIn('if (smallHeader)', narrow)
        self.assertIn('if (minMax.isNotEmpty())', narrow)
        self.assertIn('data.confidencePct?.let', narrow)
        self.assertIn('WidgetMinMaxWithApparent(', large.split('if (smallHeader)', 1)[1].split('} else {', 1)[0])
        self.assertIn('ConfidencePill(percent = it, compact = true)', large)
        self.assertIn('showContextLine', large)
        self.assertIn('FORECAST_CONTEXT_LINE_HEIGHT_DP', large)
        self.assertIn('text = extrasLine', large)

    def test_resizing_minimums_allow_small_grid_cells(self):
        for name in ('weather', 'insight'):
            doc = ET.parse(RES / ('meteocompare_widget_info_' + name + '.xml')).getroot()
            self.assertEqual(doc.get(ANDROID + 'resizeMode'), 'horizontal|vertical')
            self.assertTrue(doc.get(ANDROID + 'minResizeWidth', '').endswith('dp'))
            self.assertTrue(doc.get(ANDROID + 'minResizeHeight', '').endswith('dp'))

    def test_forecast_loading_and_bitmap_payload_are_shared_and_bounded(self):
        for filename in ('MeteoWidget.kt', 'MeteoValueWidgets.kt'):
            source = (WIDGET / filename).read_text(encoding='utf-8')
            self.assertIn('sharedWidgetDataLoad.getOrLoad(', source)
        source = (WIDGET / 'MeteoWidget.kt').read_text(encoding='utf-8')
        self.assertIn('widgetResponsiveForecastBitmapDensity(', source)
        self.assertIn('widgetResponsiveForecastVariantPixelBudget()', source)
        budget = (WIDGET / 'WidgetResponsiveBitmapBudget.kt').read_text(encoding='utf-8')
        self.assertIn('MAX_RESPONSIVE_FORECAST_BITMAP_PIXELS = 90_000', budget)

    def test_bitmap_modes_downgrade_gracefully_and_composition_errors_are_logged(self):
        widget = (WIDGET / 'MeteoWidget.kt').read_text(encoding='utf-8')
        policy = (WIDGET / 'WidgetChartLabelLayout.kt').read_text(encoding='utf-8')
        self.assertIn('widgetTwelveHourPresentation(', widget)
        self.assertIn('WidgetTwelveHourPresentation.NARROW_SUMMARY', widget)
        self.assertIn('WidgetTwelveHourPresentation.COMPACT_SUMMARY', widget)
        self.assertIn('widgetHeatmapLabelIndices(bitmapWidthPx: Int)', policy)
        for name in ('MeteoWidget.kt', 'MeteoValueWidgets.kt'):
            source = (WIDGET / name).read_text(encoding='utf-8')
            self.assertIn('override fun onCompositionError(', source)
            self.assertIn('super.onCompositionError(context, glanceId, appWidgetId, throwable)', source)

    def test_forecast_dividers_are_not_full_height(self):
        widget = (WIDGET / 'MeteoWidget.kt').read_text(encoding='utf-8')
        panel = widget.split('private fun ForecastTimelinePanel(', 1)[1].split('private fun ', 1)[0]
        self.assertIn('availableCardHeightDp * 0.62f', panel)
        self.assertIn('coerceIn(14f, 64f)', panel)

if __name__ == '__main__':
    unittest.main()
