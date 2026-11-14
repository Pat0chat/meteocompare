package com.meteocompare.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.util.ConditionDecision
import com.meteocompare.app.domain.util.ConditionOrigin
import com.meteocompare.app.domain.util.ConditionReason
import com.meteocompare.app.domain.util.ConditionStatus
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Rule
import org.junit.Test

/** Instrumented UI contract: run with :app:connectedDebugAndroidTest on an emulator. */
class ConditionStatusIconTest {
    @get:Rule val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun every_status_is_accessible_and_opens_its_explanation() {
        val statuses = listOf(
            ConditionStatus.RELIABLE to R.string.condition_status_reliable,
            ConditionStatus.ESTIMATED to R.string.condition_status_estimated,
            ConditionStatus.AMBIGUOUS to R.string.condition_status_ambiguous,
            ConditionStatus.INCOMPLETE to R.string.condition_status_incomplete,
            ConditionStatus.UNAVAILABLE to R.string.condition_status_unavailable
        )
        composeRule.setContent {
            MeteoCompareTheme {
                Column {
                    statuses.forEach { (status, _) ->
                        val condition = if (status == ConditionStatus.UNAVAILABLE || status == ConditionStatus.AMBIGUOUS)
                            null else WeatherCondition.CLEAR
                        ConditionStatusIcon(
                            condition = condition,
                            decision = ConditionDecision(
                                condition = condition, status = status,
                                origin = if (status == ConditionStatus.UNAVAILABLE)
                                    ConditionOrigin.NONE else ConditionOrigin.HOURLY_WMO,
                                coverage = if (status == ConditionStatus.UNAVAILABLE) 0.0 else 0.75,
                                alternatives = if (status == ConditionStatus.AMBIGUOUS)
                                    listOf(WeatherCondition.CLEAR, WeatherCondition.RAIN) else emptyList(),
                                reasons = if (status == ConditionStatus.INCOMPLETE)
                                    setOf(ConditionReason.PARTIAL_PERIOD) else emptySet()
                            )
                        )
                    }
                }
            }
        }
        statuses.forEach { (_, title) ->
            composeRule.onNodeWithContentDescription(context.getString(title), substring = true)
                .assertIsDisplayed().performClick()
            composeRule.onNodeWithTag("condition_explanation_dialog").assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(title), substring = true, useUnmergedTree = true)
                .assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.condition_explanation_close))
                .performClick()
        }
    }

    @Test
    fun partial_period_exposes_coverage_and_source() {
        composeRule.setContent {
            MeteoCompareTheme {
                ConditionStatusIcon(
                    WeatherCondition.RAIN,
                    ConditionDecision(WeatherCondition.RAIN, ConditionStatus.INCOMPLETE,
                        ConditionOrigin.HOURLY_DOMINANT, coverage = .625,
                        reasons = setOf(ConditionReason.PARTIAL_PERIOD))
                )
            }
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.condition_status_incomplete),
            substring = true).performClick()
        composeRule.onNodeWithText(context.getString(R.string.condition_explanation_time_coverage, 63))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("condition_coverage").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.condition_explanation_reasons_title))
            .assertExists()
    }

    @Test
    fun ambiguity_exposes_real_alternative_conditions() {
        composeRule.setContent {
            MeteoCompareTheme {
                ConditionStatusIcon(null, ConditionDecision(null, ConditionStatus.AMBIGUOUS,
                    ConditionOrigin.MODEL_CONSENSUS, alternatives = listOf(
                        WeatherCondition.CLEAR, WeatherCondition.RAIN)))
            }
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.condition_status_ambiguous),
            substring = true).performClick()
        composeRule.onNodeWithText(context.getString(R.string.condition_explanation_alternatives_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(
            com.meteocompare.app.core.locale.weatherConditionLabelRes(WeatherCondition.CLEAR)
        )).assertExists()
        composeRule.onNodeWithText(context.getString(
            com.meteocompare.app.core.locale.weatherConditionLabelRes(WeatherCondition.RAIN)
        )).assertExists()
    }

    @Test
    fun legend_explains_all_five_statuses() {
        composeRule.setContent { MeteoCompareTheme { ConditionStatusLegend() } }
        composeRule.onNodeWithTag("condition_status_legend").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("condition_legend_dialog").assertIsDisplayed()
        listOf(R.string.condition_status_reliable, R.string.condition_status_estimated,
            R.string.condition_status_ambiguous, R.string.condition_status_incomplete,
            R.string.condition_status_unavailable).forEach {
            composeRule.onNodeWithText(context.getString(it), substring = true, useUnmergedTree = true).assertExists()
        }
        composeRule.onNodeWithText(context.getString(R.string.condition_legend_not_probability))
            .assertExists()
    }
    @Test
    fun dialog_header_close_works_with_scrollable_diagnostics() {
        composeRule.setContent {
            MeteoCompareTheme {
                ConditionStatusIcon(
                    WeatherCondition.RAIN,
                    ConditionDecision(
                        WeatherCondition.RAIN,
                        ConditionStatus.INCOMPLETE,
                        ConditionOrigin.HOURLY_DOMINANT,
                        coverage = 0.625,
                        agreement = 0.58,
                        reasons = ConditionReason.entries.toSet(),
                        notablePhenomenon = WeatherCondition.THUNDERSTORM
                    )
                )
            }
        }
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.condition_status_incomplete), substring = true
        ).performClick()
        composeRule.onNodeWithTag("condition_explanation_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("condition_dialog_close").performClick()
        composeRule.onNodeWithTag("condition_explanation_dialog").assertDoesNotExist()
    }

    @Test
    fun unavailable_explanation_does_not_show_fake_coverage() {
        composeRule.setContent {
            MeteoCompareTheme {
                ConditionStatusIcon(
                    null,
                    ConditionDecision(null, ConditionStatus.UNAVAILABLE, ConditionOrigin.NONE)
                )
            }
        }
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.condition_status_unavailable), substring = true
        ).performClick()
        composeRule.onNodeWithText(context.getString(R.string.condition_explanation_no_condition))
            .assertExists()
        composeRule.onNodeWithText(context.getString(R.string.condition_origin_none)).assertExists()
        composeRule.onNodeWithTag("condition_coverage").assertDoesNotExist()
    }

}
