"""Guard against measuring off-viewport Compose tooltips with clipped bounds."""

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TEST_FILE = (ROOT / "app/src/androidTest/java/com/meteocompare/app/ui/graphicview/"
             "GraphicForecastContentTest.kt")


class GraphicTooltipMeasurementTests(unittest.TestCase):
    def test_badge_width_uses_unclipped_semantics_size(self):
        source = TEST_FILE.read_text(encoding="utf-8")
        self.assertIn(".fetchSemanticsNode().size.width.toFloat()", source)
        self.assertIn(".fetchSemanticsNode().size.width > 0", source)

    def test_no_badge_width_assertion_uses_clipped_bounds(self):
        source = TEST_FILE.read_text(encoding="utf-8")
        self.assertNotIn(".fetchSemanticsNode().boundsInRoot.width", source)
        self.assertIn("longLabelWidth > shortLabelWidth + 1f", source)
        self.assertIn("tooltipTags.forEach { tag ->", source)


if __name__ == "__main__":
    unittest.main()
