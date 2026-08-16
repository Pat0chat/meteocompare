package com.meteocompare.app.domain.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ForecastEvolutionSample
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate

/** Historique local des prévisions enregistrées lors des refreshs frais de MeteoCompare. */
data class ForecastEvolutionHistoryData(
    val samples: List<ForecastEvolutionSample>,
    val oldestSnapshotAt: Instant?
)

interface ForecastEvolutionRepository {
    suspend fun getPreviousForecasts(
        city: City,
        models: List<WeatherModel>,
        startDate: LocalDate,
        endDate: LocalDate,
        referenceAt: Instant
    ): ApiResult<ForecastEvolutionHistoryData>
}
