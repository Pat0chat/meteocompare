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
    fun `old cached hourly model can reconstruct clouds at a missing total slot`() {
        val cached = HourlyForecast(
            timestamps = listOf(Instant.parse("2026-08-24T10:00:00Z")),
            temperature2m = listOf(18.0), precipitation = listOf(0.0),
            windSpeed10m = listOf(8.0), weatherCode = listOf(null),
            cloudCover = listOf(null), cloudCoverLow = listOf(10),
            cloudCoverMid = listOf(40), cloudCoverHigh = listOf(25)
        )
        assertEquals(WeatherCondition.MAINLY_CLEAR, cached.resolveHourlyCondition(0))
        // La couche haute seule ne permet pas de caractériser l'état du ciel.
        assertEquals(null, cached.copy(cloudCoverMid = listOf(null)).resolveHourlyCondition(0))
    }

    @Test
    fun `daily fallback uses hourly modal condition not the single most severe hour`() {
        val hours = (0 until 24).map { Instant.parse("2026-08-23T22:00:00Z").plusSeconds(it * 3600L) }
        // Les 24 heures sont représentées : un seul orage ne domine pas.
        val series = series(
            timestamps = hours,
            dailyCode = null,
            hourlyCodes = List(24) { if (it == 21) 95 else 2 },
            hourlyPrecip = List(24) { 0.0 },
            hourlyTemp = List(24) { 20.0 },
            hourlyCloud = List(24) { 60 }
        )

        val resolved = series.resolveDailyCondition(date, zone)

        assertEquals(WeatherCondition.PARTLY_CLOUDY, resolved?.condition)
        assertEquals(DailyConditionProvenance.HOURLY_WMO, resolved?.provenance)
        assertEquals(false, resolved?.inferred)
    }

    @Test
    fun `daily with exactly equal dry and wet hours has no dominant condition without daily WMO`() {
        val hours = (0 until 24).map { Instant.parse("2026-08-23T22:00:00Z").plusSeconds(it * 3600L) }
        val series = series(
            timestamps = hours,
            dailyCode = null,
            hourlyCodes = List(12) { 2 } + List(12) { 61 },
            hourlyPrecip = List(24) { 0.0 },
            hourlyTemp = List(24) { 20.0 },
            hourlyCloud = List(24) { 60 }
        )

        assertEquals(null, series.resolveDailyCondition(date, zone))
    }

    @Test
    fun `daily severe WMO does not break a representative dry wet tie`() {
        val start = date.atStartOfDay(zone).toInstant()
        val timestamps = (0 until 24).map { start.plusSeconds(it * 3600L) }
        val forecast = series(
            timestamps = timestamps,
            dailyCode = 95,
            hourlyCodes = List(12) { 0 } + List(12) { 61 },
            hourlyPrecip = List(12) { 0.0 } + List(12) { 1.0 },
            hourlyTemp = List(24) { 19.0 },
            hourlyCloud = List(24) { 50 }
        )
        assertEquals(null, forecast.resolveDailyCondition(date, zone))
    }

    @Test
    fun `single cloudy hour cannot represent a day with no daily axis`() {
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

        assertEquals(null, resolved)
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
    fun `six sky observations do not override a full day WMO`() {
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
        assertEquals(WeatherCondition.OVERCAST, resolved?.condition)
        assertEquals(DailyConditionProvenance.DAILY_WMO, resolved?.provenance)
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

        // Deux heures ne justifient pas une moyenne affichée comme daily.
        assertEquals(null, series.dailyCloudCoverMean(date, zone))
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
    fun `sparse daytime cloud cannot produce a misleading day mean`() {
        val timestamps = listOf(6L, 9L, 12L).map { hour ->
            Instant.parse("2026-08-24T00:00:00Z").plusSeconds(hour * 3600)
        }
        val series = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = HourlyForecast(
                timestamps = timestamps,
                temperature2m = List(3) { 20.0 },
                precipitation = List(3) { 0.0 },
                windSpeed10m = List(3) { 8.0 },
                cloudCover = listOf(95, 95, 95),
                precipitationProbability = listOf(5, 80, 5)
            ),
            daily = DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        )
        val solar = ForecastSolarWindow(
            Instant.parse("2026-08-24T06:00:00Z"),
            Instant.parse("2026-08-24T18:00:00Z")
        )
        val metrics = series.dailyPeriodMetrics(DailyConditionPeriod.DAY, solar)
        assertEquals(null, metrics?.cloudCoverMean)
        // A single peak remains a valid *hourly maximum*, not a mean.
        assertEquals(80, metrics?.precipitationProbabilityMax)
    }

    @Test
    fun `eighteen consecutive early cloudy hours do not represent whole day`() {
        val start = date.atStartOfDay(zone).toInstant()
        val timestamps = (0 until 18).map { start.plusSeconds(it * 3600L) }
        val forecast = series(
            timestamps = timestamps, dailyCode = null,
            hourlyCodes = List(18) { null },
            hourlyPrecip = List(18) { 0.0 },
            hourlyTemp = List(18) { 20.0 },
            hourlyCloud = List(18) { 90 }
        )
        assertEquals(null, forecast.dailyCloudCoverMean(date, zone))
    }

    @Test
    fun `daytime average requires observations near sunset as well as sunrise`() {
        val start = Instant.parse("2026-08-24T06:00:00Z")
        val timestamps = (0 until 9).map { start.plusSeconds(it * 3600L) }
        val forecast = series(
            timestamps = timestamps, dailyCode = null,
            hourlyCodes = List(9) { null },
            hourlyPrecip = List(9) { 0.0 },
            hourlyTemp = List(9) { 20.0 },
            hourlyCloud = List(9) { 90 }
        )
        val window = ForecastSolarWindow(start, start.plusSeconds(12 * 3600L))
        assertEquals(null, forecast.dailyPeriodMetrics(DailyConditionPeriod.DAY, window)?.cloudCoverMean)
    }

    @Test
    fun `invalid duplicate never hides valid daytime data`() {
        val start = Instant.parse("2026-08-24T06:00:00Z")
        val timestamps = (0 until 12).flatMap { hour ->
            val instant = start.plusSeconds(hour * 3600L)
            listOf(instant, instant)
        }
        val series = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = HourlyForecast(
                timestamps = timestamps,
                temperature2m = List(24) { 19.0 },
                precipitation = List(24) { 0.0 },
                windSpeed10m = List(24) { 8.0 },
                cloudCover = List(24) { if (it % 2 == 0) null else 60 },
                precipitationProbability = List(24) { if (it % 2 == 0) null else 70 }
            ),
            daily = DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        )
        val solar = ForecastSolarWindow(start, start.plusSeconds(12 * 3600L))
        val metrics = series.dailyPeriodMetrics(DailyConditionPeriod.DAY, solar)
        assertEquals(60, metrics?.cloudCoverMean)
        assertEquals(70, metrics?.precipitationProbabilityMax)
    }

    @Test
    fun `daily sky average ignores invalid duplicate before a valid hour`() {
        val start = date.atStartOfDay(zone).toInstant()
        val timestamps = (0 until 24).flatMap { hour ->
            val instant = start.plusSeconds(hour * 3600L)
            listOf(instant, instant)
        }
        val forecast = series(
            timestamps = timestamps, dailyCode = null,
            hourlyCodes = List(48) { null },
            hourlyPrecip = List(48) { 0.0 },
            hourlyTemp = List(48) { 20.0 },
            hourlyCloud = List(48) { if (it % 2 == 0) null else 60 }
        )
        assertEquals(60, forecast.dailyCloudCoverMean(date, zone))
    }

    @Test
    fun `isolated rain cannot bias daily condition at any of 24 hourly positions`() {
        val start = date.atStartOfDay(zone).toInstant()
        val timestamps = (0 until 24).map { start.plusSeconds(it * 3600L) }
        for (rainHour in 0 until 24) {
            val forecast = series(
                timestamps = timestamps, dailyCode = 61,
                hourlyCodes = List(24) { if (it == rainHour) 61 else 0 },
                hourlyPrecip = List(24) { if (it == rainHour) 8.0 else 0.0 },
                hourlyTemp = List(24) { 20.0 },
                hourlyCloud = List(24) { if (it == rainHour) 90 else 5 }
            )
            assertEquals("rainHour=$rainHour", WeatherCondition.CLEAR,
                forecast.resolveDailyCondition(date, zone)?.condition)
            assertEquals(WeatherCondition.RAIN, forecast.resolveHourlyCondition(rainHour))
        }
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

    @Test
    fun `duplicate rainy timestamps cannot outvote a complete dry day`() {
        val start = Instant.parse("2026-08-23T22:00:00Z")
        val hours = (0 until 24).map { start.plusSeconds(it * 3600L) }
        val duplicateHour = start.plusSeconds(21 * 3600L)
        val extra = List(40) { duplicateHour }
        val timestamps = hours + extra
        val forecast = series(
            timestamps = timestamps, dailyCode = 61,
            hourlyCodes = List(24) { if (it == 21) 61 else 0 } + List(40) { 61 },
            hourlyPrecip = List(timestamps.size) { 0.0 },
            hourlyTemp = List(timestamps.size) { 20.0 },
            hourlyCloud = List(timestamps.size) { 10 }
        )
        assertEquals(WeatherCondition.CLEAR, forecast.resolveDailyCondition(date, zone)?.condition)
    }

    @Test
    fun `native hourly code takes precedence when a duplicate is inferred`() {
        val start = Instant.parse("2026-08-23T22:00:00Z")
        val hours = (0 until 24).map { start.plusSeconds(it * 3600L) }
        val duplicate = hours[12]
        val forecast = series(
            timestamps = hours + duplicate, dailyCode = 61,
            hourlyCodes = List(24) { 0 } + listOf(61),
            hourlyPrecip = List(25) { 0.0 },
            hourlyTemp = List(25) { 18.0 },
            hourlyCloud = List(25) { 5 }
        )
        // Une seule heure native pluvieuse reste minoritaire et la provenance
        // mixte indique honnêtement que le reste est constitué de WMO natifs.
        assertEquals(WeatherCondition.CLEAR, forecast.resolveDailyCondition(date, zone)?.condition)
    }

    @Test
    fun `six consecutive hours of rain cannot override a dry daily WMO`() {
        val hours = (0 until 6).map { Instant.parse("2026-08-23T22:00:00Z").plusSeconds(it * 3600L) }
        val forecast = series(
            timestamps = hours, dailyCode = 0,
            hourlyCodes = List(6) { 61 },
            hourlyPrecip = List(6) { 1.0 },
            hourlyTemp = List(6) { 20.0 },
            hourlyCloud = List(6) { 90 }
        )
        assertEquals(WeatherCondition.CLEAR, forecast.resolveDailyCondition(date, zone)?.condition)
        assertEquals(DailyConditionProvenance.DAILY_WMO, forecast.resolveDailyCondition(date, zone)?.provenance)
    }

    @Test
    fun `daily precipitation sum alone never proves predominantly rainy conditions`() {
        val sparse = series(
            hourlyCodes = listOf(null), hourlyPrecip = listOf(0.0),
            hourlyTemp = listOf(20.0), hourlyCloud = listOf(null)
        )
        val series = sparse.copy(daily = sparse.daily.copy(precipitationSum = listOf(12.0)))
        assertEquals(null, series.resolveDailyCondition(date, zone))
    }

    @Test
    fun `scattered hourly groups with certain clear majority override contradictory daily WMO`() {
        val start = Instant.parse("2026-08-23T22:00:00Z")
        val minutes = (0 until 10).map { it * 60L } + (15 until 24).map { it * 60L }
        val hours = minutes.map { start.plusSeconds(it * 60) }
        val series = series(
            timestamps = hours, dailyCode = 61,
            hourlyCodes = List(hours.size) { 0 },
            hourlyPrecip = List(hours.size) { 0.0 },
            hourlyTemp = List(hours.size) { 18.0 },
            hourlyCloud = List(hours.size) { 5 }
        )
        // 19/24 heures sont claires : même si les 5 autres sont pluvieuses,
        // CLEAR garde une majorité stricte. Ce verdict est démontrable.
        assertEquals(WeatherCondition.CLEAR, series.resolveDailyCondition(date, zone)?.condition)
    }

    @Test
    fun `a three hour fragment of a twelve hour night is not a night forecast`() {
        val utc = ZoneId.of("UTC")
        val start = Instant.parse("2026-08-24T19:00:00Z")
        val hours = (0 until 3).map { start.plusSeconds(it * 3600L) }
        val series = series(
            timestamps = hours, dailyCode = 61,
            hourlyCodes = List(3) { 61 },
            hourlyPrecip = List(3) { 2.0 },
            hourlyTemp = List(3) { 18.0 },
            hourlyCloud = List(3) { 95 }
        )
        val solar = ForecastSolarWindow(
            Instant.parse("2026-08-24T06:00:00Z"), Instant.parse("2026-08-24T18:00:00Z")
        )
        val following = ForecastSolarWindow(
            Instant.parse("2026-08-25T06:00:00Z"), Instant.parse("2026-08-25T18:00:00Z")
        )
        assertEquals(null, series.resolveDailyCondition(date, utc, DailyConditionPeriod.NIGHT, solar, following))
    }

    @Test
    fun `all twenty four positions of isolated hourly rain remain dry overall`() {
        val start = Instant.parse("2026-08-23T22:00:00Z")
        val hours = (0 until 24).map { start.plusSeconds(it * 3600L) }
        (0 until 24).forEach { rainHour ->
            val forecast = series(
                timestamps = hours, dailyCode = 61,
                hourlyCodes = List(24) { if (it == rainHour) 61 else 0 },
                hourlyPrecip = List(24) { if (it == rainHour) 3.0 else 0.0 },
                hourlyTemp = List(24) { 15.0 },
                hourlyCloud = List(24) { 5 }
            )
            assertEquals(WeatherCondition.CLEAR, forecast.resolveDailyCondition(date, zone)?.condition)
        }
    }

    @Test
    fun `all isolated precipitation families at any hour remain non dominant`() {
        val start = date.atStartOfDay(zone).toInstant()
        val timestamps = (0 until 24).map { start.plusSeconds(it * 3600L) }
        // Bruine, pluie, averses, neige, pluie verglaçante, orage : un
        // phénomène bref ne gagne jamais face à 23 heures de ciel sec.
        for (phenomenon in listOf(51, 61, 80, 71, 66, 95)) {
            for (rainHour in 0 until 24) {
                val series = series(
                    timestamps = timestamps,
                    dailyCode = phenomenon,
                    hourlyCodes = List(24) { if (it == rainHour) phenomenon else 0 },
                    hourlyPrecip = List(24) { if (it == rainHour) 4.0 else 0.0 },
                    hourlyTemp = List(24) { 6.0 },
                    hourlyCloud = List(24) { if (it == rainHour) 95 else 5 }
                )
                assertEquals(WeatherCondition.CLEAR, series.resolveDailyCondition(date, zone)?.condition)
                assertEquals(
                    WeatherCondition.fromWmoCode(phenomenon),
                    series.resolveHourlyCondition(rainHour)
                )
            }
        }
    }

    @Test
    fun `hourly majority remains stable with mixed sky and precipitation classes`() {
        val start = date.atStartOfDay(zone).toInstant()
        val timestamps = (0 until 24).map { start.plusSeconds(it * 3600L) }
        val codes = List(7) { 0 } + List(7) { 3 } + List(6) { 61 } + List(4) { 95 }
        val forecast = series(
            timestamps = timestamps, dailyCode = 95,
            hourlyCodes = codes,
            hourlyPrecip = List(24) { if (it >= 14) 3.0 else 0.0 },
            hourlyTemp = List(24) { 16.0 },
            hourlyCloud = List(24) { 50 }
        )
        val resolved = forecast.resolveDailyCondition(date, zone)
        // 14 h sèches contre 10 h humides : ni pluie ni orage ne domine.
        assertEquals(true, resolved?.condition?.isSky)
        assertEquals(DailyConditionProvenance.HOURLY_WMO, resolved?.provenance)
    }

    @Test
    fun `stable discontinuous majority of one sky class is provably representative`() {
        val start = date.atStartOfDay(zone).toInstant()
        // 14 heures claires, puis un trou de 7 h, puis 3 heures claires.
        val offsets = (0 until 14).toList() + (21 until 24).toList()
        val forecast = series(
            timestamps = offsets.map { start.plusSeconds(it * 3600L) },
            dailyCode = 95,
            hourlyCodes = List(offsets.size) { 0 },
            hourlyPrecip = List(offsets.size) { 0.0 },
            hourlyTemp = List(offsets.size) { 17.0 },
            hourlyCloud = List(offsets.size) { 5 }
        )
        assertEquals(WeatherCondition.CLEAR, forecast.resolveDailyCondition(date, zone)?.condition)
    }

    @Test
    fun `discontinuous sky family without a majority leaf remains indeterminate`() {
        val start = date.atStartOfDay(zone).toInstant()
        val offsets = (0 until 10).toList() + (15 until 24).toList()
        val forecast = series(
            timestamps = offsets.map { start.plusSeconds(it * 3600L) },
            dailyCode = 95,
            hourlyCodes = List(10) { 0 } + List(9) { 3 },
            hourlyPrecip = List(19) { 0.0 },
            hourlyTemp = List(19) { 17.0 },
            hourlyCloud = List(10) { 5 } + List(9) { 95 }
        )
        // 19/24 sont secs, mais ni clair (10/24) ni couvert (9/24) ne sont
        // une majorité certaine : on ne fabrique pas un sous-type.
        assertEquals(null, forecast.resolveDailyCondition(date, zone))
    }

    @Test
    fun `representative cloud mean requires a full distribution of valid hours`() {
        val start = Instant.parse("2026-08-23T22:00:00Z")
        val hours = (0 until 24).map { start.plusSeconds(it * 3600L) }
        val series = series(
            timestamps = hours, dailyCode = null,
            hourlyCodes = List(24) { null },
            hourlyPrecip = List(24) { 0.0 },
            hourlyTemp = List(24) { 18.0 },
            hourlyCloud = List(12) { 10 } + List(12) { 90 }
        )
        assertEquals(50, series.dailyCloudCoverMean(date, zone))
    }

    @Test
    fun `twenty five hour daylight saving night keeps its extra local hour`() {
        val utcStart = LocalDate.of(2026, 10, 25).atStartOfDay(zone).toInstant()
        val autumn = LocalDate.of(2026, 10, 25)
        val hours = (0 until 25).map { utcStart.plusSeconds(it * 3600L) }
        val local = series(
            timestamps = hours, dailyCode = 61,
            hourlyCodes = List(25) { if (it == 3) 61 else 0 },
            hourlyPrecip = List(25) { 0.0 },
            hourlyTemp = List(25) { 10.0 },
            hourlyCloud = List(25) { 10 }
        ).let { it.copy(daily = it.daily.copy(dates = listOf(autumn))) }
        assertEquals(WeatherCondition.CLEAR, local.resolveDailyCondition(autumn, zone)?.condition)
    }

    @Test
    fun `current day can be corrected despite missing past hours if majority is irreversible`() {
        val start = Instant.parse("2026-08-24T08:00:00Z") // 10 h à Paris
        val times = (0 until 14).map { start.plusSeconds(it * 3600L) }
        val forecast = series(
            timestamps = times, dailyCode = 61,
            hourlyCodes = List(14) { if (it == 13) 61 else 0 },
            hourlyPrecip = List(14) { if (it == 13) 1.0 else 0.0 },
            hourlyTemp = List(14) { 19.0 },
            hourlyCloud = List(14) { 5 }
        )
        assertEquals(WeatherCondition.CLEAR, forecast.resolveDailyCondition(date, zone)?.condition)
    }

    @Test
    fun `repeated half hourly clear samples cannot claim a majority over one rain event`() {
        val start = Instant.parse("2026-08-23T22:00:00Z")
        // Seulement huit heures sèches mais 16 échantillons demi-horaires.
        val dryHalfHours = (0 until 16).map { start.plusSeconds(it * 1800L) }
        val rainHour = start.plusSeconds(16 * 3600L)
        val timestamps = dryHalfHours + rainHour
        val forecast = series(
            timestamps = timestamps, dailyCode = 61,
            hourlyCodes = List(16) { 0 } + listOf(61),
            hourlyPrecip = List(17) { 0.0 },
            hourlyTemp = List(17) { 20.0 },
            hourlyCloud = List(17) { 15 }
        )
        // L'intervalle non couvert ne peut pas être inventé en comptant
        // deux fois les heures sèches.
        assertEquals(WeatherCondition.RAIN, forecast.resolveDailyCondition(date, zone)?.condition)
    }

    @Test
    fun `spring daylight saving day contains twenty three absolute hours`() {
        val spring = LocalDate.of(2026, 3, 29)
        val start = spring.atStartOfDay(zone).toInstant()
        val times = (0 until 23).map { start.plusSeconds(it * 3600L) }
        val series = series(
            timestamps = times, dailyCode = 61,
            hourlyCodes = List(23) { if (it == 21) 61 else 0 },
            hourlyPrecip = List(23) { 0.0 },
            hourlyTemp = List(23) { 10.0 },
            hourlyCloud = List(23) { 10 }
        ).let { it.copy(daily = it.daily.copy(dates = listOf(spring))) }
        assertEquals(23, times.count { it.atZone(zone).toLocalDate() == spring })
        assertEquals(WeatherCondition.CLEAR, series.resolveDailyCondition(spring, zone)?.condition)
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
