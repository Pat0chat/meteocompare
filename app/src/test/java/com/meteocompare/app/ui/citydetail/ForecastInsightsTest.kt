package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastInsightsTest {

    private val now = Instant.parse("2026-07-23T10:00:00Z")

    @Test
    fun `stable dry forecast produces a high agreement insight`() {
        val forecast = forecast(
            probabilityByModel = listOf(5, 10, 0),
            windByModel = listOf(12.0, 13.0, 11.0),
            maxByModel = listOf(22.0, 23.0, 22.5),
            dayCount = 2
        )

        val insights = buildForecastInsights(buildOverviewTimeline(forecast, now))

        assertTrue(insights.any { it.kind == ForecastInsightKind.HIGH_AGREEMENT })
        assertTrue(insights.size <= 3)
    }

    @Test
    fun `split rain signal is surfaced without a duplicate generic message`() {
        val forecast = forecast(
            probabilityByModel = listOf(10, 55, 90),
            windByModel = listOf(10.0, 12.0, 40.0),
            maxByModel = listOf(20.0, 22.0, 28.0)
        )

        val insights = buildForecastInsights(buildOverviewTimeline(forecast, now))

        assertEquals(ForecastInsightKind.RAIN_UNCERTAIN, insights.first().kind)
        assertEquals(PrecipitationSignalSource.MODEL_PROBABILITY, insights.first().precipitationSource)
        assertEquals(1, insights.count { it.point == insights.first().point })
    }

    @Test
    fun `a single model never produces a high agreement claim`() {
        val forecast = forecast(
            probabilityByModel = listOf(5),
            windByModel = listOf(12.0),
            maxByModel = listOf(22.0),
            dayCount = 2
        )

        val insights = buildForecastInsights(buildOverviewTimeline(forecast, now))

        assertFalse(insights.any { it.kind == ForecastInsightKind.HIGH_AGREEMENT })
        assertFalse(insights.any { it.kind == ForecastInsightKind.DISAGREEMENT })
    }

    private fun forecast(
        probabilityByModel: List<Int>,
        windByModel: List<Double>,
        maxByModel: List<Double>,
        dayCount: Int = 1
    ): CityForecast {
        val firstDate = LocalDate.of(2026, 7, 23)
        val dates = List(dayCount) { firstDate.plusDays(it.toLong()) }
        val models = listOf(
            WeatherModel.GFS,
            WeatherModel.ECMWF,
            WeatherModel.ICON_GLOBAL
        ).take(probabilityByModel.size)
        return CityForecast(
            city = City(
                id = "paris",
                name = "Paris",
                country = "France",
                latitude = 48.85,
                longitude = 2.35,
                timezone = "Europe/Paris"
            ),
            seriesByModel = models.mapIndexed { index, model ->
                val probability = probabilityByModel[index]
                model to ForecastSeries(
                    model = model,
                    hourly = HourlyForecast(
                        timestamps = emptyList(),
                        temperature2m = emptyList(),
                        precipitation = emptyList(),
                        windSpeed10m = emptyList()
                    ),
                    daily = DailyForecast(
                        dates = dates,
                        tempMax = List(dayCount) { maxByModel[index] },
                        tempMin = List(dayCount) { 12.0 + index },
                        precipitationSum = List(dayCount) {
                            if (probability >= 50) 2.0 else 0.0
                        },
                        windSpeedMax = List(dayCount) { windByModel[index] },
                        weatherCode = List(dayCount) { if (probability >= 50) 61 else 1 },
                        precipitationProbabilityMax = List(dayCount) { probability }
                    )
                )
            }.toMap()
        )
    }
}
