package com.meteocompare.app.domain.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.VigilanceForecast

interface VigilanceRepository {
    /**
     * Retourne la vigilance officielle pour une ville française.
     * `Success(null)` signifie que la ville n'est pas éligible ou que le Worker
     * indique que le produit n'est pas configuré/disponible.
     */
    suspend fun getVigilance(
        city: City,
        includeCoast: Boolean = false,
        forceRefresh: Boolean = false
    ): ApiResult<VigilanceForecast?>

    /**
     * Purge les réponses persistées (département + littoral) uniquement si aucun
     * autre favori français ne dépend encore de ce département.
     */
    suspend fun clearCacheForDepartment(departmentCode: String)
}
