"""Time-window integration guards (Android/JVM tests validate behavior)."""
from pathlib import Path
import unittest
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'app/src/main/java/com/meteocompare/app'

class ConditionTimeWindowContractTest(unittest.TestCase):
    def test_six_locales_explain_hourly_and_daily_ranges(self):
        for locale in ('values', 'values-en', 'values-de', 'values-es', 'values-it', 'values-tr'):
            names = {e.get('name') for e in ET.parse(ROOT / 'app/src/main/res' / locale / 'strings.xml').findall('string')}
            self.assertTrue({'condition_explanation_window_title',
                'condition_explanation_window_hourly_note',
                'condition_explanation_window_hourly_precip_note',
                'condition_explanation_window_hourly_mixed_note',
                'condition_explanation_window_precipitation_label',
                'condition_explanation_window_period_note',
                'condition_explanation_window_unavailable_note',
                'condition_explanation_window_daily_wmo_note'} <= names, locale)

    def test_windows_are_generated_where_data_exists(self):
        engine = (JAVA / 'domain/util/ConditionDecisionEngine.kt').read_text(encoding='utf-8')
        self.assertIn('ConditionTimeWindow(start, end)', engine)
        self.assertIn('ConditionWindowKind.HOURLY', engine)
        self.assertIn('ConditionWindowKind.HOURLY_PRECEDING', engine)
        self.assertIn('start.minusSeconds(HOUR_SECONDS)', engine)
        self.assertIn('.inZone(zone)', engine)
        aggregate = (JAVA / 'domain/util/ForecastAggregates.kt').read_text(encoding='utf-8')
        self.assertIn('target.plusSeconds(3_600L)', aggregate)
        self.assertIn('ConditionWindowKind.HOURLY_MIXED', aggregate)
        self.assertIn('target.minusSeconds(3_600L)', aggregate)
        self.assertIn('cityZone', aggregate)
        for file in ('ForecastConditionResolver.kt',):
            self.assertIn('timeWindow =', (JAVA / 'domain/util' / file).read_text(encoding='utf-8'))
        self.assertIn('point.conditionDiagnostic?.inZone(zone)',
            (JAVA / 'ui/citydetail/SimplifiedTimelineBuilder.kt').read_text(encoding='utf-8'))

    def test_popup_shows_time_window_and_respects_clock_format(self):
        content = (JAVA / 'ui/components/ConditionExplanationDialog.kt').read_text(encoding='utf-8')
        self.assertIn('ConditionTimeWindowPanel(timeWindow, decision)', content)
        self.assertIn('WeatherTimeFormat.clock(locale, preference)', content)
        self.assertIn('condition_time_window_value', content)
        self.assertIn('condition_time_window_precipitation', content)
        helper = (JAVA / 'core/locale/ConditionTimeWindowFormatting.kt').read_text(encoding='utf-8')
        self.assertIn('ZoneOffset.UTC', helper)
        self.assertIn('window.kind == ConditionWindowKind.HOURLY', helper)
        self.assertIn('window.endExclusive.atZone(zone)', helper)

if __name__ == '__main__':
    unittest.main()
