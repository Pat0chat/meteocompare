package com.meteocompare.app.data.preferences

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.meteocompare.app.domain.model.*

/** Independent version-tolerant keys: invalid/missing choices revert only their own dimension. */
internal object CustomUnitPreferenceCodec {
    private val temperature = stringPreferencesKey("custom_unit_temperature")
    private val wind = stringPreferencesKey("custom_unit_wind")
    private val precipitation = stringPreferencesKey("custom_unit_precipitation")
    private val pressure = stringPreferencesKey("custom_unit_pressure")
    private val height = stringPreferencesKey("custom_unit_height")
    private val distance = stringPreferencesKey("custom_unit_distance")

    private inline fun <reified T : Enum<T>> choice(prefs: Preferences, key: Preferences.Key<String>, default: T): T =
        try { enumValues<T>().firstOrNull { it.name == prefs[key] } ?: default }
        catch (_: ClassCastException) { default }

    fun read(prefs: Preferences): CustomUnitPreferences = CustomUnitPreferences(
        temperature = choice(prefs, temperature, TemperatureChoice.CELSIUS),
        wind = choice(prefs, wind, WindChoice.KMH),
        precipitation = choice(prefs, precipitation, PrecipitationChoice.MM),
        pressure = choice(prefs, pressure, PressureChoice.HPA),
        height = choice(prefs, height, HeightChoice.METRES),
        distance = choice(prefs, distance, DistanceChoice.KM)
    )

    fun write(prefs: MutablePreferences, choices: CustomUnitPreferences) {
        prefs[temperature] = choices.temperature.name
        prefs[wind] = choices.wind.name
        prefs[precipitation] = choices.precipitation.name
        prefs[pressure] = choices.pressure.name
        prefs[height] = choices.height.name
        prefs[distance] = choices.distance.name
    }
}
