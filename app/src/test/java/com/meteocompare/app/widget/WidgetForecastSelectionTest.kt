package com.meteocompare.app.widget

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetForecastSelectionTest {

    private val city = City(
        id = "paris",
        name = "Paris",
        country = "France",
        latitude = 48.85,
        longitude = 2.35,
        timezone = "Europe/Paris"
    )

    @Test
    fun `daily choisit un modele avec cinq valeurs reelles plutot que cinq dates partagees`() {
        val dates = (0L until 5L).map { LocalDate.of(2026, 7, 17).plusDays(it) }
        val shortFineModel = ForecastSeries(
            model = WeatherModel.AROME_FRANCE_HD,
            hourly = emptyHourly(),
            daily = DailyForecast(
                dates = dates,
                tempMax = listOf(21.0, 22.0, null, null, null),
                tempMin = listOf(14.0, 15.0, null, null, null),
                precipitationSum = listOf(0.0, 2.0, null, null, null),
                windSpeedMax = listOf(10.0, 11.0, null, null, null),
                weatherCode = listOf(1, 61, null, null, null)
            )
        )
        val completeModel = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = emptyHourly(),
            daily = DailyForecast(
                dates = dates,
                tempMax = listOf(20.0, 21.0, 22.0, 23.0, 24.0),
                tempMin = listOf(13.0, 14.0, 15.0, 16.0, 17.0),
                precipitationSum = listOf(0.0, 0.0, 1.5, 0.0, 3.0),
                windSpeedMax = List(5) { 12.0 },
                weatherCode = listOf(1, 2, 61, 3, 63)
            )
        )

        val items = buildForecasts(
            forecast = CityForecast(
                city = city,
                seriesByModel = linkedMapOf(
                    WeatherModel.AROME_FRANCE_HD to shortFineModel,
                    WeatherModel.GFS to completeModel
                )
            ),
            mode = ForecastMode.DAILY,
            timezone = city.timezone,
            now = Instant.parse("2026-07-17T10:00:00Z")
        )

        assertEquals(5, items.size)
        assertEquals(listOf(20.0, 21.0, 22.0, 23.0, 24.0), items.map { it.temp })
        assertTrue(items.all { it.temp != null || it.condition != null })
    }

    @Test
    fun `hourly ignore un modele fin dont les cinq prochaines valeurs sont nulles`() {
        val now = Instant.parse("2026-07-17T10:15:00Z")
        val timestamps = (1L..5L).map { now.plusSeconds(it * 3600) }
        val emptyFineModel = ForecastSeries(
            model = WeatherModel.AROME_FRANCE_HD,
            hourly = HourlyForecast(
                timestamps = timestamps,
                temperature2m = List(5) { null },
                precipitation = List(5) { null },
                windSpeed10m = List(5) { null },
                weatherCode = List(5) { null }
            ),
            daily = emptyDaily()
        )
        val completeModel = ForecastSeries(
            model = WeatherModel.ICON_EU,
            hourly = HourlyForecast(
                timestamps = timestamps,
                temperature2m = listOf(18.0, 19.0, 20.0, 21.0, 22.0),
                precipitation = listOf(0.0, 0.0, 0.2, 0.0, 0.0),
                windSpeed10m = List(5) { 10.0 },
                weatherCode = listOf(1, 1, 51, 2, 2)
            ),
            daily = emptyDaily()
        )

        val items = buildForecasts(
            forecast = CityForecast(
                city = city,
                seriesByModel = linkedMapOf(
                    WeatherModel.AROME_FRANCE_HD to emptyFineModel,
                    WeatherModel.ICON_EU to completeModel
                )
            ),
            mode = ForecastMode.HOURLY,
            timezone = city.timezone,
            now = now
        )

        assertEquals(5, items.size)
        assertEquals(listOf(18.0, 19.0, 20.0, 21.0, 22.0), items.map { it.temp })
    }

    @Test
    fun `hourly ne recycle pas les premieres heures d un cache entierement passe`() {
        val now = Instant.parse("2026-07-17T10:15:00Z")
        val hourly = HourlyForecast(
            timestamps = (5L downTo 1L).map { now.minusSeconds(it * 3600) },
            temperature2m = List(5) { 15.0 },
            precipitation = List(5) { 0.0 },
            windSpeed10m = List(5) { 8.0 },
            weatherCode = List(5) { 1 }
        )

        assertTrue(buildHourlyForecasts(hourly, java.time.ZoneId.of("UTC"), now).isEmpty())
    }

    private fun emptyHourly() = HourlyForecast(
        timestamps = emptyList(),
        temperature2m = emptyList(),
        precipitation = emptyList(),
        windSpeed10m = emptyList()
    )

    private fun emptyDaily() = DailyForecast(
        dates = emptyList(),
        tempMax = emptyList(),
        tempMin = emptyList(),
        precipitationSum = emptyList(),
        windSpeedMax = emptyList()
    )
}
