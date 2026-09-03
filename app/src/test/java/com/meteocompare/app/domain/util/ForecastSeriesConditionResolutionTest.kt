package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastSeriesConditionResolutionTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val date = LocalDate.of(2026, 8, 24)

    @Test
    fun `hourly cloud fallback contributes a dry sky condition when WMO is missing`() {
        val series = series(
            hourlyCodes = listOf(null),
            hourlyPrecip = listOf(0.0),
            hourlyTemp = listOf(20.0),
            hourlyCloud = listOf(65)
        )

        assertEquals(WeatherCondition.PARTLY_CLOUDY, series.resolveHourlyCondition(0))
    }

    @Test
    fun `daily fallback uses hourly modal condition not the single most severe hour`() {
        val hours = (0 until 6).map { Instant.parse("2026-08-23T22:00:00Z").plusSeconds(it * 3600L) }
        // Europe/Paris : six heures du 24 août. Une seule heure d'orage ne doit
        // pas transformer toute la journée en orage.
        val series = series(
            timestamps = hours,
            dailyCode = null,
            hourlyCodes = listOf(2, 2, 2, 2, 2, 95),
            hourlyPrecip = List(6) { 0.0 },
            hourlyTemp = List(6) { 20.0 },
            hourlyCloud = List(6) { 60 }
        )

        assertEquals(
            WeatherCondition.PARTLY_CLOUDY,
            series.resolveDailyCondition(date, zone)?.condition
        )
    }

    @Test
    fun `daily modal tie uses severity only as tie break`() {
        val hours = (0 until 4).map { Instant.parse("2026-08-23T22:00:00Z").plusSeconds(it * 3600L) }
        val series = series(
            timestamps = hours,
            dailyCode = null,
            hourlyCodes = listOf(2, 2, 61, 61),
            hourlyPrecip = List(4) { 0.0 },
            hourlyTemp = List(4) { 20.0 },
            hourlyCloud = List(4) { 60 }
        )

        assertEquals(WeatherCondition.RAIN, series.resolveDailyCondition(date, zone)?.condition)
    }

    @Test
    fun `daily fallback can resolve from hourly data even when daily axis is absent`() {
        val hours = listOf(Instant.parse("2026-08-24T10:00:00Z"))
        val series = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = HourlyForecast(
                timestamps = hours,
                temperature2m = listOf(19.0),
                precipitation = listOf(0.0),
                windSpeed10m = listOf(10.0),
                weatherCode = listOf(null),
                cloudCover = listOf(90)
            ),
            daily = DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        )

        assertEquals(WeatherCondition.OVERCAST, series.resolveDailyCondition(date, zone)?.condition)
    }

    @Test
    fun `significant native daily WMO remains authoritative`() {
        val series = series(
            dailyCode = 95,
            hourlyCodes = listOf(2),
            hourlyPrecip = listOf(0.0),
            hourlyTemp = listOf(20.0),
            hourlyCloud = listOf(55)
        )

        val resolved = series.resolveDailyCondition(date, zone)
        assertEquals(WeatherCondition.THUNDERSTORM, resolved?.condition)
        assertEquals(false, resolved?.inferred)
    }

    @Test
    fun `daily sky WMO max is refined by hourly cloud cover`() {
        val hours = (0 until 6).map { Instant.parse("2026-08-23T22:00:00Z").plusSeconds(it * 3600L) }
        val series = series(
            timestamps = hours,
            dailyCode = 3,
            hourlyCodes = listOf(2, 2, 2, 2, 2, 3),
            hourlyPrecip = List(6) { 0.0 },
            hourlyTemp = List(6) { 20.0 },
            hourlyCloud = listOf(45, 50, 55, 60, 65, 95)
        )

        val resolved = series.resolveDailyCondition(date, zone)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, resolved?.condition)
        assertEquals(true, resolved?.inferred)
    }

    @Test
    fun `daily sky WMO survives an hourly axis with no usable variables`() {
        val series = series(
            timestamps = listOf(Instant.parse("2026-08-24T10:00:00Z")),
            dailyCode = 1,
            hourlyCodes = listOf(null),
            hourlyPrecip = listOf(null),
            hourlyTemp = listOf(null),
            hourlyCloud = listOf(null)
        )

        val resolved = series.resolveDailyCondition(date, zone)

        assertEquals(WeatherCondition.MAINLY_CLEAR, resolved?.condition)
        assertEquals(false, resolved?.inferred)
    }

    private fun series(
        timestamps: List<Instant> = listOf(Instant.parse("2026-08-24T10:00:00Z")),
        dailyCode: Int? = null,
        hourlyCodes: List<Int?>,
        hourlyPrecip: List<Double?>,
        hourlyTemp: List<Double?>,
        hourlyCloud: List<Int?>
    ): ForecastSeries = ForecastSeries(
        model = WeatherModel.GFS,
        hourly = HourlyForecast(
            timestamps = timestamps,
            temperature2m = hourlyTemp,
            precipitation = hourlyPrecip,
            windSpeed10m = List(timestamps.size) { 10.0 },
            weatherCode = hourlyCodes,
            cloudCover = hourlyCloud
        ),
        daily = DailyForecast(
            dates = listOf(date),
            tempMax = listOf(24.0),
            tempMin = listOf(15.0),
            precipitationSum = listOf(0.0),
            windSpeedMax = listOf(15.0),
            weatherCode = listOf(dailyCode)
        )
    )
}
