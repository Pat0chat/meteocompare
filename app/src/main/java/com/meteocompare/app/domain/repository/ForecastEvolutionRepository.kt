package com.meteocompare.app.domain.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ForecastEvolutionSample
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate

/** Snapshots Previous Runs (J−1/J−2/J−3) utilisés par le moteur run-to-run. */
data class PreviousForecastEvolutionData(
    val samples: List<ForecastEvolutionSample>,
    val fetchedAt: Instant?,
    val fromCache: Boolean
)

interface ForecastEvolutionRepository {
    suspend fun getPreviousForecasts(
        city: City,
        models: List<WeatherModel>,
        startDate: LocalDate,
        endDate: LocalDate,
        forceRefresh: Boolean = false
    ): ApiResult<PreviousForecastEvolutionData>
}
