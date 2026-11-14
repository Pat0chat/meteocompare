"""Guard the isolated Compose regression for absent temperature data.

These source contracts complement Android instrumentation; they cannot verify
emulator lifecycle behavior themselves.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
TEST = ROOT / 'app/src/androidTest/java/com/meteocompare/app/ui/enginecomparison/EngineComparisonChartUiTest.kt'


class EngineComparisonTestHostContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = TEST.read_text(encoding='utf-8')

    def test_host_is_explicit_and_attached_before_set_content(self):
        self.assertIn('createAndroidComposeRule<ComponentActivity>()', self.source)
        render = self.source.split('private fun render(', 1)[1].split('@Test', 1)[0]
        self.assertLess(render.index('activity.window.decorView.isAttachedToWindow'),
                        render.index('composeRule.setContent'))
        self.assertIn('Lifecycle.State.RESUMED', render)

    def test_missing_temperature_keeps_functional_assertions_and_small_fixture(self):
        method = self.source.split('fun missingTemperatureDoesNotShowMisleadingCurvesOrValues()', 1)[1]
        method = method.split('@Test', 1)[0]
        self.assertIn('loaded.days.take(2)', method)
        self.assertIn('tempMax = null', method)
        self.assertIn('R.string.engine_comparison_not_enough_data', method)
        self.assertIn('composeRule.onNodeWithTag(TAG_ENGINE_CHART_INSPECTOR).assertDoesNotExist()', method)
        self.assertIn('composeRule.onNodeWithTag(TAG_ENGINE_CHART_SCROLL).assertDoesNotExist()', method)
        self.assertNotIn('catch (', method)


if __name__ == '__main__':
    unittest.main()
