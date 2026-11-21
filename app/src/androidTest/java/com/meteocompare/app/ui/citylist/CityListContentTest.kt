package com.meteocompare.app.ui.citylist

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.ThemePreference
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
    fun compare_action_appears_for_two_favorites_and_opens_comparison() {
        var opened = false
        composeRule.setContent {
            MeteoCompareTheme {
                CityListContent(
                    uiState = CityListUiState(items = listOf(
                        CityCardState(TestFixtures.paris, ForecastState.Loading),
                        CityCardState(TestFixtures.lyon, ForecastState.Loading)
                    )),
                    onCityClick = {}, onAddClick = {}, onDonateClick = {},
                    onSettingsClick = {}, onRemoveCity = {}, onRetry = {}, onRefresh = {},
                    onCompareClick = { opened = true }
                )
            }
        }
        composeRule.onNodeWithTag("home_compare_localities").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun compare_is_adjacent_to_reorder_in_bottom_action_row() {
        var compared = false
        var reordered = false
        composeRule.setContent {
            MeteoCompareTheme {
                CityListContent(
                    uiState = CityListUiState(items = listOf(
                        CityCardState(TestFixtures.paris, ForecastState.Loading),
                        CityCardState(TestFixtures.lyon, ForecastState.Loading)
                    )),
                    onCityClick = {}, onAddClick = {}, onDonateClick = {},
                    onSettingsClick = {}, onRemoveCity = {}, onRetry = {}, onRefresh = {},
                    onCompareClick = { compared = true }, onReorderClick = { reordered = true }
                )
            }
        }
        val compare = composeRule.onNodeWithTag("home_compare_localities")
        val reorder = composeRule.onNodeWithTag("home_reorder_action")
        // Semantics bounds may expand to the minimum 48 dp touch target and
        // shift independently of the 40 dp FAB visuals. Measure layout bounds
        // in dp to verify visual adjacency, in either LTR or RTL locales.
        val compareBounds = compare.getUnclippedBoundsInRoot()
        val reorderBounds = reorder.getUnclippedBoundsInRoot()
        val compareCenterY = (compareBounds.top.value + compareBounds.bottom.value) / 2f
        val reorderCenterY = (reorderBounds.top.value + reorderBounds.bottom.value) / 2f
        val verticalOffset = kotlin.math.abs(compareCenterY - reorderCenterY)
        assertTrue("Compare and reorder FABs are on different rows: $compareBounds vs $reorderBounds",
            verticalOffset <= 2f)
        val (left, right) = listOf(compareBounds, reorderBounds).sortedBy { it.left }
        val gap = (right.left - left.right).value
        // The Row adds 10 dp between 48 dp minimum touch targets, while a
        // SmallFloatingActionButton draws a 40 dp surface within that target.
        // The visible surfaces are thus about 18 dp apart (not 10 dp).
        assertTrue("Compare and reorder FABs are not adjacent: $compareBounds vs $reorderBounds (gap=$gap dp)",
            gap in 0f..22f)
        compare.performClick()
        reorder.performClick()
        composeRule.runOnIdle { assertTrue(compared && reordered) }
    }

    @Test
    fun home_floating_actions_share_add_city_color_in_light_theme() =
        assertHomeFloatingActionColors(ThemePreference.LIGHT)

    @Test
    fun home_floating_actions_share_add_city_color_in_dark_theme() =
        assertHomeFloatingActionColors(ThemePreference.DARK)

    /** Capture l'intérieur du fond (hors icône et bord arrondi) de chaque FAB. */
    private fun assertHomeFloatingActionColors(theme: ThemePreference) {
        composeRule.setContent {
            MeteoCompareTheme(themePreference = theme, dynamicColor = false) {
                CityListContent(
                    uiState = CityListUiState(items = listOf(
                        CityCardState(TestFixtures.paris, ForecastState.Loading),
                        CityCardState(TestFixtures.lyon, ForecastState.Loading)
                    )),
                    onCityClick = {}, onAddClick = {}, onDonateClick = {},
                    onSettingsClick = {}, onRemoveCity = {}, onRetry = {}, onRefresh = {}
                )
            }
        }

        fun backgroundPixel(tag: String): Color {
            val bitmap = composeRule.onNodeWithTag(tag).assertIsDisplayed().captureToImage()
            // Au centre horizontal, à 1/8 de la hauteur, la couleur est celle du fond
            // et non de l'icône ; fonctionne pour les petits et grands FAB.
            return bitmap.toPixelMap()[bitmap.width / 2, bitmap.height / 8]
        }

        val addCityColor = backgroundPixel(TAG_ADD_FAB)
        assertEquals(addCityColor, backgroundPixel("home_compare_localities"))
        assertEquals(addCityColor, backgroundPixel("home_reorder_action"))
    }

    @Test
    fun compare_and_reorder_are_hidden_for_only_one_city() {
        composeRule.setContent {
            MeteoCompareTheme {
                CityListContent(
                    uiState = CityListUiState(items = listOf(
                        CityCardState(TestFixtures.paris, ForecastState.Loading)
                    )),
                    onCityClick = {}, onAddClick = {}, onDonateClick = {},
                    onSettingsClick = {}, onRemoveCity = {}, onRetry = {}, onRefresh = {}
                )
            }
        }
        composeRule.onNodeWithTag("home_compare_localities").assertDoesNotExist()
        composeRule.onNodeWithTag("home_reorder_action").assertDoesNotExist()
    }

    @Test
    fun reorderActionIsVisibleWithTwoCitiesAndForwardsClick() {
        var reorder = false
        composeRule.setContent {
            MeteoCompareTheme {
                CityListContent(
                    uiState = CityListUiState(items = listOf(
                        CityCardState(TestFixtures.paris, ForecastState.Loading),
                        CityCardState(TestFixtures.lyon, ForecastState.Loading)
                    )),
                    onCityClick = {}, onAddClick = {}, onDonateClick = {},
                    onSettingsClick = {}, onRemoveCity = {}, onRetry = {}, onRefresh = {},
                    onReorderClick = { reorder = true }
                )
            }
        }
        composeRule.onNodeWithTag("home_reorder_action").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(reorder) }
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
    @Test
    fun home_title_never_wraps_on_narrow_phone() {
        composeRule.setContent {
            MeteoCompareTheme { HomeAppTitle(modifier = androidx.compose.ui.Modifier.width(145.dp)) }
        }
        val title = composeRule.onNodeWithTag("home_app_title")
        title.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        val action = title.fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action
        assertTrue("The title should expose its text layout", action?.invoke(layouts) == true)
        assertEquals(1, layouts.single().lineCount)
    }

}
