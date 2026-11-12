#!/usr/bin/env python3
"""Offline smoke tests for localized Today summary dialog explanations.

Run with: python3 scripts/test-summary-dialog-copy.py
Android layout/instrumentation tests remain responsible for visual overflow.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
UI = ROOT / 'app/src/main/java/com/meteocompare/app/ui/citydetail/CityDetailScreen.kt'
LOCALES = {
    'values': ('pluie', 'précip'),
    'values-en': ('rain', 'precip', 'wet'),
    'values-de': ('regen', 'niederschlag'),
    'values-es': ('lluvia', 'precipit'),
    'values-it': ('piogg', 'precipit'),
}
REQUIRED = (
    'metric_calc_temperature', 'metric_calc_wind', 'metric_calc_rain_method',
    'metric_calc_rain_rule',
    'metric_calc_rain_dry_with_rain', 'metric_calc_model_probability',
    'metric_calc_temperature_formula', 'metric_calc_wind_formula',
    'metric_calc_rain_formula', 'metric_calc_chart_title', 'metric_calc_chart_legend',
    'metric_calc_models_title', 'metric_calc_rain_step_risk', 'metric_calc_rain_step_scenario',
    'metric_calc_rain_dry_rounding',
)

for locale, rain_terms in LOCALES.items():
    root = ET.parse(RES / locale / 'strings.xml').getroot()
    lookup = {node.get('name'): ''.join(node.itertext()) for node in root if node.tag == 'string'}
    assert all(k in lookup for k in REQUIRED), (locale, 'missing resource')
    for key in ('metric_calc_temperature', 'metric_calc_wind'):
        text = lookup[key].lower()
        assert not any(term in text for term in rain_terms), (locale, key, text)
    assert 'Tᵢ' in lookup['metric_calc_temperature_formula'], locale
    assert 'Vᵢ' in lookup['metric_calc_wind_formula'], locale
    assert 'pᵢ' in lookup['metric_calc_rain_formula'], locale
    assert lookup['metric_calc_how_title'] != lookup['metric_calc_chart_title'], locale
    assert len(lookup['metric_calc_rain_method']) < 180, locale
    assert '< 50' in lookup['metric_calc_rain_rule'], locale
    assert '%1$d' in lookup['metric_calc_model_probability'], locale
    assert len(lookup['metric_calc_temperature']) < 310, locale
    assert len(lookup['metric_calc_wind']) < 310, locale

source = UI.read_text(encoding="utf-8")
dialog = source.split('private fun DispersionDetailsDialog(')[1].split('/** Visually separate')[0]
assert '.verticalScroll(rememberScrollState())' in dialog
assert 'summary_dispersion_dialog_scroll' in dialog
assert 'RainConsensusExplanation(rainDetails' in dialog
assert dialog.count('SummaryDialogSection(') == 2
assert 'R.string.metric_calc_models_title' in dialog
assert 'R.string.metric_calc_chart_legend' in dialog
assert 'SummaryDialogFormula(' in dialog
assert dialog.index('if (!isRain) {') < dialog.index('SummaryDialogSection(stringResource(R.string.metric_calc_how_title))')
assert dialog.index('if (isRain) {') > dialog.index('SummaryDialogSection(stringResource(R.string.metric_calc_how_title))')
assert 'if (rainDetails != null)' in dialog
assert 'SummaryDialogValueCard' not in dialog
assert 'maxLines = 1' not in dialog
assert 'TextOverflow.Ellipsis' not in dialog
print('OK: 5 locales, structured explanation/chart, independent formulae, scrollable dialog, no forced ellipsis')
