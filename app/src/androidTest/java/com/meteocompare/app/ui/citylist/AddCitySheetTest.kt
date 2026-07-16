package com.meteocompare.app.ui.citylist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AddCitySheetTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun result_click_returns_selected_city() {
        var selectedId: String? = null
        composeRule.setContent {
            MeteoCompareTheme {
                AddCitySheet(
                    state = AddCityUiState(query = "Par", results = listOf(TestFixtures.paris)),
                    onQueryChanged = {},
                    onCitySelected = { selectedId = it.id },
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithTag("$TAG_ADD_CITY_RESULT${TestFixtures.paris.id}")
            .assertIsDisplayed()
            .performClick()
        assertEquals(TestFixtures.paris.id, selectedId)
    }

    @Test
    fun text_field_forwards_query_changes() {
        var query = ""
        composeRule.setContent {
            MeteoCompareTheme {
                AddCitySheet(
                    state = AddCityUiState(),
                    onQueryChanged = { query = it },
                    onCitySelected = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithTag(TAG_ADD_CITY_SEARCH_FIELD).performTextInput("Lyon")
        assertEquals("Lyon", query)
    }

    @Test
    fun searching_state_has_dedicated_semantics() {
        composeRule.setContent {
            MeteoCompareTheme {
                AddCitySheet(
                    state = AddCityUiState(query = "Pa", isSearching = true),
                    onQueryChanged = {}, onCitySelected = {}, onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithTag(TAG_ADD_CITY_LOADING).assertIsDisplayed()
    }

    @Test
    fun error_state_has_dedicated_semantics() {
        composeRule.setContent {
            MeteoCompareTheme {
                AddCitySheet(
                    state = AddCityUiState(query = "Pa", error = "Réseau indisponible"),
                    onQueryChanged = {}, onCitySelected = {}, onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithTag(TAG_ADD_CITY_ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("Réseau indisponible").assertIsDisplayed()
    }

    @Test
    fun empty_results_state_has_dedicated_semantics() {
        composeRule.setContent {
            MeteoCompareTheme {
                AddCitySheet(
                    state = AddCityUiState(query = "Pa", results = emptyList()),
                    onQueryChanged = {}, onCitySelected = {}, onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithTag(TAG_ADD_CITY_NO_RESULTS).assertIsDisplayed()
    }
}
