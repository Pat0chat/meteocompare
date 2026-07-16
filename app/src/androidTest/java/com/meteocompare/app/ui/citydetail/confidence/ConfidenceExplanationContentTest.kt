package com.meteocompare.app.ui.citydetail.confidence

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.EqualWeighting
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConfidenceExplanationContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun loaded_explanation_displays_city_variables_and_back_action() {
        val forecast = TestFixtures.forecast()
        val calculator = ConfidenceCalculator(EqualWeighting())
        val day = calculator.dayConfidence(forecast, TestFixtures.today)
        val models = forecast.availableModels
        val breakdowns = listOf(
            VariableBreakdown(
                VariableKind.TEMP_MAX,
                models.mapIndexed { index, model -> ModelValue(model, 24.0 + index) }
            ),
            VariableBreakdown(
                VariableKind.PRECIPITATION,
                models.mapIndexed { index, model -> ModelValue(model, index.toDouble()) }
            )
        )
        var backed = false

        composeRule.setContent {
            MeteoCompareTheme {
                ConfidenceExplanationContent(
                    state = ConfidenceExplanationUiState.Loaded(
                        city = TestFixtures.paris,
                        date = TestFixtures.today,
                        dayConfidence = day,
                        variableBreakdowns = breakdowns,
                        contributingModels = models
                    ),
                    onBack = { backed = true }
                )
            }
        }

        composeRule.onNodeWithTag(TAG_CONFIDENCE_EXPLANATION_ROOT).assertIsDisplayed()
        composeRule.onNodeWithText(TestFixtures.paris.shortLabel).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_CONFIDENCE_EXPLANATION_BACK).performClick()
        assertTrue(backed)
    }

    @Test
    fun error_state_stays_inside_the_screen_scaffold() {
        composeRule.setContent {
            MeteoCompareTheme {
                ConfidenceExplanationContent(
                    state = ConfidenceExplanationUiState.Error("Date invalide"),
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithTag(TAG_CONFIDENCE_EXPLANATION_ROOT).assertIsDisplayed()
        composeRule.onNodeWithText("Date invalide").assertIsDisplayed()
    }
}
