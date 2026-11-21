package com.meteocompare.app.data.preferences

import androidx.datastore.preferences.core.*
import com.meteocompare.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class CustomUnitPreferenceCodecTest {
    @Test fun `each custom dimension survives an independent preferences round trip`() {
        val saved = CustomUnitPreferences(
            TemperatureChoice.FAHRENHEIT, WindChoice.KNOTS, PrecipitationChoice.INCHES,
            PressureChoice.INHG, HeightChoice.FEET, DistanceChoice.MILES
        )
        val preferences = emptyPreferences().toMutablePreferences()
        CustomUnitPreferenceCodec.write(preferences, saved)
        assertEquals(saved, CustomUnitPreferenceCodec.read(preferences.toPreferences()))
        assertEquals(UnitSystem.METRIC, UnitSystemPreferenceCodec.read(preferences.toPreferences()))
    }

    @Test fun `bad and missing preferences fall back independently without changing snapshots`() {
        val prefs = preferencesOf(
            stringPreferencesKey("custom_unit_temperature") to "FAHRENHEIT",
            stringPreferencesKey("custom_unit_wind") to "future-wind-unit",
            intPreferencesKey("custom_unit_precipitation") to 25,
            stringPreferencesKey("custom_unit_height") to "FEET"
        )
        val unchanged = prefs.asMap().toMap()
        assertEquals(CustomUnitPreferences(temperature = TemperatureChoice.FAHRENHEIT,
            height = HeightChoice.FEET), CustomUnitPreferenceCodec.read(prefs))
        assertEquals(unchanged, prefs.asMap())
        assertEquals(CustomUnitPreferences(), CustomUnitPreferenceCodec.read(emptyPreferences()))
    }
}
