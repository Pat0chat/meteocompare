#!/usr/bin/env python3
"""Static UV integration sanity checks; run Kotlin tests on Android CI."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/meteocompare/app"

def java(path):
    return (MAIN / path).read_text(encoding="utf-8")

class UvContractTests(unittest.TestCase):
    def test_one_endpoint_is_not_added_to_multimodel_batch(self):
        api = java("data/remote/OpenMeteoApi.kt")
        self.assertIn('suspend fun getUvForecast(', api)
        self.assertIn('"uv_index_max"', api)
        self.assertNotIn('uv_index', api.split('const val DEFAULT_HOURLY_VARS =', 1)[1])
    def test_uv_repo_cache_and_deduplication(self):
        repo = java("data/repository/UvIndexRepositoryImpl.kt")
        for needle in ('inFlight', 'Mutex()', 'UvCacheRecord', 'isUvCacheFresh',
                       'UV_FAILURE_BACKOFF_MS', 'getSharedPreferences(', 'isOnline()'):
            self.assertIn(needle, repo)
    def test_all_requested_surfaces(self):
        for file, symbol in [
            ('ui/citylist/CityListScreen.kt', 'UvIndexGauge('),
            ('ui/citydetail/CityDetailScreen.kt', 'UvIndexGauge('),
            ('ui/citydetail/SimplifiedTimeline.kt', 'UvIndexCompact('),
            ('ui/citydetail/ChronoTimeline.kt', 'UvIndexCompact('),
            ('ui/graphicview/GraphicForecastScreen.kt', 'UvIndexCompact('),
        ]:
            self.assertIn(symbol, java(file), file)
    def test_chart_uv_is_separate_from_temperature_plot(self):
        chart = java("ui/graphicview/GraphicForecastScreen.kt")
        self.assertIn('private val WeatherConditionLaneHeight =', chart)
        self.assertIn('TAG_GRAPHIC_UV_LANE', chart)
        self.assertLess(chart.index('GraphicConditionLane(\n                        points = points'),
                        chart.index('TemperaturePlot(\n                        points = points'))
        plot = chart.split('private fun TemperaturePlot(', 1)[1].split('private fun RainPlot(', 1)[0]
        self.assertNotIn('UvIndexCompact(', plot)
        lane = chart.split('private fun GraphicConditionLane(', 1)[1].split('private fun TemperaturePlot(', 1)[0]
        self.assertIn('visibleRange.forEach', lane)
        self.assertIn('UvIndexCompact(', lane)
        self.assertIn('Spacer(Modifier.height(WeatherConditionLaneHeight))', chart)

    def test_five_locales(self):
        keys = {'uv_index_title','uv_risk_low','uv_risk_moderate','uv_risk_high',
                'uv_risk_very_high','uv_risk_extreme','uv_index_value','uv_accessibility','uv_compact'}
        for language in ('values','values-en','values-de','values-es','values-it'):
            p = ROOT / 'app/src/main/res' / language / 'strings.xml'
            self.assertTrue(keys.issubset({n.get('name') for n in ET.parse(p).getroot()}), language)

if __name__ == '__main__': unittest.main(verbosity=2)
