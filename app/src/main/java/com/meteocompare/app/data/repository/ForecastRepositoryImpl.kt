package com.meteocompare.app.data.repository

import android.content.Context
import com.meteocompare.app.R
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.core.network.toUserMessage
import com.meteocompare.app.data.local.ForecastCacheDao
import com.meteocompare.app.data.local.ForecastCacheEntity
import com.meteocompare.app.data.mapper.ForecastMapper
import com.meteocompare.app.data.remote.OpenMeteoApi
import com.meteocompare.app.data.remote.dto.ForecastResponseDto
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.ForecastRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository avec cache transparent via Room.
 *
 * Stratégie de cache :
 *
 *  ┌──────────────────────────────────────────────────────────────────────┐
 *  │  getCityForecastStream(city, forceRefresh=false)                     │
 *  │                                                                       │
 *  │   1. Lecture cache (synchrone, ~1 ms par modèle)                     │
 *  │   2. Si cache existe → emit Success(cached) immédiatement            │
 *  │   3. Fetch réseau en parallèle (5 modèles)                           │
 *  │   4. Si réseau OK → écriture cache + emit Success(fresh)             │
 *  │   5. Si réseau KO :                                                  │
 *  │      - cache existait → ne pas émettre d'erreur (user voit le cache) │
 *  │      - sinon → emit Error                                            │
 *  │                                                                       │
 *  │  Avec forceRefresh=true : skip étapes 1-2, traite comme pull-to-     │
 *  │  refresh — mais si réseau KO on retombe sur cache (fallback).        │
 *  └──────────────────────────────────────────────────────────────────────┘
 *
 * Le re-parsing du JSON au read est délibérément accepté plutôt que de
 * cacher des ForecastSeries pré-parsés. Raison : le JSON brut est sérialisable
 * sans custom serializer (que des primitifs), et le coût (~1 ms × 5 modèles)
 * est invisible à l'utilisateur.
 */
