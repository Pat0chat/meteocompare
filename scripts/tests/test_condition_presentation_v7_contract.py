"""V7 placements and portable regression corpus checks, with a single themed legend surface."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / 'app/src/main/java/com/meteocompare/app/ui'


class ConditionPresentationV7Contract(unittest.TestCase):
    def test_compact_legend_has_one_surface_and_two_fixed_rows(self):
        help_ui = (SRC / 'help/HowItWorksScreen.kt').read_text(encoding='utf-8')
        self.assertIn('private fun HelpNavigationMenu(', help_ui)
        self.assertIn('testTag("help_contents_menu")', help_ui)
        self.assertIn('sections.chunked(2)', help_ui)
        self.assertIn('modifier = modifier.heightIn(min = 78.dp).testTag("help_menu_${section.step}")', help_ui)

    def test_legend_is_compact_but_keeps_its_one_themed_background_and_alignment(self):
        help_ui = (SRC / 'help/HowItWorksScreen.kt').read_text(encoding='utf-8')
        self.assertIn('color = MaterialTheme.colorScheme.surfaceContainerLow', help_ui)
        self.assertIn('onClick = onClick', help_ui)
        self.assertIn('animateScrollToItem(index + 2)', help_ui)
        self.assertNotIn('AssistChip(', help_ui)
        self.assertNotIn('LazyRow(', help_ui)

    def test_mini_forecast_android_test_awaits_attached_host_and_keeps_unavailable_visible(self):
        code = (ROOT / 'app/src/androidTest/java/com/meteocompare/app/ui/citylist/'
                'MiniForecastStripTest.kt').read_text(encoding='utf-8')
        self.assertIn('createAndroidComposeRule<ComponentActivity>()', code)
        self.assertIn('activity.window.decorView.isAttachedToWindow', code)
        self.assertIn('Lifecycle.State.RESUMED', code)
        self.assertIn('R.string.condition_status_unavailable', code)
        self.assertNotIn('runWithoutImplicitWait', code)
        self.assertNotIn('mainClock.autoAdvance = false', code)
        # The dialog's localized explanation is tested by the dedicated component
        # test, not in the heavy 24-hour strip integration fixture.
        explanation = (ROOT / 'app/src/androidTest/java/com/meteocompare/app/ui/components/'
                       'ConditionStatusIconTest.kt').read_text(encoding='utf-8')
        self.assertIn('missing_condition_without_decision_is_explained_as_unavailable', explanation)

    def test_timeline_legend_is_below_convergence_explanation(self):
        source = (SRC / 'citydetail/SimplifiedTimeline.kt').read_text(encoding='utf-8')
        self.assertIn('R.string.timeline_disagreement_hint', source)
        self.assertNotIn('ConditionStatusLegend', source)

    def test_chart_legend_is_below_plot_above_open_meteo(self):
        source = (SRC / 'graphicview/GraphicForecastScreen.kt').read_text(encoding='utf-8')
        content = source.split('internal fun GraphicForecastContent(', 1)[1].split(
            'private fun GraphicChartPanel(', 1)[0]
        self.assertLess(content.index('GraphicChartPanel('), content.index('OpenMeteoAttribution('))
        self.assertNotIn('ConditionStatusLegend', content)

    def test_crlf_only_normalization_preserves_frozen_golden_hashes(self):
        from scripts.tests.test_weather_regression_corpus import canonical_fixture_bytes
        corpus = ROOT / 'app/src/test/resources/weather_regression/v1'
        manifest = json.loads((corpus / 'manifest.json').read_text(encoding='utf-8'))
        for name, entry in manifest['files'].items():
            with self.subTest(name=name):
                raw = canonical_fixture_bytes((corpus / name).read_bytes())
                crlf = raw.replace(b'\n', b'\r\n')
                self.assertEqual(hashlib.sha256(canonical_fixture_bytes(crlf)).hexdigest(),
                                 entry['sha256'])
        self.assertRaises(ValueError, canonical_fixture_bytes, b'bad\rcontents')

    def test_generator_check_does_not_alter_fixtures(self):
        before = (ROOT / 'app/src/test/resources/weather_regression/v1/manifest.json').read_bytes()
        subprocess.run([sys.executable, 'scripts/generate_weather_regression_corpus.py', '--check'],
                       cwd=ROOT, check=True, capture_output=True, text=True)
        after = (ROOT / 'app/src/test/resources/weather_regression/v1/manifest.json').read_bytes()
        self.assertEqual(before, after)


if __name__ == '__main__':
    unittest.main()
