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


if __name__ == '__main__':
    unittest.main()
