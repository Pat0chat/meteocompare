package com.meteocompare.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.meteocompare.app.R
import com.meteocompare.app.core.units.WeatherUnits
import java.util.Locale
import kotlin.math.roundToInt

/** Translated templates and axis abbreviation, resolved from the app's current language. */
internal data class SunshineDurationFormats(
    val minutes: String,
    val hours: String,
    val hoursAndMinutes: String,
    val minuteUnit: String
)

@Composable
internal fun localizedSunshineDurationFormats(): SunshineDurationFormats = SunshineDurationFormats(
    minutes = stringResource(R.string.sunshine_duration_minutes),
    hours = stringResource(R.string.sunshine_duration_hours),
    hoursAndMinutes = stringResource(R.string.sunshine_duration_hours_minutes),
    minuteUnit = stringResource(R.string.sunshine_duration_minute_unit)
)

/**
 * Domain estimates are in hours. A single hourly slot is expressed in whole minutes;
 * a daily total uses localized hours + minutes. Missing/invalid data is not zero.
 * The format templates come from localized Android resources, never hard-coded units.
 */
internal fun formatSunshineDuration(
    hours: Double?,
    hourly: Boolean,
    units: WeatherUnits,
    locale: Locale,
    formats: SunshineDurationFormats
): String {
    if (hours == null || !hours.isFinite() || hours < 0.0 ||
        hours > (if (hourly) 1.0 else 24.0)) return "—"
    val minutes = (hours * 60.0).roundToInt()
    val minuteText = units.number(minutes.toDouble(), 0, locale)
    if (hourly || minutes < 60) return String.format(locale, formats.minutes, minuteText)

    val fullHours = minutes / 60
    val remainder = minutes % 60
    val hourText = units.number(fullHours.toDouble(), 0, locale)
    return if (remainder == 0) String.format(locale, formats.hours, hourText)
    else String.format(
        locale, formats.hoursAndMinutes, hourText,
        units.number(remainder.toDouble(), 0, locale)
    )
}
