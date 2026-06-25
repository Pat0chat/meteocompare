package com.meteocompare.app.domain.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.WeatherModel
import kotlinx.coroutines.flow.Flow

interface ForecastRepository {

    /**
     * Stream qui émet d'abord la valeur en cache (si disponible, même périmée),
     * puis la valeur fraîche depuis le réseau.
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
     */
    fun getCityForecastStream(
        city: City,
        models: List<WeatherModel> = WeatherModel.MVP_SELECTION,
        forecastDays: Int = 7,
        forceRefresh: Boolean = false
    ): Flow<ApiResult<CityForecast>>

    /**
     * Fetch one-shot depuis le réseau (toujours), puis cache. Pour le bouton refresh.
     */
    suspend fun refreshCityForecast(
        city: City,
        models: List<WeatherModel> = WeatherModel.MVP_SELECTION,
        forecastDays: Int = 7
    ): ApiResult<CityForecast>

    /** Nettoyage du cache quand une ville est retirée des favoris. */
    suspend fun clearCacheForCity(cityId: String)
}
