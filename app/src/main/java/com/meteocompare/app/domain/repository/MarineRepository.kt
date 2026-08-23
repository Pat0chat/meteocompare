package com.meteocompare.app.domain.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.MarineForecast

interface MarineRepository {
    suspend fun getMarine(city: City, forceRefresh: Boolean = false): ApiResult<MarineForecast>
    /** Cache même expiré, réservé au fallback hors-ligne / affichage explicite. */
    suspend fun getCached(cityId: String): MarineForecast?
    /** Cache encore dans sa fenêtre de fraîcheur ; ne renvoie jamais une décision côtière expirée. */
    suspend fun getFreshCached(cityId: String): MarineForecast?
    suspend fun clear(cityId: String)

    companion object {
        const val COASTAL_MAX_DISTANCE_KM = 50.0
        const val AVAILABILITY_CACHE_TTL_MS = 6 * 3_600_000L
    }
}
