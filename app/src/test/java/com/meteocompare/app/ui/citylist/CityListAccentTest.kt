package com.meteocompare.app.ui.citylist

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.WeatherCondition
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CityListAccentTest {

    @Test
    fun `la selection tablette determine la condition d'accent`() {
        val state = CityListUiState(
            items = listOf(
                cityState("paris", WeatherCondition.CLEAR),
                cityState("brest", WeatherCondition.RAIN)
            )
        )

        assertEquals(
            WeatherCondition.RAIN,
            state.weatherAccentCondition(selectedCityId = "brest")
        )
    }

    @Test
    fun `une selection encore en chargement n'emprunte pas la couleur d'une autre ville`() {
        val state = CityListUiState(
            items = listOf(
                CityCardState(city("paris"), ForecastState.Loading),
                cityState("brest", WeatherCondition.RAIN)
            )
        )

        assertNull(state.weatherAccentCondition(selectedCityId = "paris"))
    }

    @Test
    fun `sur telephone la premiere condition disponible fournit l'accent`() {
        val state = CityListUiState(
            items = listOf(
                CityCardState(city("paris"), ForecastState.Loading),
                cityState("brest", WeatherCondition.OVERCAST)
            )
        )

        assertEquals(
            WeatherCondition.OVERCAST,
            state.weatherAccentCondition(selectedCityId = null)
        )
    }

    private fun cityState(id: String, condition: WeatherCondition) = CityCardState(
        city = city(id),
        forecast = ForecastState.Loaded(
            today = DayConfidence(
                date = LocalDate.of(2026, 9, 9),
                tempMax = null,
                tempMin = null,
                precipitation = null,
                windMax = null
            ),
            currentTemp = null,
            currentCondition = condition
        )
    )

    private fun city(id: String) = City(
        id = id,
        name = id,
        country = "France",
        latitude = 0.0,
        longitude = 0.0
    )
}
