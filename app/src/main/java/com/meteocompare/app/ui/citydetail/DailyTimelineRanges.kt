package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.core.units.WeatherUnit
import com.meteocompare.app.core.units.WeatherUnits
import java.util.Locale

/** A range is meaningful only with two valid forecast endpoints. Values stay in metric units. */
internal fun dailyTimelineRange(
    min: Double?, max: Double?, units: WeatherUnits, metricUnit: WeatherUnit,
    digits: Int = 0, locale: Locale = Locale.getDefault()
): String? {
    if (min == null || max == null || !min.isFinite() || !max.isFinite() || min > max) return null
    val low = units.value(min, metricUnit, digits, locale)
    val high = units.value(max, metricUnit, digits, locale)
    if (low == "—" || high == "—") return null
    return "($low–$high)"
}
