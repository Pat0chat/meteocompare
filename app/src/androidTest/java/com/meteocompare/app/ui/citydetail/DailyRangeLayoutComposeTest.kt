package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
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
                Surface {
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
        val toggle = rule.onNodeWithTag(TAG_TIMELINE_RANGES_TOGGLE)
        toggle.assertIsDisplayed()
        toggle.assertIsOff()
        toggle.assertHasClickAction()
        rule.onNodeWithTag("columns_daily_temp_max_range", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("columns_daily_rain_amount_range", useUnmergedTree = true).assertDoesNotExist()
        toggle.performClick()
        toggle.assertIsOn()
        rule.onNodeWithTag("columns_daily_temp_max_range", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("columns_daily_rain_amount_range", useUnmergedTree = true).assertIsDisplayed()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithText(context.getString(R.string.timeline_layout_chrono)).performClick()
        rule.onNodeWithTag("timeline_daily_temp_max_range", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("timeline_daily_rain_range", useUnmergedTree = true).assertIsDisplayed()
        toggle.performClick()
        toggle.assertIsOff()
        rule.onNodeWithTag("timeline_daily_temp_max_range", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("timeline_daily_rain_range", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.timeline_layout_columns)).performClick()
        rule.onNodeWithTag("columns_daily_temp_max_range", useUnmergedTree = true).assertDoesNotExist()
        rule.runOnIdle { assertEquals(false, enabled.value) }
    }

    @Test fun hourly_timeline_does_not_offer_the_daily_only_toggle() {
        rule.setContent {
            MeteoCompareTheme {
                SimplifiedTimelineCard(
                    points = listOf(SimplifiedTimelinePoint(instant = now, temperatureC = 18.0)),
                    mode = DisplayMode.HOURLY, timezone = "UTC", now = now,
                    onShowDailyRangesChange = {}
                )
            }
        }
        rule.onNodeWithTag(TAG_TIMELINE_RANGES_TOGGLE).assertDoesNotExist()
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
                            modifier = Modifier.width(60.dp)
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
        rule.setContent {
            MeteoCompareTheme {
                SimplifiedTimelineCard(
                    points = listOf(dailyPoint().copy(divergenceReasons = setOf(DivergenceReason.WIND))),
                    mode = DisplayMode.DAILY, timezone = "UTC", now = now,
                    layout = layout.value, onLayoutChange = { layout.value = it },
                    onShowDailyRangesChange = {}
                )
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hint = context.getString(R.string.timeline_disagreement_hint)
        val switch = rule.onNodeWithTag(TAG_TIMELINE_RANGES_TOGGLE)
        val columnsY = rule.onNodeWithTag(TAG_TIMELINE_HEATMAP_BAND, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(switch.fetchSemanticsNode().boundsInRoot.top > columnsY)
        rule.onNodeWithText(hint).assertDoesNotExist()

        rule.onNodeWithText(context.getString(R.string.timeline_layout_chrono)).performClick()
        val chronoBottom = rule.onNodeWithTag(TAG_TIMELINE_CHRONO_VIEW)
            .fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(switch.fetchSemanticsNode().boundsInRoot.top >= chronoBottom)
        rule.onNodeWithText(hint).assertDoesNotExist()
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
        val original = rule.onNodeWithTag(TAG_TIMELINE_HEATMAP_BAND, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(64f * rule.density.density, original.height, 1f * rule.density.density)
        rule.runOnIdle { enabled.value = true }
        val withRanges = rule.onNodeWithTag(TAG_TIMELINE_HEATMAP_BAND, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals("The daily heatmap must never grow when ranges are enabled",
            original.height, withRanges.height, 1f)
        for (tag in listOf("columns_daily_temp_max_range", "columns_daily_temp_min_range")) {
            val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$tag must fit vertically into the original heatmap",
                bounds.top >= withRanges.top - 1f && bounds.bottom <= withRanges.bottom + 1f)
        }
        rule.runOnIdle { enabled.value = false }
        assertEquals(original.height,
            rule.onNodeWithTag(TAG_TIMELINE_HEATMAP_BAND, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.height, 1f)
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
