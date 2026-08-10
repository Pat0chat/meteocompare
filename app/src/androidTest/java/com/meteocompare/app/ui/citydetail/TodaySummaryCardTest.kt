package com.meteocompare.app.ui.citydetail

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Rule
import org.junit.Test

class TodaySummaryCardTest {
    @get:Rule val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(today: DayConfidence, modelCount: Int = 5) {
        composeRule.setContent {
            MeteoCompareTheme {
                Surface { TodaySummaryCard(today = today, modelCount = modelCount, currentTemp = null) }
            }
        }
    }

    @Test
    fun displays_all_present_variables() {
        render(
            DayConfidence(
                date = TestFixtures.today,
                tempMax = ConfidenceScore(85, 21.0, 24.0, 22.5, 0.8, 5),
                tempMin = ConfidenceScore(78, 14.0, 17.0, 15.5, 1.0, 5),
                precipitation = PrecipitationConfidence.NoRain(100, 5, 0.0),
                windMax = ConfidenceScore(72, 12.0, 18.0, 15.0, 2.5, 5),
                windGustMax = ConfidenceScore(68, 28.0, 36.0, 32.0, 3.2, 5)
            )
        )

        composeRule.onNodeWithText(context.getString(R.string.models_analysed_many, 5), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.var_temp_max), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.var_temp_min), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.var_precipitation), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.metric_detail_wind), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.metric_gust_detail, "28–36"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.precip_dry), useUnmergedTree = true).assertIsDisplayed()
        // TodaySummaryCard merges descendants for accessibility. The confidence label is
        // intentionally a secondary visual element, so this test verifies that it is
        // emitted in the unmerged semantics tree rather than requiring viewport visibility.
        composeRule.onNodeWithText(
            context.getString(R.string.metric_confidence_high, 85),
            useUnmergedTree = true
        ).assertExists()
    }

    @Test
    fun omits_absent_variables() {
        render(
            DayConfidence(
                date = TestFixtures.today,
                tempMax = ConfidenceScore(85, 21.0, 24.0, 22.5, 0.8, 3),
                tempMin = null,
                precipitation = null,
                windMax = null
            ),
            modelCount = 3
        )
        composeRule.onNodeWithText(context.getString(R.string.var_temp_max), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.var_temp_min), useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.var_precipitation), useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.metric_detail_wind), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun rain_state_shows_precipitation_range() {
        render(
            DayConfidence(
                date = TestFixtures.today,
                tempMax = null,
                tempMin = null,
                precipitation = PrecipitationConfidence.Rain(75, 5, 2.0, 6.0, 4.0),
                windMax = null
            )
        )
        composeRule.onNodeWithText("2–6", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun divided_state_shows_model_split() {
        render(
            DayConfidence(
                date = TestFixtures.today,
                tempMax = null,
                tempMin = null,
                precipitation = PrecipitationConfidence.Divided(
                    percent = 20,
                    modelCount = 5,
                    modelsForRain = 3,
                    modelsAgainstRain = 2,
                    rainMinMm = 1.5,
                    rainMaxMm = 3.0,
                    rainMeanMm = 2.2
                ),
                windMax = null
            )
        )
        composeRule.onNodeWithText("2–3", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.metric_precip_models_rain, 3, 5),
            useUnmergedTree = true
        ).assertIsDisplayed()
    }
}
