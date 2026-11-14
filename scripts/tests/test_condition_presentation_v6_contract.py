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
        code = (MAIN / 'ui/components/ConditionStatusLegend.kt').read_text(encoding='utf-8')
        first = code.split('testTag("condition_legend_first_row")', 1)[1].split('testTag("condition_legend_second_row")', 1)[0]
        second = code.split('testTag("condition_legend_second_row")', 1)[1].split('if (open)', 1)[0]
        self.assertEqual(['ESTIMATED', 'AMBIGUOUS'],
                         re.findall(r'LegendChip\(ConditionStatus\.(\w+)', first))
        self.assertEqual(['INCOMPLETE', 'UNAVAILABLE'],
                         re.findall(r'LegendChip\(ConditionStatus\.(\w+)', second))
        self.assertIn('Modifier.weight(1f)', first)
        self.assertIn('Modifier.weight(1f)', second)
        self.assertIn('fontSize = 19.sp', code)
        self.assertIn('maxLines = 1, overflow = TextOverflow.Ellipsis', code)

    def test_short_legend_labels_translated_and_nonblank_in_six_languages(self):
        keys = {'condition_legend_estimated_short', 'condition_legend_alternatives_short',
                'condition_legend_partial_short', 'condition_legend_unavailable_short'}
        for folder in ('values', 'values-en', 'values-de', 'values-es', 'values-it', 'values-tr'):
            with self.subTest(folder=folder):
                strings = {node.get('name'): (node.text or '').strip() for node in
                           ET.parse(ROOT / f'app/src/main/res/{folder}/strings.xml').iter('string')}
                self.assertTrue(keys.issubset(strings), keys - strings.keys())
                self.assertTrue(all(strings[k] for k in keys))
                # The compact labels must not contain symbols, which have a prominent style of their own.
                self.assertTrue(all(not any(mark in strings[k] for mark in ('≈', '≠', '◔', '—')) for k in keys))

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
        for tag in ('condition_legend_first_row', 'condition_legend_second_row',
                    'condition_alternative_primary', 'condition_alternative_secondary'):
            self.assertIn(f'onNodeWithTag("{tag}"', code)


if __name__ == '__main__':
    unittest.main()
