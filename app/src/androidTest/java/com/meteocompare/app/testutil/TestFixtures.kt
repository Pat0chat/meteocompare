package com.meteocompare.app.testutil

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object TestFixtures {
    private val parisZone = ZoneId.of("Europe/Paris")
    private val currentHour: ZonedDateTime = ZonedDateTime.now(parisZone)
        .withMinute(0)
        .withSecond(0)
        .withNano(0)

    /** Horloge figée une fois par processus de test, mais toujours relative au jour d'exécution. */
    val now: Instant = currentHour.toInstant()
    val today: LocalDate = currentHour.toLocalDate()

    val paris = City(
        id = "2988507",
        name = "Paris",
        admin1 = "Île-de-France",
        country = "France",
        latitude = 48.8566,
        longitude = 2.3522,
        timezone = "Europe/Paris"
    )

    val lyon = City(
        id = "2996944",
        name = "Lyon",
        admin1 = "Auvergne-Rhône-Alpes",
        country = "France",
        latitude = 45.7640,
        longitude = 4.8357,
        timezone = "Europe/Paris"
    )

    fun forecast(
        city: City = paris,
        models: List<WeatherModel> = listOf(
            WeatherModel.AROME_FRANCE_HD,
            WeatherModel.ICON_EU,
            WeatherModel.GFS
        )
    ): CityForecast {
        val dates = List(7) { today.plusDays(it.toLong()) }
        val hours = List(48) { now.plus(it.toLong(), ChronoUnit.HOURS) }
        val series = models.mapIndexed { modelIndex, model ->
            val offset = modelIndex * 0.8
            model to ForecastSeries(
                model = model,
                hourly = HourlyForecast(
                    timestamps = hours,
                    temperature2m = hours.indices.map { 20.0 + offset + (it % 8) * 0.25 },
                    precipitation = hours.indices.map { if (it % 9 == 0) 0.8 + modelIndex else 0.0 },
                    windSpeed10m = hours.indices.map { 12.0 + modelIndex + (it % 4) },
                    weatherCode = hours.indices.map { if (it % 9 == 0) 61 else 1 },
                    windDirection10m = hours.indices.map { 180 + modelIndex * 10 },
                    precipitationProbability = hours.indices.map { if (it % 9 == 0) 70 else 10 },
                    cloudCover = hours.indices.map { if (it % 9 == 0) 90 else 25 }
                ),
                daily = DailyForecast(
                    dates = dates,
                    tempMax = dates.indices.map { 24.0 + offset + it * 0.3 },
                    tempMin = dates.indices.map { 15.0 + offset + it * 0.2 },
                    precipitationSum = dates.indices.map { if (it == 2) 4.0 + modelIndex else 0.0 },
                    windSpeedMax = dates.indices.map { 20.0 + modelIndex + it },
                    weatherCode = dates.indices.map { if (it == 2) 61 else 1 },
                    windDirection10mDominant = dates.indices.map { 190 + modelIndex * 10 },
                    precipitationProbabilityMax = dates.indices.map { if (it == 2) 80 else 15 }
                )
            )
        }.toMap()
        return CityForecast(city = city, seriesByModel = series, fetchedAt = now)
    }
}
