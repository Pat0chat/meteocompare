"""Source-contract audit for MeteoCompare 1.19.3 meteorogram cleanup.

These are static regression guards, NOT an Android/Kotlin compiler or screenshot.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCREEN = ROOT / "app/src/main/java/com/meteocompare/app/ui/meteogram/MeteogramScreen.kt"
NAV = ROOT / "app/src/main/java/com/meteocompare/app/ui/navigation/AppNavHost.kt"
DEST = ROOT / "app/src/main/java/com/meteocompare/app/ui/navigation/Destinations.kt"
VIEWMODEL = ROOT / "app/src/main/java/com/meteocompare/app/ui/graphicview/GraphicForecastViewModel.kt"
DTO = ROOT / "app/src/main/java/com/meteocompare/app/data/remote/dto/ForecastResponseDto.kt"
SPLITTER = ROOT / "app/src/main/java/com/meteocompare/app/data/remote/BatchedForecastSplitter.kt"
MAPPER = ROOT / "app/src/main/java/com/meteocompare/app/data/mapper/ForecastMapper.kt"
SERIES = ROOT / "app/src/main/java/com/meteocompare/app/domain/model/ForecastSeries.kt"


class MeteogramCleanupAudit(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.screen = SCREEN.read_text(encoding="utf-8")

    def test_frise_is_compact_and_graph_height_budget_increases(self):
        values = dict((name, int(value)) for name, value in re.findall(
            r"private val (\w+)Height = (\d+)\.dp", self.screen))
        self.assertEqual(49, values['Time'])
        old = dict(Weather=232, Temperature=216, Humidity=176,
                   Pressure=176, Wind=206, Combined=442)
        for key, before in old.items():
            with self.subTest(panel=key):
                self.assertGreater(values[key], before)
                self.assertLess(values[key] + values['Time'], before + 66)

    def test_solar_labels_fit_within_shorter_strip_and_keep_exact_minutes(self):
        axis = self.screen.split('private fun MeteogramTimeAxis(', 1)[1].split(
            'private fun MeteogramPlot(', 1)[0]
        self.assertIn('WeatherTimeFormat.clock(locale, clockPreference)', axis)
        self.assertIn('marker.instant.atZone(zone).format(solarClockFormatter)', axis)
        self.assertIn('val badgeHeight = 19.dp.toPx()', axis)
        self.assertIn('val top = 15.dp.toPx()', axis)
        self.assertIn('size.height - 3.dp.toPx()', axis)
        self.assertNotIn('59.dp.toPx()', axis)
        self.assertNotIn('top = 22.dp.toPx()', axis)

    def test_plot_computes_its_own_viewport_and_clamps_stale_scroll(self):
        content = self.screen.split('internal fun MeteogramContent(', 1)[1].split(
            'private fun meteogramKindLabel(', 1)[0]
        plot = self.screen.split('private fun MeteogramPlot(', 1)[1].split(
            'private fun DrawScope.drawChartFrame(', 1)[0]
        self.assertIn('LaunchedEffect(hours.size, hourPx, widthPx)', content)
        self.assertIn('scrollPx = scrollPx.coerceIn(', content)
        self.assertIn('meteogramViewport(offset, size.width, step, hours.size)', plot)
        self.assertNotIn('range: IntRange', plot)
        self.assertNotIn('val visible by', content)

    def test_remove_dead_meteogram_cloud_total_but_preserve_real_layers(self):
        hour = self.screen.split('internal data class MeteogramHour(', 1)[1].split(
            'internal data class MeteogramDomain(', 1)[0]
        self.assertNotIn('cloudPercent', hour)
        for layer in ('Low', 'Mid', 'High'):
            self.assertIn(f'cloud{layer}Percent', hour)
        self.assertNotIn('drawCloudBars(', self.screen)
        self.assertNotIn('drawDailyRangeMarkers(', self.screen)
        self.assertNotIn('DailyHeight', self.screen)

    def test_source_to_viewmodel_cloud_layer_chain_remains_intact(self):
        for file in (DTO, SPLITTER, MAPPER, SERIES, VIEWMODEL):
            with self.subTest(file=file.name):
                src = file.read_text(encoding='utf-8')
                for key in ('Low', 'Mid', 'High'):
                    # Source can use the proper field prefix based on layer.
                    self.assertTrue(any(s in src for s in
                        (f'cloudCover{key}', f'cloud{key}Percent')))

    def test_navigation_wiring_and_version_guard(self):
        nav = NAV.read_text(encoding='utf-8')
        destinations = DEST.read_text(encoding='utf-8')
        build = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
        self.assertIn('MeteogramScreen(', nav)
        self.assertIn('Destinations.meteogram(cityId)', nav)
        self.assertIn('fun meteogram(cityId:', destinations)
        self.assertIn('versionCode = 49', build)
        self.assertIn('versionName = "1.19.3"', build)

    def test_shared_timeline_backing_and_frozen_compare_domains(self):
        self.assertEqual(1, self.screen.count('val timeStrip = MaterialTheme.colorScheme.surfaceContainerHighest'))
        self.assertEqual(2, self.screen.count('MeteogramGraphFrame {'))
        content = self.screen.split('internal fun MeteogramContent(', 1)[1].split(
            'private fun meteogramKindLabel(', 1)[0]
        self.assertIn('modelHoursByName.values.flatten()', content)
        self.assertNotIn('compareHours = remember(modelHoursByName, visible', content)

    def test_wind_arrows_have_stable_positions_on_scroll(self):
        arrows = self.screen.split('private fun DrawScope.drawWindDirectionArrows(', 1)[1].split(
            'private fun DrawScope.drawWindArrow(', 1)[0]
        self.assertIn('val start = first + (3 - first % 3) % 3', arrows)
        self.assertIn('for (i in start..last step 3)', arrows)
        self.assertIn('hours.binarySearch { it.instant.compareTo(instant) }', self.screen)


if __name__ == '__main__':
    unittest.main()
