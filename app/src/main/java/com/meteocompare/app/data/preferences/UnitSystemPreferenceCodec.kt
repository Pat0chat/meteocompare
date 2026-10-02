package com.meteocompare.app.data.preferences

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.meteocompare.app.domain.model.UnitSystem

/** Backward-compatible decoder for current preferences and Android-restored snapshots. */
internal object UnitSystemPreferenceCodec {
    val key = stringPreferencesKey("unit_system")

    fun read(preferences: Preferences): UnitSystem = try {
        UnitSystem.fromStorage(preferences[key])
    } catch (_: ClassCastException) {
        // A malformed/older snapshot may have stored this key with another type.
        UnitSystem.METRIC
    }
}
