package com.meteocompare.app.core.units

/** Canonical input quantity. Symbols are resolved only by [WeatherUnits]. */
enum class WeatherUnit {
    TEMPERATURE, TEMPERATURE_COMPACT, WIND_SPEED, PRECIPITATION, PRECIPITATION_RATE,
    HEIGHT, DISTANCE, PRESSURE, PERCENT, SECONDS;

    val isTemperature: Boolean get() = this == TEMPERATURE || this == TEMPERATURE_COMPACT
    val isPrecipitation: Boolean get() = this == PRECIPITATION || this == PRECIPITATION_RATE
    val separated: Boolean get() = this != TEMPERATURE_COMPACT && this != PERCENT
}
