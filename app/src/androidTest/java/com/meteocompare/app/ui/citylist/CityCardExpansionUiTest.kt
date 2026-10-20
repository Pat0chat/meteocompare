package com.meteocompare.app.ui.citylist

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.domain.model.VigilanceColor
import com.meteocompare.app.domain.model.VigilanceForecast
import com.meteocompare.app.domain.model.VigilanceInterval
import com.meteocompare.app.domain.model.VigilancePeriod
import com.meteocompare.app.domain.model.VigilancePhenomenon
import com.meteocompare.app.domain.model.VigilancePhenomenonAlert
import com.meteocompare.app.domain.model.VigilanceScope
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test

class CityCardExpansionUiTest {
    @get:Rule val composeRule = createComposeRule()

    private val score = DayConfidence(
        date = TestFixtures.today,
        tempMax = ConfidenceScore(60, 18.0, 22.0, 20.0, 1.5, 5),
        tempMin = null,
        precipitation = PrecipitationConfidence.NoRain(100, 5, 0.0),
        windMax = ConfidenceScore(70, 10.0, 14.0, 12.0, 1.0, 5)
    )
    private val paris = CityCardState(
        TestFixtures.paris,
        ForecastState.Loaded(
            today = score,
            currentTemp = 19.0,
            currentCondition = WeatherCondition.CLEAR,
            currentCloudCover = 67,
            next12hTemps = listOf(19.0, 18.0, 17.0, 16.0)
        )
    )
    private val lyon = CityCardState(TestFixtures.lyon, ForecastState.Loading)

    private fun warning(level: VigilanceColor): VigilanceForecast {
        val now = TestFixtures.now
        return VigilanceForecast(
            source = "Météo-France", department = "75", includeCoast = false,
            updateTime = now, productDatetime = now, generationTimestamp = now,
            fetchedAt = now, evaluationTime = now,
            periods = listOf(VigilancePeriod(
                term = "J", begin = now, end = now.plusSeconds(86400),
                maxColor = level, departmentMaxColor = level, coastMaxColor = null,
                phenomena = listOf(VigilancePhenomenonAlert(
                    phenomenon = VigilancePhenomenon.WIND,
                    maxColor = level,
                    intervals = listOf(VigilanceInterval(
                        begin = now, end = now.plusSeconds(3600),
                        color = level, scope = VigilanceScope.DEPARTMENT
                    ))
                ))
            ))
        )
    }

    private fun card(cityId: String) = composeRule.onNodeWithTag("$TAG_CITY_CARD$cityId")

    private fun assertExpanded(cityId: String, value: Boolean) {
        card(cityId).assert(SemanticsMatcher.expectValue(CityCardExpandedKey, value))
    }

    @Test fun managed_home_selection_displays_saved_city_and_updates_on_tap() {
        val savedSelection = mutableStateOf<String?>(TestFixtures.paris.id)
        var opened: String? = null
        composeRule.setContent {
            MeteoCompareTheme {
                CityList(
                    items = listOf(paris, lyon),
                    homeExpandedCityId = savedSelection.value,
                    onHomeCityExpanded = { savedSelection.value = it },
                    onCityClick = { opened = it },
                    onRemove = {}, onRetry = {}
                )
            }
        }
        assertExpanded(TestFixtures.paris.id, true)
        assertExpanded(TestFixtures.lyon.id, false)
        card(TestFixtures.lyon.id).performClick()
        assertExpanded(TestFixtures.lyon.id, true)
        composeRule.runOnIdle {
            assertEquals(TestFixtures.lyon.id, savedSelection.value)
            assertEquals(null, opened)
        }
    }

    @Test fun single_city_stays_expanded_and_opens_details_on_first_tap() {
        var opened: String? = null
        composeRule.setContent {
            MeteoCompareTheme {
                CityList(
                    items = listOf(paris),
                    onCityClick = { opened = it },
                    onRemove = {},
                    onRetry = {}
                )
            }
        }

        assertExpanded(TestFixtures.paris.id, true)
        card(TestFixtures.paris.id).performClick()
        composeRule.runOnIdle { assertEquals(TestFixtures.paris.id, opened) }
    }

