package com.meteocompare.app.ui.citydetail

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.City
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

    @Test
    fun loading_state_is_identifiable() {
        composeRule.setContent {
            MeteoCompareTheme {
                CityDetailContent(
                    state = CityDetailUiState.Loading,
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    snackbarHostState = SnackbarHostState(),
                    onBack = {}, onRefresh = {}
                )
            }
        }
        composeRule.onNodeWithTag(TAG_DETAIL_LOADING).assertIsDisplayed()
    }

    @Test
    fun error_state_is_identifiable() {
        composeRule.setContent {
            MeteoCompareTheme {
                CityDetailContent(
                    state = CityDetailUiState.Error("Indisponible"),
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    snackbarHostState = SnackbarHostState(),
                    onBack = {}, onRefresh = {}
                )
            }
        }
        composeRule.onNodeWithTag(TAG_DETAIL_ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("Indisponible").assertIsDisplayed()
    }

    @Test
    fun retry_back_and_refresh_callbacks_are_forwarded() {
        var back = 0
        var refresh = 0
        composeRule.setContent {
            MeteoCompareTheme {
                CityDetailContent(
                    state = CityDetailUiState.Error("Indisponible"),
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    snackbarHostState = SnackbarHostState(),
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
        var clickedDate: String? = null
        composeRule.setContent {
            MeteoCompareTheme {
                CityDetailContent(
                    state = CityDetailUiState.Loaded(
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
                    ),
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    snackbarHostState = SnackbarHostState(),
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
                    state = CityDetailUiState.Loaded(
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
                    ),
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    vigilanceState = VigilanceUiState.Loaded(vigilance),
                    collapsedSections = setOf(CityDetailSection.TODAY_SUMMARY),
                    snackbarHostState = SnackbarHostState(),
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
        var changedSection: CityDetailSection? = null
        var expandedValue: Boolean? = null

        composeRule.setContent {
            MeteoCompareTheme {
                CityDetailContent(
                    state = CityDetailUiState.Loaded(
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
                    ),
                    isRefreshing = false,
                    biasState = BiasScreenState.EMPTY,
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onRefresh = {},
                    onSectionExpandedChange = { section, expanded ->
                        changedSection = section
                        expandedValue = expanded
                    }
                )
            }
        }

        composeRule.onNodeWithTag(TAG_DETAIL_LOADED)
            .performScrollToNode(hasTestTag(TAG_LOCAL_RELIABILITY_HEADER))
        composeRule.onNodeWithTag(TAG_LOCAL_RELIABILITY_HEADER)
            .assertIsDisplayed()
            .performClick()

        assertEquals(CityDetailSection.CONFIDENCE, changedSection)
        assertEquals(false, expandedValue)
    }
}
