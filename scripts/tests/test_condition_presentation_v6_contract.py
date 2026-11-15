"""V6 regressions: deliberate legend grouping, readable alternatives and JVM assertions.

Static checks complement the on-device Compose tests; they do not prove layout.
"""
from pathlib import Path
import re
import unittest
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'app/src/main/java/com/meteocompare/app'
TEST = ROOT / 'app/src/test/java/com/meteocompare/app/domain'


class ConditionPresentationV6Contract(unittest.TestCase):
    def test_compact_legend_has_exactly_two_semantic_rows_in_requested_order(self):
        help_ui = (MAIN / 'ui/help/HowItWorksScreen.kt').read_text(encoding='utf-8')
        self.assertIn('ConditionStatusesIllustration()', help_ui)
        self.assertIn('ConditionStatus.entries', help_ui)
        self.assertNotIn('ConditionStatusLegend', help_ui)
        self.assertFalse((MAIN / 'ui/components/ConditionStatusLegend.kt').exists())

    def test_short_legend_labels_translated_and_nonblank_in_six_languages(self):
        keys = {'help_contents_menu_title', 'help_menu_models', 'help_menu_engines',
                'help_menu_conditions', 'help_menu_statuses', 'help_menu_agreement',
                'help_menu_sunshine'}
        for folder in ('values', 'values-en', 'values-de', 'values-es', 'values-it', 'values-tr'):
            with self.subTest(folder=folder):
                strings = {node.get('name'): (node.text or '').strip() for node in
                           ET.parse(ROOT / f'app/src/main/res/{folder}/strings.xml').iter('string')}
                self.assertTrue(keys.issubset(strings), keys - strings.keys())
                self.assertTrue(all(strings[k] for k in keys))

    def test_ambiguous_forecast_renders_two_larger_icons_and_reads_both_alternatives(self):
        code = (MAIN / 'ui/components/ConditionStatusIcon.kt').read_text(encoding='utf-8')
        self.assertNotIn('size * 0.50f', code)
        self.assertEqual(2, code.count('size = size * 0.76f'))
        for tag in ('condition_alternative_primary', 'condition_alternative_secondary'):
            self.assertIn(f'testTag("{tag}")', code)
        self.assertIn('alternativeText', code)
        self.assertIn('Modifier.align(Alignment.TopEnd)', code)

    def test_regressed_double_assertions_use_delta(self):
        for filename in ('util/ConditionDecisionEngineTest.kt', 'util/ConditionEnginePropertyTest.kt',
                         'util/ConditionStatusContractTest.kt', 'util/ConditionStatusCorpusTest.kt'):
            code = (TEST / filename).read_text(encoding='utf-8')
            self.assertIn('1e-9)', code, filename)
        source = (TEST / 'usecase/WeatherConditionTest.kt').read_text(encoding='utf-8')
        self.assertIn('ConditionStatus.UNAVAILABLE', source)
        self.assertIn('ConditionStatus.INCOMPLETE', source)
        self.assertIn('Aucune condition météo inventée', source)

    def test_compose_instrumentation_exercises_new_semantics(self):
        code = (ROOT / 'app/src/androidTest/java/com/meteocompare/app/ui/components/ConditionStatusIconTest.kt').read_text(encoding='utf-8')
        for tag in ('condition_alternative_primary', 'condition_alternative_secondary'):
            self.assertIn(f'onNodeWithTag("{tag}"', code)
        self.assertIn('condition_explanation_dialog', code)
        self.assertNotIn('condition_legend_first_row', code)

if __name__ == '__main__':
    unittest.main()
