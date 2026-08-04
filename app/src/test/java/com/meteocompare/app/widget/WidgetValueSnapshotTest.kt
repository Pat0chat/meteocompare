package com.meteocompare.app.widget

import com.meteocompare.app.ui.citydetail.ForecastInsight
import com.meteocompare.app.ui.citydetail.ForecastInsightKind
import com.meteocompare.app.ui.citydetail.ForecastInsightLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetValueSnapshotTest {

    @Test
    fun `le widget privilegie une alerte plus lointaine a une info proche`() {
        val info = ForecastInsight(
            kind = ForecastInsightKind.WEATHER_CHANGE,
            level = ForecastInsightLevel.INFO,
            priority = 90
        )
        val alert = ForecastInsight(
            kind = ForecastInsightKind.RAIN_LIKELY,
            level = ForecastInsightLevel.ALERT,
            priority = 70
        )

        assertEquals(alert, selectWidgetKeyInsight(listOf(info, alert)))
    }

    @Test
    fun `a niveau egal la priorite editoriale departage les insights`() {
        val lowerPriority = ForecastInsight(
            kind = ForecastInsightKind.WIND_EVENT,
            level = ForecastInsightLevel.WATCH,
            priority = 60
        )
        val higherPriority = ForecastInsight(
            kind = ForecastInsightKind.RAIN_UNCERTAIN,
            level = ForecastInsightLevel.WATCH,
            priority = 88
        )

        assertEquals(higherPriority, selectWidgetKeyInsight(listOf(lowerPriority, higherPriority)))
    }

    @Test
    fun `une liste vide ne produit pas de signal principal`() {
        assertTrue(selectWidgetKeyInsight(emptyList()) == null)
    }

    @Test
    fun `le resolver retourne le widget insight specialise`() {
        assertTrue(
            glanceWidgetForProviderClassName(MeteoInsightWidgetReceiver::class.java.name) is
                MeteoInsightWidget
        )
        assertTrue(glanceWidgetForProviderClassName(null) is MeteoWidget)
        assertTrue(isInsightWidgetProvider(MeteoInsightWidgetReceiver::class.java.name))
        assertTrue(!isInsightWidgetProvider(MeteoWidgetReceiver4x2::class.java.name))
    }
}
