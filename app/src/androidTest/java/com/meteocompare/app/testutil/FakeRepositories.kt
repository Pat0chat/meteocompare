package com.meteocompare.app.testutil

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DayNormals
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ClimateNormalsRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeCityRepository @Inject constructor() : CityRepository {
    private val favorites = MutableStateFlow<List<City>>(emptyList())
    private val searchResults = MutableStateFlow<List<City>>(listOf(TestFixtures.paris, TestFixtures.lyon))
    var searchError: String? = null
    val searchQueries = mutableListOf<String>()

    override suspend fun searchCities(query: String): ApiResult<List<City>> {
        searchQueries += query
        searchError?.let { return ApiResult.Error(IllegalStateException(it), it) }
        return ApiResult.Success(
            searchResults.value.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.admin1?.contains(query, ignoreCase = true) == true
            }
        )
    }

    override fun observeFavorites(): Flow<List<City>> = favorites

    override suspend fun addFavorite(city: City) {
        favorites.value = (favorites.value + city).distinctBy(City::id)
    }

    override suspend fun removeFavorite(cityId: String) {
        favorites.value = favorites.value.filterNot { it.id == cityId }
    }

    fun setFavorites(cities: List<City>) {
        favorites.value = cities
    }

    fun setSearchResults(cities: List<City>) {
        searchResults.value = cities
    }

    fun reset() {
        favorites.value = emptyList()
        searchResults.value = listOf(TestFixtures.paris, TestFixtures.lyon)
        searchError = null
        searchQueries.clear()
    }
}

@Singleton
class FakeForecastRepository @Inject constructor() : ForecastRepository {
    private val streams = ConcurrentHashMap<String, MutableStateFlow<ApiResult<CityForecast>>>()
    val clearedCityIds = mutableListOf<String>()
    val refreshRequests = mutableListOf<String>()

    override fun getCityForecastStream(
        city: City,
        models: List<WeatherModel>,
        forecastDays: Int,
        forceRefresh: Boolean,
        maxCacheAgeMs: Long?
    ): Flow<ApiResult<CityForecast>> = streamFor(city)

    override suspend fun refreshCityForecast(
        city: City,
        models: List<WeatherModel>,
        forecastDays: Int
    ): ApiResult<CityForecast> {
        refreshRequests += city.id
        return streamFor(city).value
    }

    override suspend fun clearCacheForCity(cityId: String) {
        clearedCityIds += cityId
        streams.remove(cityId)
    }

    fun setForecast(city: City, forecast: CityForecast = TestFixtures.forecast(city)) {
        streamFor(city).value = ApiResult.Success(forecast)
    }

    fun setError(city: City, message: String) {
        streamFor(city).value = ApiResult.Error(IllegalStateException(message), message)
    }

    fun reset() {
        streams.clear()
        clearedCityIds.clear()
        refreshRequests.clear()
    }

    private fun streamFor(city: City): MutableStateFlow<ApiResult<CityForecast>> =
        streams.getOrPut(city.id) { MutableStateFlow(ApiResult.Success(TestFixtures.forecast(city))) }
}

@Singleton
class FakeUserPreferencesRepository @Inject constructor() : UserPreferencesRepository {
    private val models = MutableStateFlow(WeatherModel.MVP_SELECTION)
    private val theme = MutableStateFlow(ThemePreference.SYSTEM)
    private val language = MutableStateFlow(LanguagePreference.SYSTEM)
    private val refresh = MutableStateFlow(RefreshInterval.DEFAULT)

    override fun observeEnabledModels(): Flow<List<WeatherModel>> = models
    override suspend fun setEnabledModels(models: List<WeatherModel>) { this.models.value = models }
    override fun observeThemePreference(): Flow<ThemePreference> = theme
    override suspend fun setThemePreference(preference: ThemePreference) { theme.value = preference }
    override fun observeLanguagePreference(): Flow<LanguagePreference> = language
    override suspend fun setLanguagePreference(preference: LanguagePreference) { language.value = preference }
    override fun observeRefreshInterval(): Flow<RefreshInterval> = refresh
    override suspend fun setRefreshInterval(interval: RefreshInterval) { refresh.value = interval }

    fun reset() {
        models.value = WeatherModel.MVP_SELECTION
        theme.value = ThemePreference.SYSTEM
        language.value = LanguagePreference.SYSTEM
        refresh.value = RefreshInterval.DEFAULT
    }
}

@Singleton
class FakeClimateNormalsRepository @Inject constructor() : ClimateNormalsRepository {
    var result: ApiResult<List<DayNormals>> = ApiResult.Success(emptyList())
    override suspend fun getNormalsForCity(city: City): ApiResult<List<DayNormals>> = result
    fun reset() { result = ApiResult.Success(emptyList()) }
}

@Singleton
class FakeBiasSampleRepository @Inject constructor() : BiasSampleRepository {
    override fun observeSamples(
        cityId: String,
        model: WeatherModel,
        variable: BiasVariable,
        windowDays: Int
    ): Flow<List<BiasSample>> = flowOf(emptyList())

    override suspend fun recordForecast(
        cityId: String,
        model: WeatherModel,
        variable: BiasVariable,
        targetDate: LocalDate,
        issuedAt: Instant,
        value: Double
    ) = Unit

    override suspend fun recordObservation(
        cityId: String,
        variable: BiasVariable,
        targetDate: LocalDate,
        value: Double
    ) = Unit

    override suspend fun latestObservationDate(cityId: String, variable: BiasVariable): LocalDate? = null
    override suspend fun countPastForecastSamples(
        cityId: String,
        model: WeatherModel,
        beforeDate: LocalDate
    ): Int = 0
    override suspend fun purgeOlderThan(beforeDate: LocalDate) = Unit
}
