package com.meteocompare.app.ui.citydetail

import org.junit.Assert.assertTrue
import org.junit.Test

class CombinedTemperatureDispersionDomainTest {
    @Test
    fun `shared scale includes feels-like values colder than air temperatures`() {
        val (min, max) = combinedTemperatureDispersionDomain(
            primaryMin = 12.0,
            primaryMax = 18.0,
            apparentMin = 5.0,
            apparentMax = 9.0,
            apparentCentral = 7.0
        )
        assertTrue(min < 5.0)
        assertTrue(max > 18.0)
    }

    @Test
    fun `shared scale includes hotter feels-like extremes`() {
        val (min, max) = combinedTemperatureDispersionDomain(
            primaryMin = 28.0,
            primaryMax = 32.0,
            apparentMin = 34.0,
            apparentMax = 41.0,
            apparentCentral = 38.0
        )
        assertTrue(min < 28.0)
        assertTrue(max > 41.0)
    }

    @Test
    fun `missing felt values preserve the primary domain`() {
        val base = combinedTemperatureDispersionDomain(10.0, 12.0, null, null, null)
        val invalid = combinedTemperatureDispersionDomain(10.0, 12.0, Double.NaN, Double.POSITIVE_INFINITY, null)
        assertTrue(base.first < 10.0 && base.second > 12.0)
        assertTrue(base == invalid)
    }
}
