package com.meteocompare.app.core.units

import com.meteocompare.app.domain.model.UnitSystem
import com.meteocompare.app.domain.model.CustomUnitPreferences
import com.meteocompare.app.domain.model.TemperatureChoice
import com.meteocompare.app.domain.model.WindChoice
import com.meteocompare.app.domain.model.PrecipitationChoice
import com.meteocompare.app.domain.model.PressureChoice
import com.meteocompare.app.domain.model.HeightChoice
import com.meteocompare.app.domain.model.DistanceChoice
import java.util.Locale
import kotlin.math.abs

// Fixed precision domain; avoid rebuilding patterns and powers for every label.
private val decimalScales = DoubleArray(7) { Math.pow(10.0, it.toDouble()) }
private val decimalPatterns = Array(7) { "%.${it}f" }

/** Presentation boundary: every input is metric, every output is display-only. */
data class WeatherUnits(
    val system: UnitSystem = UnitSystem.METRIC,
    val custom: CustomUnitPreferences = CustomUnitPreferences()
) {
    /** Existing presets are unaffected by custom choices. */
    val imperial: Boolean get() = system == UnitSystem.IMPERIAL
    val temperatureInFahrenheit: Boolean get() = if (system == UnitSystem.CUSTOM)
        custom.temperature == TemperatureChoice.FAHRENHEIT else imperial
    val windInMph: Boolean get() = if (system == UnitSystem.CUSTOM)
        custom.wind == WindChoice.MPH else system != UnitSystem.METRIC
            && system != UnitSystem.CUSTOM
    val distanceInMiles: Boolean get() = if (system == UnitSystem.CUSTOM)
        custom.distance == DistanceChoice.MILES else system != UnitSystem.METRIC
    val precipitationInInches: Boolean get() = if (system == UnitSystem.CUSTOM)
        custom.precipitation == PrecipitationChoice.INCHES else imperial
    val pressureInInHg: Boolean get() = if (system == UnitSystem.CUSTOM)
        custom.pressure == PressureChoice.INHG else imperial
    val heightInFeet: Boolean get() = if (system == UnitSystem.CUSTOM)
        custom.height == HeightChoice.FEET else imperial
    val temperatureUnit: String get() = if (temperatureInFahrenheit) "°F" else "°C"
    val temperatureSuffix: String get() = if (temperatureInFahrenheit) "°F" else "°"
    val windUnit: String get() = when {
        system == UnitSystem.CUSTOM -> custom.wind.symbol
        windInMph -> "mph"
        else -> "km/h"
    }
    val precipitationUnit: String get() = if (precipitationInInches) "in" else "mm"
    val heightUnit: String get() = if (heightInFeet) "ft" else "m"
    val distanceUnit: String get() = if (distanceInMiles) "mi" else "km"
    val precipitationDigits: Int get() = if (precipitationInInches) 2 else 1

    fun temperature(celsius: Double): Double = if (temperatureInFahrenheit) celsius * 1.8 + 32.0 else celsius
    /** Differences, MAE, spread and standard deviation must never receive the +32 offset. */
    fun temperatureDelta(celsius: Double): Double = if (temperatureInFahrenheit) celsius * 1.8 else celsius
    fun wind(kmh: Double): Double = when (windUnit) {
        "mph" -> kmh / 1.609344
        "kn" -> kmh / 1.852
        "m/s" -> kmh / 3.6
        else -> kmh
    }
    fun precipitation(mm: Double): Double = if (precipitationInInches) mm / 25.4 else mm
    fun height(metres: Double): Double = if (heightInFeet) metres / 0.3048 else metres
    fun distance(km: Double): Double = if (distanceInMiles) km / 1.609344 else km

    // NIST SP 811: conventional inch of mercury = 3386.389 Pa.
    fun pressure(hpa: Double): Double = if (pressureInInHg) hpa / 33.86389 else hpa

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
        WeatherUnit.PRESSURE -> if (pressureInInHg) "inHg" else "hPa"
        WeatherUnit.PERCENT -> "%"
        WeatherUnit.SECONDS -> "s"
    }

    fun suffix(metricUnit: WeatherUnit): String =
        (if (metricUnit.separated) " " else "") + label(metricUnit)

    fun number(value: Double?, digits: Int = 0, locale: Locale = Locale.getDefault()): String {
        if (value == null || !value.isFinite()) return "—"
        val precision = digits.coerceIn(0, 6)
        val scale = decimalScales[precision]
        val safe = if (abs(value) < 0.5 / scale) 0.0 else value
        // Match the app's historical roundToInt convention without overflowing an Int.
        return if (abs(safe) >= 1e9) String.format(locale, "%.3g", safe)
        else if (precision == 0) String.format(locale, "%.0f", kotlin.math.floor(safe + 0.5))
        else String.format(locale, decimalPatterns[precision], safe)
    }

    fun value(value: Double?, metricUnit: WeatherUnit, digits: Int = 0,
              locale: Locale = Locale.getDefault(), delta: Boolean = false): String {
        if (value == null || !value.isFinite()) return "—"
        val precision = precision(metricUnit, digits)
        val converted = convert(value, metricUnit, delta)
        // Keep a nonzero trace visible instead of presenting rain as a dry value.
        // Only precipitation traces need the threshold (and a different format).
        if (metricUnit.isPrecipitation && !delta && value > 0.0) {
            val limit = 1.0 / decimalScales[precision]
            if (converted < limit / 2.0) return "<${number(limit, precision, locale)}"
        }
        return number(converted, precision, locale)
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
        unit.isPrecipitation -> maxOf(precipitationDigits, digits)
        pressureInInHg && unit == WeatherUnit.PRESSURE -> maxOf(2, digits)
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
