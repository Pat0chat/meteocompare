package com.meteocompare.app.ui.citylist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class CityListContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun empty_state_shown_when_no_cities() {
        composeRule.setContent {
            MeteoCompareTheme {
                CityListContent(
                    uiState = CityListUiState(items = emptyList()),
                    onCityClick = {}, onAddClick = {}, onSettingsClick = {},
                    onRemoveCity = {}, onRetry = {}, onRefresh = {}
                )
            }
        }
        composeRule.onNodeWithTag(TAG_EMPTY_STATE).assertIsDisplayed()
        composeRule.onNodeWithText("Aucune ville en favoris").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ADD_FAB).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SETTINGS_BUTTON).assertIsDisplayed()
    }

    @Test
    fun list_shown_when_cities_present() {
        val items = listOf(
            CityCardState(
                city = City(id = "1", name = "Paris", country = "France",
                    latitude = 48.85, longitude = 2.35),
                forecast = ForecastState.Loading
            ),
            CityCardState(
                city = City(id = "2", name = "Lyon", country = "France",
                    latitude = 45.75, longitude = 4.85),
                forecast = ForecastState.Loaded(
                    DayConfidence(
                        date = LocalDate.now(),
                        tempMax = ConfidenceScore(85, 25.0, 27.0, 26.0, 0.7, 5),
                        tempMin = null,
                        precipitation = PrecipitationConfidence.NoRain(100, 5, 0.0),
                        windMax = null
                    )
                )
            )
        )

        composeRule.setContent {
            MeteoCompareTheme {
                CityListContent(
                    uiState = CityListUiState(items = items),
                    onCityClick = {}, onAddClick = {}, onSettingsClick = {},
                    onRemoveCity = {}, onRetry = {}, onRefresh = {}
                )
            }
        }

        composeRule.onNodeWithText("Paris").assertIsDisplayed()
        composeRule.onNodeWithText("Lyon").assertIsDisplayed()
        composeRule.onNodeWithText("25-27°").assertIsDisplayed()
    }

    @Test
    fun add_fab_click_triggers_callback() {
        var addClicked = false
        composeRule.setContent {
            MeteoCompareTheme {
                CityListContent(
                    uiState = CityListUiState(items = emptyList()),
                    onCityClick = {}, onAddClick = { addClicked = true },
                    onSettingsClick = {}, onRemoveCity = {}, onRetry = {}, onRefresh = {}
                )
            }
        }
        composeRule.onNodeWithTag(TAG_ADD_FAB).performClick()
        assert(addClicked) { "onAddClick should have been invoked" }
    }

    @Test
    fun settings_click_triggers_callback() {
        var settingsClicked = false
        composeRule.setContent {
            MeteoCompareTheme {
                CityListContent(
                    uiState = CityListUiState(items = emptyList()),
                    onCityClick = {}, onAddClick = {},
                    onSettingsClick = { settingsClicked = true },
                    onRemoveCity = {}, onRetry = {}, onRefresh = {}
                )
            }
        }
        composeRule.onNodeWithTag(TAG_SETTINGS_BUTTON).performClick()
        assert(settingsClicked) { "onSettingsClick should have been invoked" }
    }

    @Test
    fun clicking_card_invokes_callback_with_city_id() {
        var clickedId: String? = null
        val items = listOf(
            CityCardState(
                city = City(id = "42", name = "Marseille", country = "France",
                    latitude = 43.3, longitude = 5.4),
                forecast = ForecastState.Loading
            )
        )
        composeRule.setContent {
            MeteoCompareTheme {
                CityListContent(
                    uiState = CityListUiState(items = items),
                    onCityClick = { clickedId = it },
                    onAddClick = {}, onSettingsClick = {},
                    onRemoveCity = {}, onRetry = {}, onRefresh = {}
                )
            }
        }
        composeRule.onNodeWithTag("${TAG_CITY_CARD}42").performClick()
        assert(clickedId == "42") { "Expected '42', got '$clickedId'" }
    }
}