    @Test fun collapsed_city_shows_condition_temperatures_rain_wind_cloud_and_convergence() {
        composeRule.setContent {
            MeteoCompareTheme {
                CityList(
                    items = listOf(paris, lyon),
                    onCityClick = {}, onRemove = {}, onRetry = {}
                )
            }
        }

        assertExpanded(TestFixtures.paris.id, false)
        assertExpanded(TestFixtures.lyon.id, true)
        // This test verifies the compact layout, not the current scroll offset.
        // LazyColumn and AnimatedContent can expose a laid-out node before it
        // intersects the visible viewport; geometry is checked below.
        composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_METRICS${TestFixtures.paris.id}", useUnmergedTree = true
        ).assertExists()
        composeRule.onNodeWithText("19°", useUnmergedTree = true).assertExists()
        // The future temperature is the same ~H+3 trend as on the full card.
        composeRule.onNodeWithText("↓ 16°", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("76%", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("10–14 km/h", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("67%", useUnmergedTree = true).assertExists()

        val agreement = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_AGREEMENT${TestFixtures.paris.id}", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val condition = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_CONDITION${TestFixtures.paris.id}", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val temperatures = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_TEMPERATURES${TestFixtures.paris.id}", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val conditionLabel = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_CONDITION_LABEL${TestFixtures.paris.id}", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val metrics = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_METRICS${TestFixtures.paris.id}", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        assertTrue("Agreement must be on the header row", agreement.bottom <= condition.top)
        assertTrue("Condition should keep a square icon background", kotlin.math.abs(condition.width - condition.height) < 1f)
        assertTrue("Current and upcoming temperatures must be beside the condition", kotlin.math.abs(condition.center.y - temperatures.center.y) < 1f)
        assertTrue("Temperatures must follow the weather label", temperatures.left >= conditionLabel.right)
        assertTrue(
            "Temperatures should sit directly after the label, not at the card's right edge",
            temperatures.left - conditionLabel.right <= with(composeRule.density) { 18.dp.toPx() }
        )
        assertTrue("Three weather metrics must be below the condition", metrics.top >= condition.bottom)

        // The three metrics occupy identical cells instead of being weighted by
        // their content lengths; the wind must stay centered in the middle.
        val rain = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_RAIN${TestFixtures.paris.id}", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val wind = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_WIND${TestFixtures.paris.id}", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val clouds = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_CLOUD${TestFixtures.paris.id}", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "The three weather metrics must have equal widths",
            abs(rain.width - wind.width) <= 1f && abs(wind.width - clouds.width) <= 1f
        )
        assertTrue(
            "Weather metrics must be horizontally distributed evenly",
            abs((wind.center.x - rain.center.x) - (clouds.center.x - wind.center.x)) <= 2f
        )
        assertTrue(
            "Weather metrics must share one baseline row",
            abs(rain.center.y - wind.center.y) <= 1f && abs(wind.center.y - clouds.center.y) <= 1f
        )
        val compactBounds = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "The metrics row must fit inside its compact card, not be cut off",
            metrics.left >= compactBounds.left && metrics.right <= compactBounds.right &&
                metrics.top >= compactBounds.top && metrics.bottom <= compactBounds.bottom
        )
        val fullBounds = card(TestFixtures.lyon.id).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "A compact card should be 32 dp narrower than a full card",
            abs((fullBounds.width - compactBounds.width) -
                with(composeRule.density) { 32.dp.toPx() }) <= with(composeRule.density) { 2.dp.toPx() }
        )
        assertTrue(
            "Compact and full cards must share the same right edge",
            abs(compactBounds.right - fullBounds.right) <= with(composeRule.density) { 1.dp.toPx() }
        )
        assertTrue(
            "The compact card's inset should be on the left",
            compactBounds.left > fullBounds.left
        )
        // Only the expanded city renders its full forecast; the compressed card
        // must not keep the three-column temperature range visible.
        composeRule.onNodeWithText("18–22", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun compact_card_shows_french_vigilance_after_temperature_trend() {
        val vigilance = warning(VigilanceColor.ORANGE)
        composeRule.setContent {
            MeteoCompareTheme {
                CityList(
                    items = listOf(paris.copy(vigilance = vigilance), lyon),
                    onCityClick = {}, onRemove = {}, onRetry = {}
                )
            }
        }

        val level = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_VIGILANCE${TestFixtures.paris.id}", useUnmergedTree = true
        )
        level.assertExists()
        val levelRect = level.fetchSemanticsNode().boundsInRoot
        val tempRect = composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_TEMPERATURES${TestFixtures.paris.id}", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val cardRect = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot
        assertTrue("Vigilance must follow the temperature trend", levelRect.left >= tempRect.right)
        assertTrue("Vigilance must fit inside the compact card", levelRect.right <= cardRect.right)
        assertTrue("Vigilance and trend must share a row", abs(levelRect.center.y - tempRect.center.y) < with(composeRule.density) { 6.dp.toPx() })
    }

