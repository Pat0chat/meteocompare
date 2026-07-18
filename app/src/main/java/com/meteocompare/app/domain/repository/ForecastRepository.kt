package com.meteocompare.app.domain.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.WeatherModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface ForecastRepository {

    /**
     * Stream qui émet d'abord la valeur en cache (si disponible, même périmée),
     * puis la valeur fraîche depuis le réseau — sauf si le cache est plus jeune
     * que [maxCacheAgeMs], auquel cas on saute le fetch réseau.
     *
     * Émissions possibles :
     *   1. `Success(cached)` immédiatement si du cache existe pour cette ville.
     *   2. `Success(fresh)` après le fetch réseau réussi (et l'écriture en cache).
     *   3. `Error` UNIQUEMENT si aucun cache + réseau en échec.
     *      Si on a du cache et que le réseau échoue, on émet `Success(cached)` et
     *      on s'arrête là — pas besoin d'envoyer une erreur à l'UI puisque l'user
     *      voit déjà des données.
     *
     * @param forceRefresh Si true, ignore le cache pour la première émission
     *        (cas: pull-to-refresh).
     * @param maxCacheAgeMs Âge maximal du cache au-delà duquel on lance un
     *        fetch réseau. Si le cache est plus récent que cet âge, on émet
     *        uniquement `Success(cached)` sans requête réseau — économie
     *        batterie/data. `null` = comportement historique (toujours fetch).
     *        Ignoré si `forceRefresh=true`.
     */
    fun getCityForecastStream(
        city: City,
        models: List<WeatherModel> = WeatherModel.MVP_SELECTION,
        forecastDays: Int = 7,
        forceRefresh: Boolean = false,
        maxCacheAgeMs: Long? = null
    ): Flow<ApiResult<CityForecast>>

    /**
     * Fetch one-shot depuis le réseau (toujours), puis cache. Pour le bouton refresh.
     */
    suspend fun refreshCityForecast(
        city: City,
        models: List<WeatherModel> = WeatherModel.MVP_SELECTION,
        forecastDays: Int = 7
    ): ApiResult<CityForecast>

    /**
     * Émissions en mémoire des prévisions obtenues par un refresh manuel.
     *
     * Ce flux permet aux écrans déjà présents dans la pile de navigation de
     * refléter un refresh lancé ailleurs sans refaire de requête réseau. Les
     * fetchs automatiques de [getCityForecastStream] — dont ceux des widgets —
     * ne publient pas ici, afin de ne pas réveiller les écrans en arrière-plan.
     * Le flux ne rejoue pas les anciennes valeurs : un écran recréé relit Room.
     * La publication est best-effort et ne ralentit jamais le caller.
     */
    fun observeForecastUpdates(): Flow<CityForecast> = emptyFlow()

    /** Nettoyage du cache quand une ville est retirée des favoris. */
    suspend fun clearCacheForCity(cityId: String)
}
