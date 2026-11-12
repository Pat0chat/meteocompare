package com.meteocompare.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarSkyInterpretationTest {
    private fun veiled(
        cloud: Int? = 98, low: Int? = 5, mid: Int? = 12, high: Int? = 95,
        sunshineSeconds: Double? = 1500.0, dni: Double? = 280.0
    ) = SolarSkyInterpretation.isVeiledSky(cloud, low, mid, high, sunshineSeconds, dni)

    @Test fun `98 percent can be a luminous high-cloud veil`() {
        assertTrue(veiled())
        assertEquals(WeatherCondition.VEILED,
            SolarSkyInterpretation.refine(WeatherCondition.OVERCAST, veiled()))
        // The reported percentage stays 98% in the forecast model.
        assertEquals(WeatherCondition.OVERCAST, WeatherCondition.fromCloudCover(98.0))
    }

    @Test fun `opaque low-cloud cover is not a veil`() {
        assertFalse(veiled(low = 90, high = 90))
    }

    @Test fun `middle cloud deck is not called translucent high veil`() {
        assertFalse(veiled(mid = 80))
    }

    @Test fun `solar evidence is required and cannot be fabricated`() {
        assertFalse(veiled(sunshineSeconds = null, dni = null))
        assertFalse(veiled(sunshineSeconds = 0.0, dni = 450.0))
        assertTrue(veiled(sunshineSeconds = null, dni = 250.0))
        assertFalse(veiled(low = null))
        assertFalse(veiled(cloud = 80))
    }

    @Test fun `cloud thresholds have explicit and stable boundaries`() {
        val cases = listOf(
            0.0 to WeatherCondition.CLEAR,
            19.999 to WeatherCondition.CLEAR,
            20.0 to WeatherCondition.MAINLY_CLEAR,
            44.999 to WeatherCondition.MAINLY_CLEAR,
            45.0 to WeatherCondition.PARTLY_CLOUDY,
            89.999 to WeatherCondition.PARTLY_CLOUDY,
            90.0 to WeatherCondition.OVERCAST,
            98.0 to WeatherCondition.OVERCAST,
            100.0 to WeatherCondition.OVERCAST
        )
        cases.forEach { (fraction, condition) ->
            assertEquals("Cloud boundary at $fraction", condition, WeatherCondition.fromCloudCover(fraction))
        }
    }

    @Test fun `veil requires all independent thresholds and cannot hide wet weather`() {
        assertTrue(veiled(low = 25, mid = 35, high = 80, sunshineSeconds = 900.0))
        assertFalse(veiled(low = 26))
        assertFalse(veiled(mid = 36))
        assertFalse(veiled(high = 79))
        assertFalse(veiled(sunshineSeconds = 899.0, dni = 1000.0))
        assertFalse(veiled(sunshineSeconds = null, dni = 159.0))
        assertTrue(veiled(sunshineSeconds = null, dni = 160.0))
        assertEquals(WeatherCondition.RAIN,
            SolarSkyInterpretation.refine(WeatherCondition.RAIN, true))
    }

    @Test fun `fog precipitation and thunderstorms always retain their alert`() {
        for (condition in listOf(
            WeatherCondition.FOG, WeatherCondition.DRIZZLE, WeatherCondition.RAIN,
            WeatherCondition.SNOW, WeatherCondition.FREEZING_RAIN, WeatherCondition.THUNDERSTORM
        )) {
            assertEquals(condition, SolarSkyInterpretation.refine(condition, true))
        }
    }
}
