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
        self.assertIn('CloudCoverMeter(label, currentValues[index], colors[index])', collapsed)
        self.assertNotIn('CloudLayerHourlyChart(', collapsed)
        self.assertIn('CloudLayerHourlyChart(hours, names, colors, selectedHour', dialog)

    def test_each_future_cloud_band_has_an_adjacent_label_and_hour_picker(self):
        src = CLOUD.read_text(encoding='utf-8')
        self.assertIn('"today_cloud_band_label_$bandIndex"', src)
        self.assertIn('"today_cloud_hour_$index"', src)
        self.assertIn('hours.map { it.total }', src)
        for altitude in ('high', 'mid', 'low'):
            self.assertIn('hours.map { it.' + altitude + ' }', src)

    def test_sunshine_disclaimer_is_not_in_daily_timeline(self):
        self.assertNotIn('compare_sunshine_disclaimer', TIMELINE.read_text(encoding='utf-8'))
        self.assertIn('compare_sunshine_disclaimer',
                      (ROOT / 'app/src/main/java/com/meteocompare/app/ui/citydetail/DetailSolarCard.kt')
                      .read_text(encoding='utf-8'))

if __name__ == '__main__':
    unittest.main()
