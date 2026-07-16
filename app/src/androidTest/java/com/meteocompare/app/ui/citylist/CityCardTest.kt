package com.meteocompare.app.ui.citylist

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CityCardTest {
    @get:Rule val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(state: ForecastState, onRetry: () -> Unit = {}) {
        composeRule.setContent {
            MeteoCompareTheme {
                Surface {
                    CityCard(
                        state = CityCardState(TestFixtures.paris, state),
                        onClick = {}, onRemove = {}, onRetry = onRetry
                    )
                }
            }
        }
    }

    @Test
    fun loading_card_displays_city_identity() {
        render(ForecastState.Loading)
        composeRule.onNodeWithText(TestFixtures.paris.name, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(TestFixtures.paris.admin1!!, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun loaded_card_formats_temperature_and_dry_precipitation() {
        render(
            ForecastState.Loaded(
                DayConfidence(
                    date = TestFixtures.today,
                    tempMax = ConfidenceScore(85, 21.0, 24.0, 22.5, 0.8, 5),
                    tempMin = ConfidenceScore(78, 14.0, 17.0, 15.5, 1.0, 5),
                    precipitation = PrecipitationConfidence.NoRain(100, 5, 0.0),
                    windMax = null
                ),
                currentTemp = null
            )
        )
        composeRule.onNodeWithText("21-24°", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.precip_dry),
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    @Test
    fun loaded_card_formats_divided_precipitation() {
        render(
            ForecastState.Loaded(
                DayConfidence(
                    date = TestFixtures.today,
                    tempMax = ConfidenceScore(60, 20.0, 25.0, 22.0, 1.5, 5),
                    tempMin = null,
                    precipitation = PrecipitationConfidence.Divided(20, 5, 3, 2),
                    windMax = null
                ),
                currentTemp = null
            )
        )
        composeRule.onNodeWithText("3/5 ⚠", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun tight_temperature_spread_uses_single_rounded_value() {
        render(
            ForecastState.Loaded(
                DayConfidence(
                    date = TestFixtures.today,
                    tempMax = ConfidenceScore(95, 21.5, 22.5, 22.0, 0.3, 5),
                    tempMin = null,
                    precipitation = null,
                    windMax = null
                ),
                currentTemp = null
            )
        )
        composeRule.onNodeWithText("22°", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun error_state_shows_message_and_retry_callback() {
        var retried = false
        render(ForecastState.Error("Délai dépassé"), onRetry = { retried = true })
        composeRule.onNodeWithText("Délai dépassé", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_retry), useUnmergedTree = true).performClick()
        assertTrue(retried)
    }
}
