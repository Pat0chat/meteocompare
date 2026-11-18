package com.meteocompare.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.applyPersistedLocale
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.core.network.apiCall
import com.meteocompare.app.core.util.runSuspendCatching
import com.meteocompare.app.data.mapper.toDomain
import com.meteocompare.app.domain.util.FavoriteCityOrder
import com.meteocompare.app.data.remote.GeocodingApi
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.repository.CityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.favoritesDataStore by preferencesDataStore(name = "favorites")
private val FAVORITES_KEY = stringPreferencesKey("cities")
private val HOME_ORDER_KEY = stringPreferencesKey("home_city_order")

/**
 * Stockage des favoris : sérialisation JSON dans une seule clé DataStore Preferences.
 *
 * Les données des favoris restent inchangées par le classement : une préférence
 * indépendante conserve les identifiants dans l'ordre choisi sur Home.
 * Aucun changement de schéma ou de migration n'est nécessaire.
 */
@Singleton
class CityRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val geocodingApi: GeocodingApi,
    private val json: Json,
    private val networkMonitor: NetworkMonitor,
    private val locationStore: CurrentLocationStore,
    private val locationController: CurrentLocationController,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CityRepository {

    private val safeFavorites = context.favoritesDataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    private val cityListSerializer = ListSerializer(City.serializer())
    private val orderSerializer = ListSerializer(String.serializer())
    /** Déduplique l'enrichissement d'un même favori lancé par Home et Détails. */
    private val departmentResolutionLocks = ConcurrentHashMap<String, Mutex>()

    override suspend fun searchCities(query: String): ApiResult<List<City>> =
        withContext(ioDispatcher) {
            val normalizedQuery = query.trim()
            if (normalizedQuery.length < 2) {
                return@withContext ApiResult.Success(emptyList())
            }
            // La recherche doit suivre la langue choisie dans l'app, pas la
            // locale brute de l'ApplicationContext (qui peut rester système).
            val localizedContext = applyPersistedLocale(context)

            // Court-circuit hors-ligne — évite un timeout 30s sur chaque keystroke
            // déclenchant la recherche debounced.
            if (!networkMonitor.isOnline()) {
                return@withContext ApiResult.Error(
                    IOException("No network"),
                    localizedContext.getString(R.string.error_no_network)
                )
            }
            apiCall(localizedContext) {
                val locale = localizedContext.resources.configuration.locales[0]
                geocodingApi.search(
                    name = normalizedQuery,
                    language = locale.language.takeIf { it.isNotBlank() } ?: "en"
                )
                    .results
                    .orEmpty()
                    .map { it.toDomain() }
            }
        }

    override fun observeFavorites(): Flow<List<City>> =
        combine(safeFavorites, locationStore.state) { prefs, location ->
            val favorites = prefs[FAVORITES_KEY]?.let { raw ->
                runCatching { json.decodeFromString(cityListSerializer, raw) }
                    .getOrDefault(emptyList())
            }.orEmpty()
            val current = location.asCity(context)
            val allCities = if (current == null) favorites else favorites + current
            val savedOrder = prefs[HOME_ORDER_KEY]?.let { raw ->
                runCatching { json.decodeFromString(orderSerializer, raw) }.getOrDefault(emptyList())
            }.orEmpty()
            FavoriteCityOrder.apply(allCities, savedOrder, City::id)
        }
            .distinctUntilChanged()
            .flowOn(ioDispatcher)

    override suspend fun addFavorite(city: City) = withContext(ioDispatcher) {
        if (CurrentLocationStore.isCurrentCity(city.id)) return@withContext
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[FAVORITES_KEY]
                ?.let { runCatching { json.decodeFromString(cityListSerializer, it) }.getOrDefault(emptyList()) }
                ?: emptyList()
            if (current.none { it.id == city.id }) {
                prefs[FAVORITES_KEY] = json.encodeToString(cityListSerializer, current + city)
            }
        }
        Unit
    }

    override suspend fun reorderFavorites(cityIds: List<String>) = withContext(ioDispatcher) {
        // Reordering never modifies favorites themselves: only an independent,
        // stable ID list. The GPS position uses a canonical marker, not a grid ID.
        val currentLocation = locationStore.state.first().asCity(context)
        context.favoritesDataStore.edit { prefs ->
            val favorites = prefs[FAVORITES_KEY]
                ?.let { runCatching { json.decodeFromString(cityListSerializer, it) }.getOrDefault(emptyList()) }
                .orEmpty()
            val available = favorites.map(City::id) + listOfNotNull(currentLocation?.id)
            val requested = FavoriteCityOrder.normalize(cityIds, available)
            prefs[HOME_ORDER_KEY] = json.encodeToString(orderSerializer, requested)
        }
        Unit
    }

    override suspend fun removeFavorite(cityId: String) = withContext(ioDispatcher) {
        if (CurrentLocationStore.isCurrentCity(cityId)) {
            locationController.disable()
            return@withContext
        }
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[FAVORITES_KEY]
                ?.let { runCatching { json.decodeFromString(cityListSerializer, it) }.getOrDefault(emptyList()) }
                ?: emptyList()
            prefs[FAVORITES_KEY] = json.encodeToString(cityListSerializer, current.filterNot { it.id == cityId })
            // An explicitly deleted favorite must not regain its old position
            // if the user chooses to add it back later.
            prefs[HOME_ORDER_KEY]?.let { raw ->
                val saved = runCatching { json.decodeFromString(orderSerializer, raw) }.getOrNull()
                if (saved != null) {
                    prefs[HOME_ORDER_KEY] = json.encodeToString(
                        orderSerializer, saved.filterNot { it == cityId }
                    )
                }
            }
        }
        Unit
    }

    override suspend fun setMarineEnabled(cityId: String, enabled: Boolean) = withContext(ioDispatcher) {
        if (CurrentLocationStore.isCurrentCity(cityId)) return@withContext
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[FAVORITES_KEY]
                ?.let { runCatching { json.decodeFromString(cityListSerializer, it) }.getOrDefault(emptyList()) }
                ?: emptyList()
            val updated = current.map { city ->
                if (city.id == cityId) city.copy(marineEnabled = enabled) else city
            }
            prefs[FAVORITES_KEY] = json.encodeToString(cityListSerializer, updated)
        }
        Unit
    }

    override suspend fun resolveDepartmentCode(city: City): String? = withContext(ioDispatcher) {
        if (CurrentLocationStore.isCurrentCity(city.id)) return@withContext null
        city.departmentCode?.let { return@withContext it }
        // Cette résolution n'est utilisée que par la Vigilance. Ne jamais appeler
        // Open-Meteo pour enrichir une ville dont on sait déjà qu'elle n'est pas française.
        if (!city.isFrenchLocation) return@withContext null

        val id = city.id.toLongOrNull() ?: return@withContext null
        if (!networkMonitor.isOnline()) return@withContext null

        departmentResolutionLocks.getOrPut(city.id) { Mutex() }.withLock {
            // Un appel concurrent a peut-être déjà enrichi le favori pendant
            // l'attente du verrou. Relire la source persistée avant le réseau.
            val persistedDepartment = safeFavorites.first()[FAVORITES_KEY]
                ?.let { raw ->
                    runCatching { json.decodeFromString(cityListSerializer, raw) }
                        .getOrDefault(emptyList())
                }
                ?.firstOrNull { it.id == city.id }
                ?.departmentCode
            if (!persistedDepartment.isNullOrBlank()) return@withLock persistedDepartment

            // Le réseau peut avoir disparu pendant l'attente du verrou.
            if (!networkMonitor.isOnline()) return@withLock null

            val localizedContext = applyPersistedLocale(context)
            val locale = localizedContext.resources.configuration.locales[0]
            val resolved = runSuspendCatching {
                geocodingApi.get(
                    id = id,
                    language = locale.language.takeIf { it.isNotBlank() } ?: "fr"
                ).toDomain()
            }.getOrNull() ?: return@withLock null

            val code = resolved.departmentCode
            // L'enrichissement est un cache auxiliaire : sa persistance ne doit
            // pas annuler une résolution réseau déjà réussie. Une prochaine
            // reprise pourra retenter l'écriture si DataStore est indisponible.
            runSuspendCatching {
                context.favoritesDataStore.edit { prefs ->
                    val current = prefs[FAVORITES_KEY]
                        ?.let {
                            runCatching {
                                json.decodeFromString(cityListSerializer, it)
                            }.getOrDefault(emptyList())
                        }
                        ?: emptyList()
                    val updated = current.map { favorite ->
                        if (favorite.id == city.id) {
                            favorite.copy(
                                countryCode = resolved.countryCode ?: favorite.countryCode,
                                departmentName = resolved.departmentName ?: favorite.departmentName,
                                departmentCode = code ?: favorite.departmentCode,
                                timezone = resolved.timezone ?: favorite.timezone
                            )
                        } else {
                            favorite
                        }
                    }
                    prefs[FAVORITES_KEY] = json.encodeToString(cityListSerializer, updated)
                }
            }.onFailure { error ->
                android.util.Log.w(
                    "MeteoCompare/City",
                    "Unable to persist department for city=${city.id}",
                    error
                )
            }
            code
        }
    }
}
