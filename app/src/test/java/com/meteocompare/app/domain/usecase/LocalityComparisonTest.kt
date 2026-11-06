package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.model.UvForecast
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalityComparisonTest {
    private val day = LocalDate.of(2026, 6, 20)
    private val rise = Instant.parse("2026-06-20T06:00:00Z")
    private val set = Instant.parse("2026-06-20T18:00:00Z")

    @Test fun cloudAdjustedSunshine_usesOnlyOverlappingDaylight() {
        val hours = (0L until 24).map { Instant.parse("2026-06-20T00:00:00Z").plusSeconds(it * 3600) }
        val clouds = hours.mapIndexed { i, _ -> if (i in 6..17) 25 else 100 }
        assertEquals(9.0, requireNotNull(estimateCloudAdjustedDaylight(rise, set, hours, clouds)), 0.001)
    }

    @Test fun cloudAdjustedSunshine_rejectsIncompleteCloudData() {
        val hours = (6L..8L).map { rise.plusSeconds((it - 6L) * 3600L) }
        assertNull(estimateCloudAdjustedDaylight(rise, set, hours, listOf(0, 0, 0)))
    }

    @Test fun cloudAdjustedSunshine_doesNotCountRepeatedTimestamps() {
        val hours = (0L until 12).map { rise.plusSeconds(it * 3600) }
        assertEquals(6.0, requireNotNull(estimateCloudAdjustedDaylight(rise, set, hours + hours, List(24) { 50 })), 0.001)
    }

    @Test fun cloudAdjustedSunshine_invalidSolarWindowReturnsNull() {
        assertNull(estimateCloudAdjustedDaylight(set, rise, listOf(rise), listOf(0)))
    }

    @Test fun hourlySunshine_isIntervalFollowingLabelAndClippedToDaylight() {
        val solar = com.meteocompare.app.domain.util.ForecastSolarWindow(
            Instant.parse("2026-06-20T12:30:00Z"),
            Instant.parse("2026-06-20T15:30:00Z")
        )
        // 12:00-13:00: 30 minutes of daylight at 40% clouds -> 0.3 h.
        assertEquals(0.3, requireNotNull(estimateHourlySunshineHours(
            Instant.parse("2026-06-20T12:00:00Z"), 40, solar
        )), 0.0001)
        // 13:00-14:00: whole daylight hour, 40% clouds -> 0.6 h.
        assertEquals(0.6, requireNotNull(estimateHourlySunshineHours(
            Instant.parse("2026-06-20T13:00:00Z"), 40, solar
        )), 0.0001)
        assertEquals(0.0, requireNotNull(estimateHourlySunshineHours(
            Instant.parse("2026-06-20T11:00:00Z"), 40, solar
        )), 0.0001)
        assertNull(estimateHourlySunshineHours(
            Instant.parse("2026-06-20T13:00:00Z"), null, solar
        ))
        assertNull(estimateHourlySunshineHours(
            Instant.parse("2026-06-20T13:00:00Z"), 101, solar
        ))
    }

    @Test fun dailyMetrics_followExplicitDateAndIgnoreMissingDays() {
        val daily = DailyForecast(
            dates = listOf(day),
            tempMax = listOf(25.0), tempMin = listOf(15.0),
            precipitationSum = listOf(2.0), windSpeedMax = listOf(15.0),
            windDirection10mDominant = listOf(270),
            sunrise = listOf(rise), sunset = listOf(set),
            precipitationProbabilityMax = listOf(80)
        )
        val hourly = HourlyForecast(
            timestamps = (0L until 12).map { rise.plusSeconds(it * 3600) },
            temperature2m = List(12) { 20.0 }, precipitation = List(12) { 0.0 },
            windSpeed10m = List(12) { 15.0 }, cloudCover = List(12) { 0 }
        )
        val forecast = CityForecast(
            city = City("a", "Example", null, "Example", 48.0, 2.0, "UTC"),
            seriesByModel = mapOf(
                WeatherModel.GFS to ForecastSeries(WeatherModel.GFS, hourly, daily),
                WeatherModel.ICON_GLOBAL to ForecastSeries(WeatherModel.ICON_GLOBAL, hourly, daily)
            )
        )
        val calculator = ConfidenceCalculator(EqualWeighting())
        assertNull(buildLocalityDayMetrics(forecast, day.plusDays(1), calculator))
        val result = requireNotNull(buildLocalityDayMetrics(forecast, day, calculator))
        assertEquals(25.0, result.maxC!!, 0.001)
        assertEquals(15.0, result.minC!!, 0.001)
        assertEquals(15.0, result.maxWindKmh!!, 0.001)
        assertEquals(270, result.windDirectionDeg)
        assertEquals(12.0, result.estimatedSunHours!!, 0.001)
        assertEquals(2, result.modelCount)
        assertEquals(80, result.rainProbabilityPercent)
        assertEquals(0, result.cloudCoverPercent)
        assertTrue(result.agreementPercent == null || result.agreementPercent in 0..100)
    }

    @Test fun localityHourInstant_usesEachCityTimeZoneAndRejectsDstGap() {
        val summer = LocalDate.of(2026, 7, 1)
        assertEquals(Instant.parse("2026-07-01T12:00:00Z"),
            localityHourInstant(summer, 14, ZoneId.of("Europe/Paris")))
        assertEquals(Instant.parse("2026-07-01T13:00:00Z"),
            localityHourInstant(summer, 14, ZoneId.of("Europe/London")))
        assertNull(localityHourInstant(LocalDate.of(2026, 3, 29), 2, ZoneId.of("Europe/Paris")))
        assertEquals(Instant.parse("2026-10-25T00:00:00Z"),
            localityHourInstant(LocalDate.of(2026, 10, 25), 2, ZoneId.of("Europe/Paris")))
        assertNull(localityHourInstant(summer, 24, ZoneId.of("UTC")))
    }

    @Test fun hourlyMetrics_arePerHourAndUseIndependentUv() {
        val date = LocalDate.of(2026, 6, 20)
        val target = Instant.parse("2026-06-20T14:00:00Z")
        val daily = DailyForecast(
            dates = listOf(date), tempMax = listOf(28.0), tempMin = listOf(15.0),
            precipitationSum = listOf(12.0), windSpeedMax = listOf(48.0),
            windDirection10mDominant = listOf(350),
            sunrise = listOf(rise), sunset = listOf(set)
        )
        fun series(model: WeatherModel, temp: Double, rain: Double, clouds: Int, probability: Int) =
            ForecastSeries(model, HourlyForecast(
                timestamps = listOf(target), temperature2m = listOf(temp),
                precipitation = listOf(rain), windSpeed10m = listOf(18.0),
                windDirection10m = listOf(if (model == WeatherModel.GFS) 350 else 10),
                precipitationProbability = listOf(probability), cloudCover = listOf(clouds)
            ), daily)
        val forecast = CityForecast(
            City("hour", "Hour", null, "France", 48.0, 2.0, "UTC"),
            mapOf(
                WeatherModel.GFS to series(WeatherModel.GFS, 19.0, 2.0, 40, 30),
                WeatherModel.ICON_GLOBAL to series(WeatherModel.ICON_GLOBAL, 21.0, 4.0, 60, 70)
            )
        )
        val uv = UvForecast(mapOf(target to 6.2), mapOf(date to 7.8))
        val calculator = ConfidenceCalculator(EqualWeighting())
        val hourly = requireNotNull(buildLocalityHourMetrics(forecast, date, 14, calculator, uv))
        assertEquals(target, hourly.instant)
        assertEquals(20.0, hourly.temperatureC!!, 0.001)
        assertEquals(3.0, hourly.precipitationMm!!, 0.001)
        assertEquals(50, hourly.rainProbabilityPercent)
        assertEquals(50, hourly.cloudCoverPercent)
        assertEquals(18.0, hourly.windKmh!!, 0.001)
        assertEquals(0, hourly.windDirectionDeg)
        assertEquals(0.5, hourly.estimatedSunHours!!, 0.001)
        assertEquals(6.2, hourly.uvIndex!!, 0.001)
        assertEquals(2, hourly.modelCount)
        assertNotNull(hourly.agreementPercent)
        val dayMetrics = requireNotNull(buildLocalityDayMetrics(forecast, date, calculator, uv))
        assertEquals(7.8, dayMetrics.uvMax!!, 0.001)
        assertEquals(350, dayMetrics.windDirectionDeg)
        assertNull(buildLocalityHourMetrics(forecast, date, 13, calculator, uv))
    }

    @Test fun hourlyUv_respectsFractionalTimeZoneOffsets() {
        val zone = ZoneId.of("Asia/Kathmandu")
        val date = LocalDate.of(2026, 6, 20)
        val localHourInstant = requireNotNull(localityHourInstant(date, 14, zone))
        assertEquals(Instant.parse("2026-06-20T08:15:00Z"), localHourInstant)
        val daily = DailyForecast(
            dates = listOf(date), tempMax = listOf(null), tempMin = listOf(null),
            precipitationSum = listOf(null), windSpeedMax = listOf(null)
        )
        val hourly = HourlyForecast(
            timestamps = listOf(localHourInstant), temperature2m = listOf(22.0),
            precipitation = listOf(null), windSpeed10m = listOf(null)
        )
        val forecast = CityForecast(
            City("nepal", "Kathmandu", null, "Nepal", 27.7, 85.3, zone.id),
            mapOf(WeatherModel.GFS to ForecastSeries(WeatherModel.GFS, hourly, daily))
        )
        val uv = UvForecast(mapOf(localHourInstant to 9.5), emptyMap())
        assertEquals(9.5, buildLocalityHourMetrics(
            forecast, date, 14, ConfidenceCalculator(EqualWeighting()), uv
        )!!.uvIndex!!, 0.001)
    }

    @Test fun hourlyMetrics_ignoreInvalidValuesAndNeverManufactureUv() {
        val target = Instant.parse("2026-06-20T12:00:00Z")
        val forecast = cloudForecast(
            day, "UTC", List(24) { if (it == 12) 50 else 110 },
            timestamps = (0L until 24).map { day.atStartOfDay(ZoneId.of("UTC")).toInstant().plusSeconds(it * 3600) }
        )
        val result = requireNotNull(buildLocalityHourMetrics(
            forecast, day, 12, ConfidenceCalculator(EqualWeighting())
        ))
        assertEquals(target, result.instant)
        assertNull(result.temperatureC)
        assertNull(result.rainProbabilityPercent)
        assertNull(result.uvIndex)
        assertNull(result.agreementPercent)
        assertEquals(50, result.cloudCoverPercent)
    }

    private fun cloudForecast(
        date: LocalDate,
        zone: String,
        firstClouds: List<Int?>,
        secondClouds: List<Int?>? = null,
        timestamps: List<Instant> = (0L until 24).map {
            date.atStartOfDay(ZoneId.of(zone)).toInstant().plusSeconds(it * 3600)
        }
    ): CityForecast {
        val daily = DailyForecast(
            dates = listOf(date), tempMax = listOf(null), tempMin = listOf(null),
            precipitationSum = listOf(null), windSpeedMax = listOf(null)
        )
        fun series(model: WeatherModel, values: List<Int?>) = ForecastSeries(
            model, HourlyForecast(
                timestamps = timestamps, temperature2m = emptyList(),
                precipitation = emptyList(), windSpeed10m = emptyList(), cloudCover = values
            ), daily
        )
        val models = mutableMapOf(WeatherModel.GFS to series(WeatherModel.GFS, firstClouds))
        if (secondClouds != null) models[WeatherModel.ICON_GLOBAL] =
            series(WeatherModel.ICON_GLOBAL, secondClouds)
        return CityForecast(City("clouds", "Clouds", null, "France", 48.0, 2.0, zone), models)
    }

    @Test fun cloudCover_isMedianOfDailyModelMeans() {
        val forecast = cloudForecast(day, "UTC", List(24) { 20 }, List(24) { 80 })
        assertEquals(50, localityDailyCloudCoverPercent(forecast, day))
        assertNull(localityDailyCloudCoverPercent(forecast, day.plusDays(1)))
    }

    @Test fun cloudCover_rejectsSparseDataAndInvalidValues() {
        val forecast = cloudForecast(day, "UTC", List(5) { 70 })
        assertNull(localityDailyCloudCoverPercent(forecast, day))
        val invalid = cloudForecast(day, "UTC", List(24) { if (it < 13) 101 else 75 })
        assertNull(localityDailyCloudCoverPercent(invalid, day))
    }

    @Test fun cloudCover_ignoresDuplicatedHoursAndIncompleteModel() {
        val timestamps = (0L until 24).map { rise.plusSeconds(it * 3600) }
        val forecast = cloudForecast(
            day, "UTC", List(24) { 30 } + List(24) { 100 }, List(3) { 90 },
            timestamps = timestamps + timestamps
        )
        assertEquals(30, localityDailyCloudCoverPercent(forecast, day))
    }

    @Test fun cloudCover_respectsCityLocalDateAtUtcBoundary() {
        val localStart = day.atStartOfDay(ZoneId.of("Europe/Paris")).toInstant()
        val timestamps = (0L until 12).map { localStart.plusSeconds(it * 3600) }
        val forecast = cloudForecast(day, "Europe/Paris", List(12) { 45 }, timestamps = timestamps)
        assertEquals(45, localityDailyCloudCoverPercent(forecast, day))
    }

    @Test fun cloudCover_respects23And25HourDaysAtDst() {
        val zone = ZoneId.of("Europe/Paris")
        val spring = LocalDate.of(2026, 3, 29)
        val fall = LocalDate.of(2026, 10, 25)
        fun firstTwelve(date: LocalDate) = (0L until 12).map {
            date.atStartOfDay(zone).toInstant().plusSeconds(it * 3600)
        }
        assertEquals(60, localityDailyCloudCoverPercent(
            cloudForecast(spring, zone.id, List(12) { 60 }, timestamps = firstTwelve(spring)), spring
        ))
        assertNull(localityDailyCloudCoverPercent(
            cloudForecast(fall, zone.id, List(12) { 60 }, timestamps = firstTwelve(fall)), fall
        ))
    }

    @Test fun dailyRainAmountCanInferAgreementEvenWithoutNativeProbability() {
        // The comparison deliberately uses a model-agreement estimate when the
        // source provides a rain amount but no native probability. It must not
        // be confused with missing rain data (covered by the following test).
        val daily = DailyForecast(
            dates = listOf(day), tempMax = listOf(null), tempMin = listOf(null),
            precipitationSum = listOf(3.0), windSpeedMax = listOf(null),
            precipitationProbabilityMax = listOf(null)
        )
        val hourly = HourlyForecast(
            timestamps = emptyList(), temperature2m = emptyList(),
            precipitation = emptyList(), windSpeed10m = emptyList()
        )
        val forecast = CityForecast(
            City("rain", "Rain", null, "France", 48.0, 2.0, "UTC"),
            mapOf(WeatherModel.GFS to ForecastSeries(WeatherModel.GFS, hourly, daily))
        )
        val result = requireNotNull(buildLocalityDayMetrics(
            forecast, day, ConfidenceCalculator(EqualWeighting())
        ))
        assertEquals(100, result.rainProbabilityPercent)
    }

    @Test fun missingRainProbability_doesNotBecomesZeroPercent() {
        val forecast = cloudForecast(day, "UTC", List(24) { 30 })
        val result = requireNotNull(buildLocalityDayMetrics(
            forecast, day, ConfidenceCalculator(EqualWeighting())
        ))
        assertNull(result.rainProbabilityPercent)
        assertEquals(30, result.cloudCoverPercent)
    }
}
