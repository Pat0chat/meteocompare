package com.meteocompare.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApparentTemperatureTest {
    @Test fun significantDifferenceUsesCelsiusAndExactThreshold() {
        assertFalse(ApparentTemperature.significant(20.0, 21.99))
        assertTrue(ApparentTemperature.significant(20.0, 22.0))
        assertTrue(ApparentTemperature.significant(20.0, 18.0))
        assertFalse(ApparentTemperature.significant(20.0, null))
        assertFalse(ApparentTemperature.significant(Double.NaN, 10.0))
    }

    @Test fun medianExcludesMissingAndNonFiniteModels() {
        assertNull(ApparentTemperature.median(emptyList()))
        assertNull(ApparentTemperature.median(listOf(Double.NaN, Double.POSITIVE_INFINITY)))
        assertEquals(17.0, ApparentTemperature.median(listOf(15.0, 17.0, 20.0))!!, 0.0)
        assertEquals(16.5, ApparentTemperature.median(listOf(15.0, 18.0, Double.NaN))!!, 0.0)
    }
}
