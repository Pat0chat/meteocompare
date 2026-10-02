package com.meteocompare.app.core.units

import com.meteocompare.app.domain.model.UnitSystem
import java.util.Locale
import kotlin.math.abs

/** Presentation boundary: every input is metric, every output is display-only. */
data class WeatherUnits(val system: UnitSystem = UnitSystem.METRIC) {
    val imperial: Boolean get() = system == UnitSystem.IMPERIAL
    val temperatureUnit: String get() = if (imperial) "°F" else "°C"
    val temperatureSuffix: String get() = if (imperial) "°F" else "°"
    val windUnit: String get() = if (imperial) "mph" else "km/h"
    val precipitationUnit: String get() = if (imperial) "in" else "mm"
    val heightUnit: String get() = if (imperial) "ft" else "m"
    val distanceUnit: String get() = if (imperial) "mi" else "km"
    val precipitationDigits: Int get() = if (imperial) 2 else 1

    fun temperature(celsius: Double): Double = if (imperial) celsius * 1.8 + 32.0 else celsius
    /** Differences, MAE, spread and standard deviation must never receive the +32 offset. */
    fun temperatureDelta(celsius: Double): Double = if (imperial) celsius * 1.8 else celsius
    fun wind(kmh: Double): Double = if (imperial) kmh / 1.609344 else kmh
    fun precipitation(mm: Double): Double = if (imperial) mm / 25.4 else mm
    fun height(metres: Double): Double = if (imperial) metres / 0.3048 else metres
    fun distance(km: Double): Double = if (imperial) km / 1.609344 else km

    // NIST SP 811: conventional inch of mercury = 3386.389 Pa.
    fun pressure(hpa: Double): Double = if (imperial) hpa / 33.86389 else hpa

    fun convert(value: Double, metricUnit: WeatherUnit, delta: Boolean = false): Double = when (metricUnit) {
        WeatherUnit.TEMPERATURE, WeatherUnit.TEMPERATURE_COMPACT ->
            if (delta) temperatureDelta(value) else temperature(value)
        WeatherUnit.WIND_SPEED -> wind(value)
        WeatherUnit.PRECIPITATION, WeatherUnit.PRECIPITATION_RATE -> precipitation(value)
        WeatherUnit.HEIGHT -> height(value)
        WeatherUnit.DISTANCE -> distance(value)
        WeatherUnit.PRESSURE -> pressure(value)
        WeatherUnit.PERCENT, WeatherUnit.SECONDS -> value
    }

    fun label(metricUnit: WeatherUnit): String = when (metricUnit) {
        WeatherUnit.TEMPERATURE -> temperatureUnit
        WeatherUnit.TEMPERATURE_COMPACT -> temperatureSuffix
        WeatherUnit.WIND_SPEED -> windUnit
        WeatherUnit.PRECIPITATION -> precipitationUnit
        WeatherUnit.PRECIPITATION_RATE -> "$precipitationUnit/h"
        WeatherUnit.HEIGHT -> heightUnit
        WeatherUnit.DISTANCE -> distanceUnit
        WeatherUnit.PRESSURE -> if (imperial) "inHg" else "hPa"
        WeatherUnit.PERCENT -> "%"
        WeatherUnit.SECONDS -> "s"
    }

    fun suffix(metricUnit: WeatherUnit): String =
        (if (metricUnit.separated) " " else "") + label(metricUnit)

    fun number(value: Double?, digits: Int = 0, locale: Locale = Locale.getDefault()): String {
        if (value == null || !value.isFinite()) return "—"
        val precision = digits.coerceIn(0, 6)
        val scale = Math.pow(10.0, precision.toDouble())
        val safe = if (abs(value) < 0.5 / scale) 0.0 else value
        // Match the app's historical roundToInt convention without overflowing an Int.
        return if (abs(safe) >= 1e9) String.format(locale, "%.3g", safe)
        else if (precision == 0) String.format(locale, "%.0f", kotlin.math.floor(safe + 0.5))
        else String.format(locale, "%.${precision}f", safe)
    }

    fun value(value: Double?, metricUnit: WeatherUnit, digits: Int = 0,
              locale: Locale = Locale.getDefault(), delta: Boolean = false): String {
        if (value == null || !value.isFinite()) return "—"
        val precision = precision(metricUnit, digits)
        val converted = convert(value, metricUnit, delta)
        val text = number(converted, precision, locale)
        // Keep a nonzero trace visible instead of presenting rain as a dry value.
        val limit = 1.0 / Math.pow(10.0, precision.toDouble())
        return if (metricUnit.isPrecipitation && !delta && value > 0.0 && converted < limit / 2.0) {
            "<${number(limit, precision, locale)}"
        } else text
    }

    fun format(value: Double?, metricUnit: WeatherUnit, digits: Int = 0,
               locale: Locale = Locale.getDefault(), delta: Boolean = false): String {
        val text = value(value, metricUnit, digits, locale, delta)
        return if (text == "—") text else text + suffix(metricUnit)
    }

    /** Tick spacing is a metric delta, even for an absolute temperature axis. */
    fun axisValue(value: Double, unit: WeatherUnit, metricTickStep: Double, digits: Int = 0,
                  locale: Locale = Locale.getDefault()): String {
        val step = abs(convert(metricTickStep, unit, delta = true))
        val needed = if (step.isFinite() && step > 0.0) {
            kotlin.math.ceil(-kotlin.math.log10(step)).coerceIn(0.0, 6.0).toInt()
        } else 6
        // Axis coordinates are numbers, not precipitation trace descriptions.
        return number(convert(value, unit), maxOf(precision(unit, digits), needed), locale)
    }

    private fun precision(unit: WeatherUnit, digits: Int): Int = when {
        unit.isPrecipitation -> maxOf(if (imperial) 2 else 1, digits)
        imperial && unit == WeatherUnit.PRESSURE -> maxOf(2, digits)
        else -> digits
    }.coerceIn(0, 6)

    fun sameDisplayedValue(left: Double?, right: Double?, unit: WeatherUnit, digits: Int = 0,
                           locale: Locale = Locale.getDefault()): Boolean {
        if (left == null || right == null || !left.isFinite() || !right.isFinite()) return false
        val displayed = value(left, unit, digits, locale)
        return displayed != "—" && displayed == value(right, unit, digits, locale)
    }

    fun signedDelta(value: Double?, unit: WeatherUnit, digits: Int = 1,
                    locale: Locale = Locale.getDefault(), compact: Boolean = false): String {
        if (value == null || !value.isFinite()) return "—"
        val magnitude = value(abs(value), unit, digits, locale, delta = true)
        if (magnitude == "—") return magnitude
        val zero = number(0.0, precision(unit, digits), locale)
        val sign = when {
            magnitude == zero -> ""
            value > 0.0 -> "+"
            value < 0.0 -> "−"
            else -> ""
        }
        return sign + magnitude + if (compact) label(unit) else suffix(unit)
    }

    fun temp(value: Double?, digits: Int = 0, locale: Locale = Locale.getDefault()): String =
        format(value, WeatherUnit.TEMPERATURE_COMPACT, digits, locale)
    fun rain(value: Double?, locale: Locale = Locale.getDefault()): String =
        format(value, WeatherUnit.PRECIPITATION, 1, locale)
    fun speed(value: Double?, locale: Locale = Locale.getDefault()): String =
        format(value, WeatherUnit.WIND_SPEED, 0, locale)
}
