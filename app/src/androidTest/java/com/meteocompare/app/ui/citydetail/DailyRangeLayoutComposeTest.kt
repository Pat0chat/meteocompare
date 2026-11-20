package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
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
        rule.onNodeWithTag("columns_daily_temp_max_range", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("columns_daily_rain_amount_range", useUnmergedTree = true).assertDoesNotExist()
        toggle.performClick()
        rule.onNodeWithTag("columns_daily_temp_max_range", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("columns_daily_rain_amount_range", useUnmergedTree = true).assertIsDisplayed()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithText(context.getString(R.string.timeline_layout_chrono)).performClick()
        rule.onNodeWithTag("timeline_daily_temp_max_range", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("timeline_daily_rain_range", useUnmergedTree = true).assertIsDisplayed()
        toggle.performClick()
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
}
