package com.meteocompare.app.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UserPreferencesRepositoryImplTest {
    private lateinit var repository: UserPreferencesRepositoryImpl

    @Before
    fun setUp() {
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            repository = UserPreferencesRepositoryImpl(context, Dispatchers.IO)
            repository.setEnabledModels(WeatherModel.MVP_SELECTION)
            repository.setThemePreference(ThemePreference.SYSTEM)
            repository.setLanguagePreference(LanguagePreference.SYSTEM)
            repository.setRefreshInterval(RefreshInterval.DEFAULT)
        }
    }

    @Test
    fun all_preferences_round_trip() = runTest {
        repository.setEnabledModels(listOf(WeatherModel.GFS, WeatherModel.ECMWF))
        repository.setThemePreference(ThemePreference.DARK)
        repository.setLanguagePreference(LanguagePreference.ENGLISH)
        repository.setRefreshInterval(RefreshInterval.HOURS_3)

        assertEquals(
            listOf(WeatherModel.GFS, WeatherModel.ECMWF),
            repository.observeEnabledModels().first()
        )
        assertEquals(ThemePreference.DARK, repository.observeThemePreference().first())
        assertEquals(LanguagePreference.ENGLISH, repository.observeLanguagePreference().first())
        assertEquals(RefreshInterval.HOURS_3, repository.observeRefreshInterval().first())
    }

    @Test
    fun empty_model_selection_falls_back_to_product_defaults() = runTest {
        repository.setEnabledModels(emptyList())
        assertEquals(WeatherModel.MVP_SELECTION, repository.observeEnabledModels().first())
    }
}
