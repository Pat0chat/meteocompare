"""Static layout contracts, complemented by Compose instrumented tests in TodaySummaryCardTest."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CITY = ROOT / 'app/src/main/java/com/meteocompare/app/ui/citydetail/CityDetailScreen.kt'
CLOUD = ROOT / 'app/src/main/java/com/meteocompare/app/ui/citydetail/TodayCloudLayersCard.kt'
TIMELINE = ROOT / 'app/src/main/java/com/meteocompare/app/ui/citydetail/SimplifiedTimeline.kt'


class TodayCloudSummaryLayoutTest(unittest.TestCase):
    def test_current_cloud_card_is_after_wind_metrics(self):
        src = CITY.read_text(encoding='utf-8')
        self.assertGreater(src.index('TodayCloudLayersCard(cloudLayerHours, solarZone, dateLocale)'),
                           src.index('DetailMetricGrid('))

    def test_current_card_does_not_draw_the_future_timeline(self):
        src = CLOUD.read_text(encoding='utf-8')
        collapsed, dialog = src.split('    if (open) {', 1)
        self.assertIn('val current = hours.first()', collapsed)
        self.assertIn('label, currentValues[index], colors[index],', collapsed)
        self.assertIn('valueTestTag = "today_cloud_current_value_$index"', collapsed)
        self.assertNotIn('CloudLayerHourlyChart(', collapsed)
        self.assertIn('CloudLayerHourlyChart(hours, names, colors, selectedHour', dialog)

    def test_each_future_cloud_band_has_an_adjacent_label_and_hour_picker(self):
        src = CLOUD.read_text(encoding='utf-8')
        self.assertIn('"today_cloud_band_label_$bandIndex"', src)
        self.assertIn('"today_cloud_hour_$index"', src)
        self.assertIn('hours.map { it.total }', src)
        for altitude in ('high', 'mid', 'low'):
            self.assertIn('hours.map { it.' + altitude + ' }', src)

    def test_popup_has_fixed_hour_rail_and_separate_model_cards(self):
        src = CLOUD.read_text(encoding='utf-8')
        dialog = src.split('    if (open) {', 1)[1].split('\n/** All bars', 1)[0]
        self.assertIn('Dialog(', dialog)
        self.assertIn('DialogProperties(usePlatformDefaultWidth = false)', dialog)
        self.assertIn('CloudHourSelector(hours, selectedHour, zone, formatter)', dialog)
        self.assertLess(dialog.index('CloudHourSelector('), dialog.index('.verticalScroll(detailScrollState)'))
        self.assertIn('CloudSelectedHourSummary(sample, names, colors', dialog)
        self.assertIn('CloudModelDetailCard(model, names, colors)', dialog)
        self.assertIn('today_cloud_models_section', dialog)
        self.assertIn('today_cloud_hour_selector', src)
        self.assertIn('today_cloud_model_${model.model.name}', src)
        self.assertIn('LaunchedEffect(selectedHour)', dialog)

    def test_popup_keeps_missing_cloud_layers_unknown_and_distinguishes_dst(self):
        src = CLOUD.read_text(encoding='utf-8')
        self.assertIn('popupHourLabel(hours, index, zone, formatter)', src)
        self.assertIn('instant.atZone(zone).offset.id', src)
        self.assertIn('percent?.let { "$it %" } ?: "—"', src)
        self.assertIn('val modelRows = sample.readings', src)
        self.assertIn('values.joinToString', src)
        self.assertIn('MaterialTheme.colorScheme', src)

    def test_mid_cloud_has_dedicated_palette_not_dynamic_accent(self):
        src = CLOUD.read_text(encoding='utf-8')
        palette = src.split('val colors = listOf(', 1)[1].split('\n    )', 1)[0]
        self.assertNotIn('MaterialTheme.colorScheme.primary', palette)
        self.assertNotIn('MaterialTheme.colorScheme.secondary', palette)
        self.assertNotIn('MaterialTheme.colorScheme.tertiary', palette)
        self.assertIn('Color(0xFF71C9D4)', palette)
        self.assertIn('Color(0xFF187487)', palette)
        self.assertIn('surface.luminance()', src)

    def test_current_and_selected_values_have_distinct_accessibility_tags(self):
        cloud = CLOUD.read_text(encoding='utf-8')
        android = (ROOT / 'app/src/androidTest/java/com/meteocompare/app/ui/citydetail/TodaySummaryCardTest.kt').read_text(encoding='utf-8')
        self.assertIn('valueTestTag = "today_cloud_current_value_$index"', cloud)
        self.assertIn('valueTestTag = "today_cloud_selected_value_$index"', cloud)
        for value in ('91 %', '98 %'):
            self.assertIn('assertTextEquals("' + value + '")', android)
            self.assertNotIn('onNodeWithText("' + value + '", useUnmergedTree = true).assertExists()', android)
        self.assertIn('assertTextEquals("—")', android)

    def test_sunshine_disclaimer_is_not_in_daily_timeline(self):
        self.assertNotIn('compare_sunshine_disclaimer', TIMELINE.read_text(encoding='utf-8'))
        self.assertIn('compare_sunshine_disclaimer',
                      (ROOT / 'app/src/main/java/com/meteocompare/app/ui/citydetail/DetailSolarCard.kt')
                      .read_text(encoding='utf-8'))

if __name__ == '__main__':
    unittest.main()
