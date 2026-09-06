package com.meteocompare.app.ui.citylist

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CityListContentTest {
    @get:Rule val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun empty_state_exposes_primary_actions() {
        var add = false
        var donate = false
        var help = false
        var settings = false
        composeRule.setContent {
            MeteoCompareTheme {
                CityListContent(
                    uiState = CityListUiState(),
                    onCityClick = {},
                    onAddClick = { add = true },
                    onDonateClick = { donate = true },
                    onHelpClick = { help = true },
                    onSettingsClick = { settings = true },
                    onRemoveCity = {}, onRetry = {}, onRefresh = {}
                )
            }
        }
        composeRule.onNodeWithTag(TAG_EMPTY_STATE).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.empty_favorites_title)).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ADD_FAB).performClick()
        composeRule.onNodeWithTag(TAG_DONATE_BUTTON).performClick()
        composeRule.onNodeWithTag(TAG_HELP_BUTTON).performClick()
        composeRule.onNodeWithTag(TAG_SETTINGS_BUTTON).performClick()
        assertTrue(add)
        assertTrue(donate)
        assertTrue(help)
        assertTrue(settings)
    }

    @Test
    fun smartphone_cards_keep_standard_style_and_forward_selected_id() {
        var selectedId: String? = null
        val confidence = DayConfidence(
            date = TestFixtures.today,
            tempMax = ConfidenceScore(85, 25.0, 27.0, 26.0, 0.7, 5),
            tempMin = null,
            precipitation = PrecipitationConfidence.NoRain(100, 5, 0.0),
            windMax = null
        )
        composeRule.setContent {
            MeteoCompareTheme {
                CityListContent(
                    uiState = CityListUiState(
                        items = listOf(
                            CityCardState(TestFixtures.paris, ForecastState.Loading),
                            CityCardState(TestFixtures.lyon, ForecastState.Loaded(confidence, null))
                        )
                    ),
                    onCityClick = { selectedId = it },
                    onAddClick = {}, onDonateClick = {}, onHelpClick = {}, onSettingsClick = {},
                    onRemoveCity = {}, onRetry = {}, onRefresh = {}
                )
            }
        }
        composeRule.onNodeWithTag("$TAG_CITY_CARD${TestFixtures.lyon.id}")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    CityCardVisualEmphasisKey,
                    CityCardVisualEmphasis.STANDARD
                )
            )
            .assert(SemanticsMatcher.expectValue(CityCardTargetAlphaKey, 100))
            .performClick()
        composeRule.onNodeWithText("25–27", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(TestFixtures.lyon.id, selectedId)
    }

    @Test
    fun tablet_inactive_card_is_dimmed_and_clicking_it_moves_the_colored_selection() {
        val selectedCityId = mutableStateOf(TestFixtures.lyon.id)
        composeRule.setContent {
            MeteoCompareTheme {
                CityListContent(
                    uiState = CityListUiState(
                        items = listOf(
                            CityCardState(TestFixtures.paris, ForecastState.Loading),
                            CityCardState(TestFixtures.lyon, ForecastState.Loading)
                        )
                    ),
                    onCityClick = { selectedCityId.value = it },
                    onAddClick = {},
                    onDonateClick = {},
                    onHelpClick = {},
                    onSettingsClick = {},
                    onRemoveCity = {},
                    onRetry = {},
                    onRefresh = {},
                    selectedCityId = selectedCityId.value,
                    selectionEnabled = true
                )
            }
        }

        composeRule.onNodeWithTag("$TAG_CITY_CARD${TestFixtures.paris.id}")
            .assertIsNotSelected()
            .assert(
                SemanticsMatcher.expectValue(
                    CityCardVisualEmphasisKey,
                    CityCardVisualEmphasis.DEEMPHASIZED
                )
            )
            .assert(SemanticsMatcher.expectValue(CityCardTargetAlphaKey, 55))
        composeRule.onNodeWithTag("$TAG_CITY_CARD${TestFixtures.lyon.id}")
            .assertIsSelected()
            .assert(
                SemanticsMatcher.expectValue(
                    CityCardVisualEmphasisKey,
                    CityCardVisualEmphasis.WEATHER_COLORED
                )
            )
            .assert(SemanticsMatcher.expectValue(CityCardTargetAlphaKey, 100))

        composeRule.onNodeWithTag("$TAG_CITY_CARD${TestFixtures.paris.id}")
            .performClick()

        composeRule.onNodeWithTag("$TAG_CITY_CARD${TestFixtures.paris.id}")
            .assertIsSelected()
            .assert(
                SemanticsMatcher.expectValue(
                    CityCardVisualEmphasisKey,
                    CityCardVisualEmphasis.WEATHER_COLORED
                )
            )
            .assert(SemanticsMatcher.expectValue(CityCardTargetAlphaKey, 100))
        composeRule.onNodeWithTag("$TAG_CITY_CARD${TestFixtures.lyon.id}")
            .assertIsNotSelected()
            .assert(
                SemanticsMatcher.expectValue(
                    CityCardVisualEmphasisKey,
                    CityCardVisualEmphasis.DEEMPHASIZED
                )
            )
            .assert(SemanticsMatcher.expectValue(CityCardTargetAlphaKey, 55))
    }
}
