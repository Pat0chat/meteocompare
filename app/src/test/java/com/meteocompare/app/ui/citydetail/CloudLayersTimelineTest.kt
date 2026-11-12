package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLayersTimelineTest {
    private val start = Instant.parse("2026-06-20T10:00:00Z")
    private val city = City("test", "Test", country = "France", latitude = 48.0,
        longitude = 2.0, timezone = "Europe/Paris")

    private fun modelSeries(
        model: WeatherModel,
        total: List<Int?> = listOf(98, 96),
        low: List<Int?> = listOf(3, 5),
        mid: List<Int?> = listOf(9, 12),
        high: List<Int?> = listOf(98, 96),
        solar: List<Double?> = listOf(null, 2400.0, 1800.0),
        dni: List<Double?> = listOf(null, 280.0, 160.0)
    ): ForecastSeries {
        val times = List(3) { start.plusSeconds(it * 3600L) }
        return ForecastSeries(model, HourlyForecast(
            timestamps = times, temperature2m = emptyList(),
            precipitation = emptyList(), windSpeed10m = emptyList(),
            cloudCover = total, cloudCoverLow = low, cloudCoverMid = mid,
            cloudCoverHigh = high, sunshineDuration = solar,
            directNormalIrradiance = dni
        ), DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
    }

    @Test fun `98 percent high veil stays 98 and layers are never summed`() {
        val forecast = CityForecast(city, mapOf(
            WeatherModel.GFS to modelSeries(WeatherModel.GFS),
            WeatherModel.ECMWF to modelSeries(WeatherModel.ECMWF)
        ))
        val result = buildCloudLayersTimeline(forecast, start, 2)
        assertEquals(98, result[0].total)
        assertEquals(3, result[0].low)
        assertEquals(9, result[0].mid)
        assertEquals(98, result[0].high)
        assertEquals(40, result[0].readings.first().sunshineMinutes)
        assertEquals(280, result[0].readings.first().directNormalWm2)
        assertEquals(96, result[1].total)
    }

    @Test fun `solar information is taken from the following timestamp`() {
        val forecast = CityForecast(city, mapOf(WeatherModel.GFS to modelSeries(WeatherModel.GFS)))
        val result = buildCloudLayersTimeline(forecast, start, 2)
        assertEquals(40, result[0].readings.single().sunshineMinutes)
        assertEquals(30, result[1].readings.single().sunshineMinutes)
    }

    @Test fun `no cloud data means no cloud graph rather than fake clear sky`() {
        val model = modelSeries(WeatherModel.GFS, emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(buildCloudLayersTimeline(CityForecast(city, mapOf(WeatherModel.GFS to model)), start).isEmpty())
    }

    @Test fun `missing layers remain unknown even if total cloud is 98`() {
        val model = modelSeries(WeatherModel.GFS, low = emptyList(), mid = emptyList(), high = emptyList())
        val result = buildCloudLayersTimeline(CityForecast(city, mapOf(WeatherModel.GFS to model)), start)
        assertEquals(98, result[0].total)
        assertNull(result[0].low)
        assertNull(result[0].mid)
        assertNull(result[0].high)
    }

    @Test fun `invalid percentages and solar values are ignored`() {
        val model = modelSeries(WeatherModel.GFS, listOf(101, -1),
            low = listOf(20, 25), mid = listOf(null, null), high = listOf(88, 88),
            solar = listOf(null, -200.0, Double.NaN), dni = listOf(null, 2000.0, Double.NaN))
        val result = buildCloudLayersTimeline(CityForecast(city, mapOf(WeatherModel.GFS to model)), start)
        assertNull(result[0].total)
        assertEquals(20, result[0].low)
        assertNull(result[0].readings.single().sunshineMinutes)
        assertNull(result[0].readings.single().directNormalWm2)
    }

    @Test fun `sibling models are not additional independent votes`() {
        val families = mapOf(
            WeatherModel.AROME_FRANCE_HD to modelSeries(WeatherModel.AROME_FRANCE_HD, total = listOf(98)),
            WeatherModel.AROME_FRANCE to modelSeries(WeatherModel.AROME_FRANCE, total = listOf(98)),
            WeatherModel.GFS to modelSeries(WeatherModel.GFS, total = listOf(10)),
            WeatherModel.ECMWF to modelSeries(WeatherModel.ECMWF, total = listOf(10))
        )
        val result = buildCloudLayersTimeline(CityForecast(city, families), start, 1)
        assertEquals(10, result.single().total)
        assertEquals(4, result.single().readings.size)
    }

    @Test fun `duplicate timestamp does not hide later complete valid cloud reading`() {
        val original = modelSeries(WeatherModel.GFS)
        val hourly = original.hourly.copy(
            timestamps = listOf(start, start, start.plusSeconds(3600), start.plusSeconds(3600)),
            cloudCover = listOf(null, 98, null, 96),
            cloudCoverLow = listOf(null, 3, null, 5),
            cloudCoverMid = listOf(null, 9, null, 12),
            cloudCoverHigh = listOf(null, 98, null, 96),
            sunshineDuration = listOf(null, null, null, 1200.0),
            directNormalIrradiance = listOf(null, null, null, 300.0)
        )
        val forecast = CityForecast(city, mapOf(WeatherModel.GFS to original.copy(hourly = hourly)))
        val point = buildCloudLayersTimeline(forecast, start, 1).single()
        assertEquals(98, point.total)
        assertEquals(3, point.low)
        assertEquals(20, point.readings.single().sunshineMinutes)
        assertEquals(300, point.readings.single().directNormalWm2)
    }

    @Test fun `unsupported horizon leaves gaps rather than invented zeroes`() {
        val result = buildCloudLayersTimeline(CityForecast(city,
            mapOf(WeatherModel.GFS to modelSeries(WeatherModel.GFS))), start, 9)
        assertTrue(result.first().hasCloudData)
        assertFalse(result.last().hasCloudData)
        assertNull(result.last().high)
    }
}
