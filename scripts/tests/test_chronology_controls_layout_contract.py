"""Fast source-level guards for chronology layout, usable without Android SDK.

Compose instrumentation in DailyRangeLayoutComposeTest and CityListContentTest
checks behavior and geometry on device; these checks prevent obvious regressions in
CI jobs that only execute Python QA.
"""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
CHRONO = (ROOT / 'app/src/main/java/com/meteocompare/app/ui/citydetail/ChronoTimeline.kt').read_text(encoding='utf-8')
COLUMNS = (ROOT / 'app/src/main/java/com/meteocompare/app/ui/citydetail/SimplifiedTimeline.kt').read_text(encoding='utf-8')
HOME = (ROOT / 'app/src/main/java/com/meteocompare/app/ui/citylist/CityListScreen.kt').read_text(encoding='utf-8')


def section(text, start, end):
    return text.split(start, 1)[1].split(end, 1)[0]


class ChronologyUiContract(unittest.TestCase):
    def test_ranges_toggle_is_unframed_and_accessible(self):
        body = section(COLUMNS, 'private fun TimelineDailyRangesToggle(',
                       'private fun TimelineRangeSelector(')
        self.assertIn('role = Role.Switch', body)
        self.assertIn('onCheckedChange = null', body)
        self.assertIn('Modifier.scale(0.72f)', body)
        self.assertNotIn('Surface(', body)
        self.assertNotIn('.background(', body)

    def test_ranges_control_stays_below_both_layouts(self):
        body = section(COLUMNS, 'when (layout) {',
                       'if (points.any { it.isDivergent })')
        self.assertIn('TimelineLayout.COLUMNS ->', body)
        self.assertIn('TimelineLayout.CHRONO ->', body)
        self.assertIn('TimelineDailyRangesToggle(', body)
        self.assertLess(body.index('TimelineLayout.CHRONO ->'), body.index('TimelineDailyRangesToggle('))

    def test_daily_columns_use_shared_icon_rail_and_secondary_ranges(self):
        body = section(COLUMNS, 'private fun TimelineMetric(',
                       'private fun TimelineSupportingText(')
        self.assertIn('Box(Modifier.width(15.dp)', body)
        self.assertIn('modifier = Modifier.weight(1f)', body)
        self.assertIn('rangeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)', body)
        self.assertIn('horizontalAlignment = Alignment.Start', body)

    def test_timeline_title_icons_are_kept_but_cell_icons_removed(self):
        title = section(CHRONO, 'private fun ChronoLabelsColumn(', 'private fun ChronoLabelGrid(')
        self.assertIn('iconTag = "timeline_title_rain_icon"', title)
        self.assertIn('iconTag = "timeline_title_wind_icon"', title)
        rain = section(CHRONO, 'private fun ChronoRainLane(', 'private fun ChronoCloudLane(')
        wind = section(CHRONO, 'private fun ChronoWindLane(', 'private fun ChronoAgreementLane(')
        self.assertNotIn('Icon(', rain)
        self.assertNotIn('Icon(', wind)
        self.assertIn('timeline_daily_rain_probability_value', rain)
        self.assertIn('timeline_daily_wind_value', wind)

    def test_home_compare_is_only_in_fab_row_next_to_reorder(self):
        top = section(HOME, 'actions = {', 'floatingActionButton = {')
        bottom = section(HOME, 'floatingActionButton = {', ') { padding ->')
        self.assertNotIn('home_compare_localities', top)
        self.assertEqual(bottom.count('home_compare_localities'), 1)
        self.assertLess(bottom.index('home_compare_localities'), bottom.index('home_reorder_action'))
        self.assertIn('if (uiState.items.size >= 2)', bottom)


if __name__ == '__main__':
    unittest.main()
