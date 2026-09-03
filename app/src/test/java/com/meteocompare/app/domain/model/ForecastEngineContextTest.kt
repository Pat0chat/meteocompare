package com.meteocompare.app.domain.model

import org.junit.Assert.assertNotEquals
import org.junit.Test

class ForecastEngineContextTest {

    @Test
    fun `cache signature distinguishes real zero from missing calibration value`() {
        val base = ForecastCalibrationProfile(
            bias = 0.0,
            score = 0,
            standardDeviation = 0.0,
            meanAbsoluteError = 0.0,
            sampleSize = 30,
            leadDay = 1
        )
        val missingWet = ForecastEngineContext(
            engine = ForecastEngine.CALIBRATION,
            calibrationByLeadDay = mapOf(
                ForecastEngineVariable.PRECIPITATION to mapOf(
                    1 to mapOf(WeatherModel.GFS to base.copy(wetHitBias = null))
                )
            )
        )
        val zeroWet = ForecastEngineContext(
            engine = ForecastEngine.CALIBRATION,
            calibrationByLeadDay = mapOf(
                ForecastEngineVariable.PRECIPITATION to mapOf(
                    1 to mapOf(
                        WeatherModel.GFS to base.copy(
                            wetHitBias = 0.0,
                            wetHitScore = 0,
                            wetHitStandardDeviation = 0.0,
                            wetHitMeanAbsoluteError = 0.0,
                            wetHitSampleSize = 30
                        )
                    )
                )
            )
        )

        assertNotEquals(missingWet.cacheSignature, zeroWet.cacheSignature)
    }

    @Test
    fun `cache signature includes lead day profiles`() {
        val profile = ForecastCalibrationProfile(
            bias = 1.0,
            score = 80,
            standardDeviation = 1.0,
            meanAbsoluteError = 1.0,
            sampleSize = 30
        )
        val j1 = ForecastEngineContext(
            engine = ForecastEngine.CALIBRATION,
            calibrationByLeadDay = mapOf(
                ForecastEngineVariable.TEMPERATURE to mapOf(
                    1 to mapOf(WeatherModel.ECMWF to profile.copy(leadDay = 1))
                )
            )
        )
        val j3 = ForecastEngineContext(
            engine = ForecastEngine.CALIBRATION,
            calibrationByLeadDay = mapOf(
                ForecastEngineVariable.TEMPERATURE to mapOf(
                    3 to mapOf(WeatherModel.ECMWF to profile.copy(leadDay = 3))
                )
            )
        )

        assertNotEquals(j1.cacheSignature, j3.cacheSignature)
    }
}
