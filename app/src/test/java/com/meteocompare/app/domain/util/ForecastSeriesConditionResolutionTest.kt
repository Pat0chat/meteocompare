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

        val resolved = series.resolveDailyCondition(date, zone)

        assertEquals(WeatherCondition.PARTLY_CLOUDY, resolved?.condition)
        assertEquals(DailyConditionProvenance.HOURLY_WMO, resolved?.provenance)
        assertEquals(false, resolved?.inferred)
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

        val resolved = series.resolveDailyCondition(date, zone)

        assertEquals(WeatherCondition.RAIN, resolved?.condition)
        assertEquals(DailyConditionProvenance.HOURLY_WMO, resolved?.provenance)
        assertEquals(false, resolved?.inferred)
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

        val resolved = series.resolveDailyCondition(date, zone)

        assertEquals(WeatherCondition.OVERCAST, resolved?.condition)
        assertEquals(DailyConditionProvenance.DERIVED_VARIABLES, resolved?.provenance)
        assertEquals(true, resolved?.inferred)
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
        assertEquals(DailyConditionProvenance.DAILY_WMO, resolved?.provenance)
        assertEquals(false, resolved?.inferred)
    }

    @Test
    fun `daily sky WMO uses representative hourly WMO without becoming inferred`() {
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
        assertEquals(DailyConditionProvenance.HOURLY_WMO, resolved?.provenance)
        assertEquals(false, resolved?.inferred)
    }

    @Test
    fun `late overcast hours do not turn a mostly clear day into overcast`() {
        val hours = (0 until 24).map {
            Instant.parse("2026-08-23T22:00:00Z").plusSeconds(it * 3600L)
        }
        val series = series(
            timestamps = hours,
            dailyCode = 3,
            hourlyCodes = List(21) { 0 } + List(3) { 3 },
            hourlyPrecip = List(24) { 0.0 },
            hourlyTemp = List(24) { 20.0 },
            hourlyCloud = List(21) { 5 } + List(3) { 95 }
        )

        val resolved = series.resolveDailyCondition(date, zone)

        assertEquals(WeatherCondition.CLEAR, resolved?.condition)
        assertEquals(DailyConditionProvenance.HOURLY_WMO, resolved?.provenance)
        assertEquals(false, resolved?.inferred)
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
        assertEquals(DailyConditionProvenance.DAILY_WMO, resolved?.provenance)
        assertEquals(false, resolved?.inferred)
    }

    @Test
    fun `daily cloud mean keeps only valid values from the requested local day`() {
        val timestamps = listOf(
            Instant.parse("2026-08-23T20:00:00Z"), // 22:00 le 23 à Paris
            Instant.parse("2026-08-23T22:00:00Z"), // 00:00 le 24
            Instant.parse("2026-08-24T10:00:00Z"), // 12:00 le 24
            Instant.parse("2026-08-24T22:00:00Z")  // 00:00 le 25
        )
        val series = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = HourlyForecast(
                timestamps = timestamps,
                temperature2m = List(4) { 20.0 },
                precipitation = List(4) { 0.0 },
                windSpeed10m = List(4) { 10.0 },
                cloudCover = listOf(5, 20, 80, 95)
            ),
            daily = DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        )

        assertEquals(50, series.dailyCloudCoverMean(date, zone))
    }

    @Test
    fun `issue 40 one rainy hour at 21 does not paint a mostly sunny day rainy`() {
        val times = (0 until 24).map {
            Instant.parse("2026-08-23T22:00:00Z").plusSeconds(it * 3600L)
        }
        val codes = List(24) { if (it == 21) 61 else 0 }
        val forecast = series(
            timestamps = times,
            dailyCode = 61, // Open-Meteo transmet le phénomène le plus sévère
            hourlyCodes = codes,
            hourlyPrecip = List(24) { if (it == 21) 1.2 else 0.0 },
            hourlyTemp = List(24) { 19.0 },
            hourlyCloud = List(24) { if (it == 21) 90 else 10 }
        )
        assertEquals(WeatherCondition.CLEAR, forecast.resolveDailyCondition(date, zone)?.condition)
        assertEquals(DailyConditionProvenance.HOURLY_WMO, forecast.resolveDailyCondition(date, zone)?.provenance)
        // La pluie reste présente dans le code de l'heure 21.
        assertEquals(WeatherCondition.RAIN, forecast.resolveHourlyCondition(21))
    }

    @Test
    fun `dry sky subtypes together defeat the most frequent single rain subtype`() {
        val times = (0 until 24).map {
            Instant.parse("2026-08-23T22:00:00Z").plusSeconds(it * 3600L)
        }
        val forecast = series(
            timestamps = times, dailyCode = 61,
            hourlyCodes = List(9) { 0 } + List(8) { 2 } + List(7) { 61 },
            hourlyPrecip = List(24) { 0.0 },
            hourlyTemp = List(24) { 20.0 },
            hourlyCloud = List(24) { 40 }
        )
        assertEquals(WeatherCondition.CLEAR, forecast.resolveDailyCondition(date, zone)?.condition)
    }

    @Test
    fun `precipitation subtypes together defeat a fragmented sunny minority`() {
        val times = (0 until 24).map {
            Instant.parse("2026-08-23T22:00:00Z").plusSeconds(it * 3600L)
        }
        val forecast = series(
            timestamps = times, dailyCode = 61,
            hourlyCodes = List(8) { 51 } + List(8) { 80 } + List(8) { 0 },
            hourlyPrecip = List(24) { 0.0 },
            hourlyTemp = List(24) { 20.0 },
            hourlyCloud = List(24) { 40 }
        )
        assertEquals(WeatherCondition.RAIN_SHOWERS, forecast.resolveDailyCondition(date, zone)?.condition)
    }

    @Test
    fun `sparse hourly cache does not override significant daily alert`() {
        val forecast = series(
            dailyCode = 95, hourlyCodes = listOf(0),
            hourlyPrecip = listOf(0.0), hourlyTemp = listOf(20.0), hourlyCloud = listOf(10)
        )
        assertEquals(WeatherCondition.THUNDERSTORM, forecast.resolveDailyCondition(date, zone)?.condition)
        assertEquals(DailyConditionProvenance.DAILY_WMO, forecast.resolveDailyCondition(date, zone)?.provenance)
    }

    @Test
    fun `daily rainy code can be corrected from complete inferred hourly sky`() {
        val times = (0 until 24).map {
            Instant.parse("2026-08-23T22:00:00Z").plusSeconds(it * 3600L)
        }
        val forecast = series(
            timestamps = times, dailyCode = 61,
            hourlyCodes = List(24) { null },
            hourlyPrecip = List(24) { 0.0 },
            hourlyTemp = List(24) { 20.0 },
            hourlyCloud = List(24) { 15 }
        )
        assertEquals(WeatherCondition.CLEAR, forecast.resolveDailyCondition(date, zone)?.condition)
        assertEquals(DailyConditionProvenance.DERIVED_VARIABLES, forecast.resolveDailyCondition(date, zone)?.provenance)
    }

    @Test
    fun `single native wet code cannot beat 23 inferred dry hours`() {
        val times = (0 until 24).map {
            Instant.parse("2026-08-23T22:00:00Z").plusSeconds(it * 3600L)
        }
        val forecast = series(
            timestamps = times, dailyCode = 61,
            hourlyCodes = List(24) { if (it == 21) 61 else null },
            hourlyPrecip = List(24) { if (it == 21) 1.2 else 0.0 },
            hourlyTemp = List(24) { 20.0 },
            hourlyCloud = List(24) { if (it == 21) 90 else 10 }
        )
        assertEquals(WeatherCondition.CLEAR, forecast.resolveDailyCondition(date, zone)?.condition)
        assertEquals(DailyConditionProvenance.DERIVED_VARIABLES, forecast.resolveDailyCondition(date, zone)?.provenance)
    }

    @Test
    fun `day and night use sunset to following sunrise rather than pre dawn of same date`() {
        val utc = ZoneId.of("UTC")
        val times = (0 until 31).map { Instant.parse("2026-08-24T00:00:00Z").plusSeconds(it * 3600L) }
        val forecast = series(
            timestamps = times, dailyCode = 61,
            hourlyCodes = List(31) { if (it in 18..29 || it in 0..5) 61 else 0 },
            hourlyPrecip = List(31) { if (it in 18..29) 1.0 else 0.0 },
            hourlyTemp = List(31) { 19.0 },
            hourlyCloud = List(31) { 30 }
        )
        val todayWindow = ForecastSolarWindow(
            sunrise = Instant.parse("2026-08-24T06:00:00Z"),
            sunset = Instant.parse("2026-08-24T18:00:00Z")
        )
        val tomorrowWindow = ForecastSolarWindow(
            sunrise = Instant.parse("2026-08-25T06:00:00Z"),
            sunset = Instant.parse("2026-08-25T18:00:00Z")
        )
        assertEquals(WeatherCondition.CLEAR,
            forecast.resolveDailyCondition(date, utc, DailyConditionPeriod.DAY, todayWindow)?.condition)
        assertEquals(WeatherCondition.RAIN,
            forecast.resolveDailyCondition(date, utc, DailyConditionPeriod.NIGHT, todayWindow, tomorrowWindow)?.condition)
        assertEquals(null,
            forecast.resolveDailyCondition(date, utc, DailyConditionPeriod.NIGHT, todayWindow)?.condition)
    }

    @Test
    fun `night period metrics do not reuse daily maximum precipitation probability`() {
        val times = (0 until 30).map { Instant.parse("2026-08-24T00:00:00Z").plusSeconds(it * 3600L) }
        val forecast = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = HourlyForecast(
                timestamps = times, temperature2m = List(30) { 15.0 },
                precipitation = List(30) { 0.0 }, windSpeed10m = List(30) { 10.0 },
                cloudCover = List(30) { if (it in 6..17) 10 else 95 },
                precipitationProbability = List(30) { if (it in 18..29) 80 else 5 }
            ),
            daily = DailyForecast(listOf(date), listOf(20.0), listOf(10.0),
                listOf(1.0), listOf(15.0), precipitationProbabilityMax = listOf(80))
        )
        val solar = ForecastSolarWindow(
            Instant.parse("2026-08-24T06:00:00Z"), Instant.parse("2026-08-24T18:00:00Z"))
        val next = ForecastSolarWindow(
            Instant.parse("2026-08-25T06:00:00Z"), Instant.parse("2026-08-25T18:00:00Z"))
        assertEquals(5, forecast.dailyPeriodMetrics(DailyConditionPeriod.DAY, solar)?.precipitationProbabilityMax)
        assertEquals(80, forecast.dailyPeriodMetrics(DailyConditionPeriod.NIGHT, solar, next)?.precipitationProbabilityMax)
        assertEquals(10, forecast.dailyPeriodMetrics(DailyConditionPeriod.DAY, solar)?.cloudCoverMean)
        assertEquals(95, forecast.dailyPeriodMetrics(DailyConditionPeriod.NIGHT, solar, next)?.cloudCoverMean)
    }

    @Test
    fun `autumn daylight saving day includes both repeated local two o clock hours`() {
        val autumn = LocalDate.of(2026, 10, 25)
        val start = autumn.atStartOfDay(zone).toInstant()
        val timestamps = (0 until 25).map { start.plusSeconds(it * 3_600L) }
        val series = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = HourlyForecast(
                timestamps = timestamps, temperature2m = List(25) { 12.0 },
                precipitation = List(25) { if (it == 24) 1.0 else 0.0 },
                windSpeed10m = List(25) { 12.0 },
                weatherCode = List(25) { if (it == 24) 61 else 0 }
            ),
            daily = DailyForecast(
                dates = listOf(autumn), tempMax = listOf(15.0),
                tempMin = listOf(8.0), precipitationSum = listOf(1.0),
                windSpeedMax = listOf(12.0), weatherCode = listOf(61)
            )
        )
        assertEquals(WeatherCondition.CLEAR, series.resolveDailyCondition(autumn, zone)?.condition)
        assertEquals(25, timestamps.count { it.atZone(zone).toLocalDate() == autumn })
    }

    @Test
    fun `one available night hour does not claim to represent a full night`() {
        val utc = ZoneId.of("UTC")
        val series = series(
            timestamps = listOf(Instant.parse("2026-08-24T21:00:00Z")),
            dailyCode = 61, hourlyCodes = listOf(61),
            hourlyPrecip = listOf(1.0), hourlyTemp = listOf(20.0),
            hourlyCloud = listOf(90)
        )
        val todayWindow = ForecastSolarWindow(
            Instant.parse("2026-08-24T06:00:00Z"), Instant.parse("2026-08-24T18:00:00Z"))
        val nextWindow = ForecastSolarWindow(
            Instant.parse("2026-08-25T06:00:00Z"), Instant.parse("2026-08-25T18:00:00Z"))
        assertEquals(null, series.resolveDailyCondition(
            date, utc, DailyConditionPeriod.NIGHT, todayWindow, nextWindow))
        // En revanche le forecast daily natif reste consultable, sans inventer
        // la répartition du risque entre le jour et la nuit.
        assertEquals(WeatherCondition.RAIN, series.resolveDailyCondition(date, utc)?.condition)
    }

    @Test
    fun `period without reliable sunrise or sunset remains unavailable`() {
        val times = List(24) {
            Instant.parse("2026-08-23T22:00:00Z").plusSeconds(it * 3600L)
        }
        val series = series(
            timestamps = times, dailyCode = 61,
            hourlyCodes = List(24) { 0 },
            hourlyPrecip = List(24) { 0.0 },
            hourlyTemp = List(24) { 20.0 },
            hourlyCloud = List(24) { 5 }
        )
        assertEquals(null, series.resolveDailyCondition(
            date, zone, DailyConditionPeriod.DAY, ForecastSolarWindow(null, null)))
        assertEquals(null, series.resolveDailyCondition(
            date, zone, DailyConditionPeriod.NIGHT, ForecastSolarWindow(null, null)))
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
