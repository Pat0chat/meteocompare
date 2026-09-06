package com.meteocompare.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Rule
import org.junit.Test

class AdaptiveNavigationContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun smartphone_width_composes_only_the_phone_navigation() {
        composeRule.setContent {
            AdaptiveNavigationContent(
                phoneContent = {
                    Box(Modifier.fillMaxSize().testTag(TAG_TEST_PHONE_CONTENT))
                },
                tabletContent = {
                    Box(Modifier.fillMaxSize().testTag(TAG_TEST_TABLET_CONTENT))
                },
                modifier = Modifier
                    .requiredWidth(839.dp)
                    .height(400.dp)
            )
        }

        composeRule.onNodeWithTag(TAG_TEST_PHONE_CONTENT).assertExists()
        composeRule.onNodeWithTag(TAG_TEST_TABLET_CONTENT).assertDoesNotExist()
    }

    @Test
    fun tablet_width_composes_only_the_master_detail_navigation() {
        composeRule.setContent {
            AdaptiveNavigationContent(
                phoneContent = {
                    Box(Modifier.fillMaxSize().testTag(TAG_TEST_PHONE_CONTENT))
                },
                tabletContent = {
                    Box(Modifier.fillMaxSize().testTag(TAG_TEST_TABLET_CONTENT))
                },
                modifier = Modifier
                    .requiredWidth(840.dp)
                    .height(400.dp)
            )
        }

        composeRule.onNodeWithTag(TAG_TEST_TABLET_CONTENT).assertExists()
        composeRule.onNodeWithTag(TAG_TEST_PHONE_CONTENT).assertDoesNotExist()
    }

    @Test
    fun tablet_shows_both_panes_and_selects_the_first_city() {
        composeRule.setContent {
            MeteoCompareTheme {
                TestMasterDetail(cityIds = listOf("paris", "lyon"))
            }
        }

        composeRule.onNodeWithTag(TAG_TABLET_LAYOUT).assertExists()
        composeRule.onNodeWithTag(TAG_TABLET_LIST_PANE).assertExists()
        composeRule.onNodeWithTag(TAG_TABLET_DETAIL_PANE).assertExists()
        composeRule.onNodeWithTag(TAG_TEST_LIST_SELECTION).assertTextEquals("paris")
        composeRule.onNodeWithTag(TAG_TEST_ACTIVE_CITY).assertTextEquals("paris")
    }

    @Test
    fun tablet_clicking_a_city_replaces_the_detail_content() {
        composeRule.setContent {
            MeteoCompareTheme {
                TestMasterDetail(cityIds = listOf("paris", "lyon"))
            }
        }

        composeRule.onNodeWithTag("$TAG_TEST_SELECT_CITY-lyon").performClick()
        composeRule.onNodeWithTag(TAG_TEST_LIST_SELECTION).assertTextEquals("lyon")
        composeRule.onNodeWithTag(TAG_TEST_ACTIVE_CITY).assertTextEquals("lyon")
    }

    @Test
    fun tablet_falls_back_after_removing_the_selected_city_then_shows_empty_detail() {
        val cityIds = mutableStateOf(listOf("paris", "lyon"))
        composeRule.setContent {
            MeteoCompareTheme {
                TestMasterDetail(cityIds = cityIds.value)
            }
        }

        composeRule.onNodeWithTag("$TAG_TEST_SELECT_CITY-lyon").performClick()
        composeRule.onNodeWithTag(TAG_TEST_ACTIVE_CITY).assertTextEquals("lyon")

        composeRule.runOnIdle { cityIds.value = listOf("paris") }
        composeRule.onNodeWithTag(TAG_TEST_LIST_SELECTION).assertTextEquals("paris")
        composeRule.onNodeWithTag(TAG_TEST_ACTIVE_CITY).assertTextEquals("paris")

        composeRule.runOnIdle { cityIds.value = emptyList() }
        composeRule.onNodeWithTag(TAG_TEST_LIST_SELECTION).assertTextEquals("none")
        composeRule.onNodeWithTag(TAG_TEST_EMPTY_DETAIL).assertExists()
        composeRule.onNodeWithTag(TAG_TEST_ACTIVE_CITY).assertDoesNotExist()
    }

    @Test
    fun tablet_detail_subpage_keeps_the_city_list_pane_visible() {
        composeRule.setContent {
            val showSubpage = remember { mutableStateOf(false) }
            MeteoCompareTheme {
                TabletMasterDetailContent(
                    availableCityIds = listOf("paris"),
                    listContent = { _, _ ->
                        Text("cities", modifier = Modifier.testTag(TAG_TEST_LIST_MARKER))
                    },
                    detailContent = {
                        if (showSubpage.value) {
                            Text("subpage", modifier = Modifier.testTag(TAG_TEST_SUBPAGE))
                        } else {
                            Button(
                                onClick = { showSubpage.value = true },
                                modifier = Modifier.testTag(TAG_TEST_OPEN_SUBPAGE)
                            ) {
                                Text("open")
                            }
                        }
                    },
                    emptyDetailContent = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        composeRule.onNodeWithTag(TAG_TEST_OPEN_SUBPAGE).performClick()
        composeRule.onNodeWithTag(TAG_TEST_SUBPAGE).assertExists()
        composeRule.onNodeWithTag(TAG_TEST_LIST_MARKER).assertExists()
        composeRule.onNodeWithTag(TAG_TABLET_LIST_PANE).assertExists()
    }

    @Composable
    private fun TestMasterDetail(cityIds: List<String>) {
        TabletMasterDetailContent(
            availableCityIds = cityIds,
            listContent = { selectedCityId, onCityClick ->
                Column {
                    Text(
                        text = selectedCityId ?: "none",
                        modifier = Modifier.testTag(TAG_TEST_LIST_SELECTION)
                    )
                    cityIds.forEach { cityId ->
                        Button(
                            onClick = { onCityClick(cityId) },
                            modifier = Modifier.testTag("$TAG_TEST_SELECT_CITY-$cityId")
                        ) {
                            Text(cityId)
                        }
                    }
                }
            },
            detailContent = { cityId ->
                Text(
                    text = cityId,
                    modifier = Modifier.testTag(TAG_TEST_ACTIVE_CITY)
                )
            },
            emptyDetailContent = {
                Box(Modifier.fillMaxSize().testTag(TAG_TEST_EMPTY_DETAIL))
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    private companion object {
        const val TAG_TEST_PHONE_CONTENT = "test_phone_content"
        const val TAG_TEST_TABLET_CONTENT = "test_tablet_content"
        const val TAG_TEST_LIST_SELECTION = "test_list_selection"
        const val TAG_TEST_SELECT_CITY = "test_select_city"
        const val TAG_TEST_ACTIVE_CITY = "test_active_city"
        const val TAG_TEST_EMPTY_DETAIL = "test_empty_detail"
        const val TAG_TEST_LIST_MARKER = "test_list_marker"
        const val TAG_TEST_OPEN_SUBPAGE = "test_open_subpage"
        const val TAG_TEST_SUBPAGE = "test_subpage"
    }
}
