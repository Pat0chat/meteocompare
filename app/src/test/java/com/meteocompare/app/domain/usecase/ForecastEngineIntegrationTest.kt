package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastCalibrationProfile
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.ForecastEngineVariable
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastEngineIntegrationTest {
    private val city = City(
        id = "paris",
        name = "Paris",
        country = "France",
        latitude = 48.8566,
        longitude = 2.3522,
        timezone = "Europe/Paris"
    )
    private val calculator = ConfidenceCalculator(EqualWeighting())

    private fun profile(bias: Double) = ForecastCalibrationProfile(
        bias = bias,
        score = 80,
        standardDeviation = 1.0,
        meanAbsoluteError = 1.0,
        sampleSize = 30
    )

    @Test
    fun `selected engine changes central value but never raw convergence or spread`() {
        val date = LocalDate.of(2026, 8, 24)
        val forecast = dailyForecast(listOf(date))
        val calibration = forecast.seriesByModel.keys.associateWith { profile(bias = 4.0) }
        val multi = calculator.dayConfidence(
            forecast,
            date,
            ForecastEngineContext(engine = ForecastEngine.MULTI_CONSENSUS)
        )
        val calibrated = calculator.dayConfidence(
            forecast,
            date,
            ForecastEngineContext(
                engine = ForecastEngine.CALIBRATION,
                calibrationByVariable = mapOf(
                    ForecastEngineVariable.TEMPERATURE to calibration,
                    ForecastEngineVariable.WIND to calibration
                )
            )
        )

        // V3 peut changer les centrales journalières compatibles avec l'historique J+1...
        assertNotEquals(multi.tempMax!!.meanValue, calibrated.tempMax!!.meanValue, 1e-6)
        assertNotEquals(multi.windMax!!.meanValue, calibrated.windMax!!.meanValue, 1e-6)
        // ...mais jamais Tmin/rafales : l'audit 1.16 interdit d'y appliquer un biais d'une autre sémantique.
        assertEquals(multi.tempMin!!.meanValue, calibrated.tempMin!!.meanValue, 1e-9)
        assertEquals(multi.windGustMax!!.meanValue, calibrated.windGustMax!!.meanValue, 1e-9)
        // Et toute la dispersion/convergence reste celle des modèles bruts.
        assertEquals(multi.tempMax!!.convergencePercent, calibrated.tempMax!!.convergencePercent)
        assertEquals(multi.tempMax!!.minValue, calibrated.tempMax!!.minValue, 1e-9)
        assertEquals(multi.tempMax!!.maxValue, calibrated.tempMax!!.maxValue, 1e-9)
        assertEquals(multi.tempMax!!.stdDev, calibrated.tempMax!!.stdDev, 1e-9)
    }

    @Test
    fun `daily j plus one calibration is never applied to current hourly temperature`() {
        val now = Instant.parse("2026-08-23T05:00:00Z")
        val forecast = hourlyForecast(now)
        val hugeCalibration = forecast.seriesByModel.keys.associateWith { profile(bias = 20.0) }
        val multi = calculator.currentTemperature(
            forecast,
            now,
            ForecastEngineContext(engine = ForecastEngine.MULTI_CONSENSUS)
        )
        val calibrationSelected = calculator.currentTemperature(
            forecast,
            now,
            ForecastEngineContext(
                engine = ForecastEngine.CALIBRATION,
                calibrationByVariable = mapOf(ForecastEngineVariable.TEMPERATURE to hugeCalibration)
            )
        )

        assertEquals(multi!!, calibrationSelected!!, 1e-9)
    }

    @Test
    fun `daily j plus one calibration is never applied to any hourly series`() {
        val now = Instant.parse("2026-08-23T05:00:00Z")
        val forecast = hourlyForecast(now)
        val hugeCalibration = forecast.seriesByModel.keys.associateWith { profile(bias = 20.0) }
        val multiContext = ForecastEngineContext(engine = ForecastEngine.MULTI_CONSENSUS)
        val calibratedContext = ForecastEngineContext(
            engine = ForecastEngine.CALIBRATION,
            calibrationByVariable = mapOf(
                ForecastEngineVariable.TEMPERATURE to hugeCalibration,
                ForecastEngineVariable.WIND to hugeCalibration,
                ForecastEngineVariable.PRECIPITATION to hugeCalibration
            )
        )

        val multiTemp = calculator.hourlyTemperatureConfidence(forecast, engineContext = multiContext).single()
        val calibratedTemp = calculator.hourlyTemperatureConfidence(forecast, engineContext = calibratedContext).single()
        val multiWind = calculator.hourlyWindConfidence(forecast, engineContext = multiContext).single()
        val calibratedWind = calculator.hourlyWindConfidence(forecast, engineContext = calibratedContext).single()
        val multiRain = calculator.hourlyPrecipitationConfidence(forecast, engineContext = multiContext).single()
        val calibratedRain = calculator.hourlyPrecipitationConfidence(forecast, engineContext = calibratedContext).single()

        assertEquals(multiTemp, calibratedTemp)
        assertEquals(multiWind, calibratedWind)
        assertEquals(multiRain, calibratedRain)
    }

    @Test
    fun `engine comparison uses same forecast filters past days and is limited to seven days`() {
        val now = Instant.parse("2026-08-23T05:00:00Z") // 07:00 Europe/Paris
        val dates = (22..31).map { LocalDate.of(2026, 8, it) }
        val forecast = dailyForecast(dates)
        val calibration = forecast.seriesByModel.keys.associateWith { profile(bias = 2.0) }
        val context = ForecastEngineContext(
            engine = ForecastEngine.ADAPTIVE,
            calibrationByVariable = mapOf(
                ForecastEngineVariable.TEMPERATURE to calibration,
                ForecastEngineVariable.WIND to calibration,
                ForecastEngineVariable.PRECIPITATION to calibration
            )
        )

        val sourceSnapshot = forecast.copy(
            seriesByModel = forecast.seriesByModel.mapValues { (_, series) ->
                series.copy(
                    hourly = series.hourly.copy(),
                    daily = series.daily.copy()
                )
            }
        )
        val days = EngineComparisonBuilder(calculator).build(forecast, context, now)

        assertEquals(sourceSnapshot, forecast)
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 8, 23), days.first().date)
        assertEquals(LocalDate.of(2026, 8, 29), days.last().date)
        days.forEach { day ->
            assertEquals(ForecastEngine.entries.toSet(), day.byEngine.keys)
            assertTrue(day.divergence.score >= 0.0)
        }
    }

    private fun dailyForecast(dates: List<LocalDate>): CityForecast {
        val valuesByModel = linkedMapOf(
            WeatherModel.GFS to 20.0,
            WeatherModel.ECMWF to 21.0,
            WeatherModel.ARPEGE_EUROPE to 22.0,
            WeatherModel.UKMO_GLOBAL to 23.0
        )
        return CityForecast(
            city = city,
            seriesByModel = valuesByModel.mapValues { (model, base) ->
                ForecastSeries(
                    model = model,
                    hourly = HourlyForecast(emptyList(), emptyList(), emptyList(), emptyList()),
                    daily = DailyForecast(
                        dates = dates,
                        tempMax = dates.map { base },
                        tempMin = dates.map { base - 8.0 },
                        precipitationSum = dates.map { 2.0 + (base - 20.0) * 0.2 },
                        windSpeedMax = dates.map { 20.0 + (base - 20.0) },
                        precipitationProbabilityMax = dates.map { 60 },
                        windGustsMax = dates.map { 35.0 + (base - 20.0) }
                    )
                )
            }
        )
    }

    private fun hourlyForecast(now: Instant): CityForecast {
        val valuesByModel = linkedMapOf(
            WeatherModel.GFS to 20.0,
            WeatherModel.ECMWF to 21.0,
            WeatherModel.ARPEGE_EUROPE to 22.0,
            WeatherModel.UKMO_GLOBAL to 23.0
        )
        return CityForecast(
            city = city,
            seriesByModel = valuesByModel.mapValues { (model, value) ->
                ForecastSeries(
                    model = model,
                    hourly = HourlyForecast(
                        timestamps = listOf(now),
                        temperature2m = listOf(value),
                        precipitation = listOf(0.2),
                        windSpeed10m = listOf(10.0),
                        precipitationProbability = listOf(55)
                    ),
                    daily = DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
                )
            }
        )
    }
}
