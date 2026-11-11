package com.meteocompare.app.ui.citydetail

import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.DayCellExtras
import com.meteocompare.app.domain.usecase.DayConditionsRow
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Vérifications géométriques des deux affichages daily (nécessitent Android/Compose). */
class DailyConditionsVisualTest {
    @get:Rule val composeRule = createComposeRule()

    private val date = LocalDate.of(2026, 8, 24)
    private val now = Instant.parse("2026-08-24T11:00:00Z")
    private val point = SimplifiedTimelinePoint(
        date = date,
        condition = WeatherCondition.CLEAR,
        dayCondition = WeatherCondition.CLEAR,
        nightCondition = WeatherCondition.RAIN
    )

    @Test
    fun daily_column_icons_are_larger_than_the_original_twenty_two_dp() {
        composeRule.setContent {
            MeteoCompareTheme {
                Surface {
                    SimplifiedTimelineCard(
                        points = listOf(point), mode = DisplayMode.DAILY,
                        timezone = "UTC", now = now
                    )
                }
            }
        }
        assertTrue(composeRule.onNodeWithTag(TAG_DAILY_DAY_ICON).getUnclippedBoundsInRoot().width >= 30.dp)
        assertTrue(composeRule.onNodeWithTag(TAG_DAILY_NIGHT_ICON).getUnclippedBoundsInRoot().width >= 30.dp)
    }

    @Test
    fun daily_chrono_icons_have_the_same_enlarged_size() {
        composeRule.setContent {
            MeteoCompareTheme {
                Surface {
                    ChronoTimelineView(
                        points = listOf(point), mode = DisplayMode.DAILY,
                        timezone = "UTC", now = now
                    )
                }
            }
        }
        assertEquals(32.dp, composeRule.onNodeWithTag(TAG_DAILY_DAY_ICON).getUnclippedBoundsInRoot().width)
        assertEquals(32.dp, composeRule.onNodeWithTag(TAG_DAILY_NIGHT_ICON).getUnclippedBoundsInRoot().width)
    }

    @Test
    fun detailed_table_day_and_night_labels_remain_top_aligned_when_badge_appears() {
        val badgeEnabled = mutableStateOf(false)
        val model = WeatherModel.GFS
        composeRule.setContent {
            MeteoCompareTheme {
                WeatherByModelTable(
                    rows = listOf(DayConditionsRow(
                        date = date,
                        byModel = mapOf(model to WeatherCondition.CLEAR),
                        daytimeByModel = mapOf(model to WeatherCondition.CLEAR),
                        nighttimeByModel = mapOf(model to WeatherCondition.CLEAR),
                        daytimeExtrasByModel = if (badgeEnabled.value)
                            mapOf(model to DayCellExtras(precipProbabilityMax = 90)) else emptyMap(),
                        nighttimeExtrasByModel = if (badgeEnabled.value)
                            mapOf(model to DayCellExtras(precipProbabilityMax = 90)) else emptyMap()
                    )),
                    modelOrder = listOf(model), today = date
                )
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val day = context.getString(R.string.condition_period_day)
        val night = context.getString(R.string.condition_period_night)
        fun positions() = Pair(
            composeRule.onNodeWithText(day, useUnmergedTree = true).getUnclippedBoundsInRoot().top,
            composeRule.onNodeWithText(night, useUnmergedTree = true).getUnclippedBoundsInRoot().top
        )
        val before = positions()
        assertEquals(before.first, before.second)
        composeRule.runOnIdle { badgeEnabled.value = true }
        composeRule.waitForIdle()
        assertEquals(before, positions())
    }
}