@Singleton
class ForecastRepositoryImpl @Inject constructor(
    private val api: OpenMeteoApi,
    private val mapper: ForecastMapper,
    private val cacheDao: ForecastCacheDao,
    private val json: Json,
    private val networkMonitor: NetworkMonitor,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ForecastRepository {

    override fun getCityForecastStream(
        city: City,
        models: List<WeatherModel>,
        forecastDays: Int,
        forceRefresh: Boolean
    ): Flow<ApiResult<CityForecast>> = flow {
        var hasCached = false

        // ── Étape 1 : émission immédiate depuis le cache (si non forcé) ──
        if (!forceRefresh) {
            val cached = readCache(city, models)
            if (cached != null) {
                hasCached = true
                emit(ApiResult.Success(cached))
            }
        }

        // ── Étape 2 : fetch réseau + écriture cache ──
        val networkResult = fetchAndCache(city, models, forecastDays)

        when (networkResult) {
            is ApiResult.Success -> emit(networkResult)
            is ApiResult.Error -> {
                if (!hasCached) {
                    // Pas de cache pour adoucir l'échec → on remonte l'erreur.
                    // Mais on essaie une dernière fois de lire le cache, au cas
                    // où on avait forceRefresh=true et il existe quand même.
                    val fallback = readCache(city, models)
                    if (fallback != null) emit(ApiResult.Success(fallback))
                    else emit(networkResult)
                }
                // Si on a déjà émis du cache, on n'émet PAS l'erreur — l'UI
                // garde les données qu'elle a, pas de message d'erreur intrusif.
            }
        }
    }

    override suspend fun refreshCityForecast(
        city: City,
        models: List<WeatherModel>,
        forecastDays: Int
    ): ApiResult<CityForecast> = withContext(ioDispatcher) {
        // Fix faux positif "Prévisions mises à jour" en mode avion :
        //   AVANT : si réseau KO mais cache existe → on retournait Success(cached)
        //           → l'UI affichait "Prévisions mises à jour" alors qu'aucune
        //              donnée fraîche n'avait été obtenue. Mensonger.
        //   APRÈS : on retourne directement le résultat de fetchAndCache.
        //           - Réseau OK → Success(fresh)
        //           - Réseau KO → Error("Pas de connexion") → snackbar honnête.
        //
        // Les données déjà affichées dans l'UI ne sont pas effacées : la VM
        // garde son state Loaded (philosophie tolerant côté CityDetailViewModel).
        fetchAndCache(city, models, forecastDays)
    }

    override suspend fun clearCacheForCity(cityId: String) = withContext(ioDispatcher) {
        cacheDao.deleteForCity(cityId)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Internals
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Récupère TOUS les modèles depuis le cache pour cette ville et les
     * reconstruit en un CityForecast. Renvoie null si le cache ne contient
     * AUCUN modèle parmi ceux demandés.
     *
     * Note : on ne filtre PAS par fraîcheur ici — un cache vieux de 6 h est
     * meilleur qu'un écran blanc. L'utilisateur peut toujours rafraîchir
     * manuellement, et le réseau écrasera de toute façon.
     */
    private suspend fun readCache(
        city: City,
        models: List<WeatherModel>
    ): CityForecast? = withContext(ioDispatcher) {
        val entries = cacheDao.getForCity(city.id)
            .filter { entry -> models.any { it.apiKey == entry.modelKey } }
        if (entries.isEmpty()) return@withContext null

        val series = entries.mapNotNull { entry ->
            val model = models.firstOrNull { it.apiKey == entry.modelKey } ?: return@mapNotNull null
            runCatching {
                val dto = json.decodeFromString<ForecastResponseDto>(entry.responseJson)
                mapper.toSeries(model, dto)
            }.getOrNull()
        }
        if (series.isEmpty()) return@withContext null

        CityForecast(
            city = city,
            seriesByModel = series.associateBy { it.model },
            errors = emptyMap() // on n'a pas mémorisé les erreurs en cache
        )
    }

    /**
     * Fetch parallèle + écriture cache. Renvoie un Success si au moins un
     * modèle a répondu (avec les erreurs des autres dans `errors`).
     */
    private suspend fun fetchAndCache(
        city: City,
        models: List<WeatherModel>,
        forecastDays: Int
    ): ApiResult<CityForecast> = withContext(ioDispatcher) {
        require(models.isNotEmpty()) { "models must not be empty" }

        // Court-circuit hors-ligne : on évite N requêtes parallèles qui vont
        // chacune timeout après 30s. Vérification instantanée via
        // ConnectivityManager. Message localisé pour la locale courante.
        if (!networkMonitor.isOnline()) {
            return@withContext ApiResult.Error(
                IOException("No network"),
                context.getString(R.string.error_no_network)
            )
        }

        val now = System.currentTimeMillis()

        val results: List<Triple<WeatherModel, ForecastSeries?, Throwable?>> = coroutineScope {
            models.map { model ->
                async {
                    try {
                        val dto = api.getForecast(
                            latitude = city.latitude,
                            longitude = city.longitude,
                            models = model.apiKey,
                            forecastDays = forecastDays.coerceAtMost(model.maxForecastDays)
                        )
                        // Écriture cache immédiate dès qu'on a la réponse.
                        // Fait dans la coroutine du modèle → parallèle aussi.
                        val cacheEntry = ForecastCacheEntity(
                            cityId = city.id,
                            modelKey = model.apiKey,
                            fetchedAtEpochMs = now,
                            responseJson = json.encodeToString(
                                ForecastResponseDto.serializer(),
                                dto
                            )
                        )
                        runCatching { cacheDao.upsert(cacheEntry) }
                            // En cas d'erreur d'écriture cache, on s'en fiche —
                            // l'utilisateur a déjà sa donnée fraîche.

                        Triple(model, mapper.toSeries(model, dto), null as Throwable?)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Triple(model, null, e)
                    }
                }
            }.awaitAll()
        }

        val successes = mutableMapOf<WeatherModel, ForecastSeries>()
        val errors = mutableMapOf<WeatherModel, String>()

        results.forEach { (model, series, error) ->
            if (series != null) successes[model] = series
            if (error != null) errors[model] = error.toUserMessage(context)
        }

        if (successes.isEmpty()) {
            val firstFailure = results.firstNotNullOfOrNull { it.third }
                ?: IllegalStateException("All models failed without exception")
            ApiResult.Error(firstFailure, firstFailure.toUserMessage(context))
        } else {
            ApiResult.Success(
                CityForecast(
                    city = city,
                    seriesByModel = successes,
                    errors = errors
                )
            )
        }
    }
}
