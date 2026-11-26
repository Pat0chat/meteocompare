package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DailyRangeLayoutComposeTest {
    @get:Rule val rule = createComposeRule()
    private val now = Instant.parse("2026-09-16T12:00:00Z")

    private fun dailyPoint(models: Int = 3) = SimplifiedTimelinePoint(
        date = LocalDate.of(2026, 9, 16),
        tempMaxC = 30.0, tempMinC = 12.0,
        apparentMinC = 9.0, apparentMaxC = 33.0,
        apparentMinMinAcrossModels = 5.0, apparentMinMaxAcrossModels = 14.0, apparentMinModelCount = models,
        apparentMaxMinAcrossModels = 27.0, apparentMaxMaxAcrossModels = 36.0, apparentMaxModelCount = models,
        temperatureMinAcrossModels = 24.0, temperatureMaxAcrossModels = 34.0,
        temperatureModelCount = models,
        tempMinMinAcrossModels = 9.0, tempMinMaxAcrossModels = 16.0,
        tempMinModelCount = models,
        precipitationPercent = 55,
        precipitationProbabilityMin = 20, precipitationProbabilityMax = 90,
        precipitationProbabilityModelCount = models,
        precipitationMm = 7.0,
        precipitationMinAcrossModelsMm = 1.0, precipitationMaxAcrossModelsMm = 15.0,
        precipitationAmountModelCount = models
    )

    @Test fun opt_in_toggle_controls_both_daily_columns_and_chrono_without_losing_state() {
        val enabled = mutableStateOf(false)
        val layout = mutableStateOf(TimelineLayout.COLUMNS)
        rule.setContent {
            MeteoCompareTheme {
                // Daily columns and especially chrono are taller than a phone viewport.
                // Give this host a real vertical scroller so the toggle remains tappable.
                Surface {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SimplifiedTimelineCard(
                            points = listOf(dailyPoint()),
                            mode = DisplayMode.DAILY, timezone = "UTC", now = now,
                            layout = layout.value, onLayoutChange = { layout.value = it },
                            showDailyRanges = enabled.value,
                            onShowDailyRangesChange = { enabled.value = it }
                        )
                    }
                }
            }
        }
        val toggle = rule.onNodeWithTag(TAG_TIMELINE_RANGES_TOGGLE)
        toggle.performScrollTo().assertIsDisplayed().assertIsOff().assertHasClickAction()
        rule.onNodeWithTag("columns_daily_temp_max_range", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("columns_daily_rain_amount_range", useUnmergedTree = true).assertDoesNotExist()
        toggle.performClick()
        toggle.assertIsOn()
        // Both labels must be composed; the scroller may have moved them off screen.
        rule.onNodeWithTag("columns_daily_temp_max_range", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("columns_daily_rain_amount_range", useUnmergedTree = true).assertExists()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithText(context.getString(R.string.timeline_layout_chrono))
            .performScrollTo().performClick()
        rule.onNodeWithTag("timeline_daily_temp_max_range", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("timeline_daily_rain_range", useUnmergedTree = true).assertExists()
        // Layout switch moves the toggle farther down; scroll before tapping again.
        toggle.performScrollTo().assertIsOn().performClick()
        toggle.assertIsOff()
        rule.onNodeWithTag("timeline_daily_temp_max_range", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("timeline_daily_rain_range", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.timeline_layout_columns))
            .performScrollTo().performClick()
        rule.onNodeWithTag("columns_daily_temp_max_range", useUnmergedTree = true).assertDoesNotExist()
        rule.runOnIdle { assertEquals(false, enabled.value) }
    }

    @Test fun apparent_daily_extrema_are_rendered_in_both_layouts_without_ranges() {
        val layout = mutableStateOf(TimelineLayout.COLUMNS)
        rule.setContent {
            MeteoCompareTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SimplifiedTimelineCard(points = listOf(dailyPoint()), mode = DisplayMode.DAILY,
                        timezone = "UTC", now = now, layout = layout.value,
                        showDailyRanges = false)
                }
            }
        }
        rule.onNodeWithTag(TAG_TIMELINE_COLUMNS_APPARENT, useUnmergedTree = true).assertExists()
        rule.onNodeWithText("9° / 33°", substring = true, useUnmergedTree = true).assertExists()
        rule.runOnIdle { layout.value = TimelineLayout.CHRONO }
        rule.onNodeWithTag("timeline_chrono_daily_apparent_lane", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("9° / 33°", useUnmergedTree = true).assertExists()
    }

    @Test fun apparent_ranges_follow_toggle_layout_mode_and_model_availability() {
        val layout = mutableStateOf(TimelineLayout.COLUMNS)
        val enabled = mutableStateOf(false)
        val mode = mutableStateOf(DisplayMode.DAILY)
        val models = mutableStateOf(3)
        rule.setContent {
            MeteoCompareTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SimplifiedTimelineCard(
                        points = listOf(if (mode.value == DisplayMode.DAILY) dailyPoint(models.value)
                            else SimplifiedTimelinePoint(instant = now, apparentTemperatureC = 9.0)),
                        mode = mode.value, timezone = "UTC", now = now, layout = layout.value,
                        showDailyRanges = enabled.value)
                }
            }
        }
        for ((view, prefix) in listOf(TimelineLayout.COLUMNS to "columns_daily", TimelineLayout.CHRONO to "timeline_daily")) {
            rule.runOnIdle { layout.value = view; enabled.value = false; models.value = 3; mode.value = DisplayMode.DAILY }
            val min = rule.onNodeWithTag("${prefix}_apparent_min_range", useUnmergedTree = true)
            val max = rule.onNodeWithTag("${prefix}_apparent_max_range", useUnmergedTree = true)
            min.assertDoesNotExist(); max.assertDoesNotExist()
            rule.runOnIdle { enabled.value = true }
            min.assertExists(); max.assertExists()
            rule.onNodeWithText("(5–14)", useUnmergedTree = true).assertExists()
            rule.onNodeWithText("(27–36)", useUnmergedTree = true).assertExists()
            rule.runOnIdle { models.value = 1 }
            min.assertDoesNotExist(); max.assertDoesNotExist()
            rule.onNodeWithTag("${prefix}_apparent_min_value", useUnmergedTree = true).assertExists()
            rule.onNodeWithTag("${prefix}_apparent_max_value", useUnmergedTree = true).assertExists()
            rule.runOnIdle { models.value = 3; mode.value = DisplayMode.HOURLY }
            min.assertDoesNotExist(); max.assertDoesNotExist()
        }
    }

    @Test fun daily_columns_apparent_readings_share_the_other_metrics_start_alignment() {
        val models = mutableStateOf(3)
        val enabled = mutableStateOf(true)
        rule.setContent {
            MeteoCompareTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SimplifiedTimelineCard(
                        points = listOf(dailyPoint(models.value)), mode = DisplayMode.DAILY,
                        timezone = "UTC", now = now, layout = TimelineLayout.COLUMNS,
                        showDailyRanges = enabled.value
                    )
                }
            }
        }
        fun left(tag: String): Float = rule.onNodeWithTag(tag, useUnmergedTree = true)
            .getUnclippedBoundsInRoot().left.value

        val icon = rule.onNodeWithTag("columns_daily_apparent_icon", useUnmergedTree = true)
        // Compare the real value baseline in dp, not the width of its parent.
        // It must not move when a range is unavailable for a single model.
        for (count in listOf(3, 1)) {
            rule.runOnIdle { models.value = count }
            icon.performScrollTo().assertIsDisplayed()
            assertEquals("Feels-like icon must share the common metric rail",
                left("columns_daily_rain_probability_icon"), left("columns_daily_apparent_icon"), 1f)
            val readingStart = left("columns_daily_rain_probability_value")
            for (tag in listOf("columns_daily_apparent_min_value", "columns_daily_apparent_max_value")) {
                assertEquals("$tag must align with the other daily readings", readingStart, left(tag), 1f)
            }
        }
        rule.runOnIdle { enabled.value = false }
        icon.performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("9° / 33°", substring = true, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("columns_daily_apparent_min_range", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("columns_daily_apparent_max_range", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun hourly_columns_apparent_reading_shares_the_metric_alignment() {
        val models = mutableStateOf(3)
        val enabled = mutableStateOf(true)
        rule.setContent {
            MeteoCompareTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    val point = dailyPoint(models.value).copy(
                        date = null, instant = now, temperatureC = 30.0,
                        apparentTemperatureC = 33.0, apparentTemperatureMinAcrossModels = 27.0,
                        apparentTemperatureMaxAcrossModels = 36.0, apparentTemperatureModelCount = models.value
                    )
                    SimplifiedTimelineCard(points = listOf(point), mode = DisplayMode.HOURLY,
                        timezone = "UTC", now = now, layout = TimelineLayout.COLUMNS,
                        showDailyRanges = enabled.value)
                }
            }
        }
        val icon = rule.onNodeWithTag("columns_hourly_apparent_icon", useUnmergedTree = true)
        val reading = rule.onNodeWithTag("columns_hourly_apparent_value", useUnmergedTree = true)
        val rain = rule.onNodeWithTag("columns_hourly_rain_probability_value", useUnmergedTree = true)
        val range = rule.onNodeWithTag("columns_hourly_apparent_range", useUnmergedTree = true)
        for (count in listOf(3, 1)) {
            rule.runOnIdle { models.value = count }
            reading.performScrollTo().assertIsDisplayed()
            icon.assertIsDisplayed()
            assertEquals("Feels-like reading must start on the common metric rail",
                rain.getUnclippedBoundsInRoot().left.value,
                reading.getUnclippedBoundsInRoot().left.value, 1f)
            if (count == 3) range.performScrollTo().assertIsDisplayed() else range.assertDoesNotExist()
        }
        rule.runOnIdle { models.value = 3; enabled.value = false }
        reading.performScrollTo().assertIsDisplayed()
        icon.assertIsDisplayed()
        range.assertDoesNotExist()
    }

    @Test fun hourly_chrono_both_temperature_ranges_stay_inside_the_heatmap_with_clearance() {
        val enabled = mutableStateOf(true)
        rule.setContent {
            MeteoCompareTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    val point = SimplifiedTimelinePoint(instant = now, temperatureC = 30.0,
                        temperatureMinAcrossModels = 24.0, temperatureMaxAcrossModels = 34.0,
                        temperatureModelCount = 3,
                        apparentTemperatureC = 33.0, apparentTemperatureMinAcrossModels = 27.0,
                        apparentTemperatureMaxAcrossModels = 36.0, apparentTemperatureModelCount = 3)
                    ChronoTimelineView(points = listOf(point), mode = DisplayMode.HOURLY,
                        timezone = "UTC", now = now, showDailyRanges = enabled.value)
                }
            }
        }
        val topRange = rule.onNodeWithTag("timeline_hourly_temperature_range", useUnmergedTree = true)
        val range = rule.onNodeWithTag("timeline_hourly_apparent_range", useUnmergedTree = true)
        topRange.performScrollTo().assertIsDisplayed()
        val topPlot = rule.onNodeWithTag(TAG_TIMELINE_CHRONO_TEMPERATURE_PLOT, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val topBounds = topRange.getUnclippedBoundsInRoot()
        // The heatmap starts 6 dp below the plot top; require at least 5 dp clearance.
        assertTrue("Temperature range must stay inside heatmap: $topBounds vs $topPlot",
            topBounds.top.value - topPlot.top.value >= 11f)
        range.performScrollTo().assertIsDisplayed()
        val plot = rule.onNodeWithTag(TAG_TIMELINE_CHRONO_TEMPERATURE_PLOT, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val rangeBounds = range.getUnclippedBoundsInRoot()
        // Heatmap background ends 6 dp above the plot bottom. Allow 1 dp
        // rounding tolerance while requiring at least 5 dp inside that edge.
        assertTrue("Feels-like range must stay inside heatmap: $rangeBounds vs $plot",
            plot.bottom.value - rangeBounds.bottom.value >= 11f)
        rule.runOnIdle { enabled.value = false }
        topRange.assertDoesNotExist()
        range.assertDoesNotExist()
        rule.onNodeWithTag(TAG_TIMELINE_CHRONO_APPARENT, useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun shared_toggle_controls_all_four_presentations_and_all_hourly_metrics() {
        val enabled = mutableStateOf(false)
        val mode = mutableStateOf(DisplayMode.HOURLY)
        val layout = mutableStateOf(TimelineLayout.COLUMNS)
        val models = mutableStateOf(2)
        rule.setContent {
            MeteoCompareTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    val point = dailyPoint(models.value)
                    val hourly = point.copy(date = null, instant = now, temperatureC = 30.0,
                        apparentTemperatureC = 33.0, apparentTemperatureMinAcrossModels = 27.0,
                        apparentTemperatureMaxAcrossModels = 36.0, apparentTemperatureModelCount = models.value,
                        cloudCoverPercent = 50, cloudCoverMinAcrossModels = 20, cloudCoverMaxAcrossModels = 80, cloudCoverModelCount = models.value,
                        pressureHpa = 1015.0, pressureMinAcrossModels = 1000.0, pressureMaxAcrossModels = 1030.0, pressureModelCount = models.value,
                        humidityPercent = 60, humidityMinAcrossModels = 40, humidityMaxAcrossModels = 80, humidityModelCount = models.value,
                        dewPointC = 12.0, dewPointMinAcrossModels = 9.0, dewPointMaxAcrossModels = 17.0, dewPointModelCount = models.value,
                        windKmh = 25.0, windMinAcrossModels = 18.52, windMaxAcrossModels = 37.04, windModelCount = models.value,
                        windGustKmh = 50.0, windGustMinAcrossModels = 46.3, windGustMaxAcrossModels = 92.6, windGustModelCount = models.value)
                    SimplifiedTimelineCard(points = listOf(if (mode.value == DisplayMode.HOURLY) hourly else point),
                        mode = mode.value, timezone = "UTC", now = now, layout = layout.value,
                        showDailyRanges = enabled.value, onShowDailyRangesChange = { enabled.value = it })
                }
            }
        }
        val toggle = rule.onNodeWithTag(TAG_TIMELINE_RANGES_TOGGLE)
        val common = listOf("temperature", "apparent", "rain_probability", "cloud", "pressure", "humidity", "dew_point", "wind", "gust")
        fun assertHourlyRanges(view: TimelineLayout, visible: Boolean) {
            val prefix = if (view == TimelineLayout.COLUMNS) "columns_hourly" else "timeline_hourly"
            val metrics = common + if (view == TimelineLayout.COLUMNS) "rain_amount" else "rain"
            for (metric in metrics) {
                val node = rule.onNodeWithTag("${prefix}_${metric}_range", useUnmergedTree = true)
                if (visible) node.assertExists() else node.assertDoesNotExist()
            }
        }
        toggle.performScrollTo().assertIsDisplayed().assertIsOff()
        assertHourlyRanges(TimelineLayout.COLUMNS, false)
        toggle.performClick().assertIsOn()
        for (view in TimelineLayout.entries) {
            rule.runOnIdle { layout.value = view; mode.value = DisplayMode.HOURLY; models.value = 2 }
            assertHourlyRanges(view, true)
            toggle.performScrollTo().assertIsDisplayed().assertIsOn()
            rule.runOnIdle { models.value = 1 }
            assertHourlyRanges(view, false)
            rule.runOnIdle { models.value = 2; mode.value = DisplayMode.DAILY }
            toggle.performScrollTo().assertIsDisplayed().assertIsOn()
            val prefix = if (view == TimelineLayout.COLUMNS) "columns_daily" else "timeline_daily"
            rule.onNodeWithTag("${prefix}_temp_max_range", useUnmergedTree = true).assertExists()
        }
        toggle.performClick().assertIsOff()
        rule.runOnIdle { mode.value = DisplayMode.HOURLY }
        assertHourlyRanges(TimelineLayout.CHRONO, false)
        rule.runOnIdle { layout.value = TimelineLayout.COLUMNS }
        assertHourlyRanges(TimelineLayout.COLUMNS, false)
    }

    @Test fun ranges_with_only_one_model_are_absent_even_if_enabled() {
        rule.setContent {
            MeteoCompareTheme {
                SimplifiedTimelineCard(
                    points = listOf(dailyPoint(models = 1)),
                    mode = DisplayMode.DAILY, timezone = "UTC", now = now,
                    showDailyRanges = true
                )
            }
        }
        rule.onNodeWithTag("columns_daily_temp_max_range", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("columns_daily_rain_probability_range", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun range_is_right_of_reading_when_it_fits_and_below_when_it_does_not() {
        rule.setContent {
            MeteoCompareTheme {
                Surface {
                    Column {
                        DailyRangeLabel(
                            value = "25°", range = "(22–28)",
                            rangeTag = "wide_range", valueTag = "wide_value",
                            modifier = Modifier.width(240.dp)
                        )
                        DailyRangeLabel(
                            value = "25°", range = "(22–28)",
                            rangeTag = "narrow_range", valueTag = "narrow_value",
                            // 60 dp can fit both labels on devices with compact fonts.
                            // Force the fallback branch even at small font scales.
                            modifier = Modifier.width(32.dp)
                        )
                    }
                }
            }
        }
        val wideValue = rule.onNodeWithTag("wide_value", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val wideRange = rule.onNodeWithTag("wide_range", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val narrowValue = rule.onNodeWithTag("narrow_value", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val narrowRange = rule.onNodeWithTag("narrow_range", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(wideRange.left >= wideValue.right)
        assertTrue(wideRange.top < wideValue.bottom)
        assertTrue(narrowRange.top >= narrowValue.bottom)
    }

    @Test fun switch_is_below_both_visualizations_without_persistent_convergence_explanation() {
        val layout = mutableStateOf(TimelineLayout.COLUMNS)
        val enabled = mutableStateOf(false)
        val point = dailyPoint().copy(divergenceReasons = setOf(DivergenceReason.WIND))
        rule.setContent {
            MeteoCompareTheme {
                Surface {
                    // Deliberately use a short viewport: the real city detail page
                    // scrolls vertically, and the daily lanes exceed this height.
                    Column(Modifier.fillMaxWidth().height(360.dp).verticalScroll(rememberScrollState())) {
                        SimplifiedTimelineCard(
                            points = listOf(point),
                            mode = DisplayMode.DAILY, timezone = "UTC", now = now,
                            layout = layout.value, onLayoutChange = { layout.value = it },
                            showDailyRanges = enabled.value,
                            onShowDailyRangesChange = { enabled.value = it }
                        )
                    }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hint = context.getString(R.string.timeline_disagreement_hint)
        val toggle = rule.onNodeWithTag(TAG_TIMELINE_RANGES_TOGGLE)

        fun assertToggleBelow(visualizationTag: String, rangesEnabled: Boolean) {
            // Scroll first, then measure both nodes in the same root coordinates.
            // boundsInRoot clips offscreen content and is not a layout measurement.
            toggle.performScrollTo().assertIsDisplayed().assertHasClickAction()
            if (rangesEnabled) toggle.assertIsOn() else toggle.assertIsOff()
            val visualization = rule.onNodeWithTag(visualizationTag, useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            val switchBounds = toggle.getUnclippedBoundsInRoot()
            assertTrue("$visualizationTag must have a positive height: $visualization",
                visualization.bottom.value > visualization.top.value)
            assertTrue("Toggle must have a positive height: $switchBounds",
                switchBounds.bottom.value > switchBounds.top.value)
            assertTrue("Toggle must be below $visualizationTag (ranges=$rangesEnabled): " +
                "$switchBounds vs $visualization", switchBounds.top.value >= visualization.bottom.value)
            rule.onNodeWithText(hint, useUnmergedTree = true).assertDoesNotExist()
            rule.onNodeWithTag(TAG_TIMELINE_CONVERGENCE_INFO_DIALOG).assertDoesNotExist()
        }

        // One point fills the Columns visualization vertically. Check the entire
        // column, including the convergence area, not just its temperature band.
        val columnTag = "timeline_point_${timelinePointKey(point)}"
        assertToggleBelow(columnTag, rangesEnabled = false)
        toggle.performClick()
        assertToggleBelow(columnTag, rangesEnabled = true)

        rule.onNodeWithText(context.getString(R.string.timeline_layout_chrono))
            .performScrollTo().performClick()
        assertToggleBelow(TAG_TIMELINE_CHRONO_VIEW, rangesEnabled = true)
        toggle.performClick()
        assertToggleBelow(TAG_TIMELINE_CHRONO_VIEW, rangesEnabled = false)
    }

    @Test fun tapping_convergence_in_daily_columns_opens_and_dismisses_explanation() {
        val point = dailyPoint().copy(
            consensusPercent = 63, consensusLevel = ModelConsensusLevel.MEDIUM,
            divergenceReasons = setOf(DivergenceReason.WIND)
        )
        rule.setContent {
            MeteoCompareTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SimplifiedTimelineCard(
                        points = listOf(point), mode = DisplayMode.DAILY,
                        timezone = "UTC", now = now
                    )
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hint = context.getString(R.string.timeline_disagreement_hint)
        rule.onNodeWithText(hint).assertDoesNotExist()
        rule.onNodeWithTag(TAG_TIMELINE_CONSENSUS_BADGE)
            .assertHasClickAction().performScrollTo().performClick()
        rule.onNodeWithTag(TAG_TIMELINE_CONVERGENCE_INFO_DIALOG).assertIsDisplayed()
        rule.onNodeWithText(hint).assertIsDisplayed()
        rule.onNodeWithTag(TAG_TIMELINE_CONVERGENCE_INFO_CLOSE).performClick()
        rule.onNodeWithTag(TAG_TIMELINE_CONVERGENCE_INFO_DIALOG).assertDoesNotExist()
        rule.onNodeWithText(hint).assertDoesNotExist()
        // The divergence icons are part of the same interactive score area.
        rule.onNodeWithTag(TAG_TIMELINE_DIVERGENCE_REASON)
            .assertHasClickAction().performScrollTo().performClick()
        rule.onNodeWithText(hint).assertIsDisplayed()
    }

    @Test fun hourly_columns_score_opens_help_without_divergence() {
        rule.setContent {
            MeteoCompareTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SimplifiedTimelineCard(
                        points = listOf(SimplifiedTimelinePoint(
                            instant = now, temperatureC = 19.0,
                            consensusPercent = 90,
                            consensusLevel = ModelConsensusLevel.HIGH
                        )),
                        mode = DisplayMode.HOURLY, timezone = "UTC", now = now
                    )
                }
            }
        }
        val hint = InstrumentationRegistry.getInstrumentation().targetContext.getString(
            R.string.timeline_disagreement_hint
        )
        rule.onNodeWithText(hint).assertDoesNotExist()
        rule.onNodeWithTag(TAG_TIMELINE_CONSENSUS_BADGE)
            .assertHasClickAction().performScrollTo().performClick()
        rule.onNodeWithText(hint).assertIsDisplayed()
        rule.onNodeWithTag(TAG_TIMELINE_CONVERGENCE_INFO_CLOSE).performClick()
        rule.onNodeWithText(hint).assertDoesNotExist()
    }

    @Test fun tapping_convergence_in_chrono_opens_explanation_in_daily_and_hourly() {
        val mode = mutableStateOf(DisplayMode.DAILY)
        rule.setContent {
            MeteoCompareTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SimplifiedTimelineCard(
                        points = listOf(if (mode.value == DisplayMode.DAILY) {
                            dailyPoint().copy(consensusPercent = 75)
                        } else {
                            SimplifiedTimelinePoint(
                                instant = now, temperatureC = 17.0,
                                consensusPercent = 75,
                                consensusLevel = ModelConsensusLevel.HIGH
                            )
                        }),
                        mode = mode.value, timezone = "UTC", now = now,
                        layout = TimelineLayout.CHRONO
                    )
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hint = context.getString(R.string.timeline_disagreement_hint)
        for (displayMode in listOf(DisplayMode.DAILY, DisplayMode.HOURLY)) {
            rule.runOnIdle { mode.value = displayMode }
            rule.waitForIdle()
            rule.onNodeWithText(hint).assertDoesNotExist()
            rule.onNodeWithTag(TAG_TIMELINE_CHRONO_CONVERGENCE_CELL)
                .assertHasClickAction().performScrollTo().performClick()
            rule.onNodeWithTag(TAG_TIMELINE_CONVERGENCE_INFO_DIALOG).assertIsDisplayed()
            rule.onNodeWithText(hint).assertIsDisplayed()
            rule.onNodeWithTag(TAG_TIMELINE_CONVERGENCE_INFO_CLOSE).performClick()
            rule.onNodeWithText(hint).assertDoesNotExist()
            rule.onNodeWithTag(TAG_TIMELINE_CHRONO_CONVERGENCE_TITLE)
                .assertHasClickAction().performScrollTo().performClick()
            rule.onNodeWithText(hint).assertIsDisplayed()
            rule.onNodeWithTag(TAG_TIMELINE_CONVERGENCE_INFO_CLOSE).performClick()
        }
    }

    @Test fun daily_heatmap_keeps_exact_height_and_visible_temperatures_with_ranges() {
        val enabled = mutableStateOf(false)
        rule.setContent {
            MeteoCompareTheme {
                SimplifiedTimelineCard(
                    points = listOf(dailyPoint()),
                    mode = DisplayMode.DAILY, timezone = "UTC", now = now,
                    showDailyRanges = enabled.value,
                    onShowDailyRangesChange = { enabled.value = it }
                )
            }
        }
        // Test layout in dp rather than clipped semantics pixels. The latter
        // can use a different density on emulators with display overrides.
        val original = rule.onNodeWithTag(TAG_TIMELINE_HEATMAP_BAND, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val originalHeight = (original.bottom - original.top).value
        assertEquals(58f, originalHeight, 0.5f)
        rule.runOnIdle { enabled.value = true }
        val withRanges = rule.onNodeWithTag(TAG_TIMELINE_HEATMAP_BAND, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals("The daily heatmap must never grow when ranges are enabled",
            originalHeight, (withRanges.bottom - withRanges.top).value, 0.5f)
        for (tag in listOf("columns_daily_temp_max_range", "columns_daily_temp_min_range")) {
            val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true)
                .assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("$tag must fit vertically into the original heatmap",
                bounds.top >= withRanges.top - 1.dp && bounds.bottom <= withRanges.bottom + 1.dp)
        }
        rule.runOnIdle { enabled.value = false }
        val restored = rule.onNodeWithTag(TAG_TIMELINE_HEATMAP_BAND, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals(originalHeight, (restored.bottom - restored.top).value, 0.5f)
    }

    @Test fun single_line_temperature_range_never_expands_vertically_on_narrow_columns() {
        rule.setContent {
            MeteoCompareTheme {
                DailyRangeLabel(
                    value = "101°F", range = "(84–115°F)", rangeTag = "fixed_range",
                    valueTag = "fixed_value", modifier = Modifier.width(90.dp),
                    singleLine = true
                )
            }
        }
        val value = rule.onNodeWithTag("fixed_value", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val range = rule.onNodeWithTag("fixed_range", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Secondary range should remain on the same line", range.top < value.bottom)
        assertTrue("Main temperature should remain readable", value.width > 0f)
    }

    @Test fun columns_icons_remain_next_to_values_with_and_without_model_ranges() {
        val visible = mutableStateOf(true)
        rule.setContent {
            MeteoCompareTheme {
                SimplifiedTimelineCard(
                    points = listOf(dailyPoint()),
                    mode = DisplayMode.DAILY, timezone = "UTC", now = now,
                    showDailyRanges = visible.value,
                    onShowDailyRangesChange = { visible.value = it }
                )
            }
        }
        fun iconGap(icon: String, value: String): Float {
            val a = rule.onNodeWithTag(icon, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val b = rule.onNodeWithTag(value, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            return b.left - a.right
        }
        // 3 dp spacer; allow extra rounding due to density.
        val density = rule.density.density
        assertTrue(iconGap("columns_daily_rain_probability_icon",
            "columns_daily_rain_probability_value") in 0f..(5f * density))
        assertTrue(iconGap("columns_daily_cloud_icon",
            "columns_daily_cloud_value") in 0f..(5f * density))
        rule.runOnIdle { visible.value = false }
        assertTrue(iconGap("columns_daily_rain_probability_icon",
            "columns_daily_rain_probability_value") in 0f..(6f * density))
    }

    @Test fun compact_toggle_is_card_free_and_keeps_accessible_hit_target() {
        val enabled = mutableStateOf(false)
        rule.setContent {
            MeteoCompareTheme {
                SimplifiedTimelineCard(
                    points = listOf(dailyPoint()),
                    mode = DisplayMode.DAILY, timezone = "UTC", now = now,
                    showDailyRanges = enabled.value,
                    onShowDailyRangesChange = { enabled.value = it }
                )
            }
        }
        val toggle = rule.onNodeWithTag(TAG_TIMELINE_RANGES_TOGGLE)
        toggle.assertIsDisplayed().assertIsOff().assertHasClickAction()
        // Height of a small unframed label+switch, not a large filled card.
        val height = toggle.fetchSemanticsNode().boundsInRoot.height
        assertTrue("Ranges toggle is too tall: $height", height <= 40f * rule.density.density)
        toggle.performClick()
        toggle.assertIsOn()
        rule.runOnIdle { assertTrue(enabled.value) }
    }

    @Test fun chrono_rain_and_wind_use_title_icons_only_in_daily_and_hourly() {
        val mode = mutableStateOf(DisplayMode.DAILY)
        val ranges = mutableStateOf(false)
        rule.setContent {
            MeteoCompareTheme {
                val point = if (mode.value == DisplayMode.DAILY) dailyPoint().copy(
                    windKmh = 28.0, windMinAcrossModels = 12.0,
                    windMaxAcrossModels = 45.0, windModelCount = 3
                ) else SimplifiedTimelinePoint(
                    instant = now, precipitationPercent = 65, windKmh = 28.0
                )
                ChronoTimelineView(
                    points = listOf(point), mode = mode.value,
                    timezone = "UTC", now = now, showDailyRanges = ranges.value
                )
            }
        }
        for (displayMode in listOf(DisplayMode.DAILY, DisplayMode.HOURLY)) {
            for (show in listOf(false, true)) {
                rule.runOnIdle { mode.value = displayMode; ranges.value = show }
                rule.waitForIdle()
                // The fixed title rail keeps its icons; cells don't repeat them.
                rule.onNodeWithTag("timeline_title_rain_icon", useUnmergedTree = true)
                    .assertExists()
                rule.onNodeWithTag("timeline_title_wind_icon", useUnmergedTree = true)
                    .assertExists()
                rule.onNodeWithTag("timeline_daily_rain_probability_icon", useUnmergedTree = true)
                    .assertDoesNotExist()
                rule.onNodeWithTag("timeline_daily_wind_icon", useUnmergedTree = true)
                    .assertDoesNotExist()
                rule.onNodeWithTag("timeline_daily_rain_probability_value", useUnmergedTree = true)
                    .assertExists()
                rule.onNodeWithTag("timeline_daily_wind_value", useUnmergedTree = true)
                    .assertExists()
            }
        }
    }

    @Test fun column_daily_range_preserves_main_values_and_left_icon_rail() {
        rule.setContent {
            MeteoCompareTheme {
                SimplifiedTimelineCard(
                    points = listOf(dailyPoint()),
                    mode = DisplayMode.DAILY, timezone = "UTC", now = now,
                    showDailyRanges = true
                )
            }
        }
        for ((icon, value, range) in listOf(
            Triple("columns_daily_rain_probability_icon",
                "columns_daily_rain_probability_value", "columns_daily_rain_probability_range"),
            Triple("columns_daily_cloud_icon",
                "columns_daily_cloud_value", "columns_daily_cloud_range")
        )) {
            // Even if a metric has no multi-model samples, its value stays put.
            val iconBounds = rule.onNodeWithTag(icon, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val valueBounds = rule.onNodeWithTag(value, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue(valueBounds.left >= iconBounds.right)
            assertTrue(valueBounds.left - iconBounds.right <= 5f * rule.density.density)
            if (range == "columns_daily_rain_probability_range") {
                rule.onNodeWithTag(range, useUnmergedTree = true).assertExists()
            }
        }
    }

}
