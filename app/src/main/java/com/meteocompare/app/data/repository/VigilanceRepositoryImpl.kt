package com.meteocompare.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meteocompare.app.core.locale.applyPersistedLocale
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.core.network.apiCall
import com.meteocompare.app.data.mapper.toDomain
import com.meteocompare.app.data.remote.MeteoCompareApi
import com.meteocompare.app.data.remote.dto.VigilanceCacheRecord
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.VigilanceForecast
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.VigilanceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vigilanceDataStore by preferencesDataStore(name = "vigilance_cache")

@Singleton
class VigilanceRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: MeteoCompareApi,
    private val cityRepository: CityRepository,
    private val json: Json,
    private val networkMonitor: NetworkMonitor,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : VigilanceRepository {

    private val safeCache = context.vigilanceDataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override suspend fun getVigilance(
        city: City,
        includeCoast: Boolean,
        forceRefresh: Boolean
    ): ApiResult<VigilanceForecast?> = withContext(ioDispatcher) {
        // Garde réseau absolu : aucune résolution de département et aucun appel au Worker
        // pour une ville non française. Les favoris legacy FR sont reconnus par City.
        if (!city.isFrenchLocation) return@withContext ApiResult.Success(null)

        val department = (city.departmentCode ?: cityRepository.resolveDepartmentCode(city))
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotEmpty() }
            ?: return@withContext ApiResult.Success(null)

        val now = clock.instant()
        val key = cacheKey(department, includeCoast)
        val cachedRecord = readCache(key)
        val cached = cachedRecord?.forecast
        val cachedAge = cachedRecord?.let { Duration.between(it.fetchedAt, now) }

        // La Vigilance Météo-France est limitée à un rafraîchissement réseau par heure
        // pour un même département/mode côte, y compris lors d'un refresh manuel global.
        if (cachedRecord != null && cachedAge != null && cachedAge <= FRESH_CACHE_AGE) {
            return@withContext ApiResult.Success(cached?.copy(evaluationTime = now))
        }

        if (!networkMonitor.isOnline()) {
            return@withContext cached?.takeIf { cachedAge != null && cachedAge <= MAX_STALE_AGE }
                ?.let { ApiResult.Success(it.copy(isStale = true, evaluationTime = now)) }
                ?: ApiResult.Success(null)
        }

        val localizedContext = applyPersistedLocale(context)
        when (val result = apiCall(localizedContext) {
            api.getVigilance(department = department, coast = if (includeCoast) 1 else null)
        }) {
            is ApiResult.Success -> {
                val response = result.data
                val domain = response.toDomain(fetchedAt = now)
                context.vigilanceDataStore.edit { prefs ->
                    prefs[key] = json.encodeToString(
                        VigilanceCacheRecord.serializer(),
                        VigilanceCacheRecord(now.toEpochMilli(), response)
                    )
                }
                ApiResult.Success(domain)
            }
            is ApiResult.Error -> {
                cached?.takeIf { cachedAge != null && cachedAge <= MAX_STALE_AGE }
                    ?.let { ApiResult.Success(it.copy(isStale = true, evaluationTime = now)) }
                    ?: result
            }
        }
    }

    override suspend fun clearCacheForDepartment(departmentCode: String) = withContext(ioDispatcher) {
        val normalized = departmentCode.trim().uppercase()
        if (normalized.isEmpty()) return@withContext

        // Le cache Vigilance est partagé par département, pas par ville. Ne jamais
        // le purger tant qu'un autre favori peut encore l'utiliser. Pour un ancien
        // favori FR dont le département n'est pas encore résolu, on conserve le
        // cache par prudence : on ne peut pas prouver qu'il appartient à un autre
        // département.
        val remainingFavorites = cityRepository.observeFavorites().first()
        if (isVigilanceDepartmentStillUsed(normalized, remainingFavorites)) {
            return@withContext
        }

        try {
            context.vigilanceDataStore.edit { prefs ->
                prefs.remove(cacheKey(normalized, includeCoast = false))
                prefs.remove(cacheKey(normalized, includeCoast = true))
            }
        } catch (_: IOException) {
            // Nettoyage best-effort : la suppression de la ville ne doit jamais échouer
            // à cause d'une erreur ponctuelle d'écriture du cache Vigilance.
        }
    }

    private suspend fun readCache(
        key: androidx.datastore.preferences.core.Preferences.Key<String>
    ): CachedVigilance? {
        val raw = safeCache.first()[key] ?: return null
        val record = runCatching {
            json.decodeFromString(VigilanceCacheRecord.serializer(), raw)
        }.getOrNull() ?: return null
        val fetchedAt = java.time.Instant.ofEpochMilli(record.fetchedAtEpochMs)
        return CachedVigilance(
            fetchedAt = fetchedAt,
            forecast = record.response.toDomain(fetchedAt = fetchedAt)
        )
    }

    private data class CachedVigilance(
        val fetchedAt: java.time.Instant,
        val forecast: VigilanceForecast?
    )

    private fun cacheKey(department: String, includeCoast: Boolean) =
        stringPreferencesKey("vigilance_${department}_${if (includeCoast) "coast" else "department"}")

    companion object {
        internal val FRESH_CACHE_AGE: Duration = Duration.ofHours(1)
        internal val MAX_STALE_AGE: Duration = Duration.ofHours(1)
    }
}

internal fun isVigilanceDepartmentStillUsed(
    departmentCode: String,
    favorites: List<City>
): Boolean {
    val normalized = departmentCode.trim().uppercase()
    if (normalized.isEmpty()) return false

    return favorites.any { city ->
        if (!city.isFrenchLocation) return@any false
        val cityDepartment = city.departmentCode
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotEmpty() }

        // Un favori FR legacy non résolu peut potentiellement être dans le même
        // département : conserver le cache jusqu'à ce que sa résolution soit connue.
        cityDepartment == null || cityDepartment == normalized
    }
}
