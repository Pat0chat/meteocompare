package com.meteocompare.app.widget

import com.meteocompare.app.data.repository.CurrentLocationStore
import com.meteocompare.app.domain.model.City
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentLocationWidgetConfigurationTest {
    private val current = City(id = "current:977:47", name = "My location", country = "", latitude = 48.85, longitude = 2.35)
    private val city = City(id = "2988507", name = "Paris", country = "France", latitude = 48.85, longitude = 2.35)

    @Test fun `logical current widget id survives moving grid coordinates`() {
        val config = WidgetConfiguration(cityId = CurrentLocationStore.WIDGET_CITY_ID)
        assertEquals(CurrentLocationStore.WIDGET_CITY_ID, config.selectableCityId(listOf(current, city)))
        assertEquals(CurrentLocationStore.WIDGET_CITY_ID, config.selectableCityId(listOf(current.copy(id = "current:915:96"), city)))
    }

    @Test fun `missing current location does not select a different favorite`() {
        assertNull(WidgetConfiguration(cityId = CurrentLocationStore.WIDGET_CITY_ID).selectableCityId(listOf(city)))
    }

    @Test fun `fixed city selection is unchanged`() {
        assertEquals(city.id, WidgetConfiguration(cityId = city.id).selectableCityId(listOf(current, city)))
    }
}
