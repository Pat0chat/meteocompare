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
        self.assertIn('modifier = Modifier.weight(1f).testTag(centralTestTag)', code)
        self.assertIn('testTag = apparent.testTag.removeSuffix("_central") + "_inline_bar"', code)


if __name__ == "__main__":
    unittest.main()
