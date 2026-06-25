package com.meteocompare.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.apiCall
import com.meteocompare.app.data.mapper.toDomain
import com.meteocompare.app.data.remote.GeocodingApi
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.repository.CityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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
    @ApplicationContext private val context: Context,
    private val geocodingApi: GeocodingApi,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CityRepository {

    private val cityListSerializer = ListSerializer(City.serializer())

    override suspend fun searchCities(query: String): ApiResult<List<City>> =
        withContext(ioDispatcher) {
            if (query.length < 2) {
                return@withContext ApiResult.Success(emptyList())
            }
            apiCall {
                geocodingApi.search(name = query)
                    .results
                    .orEmpty()
                    .map { it.toDomain() }
            }
        }

    override fun observeFavorites(): Flow<List<City>> =
        context.favoritesDataStore.data.map { prefs ->
            val raw = prefs[FAVORITES_KEY] ?: return@map emptyList()
            runCatching {
                json.decodeFromString(cityListSerializer, raw)
            }.getOrDefault(emptyList())
        }

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
