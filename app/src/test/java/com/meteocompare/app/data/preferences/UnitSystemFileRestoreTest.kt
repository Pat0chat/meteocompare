package com.meteocompare.app.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.meteocompare.app.domain.model.UnitSystem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.FileSystem
import okio.Path.Companion.toOkioPath
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
        val originalBytes = original.readBytes()
        original.copyTo(restored)
        assertArrayEquals(originalBytes, restored.readBytes())
        val restoredJob = SupervisorJob()
        // The Android artifact's default FileStorage reads the stub SDK_INT in local
        // JVM tests and falls back to File.renameTo. Replacing an existing file then
        // fails on Windows. Okio uses the host filesystem's replacement operation;
        // the real preferences protobuf serializer and DataStore lifecycle are retained.
        val restoredStore = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                restored.absoluteFile.toOkioPath()
            },
            scope = CoroutineScope(Dispatchers.IO + restoredJob))
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
        // A new default FileStorage instance must read the bytes written by Okio.
        // This also verifies persistence rather than only DataStore's in-memory value.
        val reopenedJob = SupervisorJob()
        val reopenedStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + reopenedJob), produceFile = { restored })
        try {
            val persisted = reopenedStore.data.first()
            assertEquals(UnitSystem.METRIC.storageKey, persisted[UnitSystemPreferenceCodec.key])
            assertEquals("SCENARIOS", persisted[stringPreferencesKey("forecast_engine")])
            assertArrayEquals(originalBytes, original.readBytes())
            assertFalse(originalBytes.contentEquals(restored.readBytes()))
        } finally {
            reopenedJob.cancelAndJoin()
        }
    }
}
