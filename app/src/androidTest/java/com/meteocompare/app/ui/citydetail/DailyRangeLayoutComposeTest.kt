package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
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

    @Test fun switch_is_below_both_visualizations_and_before_convergence_explanation() {
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
        val hintY = rule.onNodeWithText(hint).fetchSemanticsNode().boundsInRoot.top
        val columnsY = rule.onNodeWithTag(TAG_TIMELINE_HEATMAP_BAND, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(switch.fetchSemanticsNode().boundsInRoot.top > columnsY)
        assertTrue(switch.fetchSemanticsNode().boundsInRoot.bottom <= hintY)

        rule.onNodeWithText(context.getString(R.string.timeline_layout_chrono)).performClick()
        val chronoBottom = rule.onNodeWithTag(TAG_TIMELINE_CHRONO_VIEW)
            .fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(switch.fetchSemanticsNode().boundsInRoot.top >= chronoBottom)
        assertTrue(switch.fetchSemanticsNode().boundsInRoot.bottom <=
            rule.onNodeWithText(hint).fetchSemanticsNode().boundsInRoot.top)
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

    @Test fun chrono_icon_values_stay_together_with_ranges_enabled() {
        rule.setContent {
            MeteoCompareTheme {
                ChronoTimelineView(
                    points = listOf(dailyPoint().copy(windKmh = 28.0,
                        windMinAcrossModels = 12.0, windMaxAcrossModels = 45.0,
                        windModelCount = 3)),
                    mode = DisplayMode.DAILY, timezone = "UTC", now = now,
                    showDailyRanges = true
                )
            }
        }
        val density = rule.density.density
        for ((icon, value) in listOf(
            "timeline_daily_rain_probability_icon" to "timeline_daily_rain_probability_value",
            "timeline_daily_wind_icon" to "timeline_daily_wind_value"
        )) {
            val right = rule.onNodeWithTag(icon, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.right
            val left = rule.onNodeWithTag(value, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.left
            assertTrue("Icon and reading drifted apart: $icon", left - right in 0f..(7f * density))
        }
    }
}
