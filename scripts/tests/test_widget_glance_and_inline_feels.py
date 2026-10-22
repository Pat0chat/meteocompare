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
    for name, end in [('MediumLayout', 'LargeLayout'),
                      ('LargeLayout', 'CompactTallLayout'),
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


def test_feels_helper_is_one_atomic_text_next_to_extremes_not_current():
    source = (WIDGET / 'MeteoWidget.kt').read_text(encoding='utf-8')
    helper = source.split('internal fun WidgetMinMaxWithApparent(', 1)[1].split('/** Layout 3×1', 1)[0]
    assert 'text = minMaxAndApparentText(' in helper
    assert helper.count('\n    Text(') == 1
    assert 'Row(' not in helper
    assert 'data.currentTemp' not in helper
    formatter = (WIDGET / 'WidgetFormatting.kt').read_text(encoding='utf-8')
    composed = formatter.split('internal fun minMaxAndApparentText(', 1)[1].split('/**', 1)[0]
    assert 'apparentNextToMinMax(minMax, apparentTemp, units, prefix, show)' in composed
    assert '"$minMax · $apparent"' in composed
    assert 'else minMax' in composed
    assert 'show = show && minMax.isNotBlank()' in formatter


def test_all_dynamic_glance_rows_are_bounded_for_widget_variants():
    main = (WIDGET / 'MeteoWidget.kt').read_text(encoding='utf-8')
    value = (WIDGET / 'MeteoValueWidgets.kt').read_text(encoding='utf-8')
    layout = (WIDGET / 'WidgetForecastCardLayout.kt').read_text(encoding='utf-8')
    # Timeline: a single nested Row per forecast, up to five.
    assert 'itemCount.coerceIn(0, MAX_GLANCE_FORECAST_TIMELINE_ITEMS)' in main
    assert 'MAX_GLANCE_FORECAST_TIMELINE_ITEMS = 5' in layout
    assert 'Row(modifier = GlanceModifier.defaultWeight().fillMaxHeight())' in main
    # Confidence by day: a single nested Row per day, up to five.
    assert 'bucketCount.coerceIn(1, 5)' in main
    assert 'days.forEachIndexed { index, day ->' in main
    # Inline forecast: two items maximum, so at most three Row children
    # (two cards and a spacer); insight metrics: three + two spacers.
    assert 'else -> 2' in layout.split('internal fun inlineForecastItemCount(', 1)[1].split('}', 1)[0]
    assert 'data.forecasts.take(inlineForecastItems).forEachIndexed' in main
    assert 'data.comparisonSnapshot?.metrics.orEmpty().take(3)' in value
    # All view types (tiny, small, medium, large, compact-tall, extra-large,
    # insight and confidence) either have fixed Row structure or a bounded loop.
    assert 'WidgetMinMaxWithApparent(' in _function(main, 'MediumLayout', 'LargeLayout')
    assert 'WidgetMinMaxWithApparent(' in _function(main, 'LargeLayout', 'CompactTallLayout')
    assert 'WidgetMinMaxWithApparent(' in _function(main, 'CompactTallLayout', 'ColumnScope.CompactConfidenceSummary')
    assert 'WidgetMinMaxWithApparent(' in _function(main, 'ExtraLargeLayout', 'TwelveHourForecastStrip')
    assert 'WidgetMinMaxWithApparent(' in _function(value, 'InsightWidgetLayout', 'InsightSurface')
