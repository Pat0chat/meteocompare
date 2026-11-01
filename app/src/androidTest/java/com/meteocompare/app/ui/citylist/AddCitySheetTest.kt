package com.meteocompare.app.ui.citylist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
    fun current_location_requires_an_explicit_click() {
        var clicked = 0
        composeRule.setContent {
            MeteoCompareTheme {
                AddCitySheet(
                    state = AddCityUiState(),
                    onQueryChanged = {},
                    onCitySelected = {},
                    onCurrentLocationSelected = { clicked++ },
                    onDismiss = {}
                )
            }
        }
        assertEquals(0, clicked)
        composeRule.onNodeWithTag("add_current_location").performClick()
        assertEquals(1, clicked)
    }

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
    @Test
    fun same_named_cities_in_same_country_show_two_distinct_location_lines() {
        val first = TestFixtures.paris.copy(
            id = "city-a", name = "Saint-Martin", country = "France",
            admin1 = "Normandie", departmentName = "Manche", postalCode = "50300"
        )
        val second = first.copy(
            id = "city-b", admin1 = "Bretagne", departmentName = "Finistère", postalCode = "29270"
        )
        composeRule.setContent {
            MeteoCompareTheme {
                AddCitySheet(
                    state = AddCityUiState(query = "Saint-Martin", results = listOf(first, second)),
                    onQueryChanged = {}, onCitySelected = {}, onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithTag("add_city_location_city-a", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("France", useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithText("Normandie · Manche · 50300", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Bretagne · Finistère · 29270", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription(
            "France, Normandie, Manche, 50300", useUnmergedTree = true
        ).assertExists()
        composeRule.onNodeWithContentDescription(
            "France, Bretagne, Finistère, 29270", useUnmergedTree = true
        ).assertExists()
    }

    @Test
    fun search_results_keep_two_single_line_labels_on_narrow_phones() {
        val city = TestFixtures.paris.copy(
            id = "narrow-city", name = "Saint-Martin", country = "France",
            admin1 = "Normandie", departmentName = "Manche", admin3 = "Avranches",
            postalCode = "50300"
        )
        composeRule.setContent {
            MeteoCompareTheme {
                Box(Modifier.width(180.dp)) {
                    CityResultRow(city = city, onClick = {})
                }
            }
        }
        val firstLine = composeRule.onNodeWithText("France", useUnmergedTree = true)
        val secondLine = composeRule.onNodeWithText(
            "Normandie · Manche · Avranches · 50300", useUnmergedTree = true
        )
        firstLine.assertIsDisplayed()
        secondLine.assertExists() // Content may be ellipsized, but not lost.
        val first = firstLine.fetchSemanticsNode().boundsInRoot
        val second = secondLine.fetchSemanticsNode().boundsInRoot
        assertTrue("The country must appear above the subdivisions", second.top >= first.bottom - 1f)
        for (node in listOf(firstLine, secondLine)) {
            val layouts = mutableListOf<TextLayoutResult>()
            val action = node.fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action
            assertTrue("Each location label must expose its text layout", action?.invoke(layouts) == true)
            assertEquals(1, layouts.single().lineCount)
        }
        composeRule.onNodeWithContentDescription(
            "France, Normandie, Manche, Avranches, 50300", useUnmergedTree = true
        ).assertExists()
    }

    @Test
    fun search_result_with_country_only_does_not_add_an_empty_second_line() {
        val city = TestFixtures.paris.copy(admin1 = null, country = "France", departmentName = null)
        composeRule.setContent {
            MeteoCompareTheme { CityResultRow(city = city, onClick = {}) }
        }
        composeRule.onNodeWithText("France", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("France", useUnmergedTree = true).assertExists()
    }

}
