package com.meteocompare.app.domain.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.MarineForecast

interface MarineRepository {
    suspend fun getMarine(city: City, forceRefresh: Boolean = false): ApiResult<MarineForecast>
    suspend fun getCached(cityId: String): MarineForecast?
    suspend fun clear(cityId: String)

    companion object {
        const val COASTAL_MAX_DISTANCE_KM = 50.0
    }
}
