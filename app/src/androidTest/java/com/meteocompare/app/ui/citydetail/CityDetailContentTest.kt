package com.meteocompare.app.ui.citydetail

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.CityDetailSection
import com.meteocompare.app.domain.model.VigilanceColor
import com.meteocompare.app.domain.model.VigilanceForecast
import com.meteocompare.app.domain.model.VigilanceInterval
import com.meteocompare.app.domain.model.VigilancePeriod
import com.meteocompare.app.domain.model.VigilancePhenomenon
import com.meteocompare.app.domain.model.VigilancePhenomenonAlert
import com.meteocompare.app.domain.model.VigilanceScope
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.EqualWeighting
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.components.TAG_VIGILANCE_DETAIL
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CityDetailContentTest {
    @get:Rule val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val snackbar = SnackbarHostState()

    // Compute all forecast aggregates once, outside setContent. Rebuilding a new
    // Loaded target on every Compose frame can keep AnimatedContent busy forever
    // and masks real idle-state regressions in these instrumentation tests.
    private fun loadedState(
        forecast: CityForecast,
        calculator: ConfidenceCalculator
    ) = CityDetailUiState.Loaded(
        forecast = forecast,
        weeklyConfidence = calculator.weeklyConfidence(forecast),
        hourlyBands = calculator.hourlyTemperatureConfidence(forecast),
        hourlyPrecipBands = calculator.hourlyPrecipitationConfidence(forecast),
        hourlyWindBands = calculator.hourlyWindConfidence(forecast),
        currentTemp = calculator.currentTemperature(forecast),
        currentCondition = calculator.currentWeatherCondition(forecast),
        currentCloudCover = calculator.currentCloudCover(forecast),
        dailyConditions = calculator.dailyConditionsByModel(forecast),
        calculatedAt = TestFixtures.now,
        fetchedAt = forecast.fetchedAt
    )

    @Test
    fun loading_state_is_identifiable() {
        composeRule.setContent {
            MeteoCompareTheme {
                CityDetailContent(
                    state = CityDetailUiState.Loading,
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    snackbarHostState = snackbar,
                    onBack = {}, onRefresh = {}
                )
            }
        }
        composeRule.onNodeWithTag(TAG_DETAIL_LOADING).assertIsDisplayed()
    }

    @Test
    fun tablet_detail_hides_the_redundant_back_button() {
        composeRule.setContent {
            MeteoCompareTheme {
                CityDetailContent(
                    state = CityDetailUiState.Loading,
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    snackbarHostState = snackbar,
                    onBack = {},
                    onRefresh = {},
                    showBackButton = false
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.nav_back))
            .assertDoesNotExist()
    }

    @Test
    fun error_state_is_identifiable() {
        composeRule.setContent {
            MeteoCompareTheme {
                CityDetailContent(
                    state = CityDetailUiState.Error("Indisponible"),
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    snackbarHostState = snackbar,
                    onBack = {}, onRefresh = {}
                )
            }
        }
        composeRule.onNodeWithTag(TAG_DETAIL_ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("Indisponible").assertIsDisplayed()
    }

    @Test
    fun smartphone_retry_back_and_refresh_callbacks_are_forwarded() {
        var back = 0
        var refresh = 0
        composeRule.setContent {
            MeteoCompareTheme {
                CityDetailContent(
                    state = CityDetailUiState.Error("Indisponible"),
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    snackbarHostState = snackbar,
                    onBack = { back++ },
                    onRefresh = { refresh++ }
                )
            }
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.nav_back)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_retry)).performClick()
        assertEquals(1, back)
        assertEquals(1, refresh)
    }

    @Test
    fun loaded_state_displays_confidence_and_opens_explanation() {
        val forecast = TestFixtures.forecast()
        val calculator = ConfidenceCalculator(EqualWeighting())
        val loadedState = loadedState(forecast, calculator)
        var clickedDate: String? = null
        composeRule.setContent {
            MeteoCompareTheme {
                CityDetailContent(
                    state = loadedState,
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    snackbarHostState = snackbar,
                    onBack = {}, onRefresh = {},
                    onConfidenceClick = { clickedDate = it }
                )
            }
        }
        composeRule.onNodeWithTag(TAG_DETAIL_LOADED).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_CONFIDENCE_BADGE, useUnmergedTree = true).performClick()
        assertEquals(TestFixtures.today.toString(), clickedDate)
        assertTrue(clickedDate != null)
    }

    @Test
    fun long_city_name_keeps_its_own_header_space_and_shortcuts() {
        val city = TestFixtures.paris.copy(name = "Saint-Étienne-du-Rouvray")
        val forecast = TestFixtures.forecast(city = city)
        val calculator = ConfidenceCalculator(EqualWeighting())
        val loadedState = loadedState(forecast, calculator)
        composeRule.setContent {
            MeteoCompareTheme {
                CityDetailContent(
                    state = loadedState,
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    collapsedSections = CityDetailSection.entries.toSet(),
                    snackbarHostState = snackbar,
                    onBack = {}, onRefresh = {}
                )
            }
        }
        composeRule.onNodeWithTag(TAG_DETAIL_CITY_TITLE, useUnmergedTree = true)
            .assertTextEquals(city.name)
        composeRule.onNodeWithTag(TAG_RADAR_ACTION).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ENGINE_COMPARISON_ACTION).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_GRAPHIC_VIEW_ACTION).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_METEOGRAM_ACTION).assertIsDisplayed()
    }

    @Test
    fun loaded_state_exposes_radar_from_top_app_bar() {
        val forecast = TestFixtures.forecast()
        val calculator = ConfidenceCalculator(EqualWeighting())
        val loadedState = loadedState(forecast, calculator)
        var radarClicks = 0

        composeRule.setContent {
            MeteoCompareTheme {
                CityDetailContent(
                    state = loadedState,
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    snackbarHostState = snackbar,
                    onBack = {},
                    onRefresh = {},
                    onRadarClick = { radarClicks++ }
                )
            }
        }

        composeRule.onNodeWithTag(TAG_RADAR_ACTION)
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, radarClicks)
    }

    @Test
    fun loaded_state_exposes_engine_comparison_from_top_app_bar() {
        val forecast = TestFixtures.forecast()
        val calculator = ConfidenceCalculator(EqualWeighting())
        val loadedState = loadedState(forecast, calculator)
        var comparisonClicks = 0

        composeRule.setContent {
            MeteoCompareTheme {
                CityDetailContent(
                    state = loadedState,
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    snackbarHostState = snackbar,
                    onBack = {},
                    onRefresh = {},
                    onEngineComparisonClick = { comparisonClicks++ }
                )
            }
        }

        composeRule.onNodeWithTag(TAG_ENGINE_COMPARISON_ACTION)
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, comparisonClicks)
    }

    @Test
    fun loaded_state_exposes_graphic_view_from_top_app_bar() {
        val forecast = TestFixtures.forecast()
        val calculator = ConfidenceCalculator(EqualWeighting())
        val loadedState = loadedState(forecast, calculator)
        var graphicClicks = 0

        composeRule.setContent {
            MeteoCompareTheme {
                CityDetailContent(
                    state = loadedState,
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    snackbarHostState = snackbar,
                    onBack = {},
                    onRefresh = {},
                    onGraphicViewClick = { graphicClicks++ }
                )
            }
        }

        composeRule.onNodeWithTag(TAG_GRAPHIC_VIEW_ACTION)
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, graphicClicks)
    }

    @Test
    fun non_french_city_never_displays_official_vigilance_section() {
        val london = City(
            id = "2643743",
            name = "London",
            country = "United Kingdom",
            latitude = 51.5074,
            longitude = -0.1278,
            timezone = "Europe/London",
            countryCode = "GB"
        )
        val forecast = TestFixtures.forecast(city = london)
        val calculator = ConfidenceCalculator(EqualWeighting())
        val loadedState = loadedState(forecast, calculator)
        val start = TestFixtures.now
        val alert = VigilancePhenomenonAlert(
            phenomenon = VigilancePhenomenon.WIND,
            maxColor = VigilanceColor.RED,
            intervals = listOf(
                VigilanceInterval(
                    begin = start,
                    end = start.plusSeconds(7_200),
                    color = VigilanceColor.RED,
                    scope = VigilanceScope.DEPARTMENT
                )
            )
        )
        val vigilance = VigilanceForecast(
            source = "Météo-France",
            department = "75",
            includeCoast = false,
            updateTime = start,
            productDatetime = start,
            generationTimestamp = start,
            periods = listOf(
                VigilancePeriod(
                    term = "J",
                    begin = start,
                    end = start.plusSeconds(7_200),
                    maxColor = VigilanceColor.RED,
                    departmentMaxColor = VigilanceColor.RED,
                    coastMaxColor = null,
                    phenomena = listOf(alert)
                )
            ),
            fetchedAt = start
        )

        composeRule.setContent {
            MeteoCompareTheme {
                CityDetailContent(
                    state = loadedState,
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    vigilanceState = VigilanceUiState.Loaded(vigilance),
                    collapsedSections = setOf(CityDetailSection.TODAY_SUMMARY),
                    snackbarHostState = snackbar,
                    onBack = {},
                    onRefresh = {}
                )
            }
        }

        composeRule.onNodeWithTag(TAG_VIGILANCE_DETAIL).assertDoesNotExist()
    }

    @Test
    fun collapsible_header_forwards_the_persistent_section_change() {
        val forecast = TestFixtures.forecast()
        val calculator = ConfidenceCalculator(EqualWeighting())
        val loadedState = loadedState(forecast, calculator)
        var changedSection: CityDetailSection? = null
        var expandedValue: Boolean? = null

        composeRule.setContent {
            MeteoCompareTheme {
                // Reproduce the persisted section state normally owned by the ViewModel.
                var collapsedSections by remember { mutableStateOf(emptySet<CityDetailSection>()) }
                CityDetailContent(
                    state = loadedState,
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    collapsedSections = collapsedSections,
                    snackbarHostState = snackbar,
                    onBack = {},
                    onRefresh = {},
                    onSectionExpandedChange = { section, expanded ->
                        changedSection = section
                        expandedValue = expanded
                        collapsedSections = if (expanded) {
                            collapsedSections - section
                        } else {
                            collapsedSections + section
                        }
                    }
                )
            }
        }

        composeRule.onNodeWithTag(TAG_DETAIL_LOADED)
            .performScrollToNode(hasTestTag(TAG_LOCAL_RELIABILITY_HEADER))
        val header = composeRule.onNodeWithTag(TAG_LOCAL_RELIABILITY_HEADER)
            .assertIsDisplayed()
            .assertHasClickAction()
        // This verifies the section callback, not hit testing. After a LazyColumn
        // scroll the synthetic tap can land beneath the fixed Scaffold top bar.
        // The direct section test separately covers a real performClick().
        header.performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertEquals(CityDetailSection.CONFIDENCE, changedSection)
            assertEquals(false, expandedValue)
        }
        composeRule.onNodeWithTag(TAG_LOCAL_RELIABILITY_DETAILS).assertDoesNotExist()

        // Verify the reverse transition as well: the stored state is fed back
        // into CityDetailContent and the details become visible again.
        header.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(CityDetailSection.CONFIDENCE, changedSection)
            assertEquals(true, expandedValue)
        }
        composeRule.onNodeWithTag(TAG_LOCAL_RELIABILITY_DETAILS).assertExists()
    }

    @Test
    fun detail_header_shows_location_on_one_line_below_city_name() {
        val city = TestFixtures.paris.copy(
            name = "Saint-Martin", country = "France", admin1 = "Normandie",
            departmentName = "Manche", admin3 = "Avranches", postalCode = "50300"
        )
        val forecast = TestFixtures.forecast(city = city)
        val state = loadedState(forecast, ConfidenceCalculator(EqualWeighting()))
        composeRule.setContent {
            MeteoCompareTheme {
                CityDetailContent(
                    state = state,
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    collapsedSections = CityDetailSection.entries.toSet(),
                    snackbarHostState = snackbar,
                    onBack = {}, onRefresh = {}
                )
            }
        }
        val title = composeRule.onNodeWithTag(
            TAG_DETAIL_CITY_TITLE, useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val detailsNode = composeRule.onNodeWithTag(
            TAG_DETAIL_CITY_LOCATION, useUnmergedTree = true
        )
        val details = detailsNode.fetchSemanticsNode().boundsInRoot
        assertTrue("Location should be directly below the city title", details.top >= title.bottom)
        composeRule.onNodeWithTag(TAG_DETAIL_CITY_LOCATION, useUnmergedTree = true)
            .assertTextEquals("France · Normandie · Manche · Avranches · 50300")
        val textLayouts = mutableListOf<TextLayoutResult>()
        val action = composeRule.onNodeWithTag(TAG_DETAIL_CITY_LOCATION, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertTrue("Location should expose its text layout", action?.invoke(textLayouts) == true)
        assertEquals("Location must use one line even with many subdivisions", 1, textLayouts.single().lineCount)
    }

}
