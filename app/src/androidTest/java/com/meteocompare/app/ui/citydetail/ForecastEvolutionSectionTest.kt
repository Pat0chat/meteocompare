package com.meteocompare.app.ui.citydetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.DayForecastEvolution
import com.meteocompare.app.domain.model.ForecastEvolutionReport
import com.meteocompare.app.domain.model.ForecastEvolutionSnapshot
import com.meteocompare.app.domain.model.ForecastEvolutionTrend
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.ForecastRevision
import com.meteocompare.app.domain.model.VariableForecastEvolution
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class ForecastEvolutionSectionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun collapsing_hides_details_but_keeps_compact_card() {
        composeRule.setContent {
            MeteoCompareTheme {
                var expanded by remember { mutableStateOf(true) }
                ForecastEvolutionSection(
                    state = ForecastEvolutionState.Loaded(report(), highlight = null),
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                )
            }
        }

        composeRule.onNodeWithTag(TAG_FORECAST_EVOLUTION_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_FORECAST_EVOLUTION_DETAILS).assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_FORECAST_EVOLUTION_HEADER).performClick()

        composeRule.onNodeWithTag(TAG_FORECAST_EVOLUTION_DETAILS).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_FORECAST_EVOLUTION_CARD).assertIsDisplayed()
    }


    @Test
    fun compact_summary_exposes_meaningful_accessibility_description() {
        composeRule.setContent {
            MeteoCompareTheme {
                ForecastEvolutionSection(
                    state = ForecastEvolutionState.Loaded(report(), highlight = null),
                    expanded = false,
                    onExpandedChange = {}
                )
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = context.getString(
            R.string.forecast_evolution_metric_a11y,
            context.getString(R.string.forecast_evolution_metric_precipitation_sum),
            context.getString(R.string.forecast_evolution_precip_up)
        )

        composeRule.onNodeWithContentDescription(expected).assertIsDisplayed()
    }

    @Test
    fun building_history_card_can_also_be_collapsed() {
        composeRule.setContent {
            MeteoCompareTheme {
                var expanded by remember { mutableStateOf(true) }
                ForecastEvolutionSection(
                    state = ForecastEvolutionState.BuildingHistory(oldestSnapshotAt = null),
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                )
            }
        }

        composeRule.onNodeWithTag(TAG_FORECAST_EVOLUTION_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_FORECAST_EVOLUTION_DETAILS).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_FORECAST_EVOLUTION_HEADER).performClick()
        composeRule.onNodeWithTag(TAG_FORECAST_EVOLUTION_DETAILS).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_FORECAST_EVOLUTION_CARD).assertIsDisplayed()
    }

    private fun report(): ForecastEvolutionReport {
        val date = LocalDate.of(2026, 8, 22)
        val models = mapOf(
            WeatherModel.ECMWF to 18.0,
            WeatherModel.GFS to 16.0
        )
        val previousModels = mapOf(
            WeatherModel.ECMWF to 9.0,
            WeatherModel.GFS to 8.0
        )
        val evolution = VariableForecastEvolution(
            variable = ForecastEvolutionVariable.PRECIPITATION,
            targetDate = date,
            current = ForecastEvolutionSnapshot(0, 17.0, models),
            previous = listOf(ForecastEvolutionSnapshot(1, 8.5, previousModels, ageHours = 25)),
            revision = ForecastRevision(
                previousDaysAgo = 1,
                previousAgeHours = 25,
                medianDelta = 8.5,
                medianAbsoluteDelta = 8.5,
                increasedModels = 2,
                decreasedModels = 0,
                stableModels = 0,
                comparedModels = 2,
                deltasByModel = mapOf(
                    WeatherModel.ECMWF to 9.0,
                    WeatherModel.GFS to 8.0
                ),
                trend = ForecastEvolutionTrend.INCREASING
            )
        )
        return ForecastEvolutionReport(
            days = listOf(
                DayForecastEvolution(
                    date = date,
                    variables = mapOf(ForecastEvolutionVariable.PRECIPITATION to evolution)
                )
            )
        )
    }
}
