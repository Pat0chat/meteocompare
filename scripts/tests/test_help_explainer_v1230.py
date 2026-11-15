"""Protect the explanation page against stale scientific claims and missing locales."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCREEN = ROOT / 'app/src/main/java/com/meteocompare/app/ui/help/HowItWorksScreen.kt'
RES = ROOT / 'app/src/main/res'
LOCALES = ('values', 'values-en', 'values-de', 'values-es', 'values-it', 'values-tr')
REQUIRED = {
    'help_title', 'help_subtitle', 'help_section_1_title', 'help_section_1_body',
    'help_section_2_title', 'help_section_2_body', 'help_section_3_title',
    'help_section_3_body', 'help_section_4_title', 'help_section_4_body',
    'help_section_5_title', 'help_section_5_body', 'help_section_6_title',
    'help_section_6_body', 'help_condition_step_native', 'help_condition_step_derived',
    'help_condition_step_consensus', 'help_sunshine_window_title', 'help_sunshine_window_body',
    'help_sunshine_source_title', 'help_sunshine_source_body',
    'help_sunshine_irradiance_title', 'help_sunshine_irradiance_body',
    'help_sunshine_clouds_title', 'help_sunshine_clouds_body', 'help_sunshine_note',
    'help_convergence_example_note', 'help_hierarchical_note',
}

class HelpExplainerContractTest(unittest.TestCase):
    def test_every_locale_contains_scientific_explanation(self):
        for locale in LOCALES:
            with self.subTest(locale=locale):
                tree = ET.parse(RES / locale / 'strings.xml')
                values = {node.attrib['name']: node.text for node in tree.getroot().findall('string')}
                self.assertFalse(REQUIRED - values.keys(), f'Missing in {locale}: {REQUIRED - values.keys()}')
                for key in REQUIRED:
                    self.assertTrue(values[key] and len(values[key]) > 6, (locale, key))

    def test_description_is_preserved_and_sections_present(self):
        code = SCREEN.read_text(encoding='utf-8')
        self.assertIn('stringResource(R.string.help_subtitle)', code)
        self.assertIn('ModelSourcesIllustration()', code)
        self.assertIn('EngineModesIllustration()', code)
        self.assertIn('HierarchicalConsensusIllustration()', code)
        self.assertIn('SunshineMethodIllustration()', code)
        self.assertIn('ConditionStatusesIllustration()', code)
        for number in range(1,7):
            self.assertIn(f'step = {number},', code)
        self.assertEqual(6, len(re.findall(r'\bstep = [1-6],',code)))

    def test_statuses_share_real_labels_and_icons(self):
        code = SCREEN.read_text(encoding='utf-8')
        self.assertIn('ConditionStatus.entries', code)
        self.assertIn('statusLabelRes(status)', code)
        self.assertIn('statusDescriptionRes(status)', code)
        self.assertIn('statusSymbol(status)', code)
        self.assertIn('statusAccent(status)', code)
        self.assertIn('condition_legend_not_probability', code)

    def test_illustrations_dont_claim_calibrated_probabilities(self):
        texts = [(RES / p / 'strings.xml').read_text(encoding='utf-8') for p in LOCALES]
        for txt in texts:
            self.assertIn('help_convergence_example_note',txt)
            self.assertIn('help_hierarchical_note',txt)
        # The old engine explanation that mixes central-model medians must not return.
        fr = texts[0].lower()
        self.assertNotIn('dérivée une seule fois des valeurs centrales',fr)

    def test_statuses_are_fourth_and_menu_shows_all_six_sections(self):
        code = SCREEN.read_text(encoding='utf-8')
        sections = code.split('val sections = listOf(', 1)[1].split('val listState', 1)[0]
        self.assertEqual([1, 2, 3, 4, 5, 6],
                         [int(v) for v in re.findall(r'step = ([1-6]),', sections)])
        order = re.findall(r'title = stringResource\(R.string.(help_section_\d_title)\)', sections)
        self.assertEqual(['help_section_1_title', 'help_section_2_title',
                          'help_section_3_title', 'help_section_6_title',
                          'help_section_4_title', 'help_section_5_title'], order)
        self.assertIn('testTag("help_contents_menu")', code)
        self.assertIn('sections.chunked(2)', code)
        self.assertIn('animateScrollToItem(index + 2)', code)
        self.assertNotIn('AssistChip(', code)
        self.assertNotIn('LazyRow(', code)

    def test_dry_and_precipitation_cards_share_intrinsic_height(self):
        code = SCREEN.read_text(encoding='utf-8')
        method = code.split('internal fun HierarchicalConsensusIllustration()', 1)[1].split(
            'private fun ConsensusBranchConnector', 1)[0]
        self.assertIn('Modifier.fillMaxWidth().height(IntrinsicSize.Max)', method)
        cards = code.split('private fun ConsensusScenario(', 1)[1].split(
            'private fun ConvergenceIllustration()', 1)[0]
        self.assertIn('modifier.fillMaxHeight().heightIn(min = 180.dp)', cards)

    def test_literal_percent_examples_are_not_android_format_strings(self):
        for folder in LOCALES:
            with self.subTest(locale=folder):
                values = {node.attrib['name']: node for node in
                          ET.parse(RES / folder / 'strings.xml').findall('./string')}
                for key in ('help_hierarchical_note', 'help_convergence_example_note'):
                    self.assertEqual('false', values[key].get('formatted'), (folder, key))
                    self.assertIn('%', values[key].text)

if __name__ == '__main__':
    unittest.main()
