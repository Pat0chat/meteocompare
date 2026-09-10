package com.meteocompare.app.core.locale

import com.meteocompare.app.R
import com.meteocompare.app.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WeatherConditionResourcesTest {

    @Test
    fun `chaque condition connue possède un libellé dédié`() {
        WeatherCondition.entries
            .filterNot { it == WeatherCondition.UNKNOWN }
            .forEach { condition ->
                assertNotEquals(
                    "$condition ne doit pas utiliser le libellé inconnu",
                    R.string.weather_unknown,
                    weatherConditionLabelRes(condition)
                )
            }
    }

    @Test
    fun `une condition absente et UNKNOWN utilisent le même libellé`() {
        assertEquals(
            weatherConditionLabelRes(WeatherCondition.UNKNOWN),
            weatherConditionLabelRes(null)
        )
    }
}
