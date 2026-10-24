"""Source-level regressions for the compact Today Summary dashboard.

These checks complement (but do not replace) Compose instrumented tests.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
DETAIL = ROOT / "app/src/main/java/com/meteocompare/app/ui/citydetail/CityDetailScreen.kt"


class TodaySummaryCompactLabelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = DETAIL.read_text(encoding="utf-8")

    def test_convergence_uses_app_palette_for_temperature_and_wind(self):
        code = self.source.split("private fun CompactSummaryMetricRow(", 1)[1].split(
            "private fun CompactApparentOnlyRow(", 1)[0]
        self.assertEqual(code.count("SummaryConvergenceValue("), 3)  # temp, wind, helper definition
        self.assertIn("confidenceColor(percent)", code)
        self.assertIn('modifier = Modifier.testTag(testTag)', code)

    def test_compact_feels_like_labels_have_no_legend_dot(self):
        code = self.source.split("private fun CompactSummaryMetricRow(", 1)[1].split(
            "private fun CompactDispersionRail(", 1)[0]
        self.assertIn('modifier = Modifier.testTag(apparent.testTag + "_name")', code)
        self.assertIn('modifier = Modifier.testTag(testTag + "_name")', code)
        self.assertNotIn('Modifier.size(6.dp).background(apparent.accent, CircleShape)', code)

    def test_temperature_center_label_is_inline_and_rails_remain_separate(self):
        code = self.source.split("private fun CompactSummaryMetricRow(", 1)[1].split(
            "private fun CompactApparentOnlyRow(", 1)[0]
        self.assertIn('modifier = Modifier.testTag(centralTestTag + "_label")', code)
        self.assertIn('modifier = Modifier.testTag(centralTestTag)', code)
        self.assertNotIn('modifier = Modifier.weight(1f).testTag(centralTestTag)', code)
        self.assertIn('compactSummaryLabelWidth(maxWidth, temperature = isTemperature)', code)
        self.assertIn('horizontalArrangement = Arrangement.spacedBy(20.dp)', code)
        self.assertIn('convergence = score.convergencePercent', code)
        self.assertIn('testTag = apparent.testTag.removeSuffix("_central") + "_inline_bar"', code)

    def test_wider_rails_with_localized_label_fallback(self):
        column = self.source.split("private fun compactSummaryLabelWidth(", 1)[1].split("/**", 1)[0]
        self.assertIn('if (temperature) 0.36f else 0.32f', column)
        self.assertIn('if (temperature) 128.dp else 116.dp', column)
        self.assertIn('availableWidth - 104.dp', column)
        compact = self.source.split("private fun CompactSummaryMetricRow(", 1)[1].split(
            "private fun SummaryConvergenceValue(", 1)[0]
        self.assertIn('horizontalArrangement = Arrangement.spacedBy(6.dp)', compact)
        self.assertIn('FlowRow(', compact)  # big fonts can wrap instead of colliding
        felt = self.source.split("private fun CompactApparentOnlyRow(", 1)[1].split(
            "private fun CompactDispersionRail(", 1)[0]
        self.assertIn('compactSummaryLabelWidth(maxWidth, temperature = true)', felt)
        rail = self.source.split('private fun CompactDispersionRail(', 1)[1].split(
            'private fun DispersionMetricRow(', 1)[0]
        self.assertIn('COMPACT_RAIL_START_FRACTION', rail)
        self.assertIn('COMPACT_RAIL_END_FRACTION', rail)
        self.assertIn('private const val COMPACT_RAIL_START_FRACTION = 0.05f', self.source)
        self.assertIn('private const val COMPACT_RAIL_END_FRACTION = 0.95f', self.source)

    def test_uploaded_temperature_order_label_convergence_value(self):
        """Match the current layout without silently changing the user's Kotlin file."""
        body = self.source.split('private fun CompactSummaryMetricRow(', 1)[1].split(
            'private fun SummaryConvergenceValue(', 1
        )[0]
        temperature = body.split('if (isTemperature) {', 1)[1].split('} else {', 1)[0]
        label = temperature.index('modifier = Modifier.testTag(centralTestTag + "_label")')
        agreement = temperature.index('SummaryConvergenceValue(')
        central = temperature.index('modifier = Modifier.testTag(centralTestTag)')
        self.assertLess(label, agreement)
        self.assertLess(agreement, central)
        self.assertIn('Arrangement.spacedBy(12.dp)', temperature)
        self.assertIn('Arrangement.spacedBy(20.dp)', temperature)

    def test_wind_convergence_does_not_expand_across_left_column(self):
        wind = self.source.split('// A fixed gap keeps Wind/Gusts', 1)[1].split('text = center,', 1)[0]
        self.assertIn('Arrangement.spacedBy(12.dp)', wind)
        self.assertNotIn('Modifier.weight(1f)', wind)

    def test_dialog_receives_air_and_wind_convergence(self):
        code = self.source.split('private fun CompactDispersionRail(', 1)[1].split('private fun DispersionMetricRow(', 1)[0]
        self.assertIn('convergence = convergence', code)
        self.assertIn('DispersionDetailsDialog(', code)

    def test_obsolete_inline_rails_are_gone(self):
        self.assertNotIn('private fun DispersionCentralValueLabel(', self.source)
        self.assertNotIn('private fun InlineApparentValueLabel(', self.source)
        self.assertNotIn('dotMarker:', self.source)


if __name__ == "__main__":
    unittest.main()
