"""V5 presentation and placement guardrails; these do not replace Compose UI tests."""
from pathlib import Path
import unittest
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'app/src/main/java/com/meteocompare/app'
RES = ROOT / 'app/src/main/res'


def kotlin(path):
    return (MAIN / path).read_text(encoding='utf-8')


class ConditionPresentationV5Contract(unittest.TestCase):
    def test_home_has_icons_but_no_full_status_legend(self):
        screen = kotlin('ui/citylist/CityListScreen.kt')
        strip = kotlin('ui/citylist/MiniForecastStrip.kt')
        self.assertIn('ConditionStatusIcon(', screen)
        self.assertIn('ConditionStatusIcon(', strip)
        self.assertNotIn('ConditionStatusLegend(', screen)

    def test_chronology_legend_precedes_both_timeline_layouts(self):
        timeline = kotlin('ui/citydetail/SimplifiedTimeline.kt')
        legend = timeline.index('ConditionStatusLegend(Modifier.padding')
        layout = timeline.index('when (layout) {', timeline.index('if (expanded) {'))
        self.assertLess(legend, layout)
        self.assertEqual(1, timeline.count('ConditionStatusLegend('))
        self.assertIn('ConditionStatusIcon(', kotlin('ui/citydetail/ChronoTimeline.kt'))

    def test_detail_dialog_has_real_icons_and_diagnostic_structure(self):
        component = '\n'.join(kotlin('ui/components/' + file) for file in (
            'ConditionStatusIcon.kt', 'ConditionExplanationDialog.kt', 'ConditionStatusLegend.kt', 'ConditionInfoDialog.kt'))
        for term in (
            'Dialog(', 'ConditionHero(', 'WeatherIconDecorative(condition, size = 60.dp',
            'visibleConditionAlternatives(decision)', 'DiagnosticProgress(',
            'condition_explanation_data_title', 'condition_explanation_reasons_title',
            'condition_explanation_dialog', 'condition_dialog_close', 'condition_legend_dialog',
            'condition_status_legend', 'DialogProperties(usePlatformDefaultWidth = false)'
        ):
            with self.subTest(term=term):
                self.assertIn(term, component)
        self.assertNotIn('AlertDialog(', component)

    def test_legend_kept_in_context_outside_home(self):
        for relative in (
            'ui/citydetail/SimplifiedTimeline.kt',
            'ui/citydetail/WeatherByModelTable.kt',
            'ui/graphicview/GraphicForecastScreen.kt',
            'ui/enginecomparison/EngineComparisonScreen.kt',
        ):
            with self.subTest(relative=relative):
                self.assertIn('ConditionStatusLegend(', kotlin(relative))

    def test_new_strings_are_localized(self):
        required = {
            'condition_detail_title', 'condition_explanation_no_condition',
            'condition_explanation_data_title', 'condition_explanation_source_title',
            'condition_explanation_alternatives_title', 'condition_explanation_reasons_title'
        }
        for language in ('values', 'values-en', 'values-de', 'values-es', 'values-it', 'values-tr'):
            entries = {node.attrib['name']: (node.text or '').strip()
                       for node in ET.parse(RES / language / 'strings.xml').findall('./string')}
            self.assertTrue(required.issubset(entries), language)
            self.assertTrue(all(entries[name] for name in required), language)

    def test_shared_dialog_frame_and_engine_legend_remains_in_view(self):
        frame = kotlin('ui/components/ConditionInfoDialog.kt')
        details = kotlin('ui/components/ConditionExplanationDialog.kt')
        legend = kotlin('ui/components/ConditionStatusLegend.kt')
        self.assertIn('heightIn(max = maxDialogHeight)', frame)
        self.assertIn('content: @Composable ColumnScope.() -> Unit', frame)
        self.assertIn('content = content', frame)
        self.assertIn('ConditionInfoDialog(', details)
        self.assertIn('ConditionInfoDialog(', legend)
        table = kotlin('ui/enginecomparison/EngineComparisonScreen.kt').split(
            'private fun EngineDailyComparisonTable(', 1)[1].split(
            'private fun EngineConditionTableRow(', 1)[0]
        self.assertLess(table.index('ConditionStatusLegend('), table.index('horizontalScroll('))

    def test_no_obsolete_dialog_strings_survive_cleanup(self):
        old = {'condition_explanation_forecast', 'condition_explanation_alternatives',
               'condition_explanation_origin', 'condition_diagnostic_ambiguous',
               'condition_diagnostic_incomplete', 'condition_diagnostic_unavailable',
               'condition_diagnostic_daily_maximum', 'weather_legend_inferred_note'}
        for language in ('values', 'values-en', 'values-de', 'values-es', 'values-it', 'values-tr'):
            names = {e.attrib['name'] for e in ET.parse(RES / language / 'strings.xml').findall('./string')}
            self.assertFalse(names & old, language)

    def test_instrumented_tests_exercise_popups(self):
        tests = (ROOT / 'app/src/androidTest/java/com/meteocompare/app/ui/components/ConditionStatusIconTest.kt').read_text(encoding='utf-8')
        for term in ('condition_explanation_dialog', 'condition_legend_dialog',
                     'condition_coverage', 'condition_explanation_alternatives_title'):
            self.assertIn(term, tests)


if __name__ == '__main__':
    unittest.main()
