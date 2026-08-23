package com.meteocompare.app.ui.enginecomparison

import com.meteocompare.app.domain.usecase.EngineDivergenceLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class DivergenceMetricScaleTest {
    @Test
    fun `variable thresholds match engine divergence normalization`() {
        assertEquals(EngineDivergenceLevel.LOW, divergenceMetricLevel(1.0, 4.0))
        assertEquals(EngineDivergenceLevel.MEDIUM, divergenceMetricLevel(1.4, 4.0))
        assertEquals(EngineDivergenceLevel.HIGH, divergenceMetricLevel(3.0, 4.0))

        assertEquals(EngineDivergenceLevel.MEDIUM, divergenceMetricLevel(3.0, 8.0))
        assertEquals(EngineDivergenceLevel.HIGH, divergenceMetricLevel(12.0, 15.0))
        assertEquals(EngineDivergenceLevel.LOW, divergenceMetricLevel(10.0, 50.0))
    }

    @Test
    fun `scale progress is clamped and invalid values stay safe`() {
        assertEquals(0f, divergenceMetricProgress(Double.NaN, 4.0))
        assertEquals(0f, divergenceMetricProgress(2.0, 0.0))
        assertEquals(0.5f, divergenceMetricProgress(2.0, 4.0))
        assertEquals(1f, divergenceMetricProgress(8.0, 4.0))
    }
}