    @Test fun compact_card_hides_vigilance_for_foreign_city() {
        val foreignParis = paris.copy(
            city = TestFixtures.paris.copy(country = "Germany", countryCode = "DE"),
            vigilance = warning(VigilanceColor.ORANGE)
        )
        composeRule.setContent {
            MeteoCompareTheme {
                CityList(items = listOf(foreignParis, lyon), onCityClick = {}, onRemove = {}, onRetry = {})
            }
        }
        composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_VIGILANCE${TestFixtures.paris.id}", useUnmergedTree = true
        ).assertDoesNotExist()
    }

    @Test fun compact_card_keeps_placeholders_when_forecast_data_is_missing() {
        val partial = paris.copy(
            forecast = ForecastState.Loaded(
                today = score,
                currentTemp = null,
                currentCondition = null,
                currentCloudCover = null
            )
        )
        composeRule.setContent {
            MeteoCompareTheme {
                CityList(
                    items = listOf(partial, lyon),
                    onCityClick = {}, onRemove = {}, onRetry = {}
                )
            }
        }
        assertExpanded(TestFixtures.paris.id, false)
        composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_CONDITION${TestFixtures.paris.id}", useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_TEMPERATURES${TestFixtures.paris.id}", useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_METRICS${TestFixtures.paris.id}", useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_HOME_TEMPERATURE_TREND, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test fun first_tap_expands_without_navigation_and_second_opens_details() {
        var opened: String? = null
        composeRule.setContent {
            MeteoCompareTheme {
                CityList(
                    items = listOf(paris, lyon),
                    onCityClick = { opened = it }, onRemove = {}, onRetry = {}
                )
            }
        }

        card(TestFixtures.paris.id).performClick()
        composeRule.runOnIdle { assertEquals(null, opened) }
        assertExpanded(TestFixtures.paris.id, true)
        assertExpanded(TestFixtures.lyon.id, false)
        // After Paris expands, Lyon's metrics can be pushed off-screen by
        // the 800-ms size morph. Check that the compact content is composed;
        // this test must not depend on the LazyColumn scroll position.
        composeRule.onNodeWithTag(
            "$TAG_CITY_CARD_COMPACT_METRICS${TestFixtures.lyon.id}", useUnmergedTree = true
        ).assertExists()

        card(TestFixtures.paris.id).performClick()
        composeRule.runOnIdle { assertEquals(TestFixtures.paris.id, opened) }
    }

    @Test fun expand_and_collapse_animate_width_without_instant_jumps() {
        // Isolate a card from LazyColumn placement animations. If width is
        // snapped rather than animated, the halfway assertion will fail.
        composeRule.mainClock.autoAdvance = false
        val expanded = mutableStateOf(false)
        composeRule.setContent {
            MeteoCompareTheme {
                CityCard(
                    state = paris,
                    expanded = expanded.value,
                    onClick = { expanded.value = !expanded.value },
                    onRemove = {},
                    onRetry = {}
                )
            }
        }
        val collapsedBounds = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle { expanded.value = true }
        composeRule.mainClock.advanceTimeBy(CITY_CARD_RESIZE_DURATION_MS / 2L)
        val expandingBounds = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.advanceTimeBy(CITY_CARD_RESIZE_DURATION_MS.toLong())
        val expandedBounds = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expansion should smoothly increase width",
            expandingBounds.width > collapsedBounds.width &&
                expandingBounds.width < expandedBounds.width
        )
        assertTrue(
            "Expansion must keep the card's right edge fixed throughout",
            abs(collapsedBounds.right - expandingBounds.right) <= 1f &&
                abs(expandingBounds.right - expandedBounds.right) <= 1f
        )
        assertTrue(
            "Expansion should smoothly increase height",
            expandingBounds.height > collapsedBounds.height &&
                expandingBounds.height < expandedBounds.height
        )

        composeRule.runOnIdle { expanded.value = false }
        composeRule.mainClock.advanceTimeBy(CITY_CARD_RESIZE_DURATION_MS / 2L)
        val collapsingBounds = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.advanceTimeBy(CITY_CARD_RESIZE_DURATION_MS.toLong())
        val collapsedAgainBounds = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Collapse should smoothly decrease width",
            collapsingBounds.width < expandedBounds.width &&
                collapsingBounds.width > collapsedAgainBounds.width
        )
        assertTrue(
            "Collapse must keep the card's right edge fixed throughout",
            abs(expandedBounds.right - collapsingBounds.right) <= 1f &&
                abs(collapsingBounds.right - collapsedAgainBounds.right) <= 1f
        )
        assertTrue(
            "Collapse should smoothly decrease height",
            collapsingBounds.height < expandedBounds.height &&
                collapsingBounds.height > collapsedAgainBounds.height
        )
        assertTrue(
            "Compact dimensions should return to their original values",
            abs(collapsedAgainBounds.width - collapsedBounds.width) <= 1f &&
                abs(collapsedAgainBounds.height - collapsedBounds.height) <= 1f
        )
    }

    @Test fun resize_starts_gently_and_can_reverse_without_snapping() {
        composeRule.mainClock.autoAdvance = false
        val expanded = mutableStateOf(false)
        composeRule.setContent {
            MeteoCompareTheme {
                CityCard(
                    state = paris,
                    expanded = expanded.value,
                    onClick = { expanded.value = !expanded.value },
                    onRemove = {},
                    onRetry = {}
                )
            }
        }
        val collapsedBounds = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot

        composeRule.runOnIdle { expanded.value = true }
        composeRule.mainClock.advanceTimeBy(CITY_CARD_RESIZE_DURATION_MS / 6L)
        val earlyBounds = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.advanceTimeBy(CITY_CARD_RESIZE_DURATION_MS / 3L)
        val halfwayBounds = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "The expansion must begin gradually, not jump to its final width",
            earlyBounds.width > collapsedBounds.width &&
                earlyBounds.width < halfwayBounds.width
        )
        assertTrue(
            "The inset animation must accelerate smoothly after its gentle start",
            earlyBounds.width - collapsedBounds.width <
                halfwayBounds.width - earlyBounds.width
        )

        // Reverse before reaching full width. A new tween should begin from
        // the current width rather than snapping to the collapsed target.
        composeRule.runOnIdle { expanded.value = false }
        composeRule.mainClock.advanceTimeBy(16)
        val reversedBounds = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Reversing mid-flight should keep the width continuous",
            abs(reversedBounds.width - halfwayBounds.width) <=
                with(composeRule.density) { 3.dp.toPx() }
        )
        assertTrue(
            "The right edge must remain anchored when reversing",
            abs(reversedBounds.right - collapsedBounds.right) <= 1f
        )
        composeRule.mainClock.advanceTimeBy(CITY_CARD_RESIZE_DURATION_MS.toLong() + 100)
        val collapsedAgainBounds = card(TestFixtures.paris.id).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Reversing should eventually return to the compact dimensions",
            abs(collapsedAgainBounds.width - collapsedBounds.width) <= 1f &&
                abs(collapsedAgainBounds.height - collapsedBounds.height) <= 1f
        )
    }

    @Test fun morph_remains_in_progress_after_old_duration_in_both_directions() {
        // Regression: 420 ms was previously the full transition. At that point
        // V6 must still be resizing rather than abruptly reaching either size.
        composeRule.mainClock.autoAdvance = false
        val expanded = mutableStateOf(false)
        composeRule.setContent {
            MeteoCompareTheme {
                CityCard(
                    state = paris,
                    expanded = expanded.value,
                    onClick = { expanded.value = !expanded.value },
                    onRemove = {},
                    onRetry = {}
                )
            }
        }
        val tag = TestFixtures.paris.id
        val compact = card(tag).fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle { expanded.value = true }
        composeRule.mainClock.advanceTimeBy(420)
        val opening = card(tag).fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.advanceTimeBy(CITY_CARD_RESIZE_DURATION_MS.toLong())
        val full = card(tag).fetchSemanticsNode().boundsInRoot
        assertTrue("Width should still be expanding after 420 ms",
            opening.width > compact.width && opening.width < full.width)
        assertTrue("Height should still be expanding after 420 ms",
            opening.height > compact.height && opening.height < full.height)

        composeRule.runOnIdle { expanded.value = false }
        composeRule.mainClock.advanceTimeBy(420)
        val closing = card(tag).fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.advanceTimeBy(CITY_CARD_RESIZE_DURATION_MS.toLong())
        val compactAgain = card(tag).fetchSemanticsNode().boundsInRoot
        assertTrue("Width should still be shrinking after 420 ms",
            closing.width < full.width && closing.width > compactAgain.width)
        assertTrue("Height should still be shrinking after 420 ms",
            closing.height < full.height && closing.height > compactAgain.height)
        assertTrue("The right edge stays fixed throughout the softer morph",
            listOf(opening, full, closing, compactAgain).all {
                abs(it.right - compact.right) <= 1f
            })
    }

    @Test fun newly_added_city_takes_expansion_and_removal_falls_back() {
        val cities = mutableStateOf(listOf(paris))
        composeRule.setContent {
            MeteoCompareTheme {
                CityList(
                    items = cities.value,
                    onCityClick = {}, onRemove = {}, onRetry = {}
                )
            }
        }
        assertExpanded(TestFixtures.paris.id, true)

        composeRule.runOnIdle { cities.value = listOf(paris, lyon) }
        assertExpanded(TestFixtures.paris.id, false)
        assertExpanded(TestFixtures.lyon.id, true)

        // Re-select Paris, then add a third city: the new favorite wins.
        card(TestFixtures.paris.id).performClick()
        assertExpanded(TestFixtures.paris.id, true)
        val brest = CityCardState(
            TestFixtures.paris.copy(id = "brest", name = "Brest"),
            ForecastState.Loading
        )
        composeRule.runOnIdle { cities.value = listOf(paris, lyon, brest) }
        assertExpanded("brest", true)
        assertExpanded(TestFixtures.paris.id, false)

        composeRule.runOnIdle { cities.value = listOf(paris, lyon) }
        assertExpanded(TestFixtures.lyon.id, true)
    }

    @Test fun collapsed_error_does_not_hide_error_and_can_be_expanded_to_retry() {
        val error = CityCardState(TestFixtures.paris, ForecastState.Error("Réseau indisponible"))
        composeRule.setContent {
            MeteoCompareTheme {
                CityList(
                    items = listOf(error, lyon),
                    onCityClick = {}, onRemove = {}, onRetry = {}
                )
            }
        }
        assertExpanded(TestFixtures.paris.id, false)
        composeRule.onNodeWithText("Réseau indisponible", useUnmergedTree = true).assertIsDisplayed()
        card(TestFixtures.paris.id).performClick()
        assertExpanded(TestFixtures.paris.id, true)
        composeRule.onNodeWithText("Réseau indisponible", useUnmergedTree = true).assertIsDisplayed()
    }
}
