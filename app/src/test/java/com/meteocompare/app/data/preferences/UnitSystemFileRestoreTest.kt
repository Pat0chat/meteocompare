package com.meteocompare.app.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.meteocompare.app.domain.model.UnitSystem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UnitSystemFileRestoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `offline saved preference survives store recreation and byte-for-byte backup restore`() = runBlocking {
        val original = folder.newFolder("original").resolve("user_prefs.preferences_pb")
        val restored = folder.newFolder("restored").resolve("user_prefs.preferences_pb")
        val originalJob = SupervisorJob()
        val originalStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + originalJob), produceFile = { original })
        try {
            assertEquals(UnitSystem.METRIC, UnitSystemPreferenceCodec.read(originalStore.data.first()))
            originalStore.edit {
                it[UnitSystemPreferenceCodec.key] = UnitSystem.IMPERIAL.storageKey
                it[stringPreferencesKey("forecast_engine")] = "SCENARIOS"
            }
        } finally {
            originalJob.cancelAndJoin()
        }
        original.copyTo(restored)
        val restoredJob = SupervisorJob()
        val restoredStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + restoredJob), produceFile = { restored })
        try {
            val preferences = restoredStore.data.first()
            assertEquals(UnitSystem.IMPERIAL, UnitSystemPreferenceCodec.read(preferences))
            assertEquals("SCENARIOS", preferences[stringPreferencesKey("forecast_engine")])
            restoredStore.edit { it[UnitSystemPreferenceCodec.key] = "invalid" }
            assertEquals(UnitSystem.METRIC, UnitSystemPreferenceCodec.read(restoredStore.data.first()))
            restoredStore.edit { it[UnitSystemPreferenceCodec.key] = UnitSystem.METRIC.storageKey }
            assertEquals(UnitSystem.METRIC, UnitSystemPreferenceCodec.read(restoredStore.data.first()))
        } finally {
            restoredJob.cancelAndJoin()
        }
    }
}
