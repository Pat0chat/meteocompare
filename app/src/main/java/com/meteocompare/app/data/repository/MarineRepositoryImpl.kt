package com.meteocompare.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.applyPersistedLocale
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.core.network.apiCall
import com.meteocompare.app.data.mapper.toDomain
import com.meteocompare.app.data.remote.MarineApi
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.MarineForecast
import com.meteocompare.app.domain.repository.MarineRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private val Context.marineDataStore by preferencesDataStore(name = "marine_cache")

@Singleton
class MarineRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: MarineApi,
    private val json: Json,
    private val networkMonitor: NetworkMonitor,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : MarineRepository {

    override suspend fun getMarine(city: City, forceRefresh: Boolean): ApiResult<MarineForecast> =
        withContext(ioDispatcher) {
            val cached = getCached(city.id)
            if (!forceRefresh && cached != null && isFresh(cached)) {
                return@withContext ApiResult.Success(cached)
            }
            val localizedContext = applyPersistedLocale(context)
            if (!networkMonitor.isOnline()) {
                // La page détail peut relire un cache même expiré hors ligne,
                // mais une activation / actualisation forcée exige une validation réseau fraîche.
                if (!forceRefresh && cached != null) return@withContext ApiResult.Success(cached)
                return@withContext ApiResult.Error(
                    IOException("No network"),
                    localizedContext.getString(R.string.error_no_network)
                )
            }
            val result = apiCall(localizedContext) {
                api.getMarine(
                    latitude = city.latitude,
                    longitude = city.longitude,
                    timezone = city.timezone?.takeIf { tz ->
                        runCatching { ZoneId.of(tz) }.isSuccess
                    } ?: "auto"
                ).toDomain(city)
            }
            // La disponibilité côtière est elle aussi mise en cache : la Home
            // peut afficher sa pastille sans revalider une ville intérieure à
            // chaque ouverture. Une activation explicite utilise forceRefresh
            // et contourne donc toujours cette décision mise en cache.
            if (result is ApiResult.Success) save(city.id, result.data)
            result
        }

    override suspend fun getCached(cityId: String): MarineForecast? = withContext(ioDispatcher) {
        val raw = context.marineDataStore.data.first()[key(cityId)] ?: return@withContext null
        runCatching { json.decodeFromString(MarineForecast.serializer(), raw) }.getOrNull()
    }

    override suspend fun clear(cityId: String) = withContext(ioDispatcher) {
        context.marineDataStore.edit { it.remove(key(cityId)) }
        Unit
    }

    private suspend fun save(cityId: String, data: MarineForecast) {
        context.marineDataStore.edit { prefs ->
            prefs[key(cityId)] = json.encodeToString(MarineForecast.serializer(), data)
        }
    }

    private fun key(cityId: String) = stringPreferencesKey("marine_$cityId")
    private fun isFresh(data: MarineForecast) = System.currentTimeMillis() - data.fetchedAtEpochMs < CACHE_TTL_MS

    companion object {
        const val CACHE_TTL_MS = 6 * 3_600_000L
    }
}
