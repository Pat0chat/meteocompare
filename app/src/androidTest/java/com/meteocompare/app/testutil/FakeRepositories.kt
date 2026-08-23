package com.meteocompare.app.testutil

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityDetailSection
import com.meteocompare.app.domain.model.CityDetailContentTab
import com.meteocompare.app.domain.model.CityDetailViewMode
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DayNormals
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.MarineForecast
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ClimateNormalsRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.ForecastEvolutionRepository
import com.meteocompare.app.domain.repository.ForecastEvolutionHistoryData
import com.meteocompare.app.domain.repository.MarineRepository
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

    override suspend fun setMarineEnabled(cityId: String, enabled: Boolean) {
        favorites.value = favorites.value.map { city ->
            if (city.id == cityId) city.copy(marineEnabled = enabled) else city
        }
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
class FakeMarineRepository @Inject constructor() : MarineRepository {
    private val cache = ConcurrentHashMap<String, MarineForecast>()
    var nextResult: ApiResult<MarineForecast>? = null

    override suspend fun getMarine(city: City, forceRefresh: Boolean): ApiResult<MarineForecast> =
        nextResult ?: cache[city.id]?.let { ApiResult.Success(it) }
        ?: ApiResult.Error(IllegalStateException("marine unavailable"), "marine unavailable")

    override suspend fun getCached(cityId: String): MarineForecast? = cache[cityId]
    override suspend fun getFreshCached(cityId: String): MarineForecast? = cache[cityId]

    override suspend fun clear(cityId: String) { cache.remove(cityId) }

    fun set(cityId: String, data: MarineForecast) { cache[cityId] = data }
    fun reset() { cache.clear(); nextResult = null }
}

@Singleton
class FakeForecastEvolutionRepository @Inject constructor() : ForecastEvolutionRepository {
    var result: ApiResult<ForecastEvolutionHistoryData> = ApiResult.Success(
        ForecastEvolutionHistoryData(
            samples = emptyList(),
            oldestSnapshotAt = null
        )
    )

    val requests = mutableListOf<String>()

    override suspend fun getPreviousForecasts(
        city: City,
        models: List<WeatherModel>,
        startDate: LocalDate,
        endDate: LocalDate,
        referenceAt: java.time.Instant
    ): ApiResult<ForecastEvolutionHistoryData> {
        requests += city.id
        return result
    }

    fun reset() {
        result = ApiResult.Success(
            ForecastEvolutionHistoryData(
                samples = emptyList(),
                oldestSnapshotAt = null
            )
        )
        requests.clear()
    }
}

@Singleton
class FakeUserPreferencesRepository @Inject constructor() : UserPreferencesRepository {
    private val models = MutableStateFlow(WeatherModel.MVP_SELECTION)
    private val theme = MutableStateFlow(ThemePreference.SYSTEM)
    private val language = MutableStateFlow(LanguagePreference.SYSTEM)
    private val refresh = MutableStateFlow(RefreshInterval.DEFAULT)
    private val forecastEngine = MutableStateFlow(ForecastEngine.DEFAULT)
    private val collapsedSectionsByCity =
        ConcurrentHashMap<String, MutableStateFlow<Set<CityDetailSection>>>()
    private val viewModeByCity =
        ConcurrentHashMap<String, MutableStateFlow<CityDetailViewMode>>()
    private val contentTabByCity =
        ConcurrentHashMap<String, MutableStateFlow<CityDetailContentTab>>()

    override fun observeEnabledModels(): Flow<List<WeatherModel>> = models
    override suspend fun setEnabledModels(models: List<WeatherModel>) { this.models.value = models }
    override fun observeThemePreference(): Flow<ThemePreference> = theme
    override suspend fun setThemePreference(preference: ThemePreference) { theme.value = preference }
    override fun observeLanguagePreference(): Flow<LanguagePreference> = language
    override suspend fun setLanguagePreference(preference: LanguagePreference) { language.value = preference }
    override fun observeRefreshInterval(): Flow<RefreshInterval> = refresh
    override suspend fun setRefreshInterval(interval: RefreshInterval) { refresh.value = interval }
    override fun observeForecastEngine(): Flow<ForecastEngine> = forecastEngine
    override suspend fun setForecastEngine(engine: ForecastEngine) { forecastEngine.value = engine }

    override fun observeCollapsedCityDetailSections(
        cityId: String
    ): Flow<Set<CityDetailSection>> = collapsedFlow(cityId)

    override suspend fun setCityDetailSectionCollapsed(
        cityId: String,
        section: CityDetailSection,
        collapsed: Boolean
    ) {
        val flow = collapsedFlow(cityId)
        flow.value = if (collapsed) {
            flow.value + section
        } else {
            flow.value - section
        }
    }

    private fun collapsedFlow(cityId: String): MutableStateFlow<Set<CityDetailSection>> =
        collapsedSectionsByCity.getOrPut(cityId) { MutableStateFlow(emptySet()) }

    override fun observeCityDetailViewMode(cityId: String): Flow<CityDetailViewMode> =
        viewModeByCity.getOrPut(cityId) { MutableStateFlow(CityDetailViewMode.DEFAULT) }

    override suspend fun setCityDetailViewMode(cityId: String, mode: CityDetailViewMode) {
        viewModeByCity.getOrPut(cityId) { MutableStateFlow(CityDetailViewMode.DEFAULT) }.value = mode
    }

    override fun observeCityDetailContentTab(cityId: String): Flow<CityDetailContentTab> =
        contentTabByCity.getOrPut(cityId) { MutableStateFlow(CityDetailContentTab.DEFAULT) }

    override suspend fun setCityDetailContentTab(cityId: String, tab: CityDetailContentTab) {
        contentTabByCity.getOrPut(cityId) { MutableStateFlow(CityDetailContentTab.DEFAULT) }.value = tab
    }

    fun reset() {
        models.value = WeatherModel.MVP_SELECTION
        theme.value = ThemePreference.SYSTEM
        language.value = LanguagePreference.SYSTEM
        refresh.value = RefreshInterval.DEFAULT
        forecastEngine.value = ForecastEngine.DEFAULT
        collapsedSectionsByCity.clear()
        viewModeByCity.clear()
        contentTabByCity.clear()
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
        asOf: LocalDate,
        timezone: String?,
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

    override suspend fun earliestMissingReferenceDate(
        cityId: String,
        upToDate: LocalDate
    ): LocalDate? = null
    override suspend fun purgeOlderThan(beforeDate: LocalDate) = Unit
}
