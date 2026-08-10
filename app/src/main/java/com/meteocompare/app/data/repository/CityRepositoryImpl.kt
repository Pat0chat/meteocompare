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
import com.meteocompare.app.data.mapper.toDomain
import com.meteocompare.app.data.remote.GeocodingApi
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.repository.CityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.favoritesDataStore by preferencesDataStore(name = "favorites")
private val FAVORITES_KEY = stringPreferencesKey("cities")

/**
 * Stockage des favoris : sérialisation JSON dans une seule clé DataStore Preferences.
 *
 * Pour un MVP ce choix est délibérément simple : pas de Room, pas de migrations,
 * pas de DAO. Tant qu'on reste sous ~100 favoris ça tient sans problème.
 * On migrera vers Room le jour où on aura besoin de requêter (recherche, tri, etc.).
 */
@Singleton
class CityRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val geocodingApi: GeocodingApi,
    private val json: Json,
    private val networkMonitor: NetworkMonitor,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CityRepository {

    private val safeFavorites = context.favoritesDataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    private val cityListSerializer = ListSerializer(City.serializer())

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
        safeFavorites.map { prefs ->
            val raw = prefs[FAVORITES_KEY] ?: return@map emptyList()
            runCatching {
                json.decodeFromString(cityListSerializer, raw)
            }.getOrDefault(emptyList())
        }
            .distinctUntilChanged()
            .flowOn(ioDispatcher)

    override suspend fun addFavorite(city: City) = withContext(ioDispatcher) {
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

    override suspend fun removeFavorite(cityId: String) = withContext(ioDispatcher) {
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[FAVORITES_KEY]
                ?.let { runCatching { json.decodeFromString(cityListSerializer, it) }.getOrDefault(emptyList()) }
                ?: emptyList()
            prefs[FAVORITES_KEY] = json.encodeToString(cityListSerializer, current.filterNot { it.id == cityId })
        }
        Unit
    }
}
