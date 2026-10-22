"""Structural regressions for Glance's 10-direct-children Row limit and feels-like placement.

Not a substitute for an Android/Glance render test on device.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WIDGET = ROOT / 'app/src/main/java/com/meteocompare/app/widget'


def _function(source: str, name: str, next_name: str) -> str:
    return source.split(f'private fun {name}(', 1)[1].split(f'private fun {next_name}(', 1)[0]


def test_five_forecasts_are_bounded_and_separators_are_one_direct_child():
    source = (WIDGET / 'MeteoWidget.kt').read_text(encoding="utf-8")
    panel = _function(source, 'ForecastTimelinePanel', 'ColumnScope.CombinedConfidenceBands')
    assert 'itemCount.coerceIn(0, MAX_GLANCE_FORECAST_TIMELINE_ITEMS)' in panel
    assert 'check(forecastTimelineDirectChildren(visibleItems.size) <= 10)' in panel
    forecasts = panel.split('visibleItems.forEachIndexed { index, item ->', 1)[1]
    assert 'Row(modifier = GlanceModifier.defaultWeight().fillMaxHeight())' in forecasts
    separators = forecasts.split('if (index > 0)', 1)[1].split('ForecastTimelineItem(', 1)[0]
    assert separators.count('Box(') == 2  # separator wrapped inside the item row
    assert 'Spacer(' not in separators  # no separator on the parent Row
    helper = (WIDGET / 'WidgetForecastCardLayout.kt').read_text(encoding='utf-8')
    assert 'itemCount.coerceIn(0, MAX_GLANCE_FORECAST_TIMELINE_ITEMS)' in helper


def test_extended_widgets_render_feels_beside_min_max_and_no_extra_line_duplicate():
    source = (WIDGET / 'MeteoWidget.kt').read_text(encoding="utf-8")
    for name, end in [('LargeLayout', 'CompactTallLayout'),
                      ('CompactTallLayout', 'ColumnScope.CompactConfidenceSummary'),
                      ('ExtraLargeLayout', 'TwelveHourForecastStrip')]:
        assert 'WidgetMinMaxWithApparent(' in _function(source, name, end)
    assert 'WidgetTemperatureWithApparent(' not in source
    assert 'includeApparent = true' not in source
    assert 'showApparent = !veryCompact && widthDp >= 320f' in source


def test_insight_widget_renders_feels_beside_min_max_instead_of_main_temperature():
    source = (WIDGET / 'MeteoValueWidgets.kt').read_text(encoding="utf-8")
    layout = _function(source, 'InsightWidgetLayout', 'InsightSurface')
    assert 'WidgetMinMaxWithApparent(' in layout
    assert 'if (!compact) data.apparentTemp?.' not in layout
    assert 'showApparent = !compact && LocalSize.current.width.value >= 300f' in layout


def test_inline_formatter_rejects_missing_and_non_finite_values():
    source = (WIDGET / 'WidgetFormatting.kt').read_text(encoding="utf-8")
    fmt = source.split('internal fun inlineApparentTemperature(', 1)[1].split('/**', 1)[0]
    assert 'takeIf(Double::isFinite)' in fmt
    assert 'if (show)' in fmt


def test_feels_helper_draws_extremes_first_and_does_not_draw_current_temperature():
    source = (WIDGET / 'MeteoWidget.kt').read_text(encoding="utf-8")
    helper = source.split('internal fun WidgetMinMaxWithApparent(', 1)[1].split('/** Layout 3×1', 1)[0]
    assert 'text = minMax' in helper
    assert 'apparent?.let' in helper
    assert helper.index('text = minMax') < helper.index('apparent?.let')
    assert 'data.currentTemp' not in helper
    assert 'apparentNextToMinMax(' in helper
    formatter = (WIDGET / 'WidgetFormatting.kt').read_text(encoding="utf-8")
    assert 'show = show && minMax.isNotBlank()' in formatter
