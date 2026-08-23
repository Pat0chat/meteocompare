package com.meteocompare.app.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meteocompare.app.domain.model.CityDetailSection
import com.meteocompare.app.domain.model.CityDetailContentTab
import com.meteocompare.app.domain.model.CityDetailViewMode
import com.meteocompare.app.domain.model.ForecastEngine
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
    private lateinit var context: Context
    private lateinit var repository: UserPreferencesRepositoryImpl

    @Before
    fun setUp() {
        runTest {
            context = ApplicationProvider.getApplicationContext()
            repository = UserPreferencesRepositoryImpl(context, Dispatchers.IO)
            repository.setEnabledModels(WeatherModel.MVP_SELECTION)
            repository.setThemePreference(ThemePreference.SYSTEM)
            repository.setLanguagePreference(LanguagePreference.SYSTEM)
            repository.setRefreshInterval(RefreshInterval.DEFAULT)
            repository.setForecastEngine(ForecastEngine.DEFAULT)
            listOf("paris", "lyon").forEach { cityId ->
                CityDetailSection.entries.forEach { section ->
                    repository.setCityDetailSectionCollapsed(cityId, section, collapsed = false)
                }
                repository.setCityDetailViewMode(cityId, CityDetailViewMode.DEFAULT)
                repository.setCityDetailContentTab(cityId, CityDetailContentTab.DEFAULT)
            }
        }
    }

    @Test
    fun all_preferences_round_trip() = runTest {
        repository.setEnabledModels(listOf(WeatherModel.GFS, WeatherModel.ECMWF))
        repository.setThemePreference(ThemePreference.DARK)
        repository.setLanguagePreference(LanguagePreference.ENGLISH)
        repository.setRefreshInterval(RefreshInterval.HOURS_3)
        repository.setForecastEngine(ForecastEngine.ADAPTIVE)

        assertEquals(
            listOf(WeatherModel.GFS, WeatherModel.ECMWF),
            repository.observeEnabledModels().first()
        )
        assertEquals(ThemePreference.DARK, repository.observeThemePreference().first())
        assertEquals(LanguagePreference.ENGLISH, repository.observeLanguagePreference().first())
        assertEquals(RefreshInterval.HOURS_3, repository.observeRefreshInterval().first())
        assertEquals(ForecastEngine.ADAPTIVE, repository.observeForecastEngine().first())
    }

    @Test
    fun empty_model_selection_falls_back_to_product_defaults() = runTest {
        repository.setEnabledModels(emptyList())
        assertEquals(WeatherModel.MVP_SELECTION, repository.observeEnabledModels().first())
    }

    @Test
    fun collapsed_city_detail_sections_round_trip_and_remain_city_specific() = runTest {
        repository.setCityDetailSectionCollapsed(
            cityId = "paris",
            section = CityDetailSection.CONFIDENCE,
            collapsed = true
        )
        repository.setCityDetailSectionCollapsed(
            cityId = "paris",
            section = CityDetailSection.WIND,
            collapsed = true
        )
        repository.setCityDetailSectionCollapsed(
            cityId = "paris",
            section = CityDetailSection.FORECAST_EVOLUTION,
            collapsed = true
        )
        repository.setCityDetailSectionCollapsed(
            cityId = "lyon",
            section = CityDetailSection.PRECIPITATION,
            collapsed = true
        )

        assertEquals(
            setOf(CityDetailSection.CONFIDENCE, CityDetailSection.WIND, CityDetailSection.FORECAST_EVOLUTION),
            repository.observeCollapsedCityDetailSections("paris").first()
        )
        assertEquals(
            setOf(CityDetailSection.PRECIPITATION),
            repository.observeCollapsedCityDetailSections("lyon").first()
        )

        // Simule une recréation du repository après redémarrage du process :
        // la nouvelle instance relit les mêmes valeurs depuis DataStore.
        val recreatedRepository = UserPreferencesRepositoryImpl(context, Dispatchers.IO)
        assertEquals(
            setOf(CityDetailSection.CONFIDENCE, CityDetailSection.WIND, CityDetailSection.FORECAST_EVOLUTION),
            recreatedRepository.observeCollapsedCityDetailSections("paris").first()
        )

        repository.setCityDetailSectionCollapsed(
            cityId = "paris",
            section = CityDetailSection.CONFIDENCE,
            collapsed = false
        )

        assertEquals(
            setOf(CityDetailSection.WIND, CityDetailSection.FORECAST_EVOLUTION),
            repository.observeCollapsedCityDetailSections("paris").first()
        )
    }
    @Test
    fun city_detail_view_choices_round_trip_and_remain_city_specific() = runTest {
        repository.setCityDetailViewMode("paris", CityDetailViewMode.HOURLY)
        repository.setCityDetailContentTab("paris", CityDetailContentTab.PRECIPITATION)
        repository.setCityDetailViewMode("lyon", CityDetailViewMode.DAILY)
        repository.setCityDetailContentTab("lyon", CityDetailContentTab.WIND)

        assertEquals(
            CityDetailViewMode.HOURLY,
            repository.observeCityDetailViewMode("paris").first()
        )
        assertEquals(
            CityDetailContentTab.PRECIPITATION,
            repository.observeCityDetailContentTab("paris").first()
        )
        assertEquals(
            CityDetailViewMode.DAILY,
            repository.observeCityDetailViewMode("lyon").first()
        )
        assertEquals(
            CityDetailContentTab.WIND,
            repository.observeCityDetailContentTab("lyon").first()
        )

        val recreatedRepository = UserPreferencesRepositoryImpl(context, Dispatchers.IO)
        assertEquals(
            CityDetailContentTab.PRECIPITATION,
            recreatedRepository.observeCityDetailContentTab("paris").first()
        )
    }

    @Test
    fun forecast_engine_survives_repository_recreation() = runTest {
        repository.setForecastEngine(ForecastEngine.SCENARIOS)
        val recreatedRepository = UserPreferencesRepositoryImpl(context, Dispatchers.IO)
        assertEquals(ForecastEngine.SCENARIOS, recreatedRepository.observeForecastEngine().first())
    }

}
