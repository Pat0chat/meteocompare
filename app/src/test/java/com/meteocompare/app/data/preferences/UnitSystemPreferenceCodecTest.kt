package com.meteocompare.app.data.preferences

import androidx.datastore.preferences.core.*
import com.meteocompare.app.domain.model.UnitSystem
import org.junit.Assert.*
import org.junit.Test

class UnitSystemPreferenceCodecTest {
    @Test fun `legacy and malformed preferences fall back to metric without altering other keys`() {
        val unrelated = stringPreferencesKey("forecast_engine")
        val snapshots = listOf(
            emptyPreferences(),
            preferencesOf(unrelated to "adaptive"),
            preferencesOf(UnitSystemPreferenceCodec.key to ""),
            preferencesOf(UnitSystemPreferenceCodec.key to "unknown-future-format"),
            preferencesOf(UnitSystemPreferenceCodec.key to "IMPERIAL"),
            preferencesOf(intPreferencesKey("unit_system") to 42),
            preferencesOf(booleanPreferencesKey("unit_system") to true)
        )
        for (snapshot in snapshots) {
            val before = snapshot.asMap().toMap()
            assertEquals(UnitSystem.METRIC, UnitSystemPreferenceCodec.read(snapshot))
            assertEquals(before, snapshot.asMap())
        }
    }

    @Test fun `restored choices are independent of locale engine and theme`() {
        for (system in UnitSystem.entries) {
            val snapshot = preferencesOf(UnitSystemPreferenceCodec.key to system.storageKey,
                stringPreferencesKey("language") to "en-US",
                stringPreferencesKey("forecast_engine") to "scenarios",
                stringPreferencesKey("theme") to "dark")
            assertEquals(system, UnitSystemPreferenceCodec.read(snapshot.toMutablePreferences().toPreferences()))
        }
    }
}
