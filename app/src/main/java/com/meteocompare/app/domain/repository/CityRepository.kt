package com.meteocompare.app.domain.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import kotlinx.coroutines.flow.Flow

interface CityRepository {

    /** Recherche de villes par nom via l'API geocoding Open-Meteo. */
    suspend fun searchCities(query: String): ApiResult<List<City>>

    /** Flow des villes favorites (mise à jour automatique). */
    fun observeFavorites(): Flow<List<City>>

    /** Ajoute une ville aux favoris. Idempotent. */
    suspend fun addFavorite(city: City)

    /** Retire une ville des favoris. */
    suspend fun removeFavorite(cityId: String)
}
