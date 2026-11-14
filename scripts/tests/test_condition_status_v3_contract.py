"""Source and fixture contract guards (not a substitute for Android compilation)."""
from pathlib import Path
import csv
import hashlib
import json
import unittest
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'app/src/main/java/com/meteocompare/app'
RESOURCE = ROOT / 'app/src/main/res'
FIXTURE = ROOT / 'app/src/test/resources/weather_regression/v2'
STATES = ('reliable', 'estimated', 'ambiguous', 'incomplete', 'unavailable')


class ConditionStatusV3ContractTest(unittest.TestCase):
    def test_all_status_labels_and_explanations_exist_in_six_languages(self):
        required = {f'condition_status_{s}' for s in STATES} | {
            f'condition_status_{s}_desc' for s in STATES
        } | {
            'condition_legend_summary', 'condition_legend_help',
            'condition_legend_title', 'condition_legend_not_probability',
            'condition_explanation_close', 'condition_explanation_alternatives',
            'condition_explanation_origin', 'condition_explanation_time_coverage',
            'condition_explanation_model_coverage', 'condition_explanation_agreement',
        }
        for folder in ('values', 'values-en', 'values-de', 'values-es', 'values-it', 'values-tr'):
            path = RESOURCE / folder / 'strings.xml'
            names = {e.attrib['name'] for e in ET.parse(path).findall('./string')}
            self.assertFalse(required - names, f'{folder} missing {required - names}')

    def test_all_five_statuses_have_explicit_visual_contract_and_explanations(self):
        visuals = (MAIN / 'domain/util/ConditionPresentation.kt').read_text(encoding='utf-8')
        components = (MAIN / 'ui/components/ConditionStatusIcon.kt').read_text(encoding='utf-8')
        for state in STATES:
            self.assertIn('ConditionStatus.' + state.upper(), visuals)
            self.assertIn('ConditionStatus.' + state.upper(), components)
        self.assertIn('visibleConditionAlternatives(decision)', components)
        self.assertIn('ConditionExplanationDialog(condition, decision', components)
        self.assertIn('ConditionStatusLegend', components)

    def test_status_metadata_reaches_each_requested_view(self):
        by_file = {
            'ui/citydetail/SimplifiedTimeline.kt': ('ConditionStatusIcon', 'ConditionStatusLegend', 'dayConditionDiagnostic'),
            'ui/citydetail/ChronoTimeline.kt': ('ConditionStatusIcon', 'dayConditionDiagnostic', 'nightConditionDiagnostic'),
            'ui/citydetail/WeatherByModelTable.kt': ('ConditionStatusIcon', 'ConditionStatusLegend'),
            'ui/citydetail/HourlyWeatherByModelTable.kt': ('ConditionStatusIcon', 'diagnosis'),
            'ui/graphicview/GraphicForecastScreen.kt': ('ConditionStatusIcon', 'ConditionStatusLegend', 'conditionDiagnostic'),
            'ui/citydetail/SimplifiedTimelineBuilder.kt': ('dayConditionDiagnostic', 'nightConditionDiagnostic', 'timelineConditionDiagnostic'),
        }
        for filename, required in by_file.items():
            source = (MAIN / filename).read_text(encoding='utf-8')
            for term in required:
                self.assertIn(term, source, f'{filename} does not include {term}')

    def test_status_corpus_is_frozen_and_has_expected_timezone_coverage(self):
        data = (FIXTURE / 'day_status.tsv').read_bytes()
        manifest = json.loads((FIXTURE / 'manifest.json').read_text(encoding='utf-8'))
        self.assertEqual(manifest['sha256'], hashlib.sha256(data).hexdigest())
        cases = list(csv.DictReader(data.decode('utf-8').splitlines(), delimiter='\t'))
        self.assertEqual(manifest['cases'], len(cases))
        self.assertGreaterEqual(len(cases), 1200)
        self.assertEqual({'UTC','Europe/Paris','America/New_York','Asia/Tokyo'}, {r['zone'] for r in cases})
        self.assertTrue({'23','24','25'}.issubset({r['total_hours'] for r in cases}))
        self.assertEqual({'native', 'estimated'}, {r['source'] for r in cases})
        self.assertTrue(all(0 <= int(r['known_hours']) <= int(r['total_hours']) for r in cases))

    def test_instrumentation_contract_covers_five_states_dialog_alternatives_and_legend(self):
        source = (ROOT / 'app/src/androidTest/java/com/meteocompare/app/ui/components/ConditionStatusIconTest.kt').read_text(encoding='utf-8')
        for status in STATES:
            self.assertIn('ConditionStatus.' + status.upper(), source)
        for term in ('ConditionStatusIcon(', 'ConditionStatusLegend()', 'performClick()', 'condition_explanation_alternatives'):
            self.assertIn(term, source)

    def test_old_cross_model_variable_median_inference_is_not_reintroduced(self):
        source = (MAIN / 'domain/usecase/WeatherConditionConsensus.kt').read_text(encoding='utf-8')
        self.assertNotIn('AggregateSource.AGGREGATED_VARIABLES', source)
        self.assertIn('resolveDecision(', source)


if __name__ == '__main__':
    unittest.main()
