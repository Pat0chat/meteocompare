"""Prevent a regression where expanded cloud instructions remain beyond the dialog viewport."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class CloudExplanationScrollContractTest(unittest.TestCase):
    def test_expanding_the_explanation_reveals_its_full_content(self):
        kotlin = (ROOT / 'app/src/main/java/com/meteocompare/app/ui/citydetail/TodayCloudLayersCard.kt')\
            .read_text(encoding='utf-8')
        self.assertIn('LaunchedEffect(showExplanation)', kotlin)
        self.assertIn('detailScrollState.scrollTo(detailScrollState.maxValue)', kotlin)
        self.assertIn('.verticalScroll(detailScrollState)', kotlin)
        self.assertIn('Modifier.testTag("today_cloud_veil_explanation")', kotlin)

    def test_android_test_checks_the_expanded_text_is_actually_visible(self):
        kotlin = (ROOT / 'app/src/androidTest/java/com/meteocompare/app/ui/citydetail/TodaySummaryCardTest.kt')\
            .read_text(encoding='utf-8')
        self.assertIn('"today_cloud_explanation_toggle"', kotlin)
        self.assertIn('"today_cloud_veil_explanation"', kotlin)
        self.assertIn('.assertExists().assertIsDisplayed()', kotlin)
        # The toggle can be present in the semantics tree but off-screen; it
        # must be scrolled into view *before* delivering the click.
        self.assertRegex(kotlin, r'"today_cloud_explanation_toggle", useUnmergedTree = true\)\s*'
                         r'\.performScrollTo\(\)\.assertExists\(\)'
                         r'\.assertIsDisplayed\(\)\.performClick\(\)')
        # Hour selection must happen before expanding the bottom explanation:
        # its nested horizontal scroller cannot move the outer vertical dialog
        # back to the selector when performScrollTo() targets an hour chip.
        self.assertLess(kotlin.index('"today_cloud_hour_1", useUnmergedTree = true'),
                        kotlin.index('"today_cloud_explanation_toggle", useUnmergedTree = true'))
        self.assertRegex(kotlin, r'"today_cloud_hour_1", useUnmergedTree = true\)\s*'
                         r'\.assertExists\(\)\.assertIsDisplayed\(\)\.performClick\(\)')
        # The expanded explanation must still be brought into view and verified.
        self.assertRegex(kotlin, r'"today_cloud_veil_explanation", useUnmergedTree = true\)\s*'
                         r'\.performScrollTo\(\)\.assertExists\(\)\.assertIsDisplayed\(\)')


if __name__ == '__main__':
    unittest.main()
