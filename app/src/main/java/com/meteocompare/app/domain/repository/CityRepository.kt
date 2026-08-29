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

    /** Active/désactive le mode Mer / côte pour un favori existant. */
    suspend fun setMarineEnabled(cityId: String, enabled: Boolean)

    /**
     * Résout et persiste le code département d'un ancien favori français si
     * nécessaire. Retourne null pour les villes hors France ou non résolues.
     */
    suspend fun resolveDepartmentCode(city: City): String?
}
