package com.meteocompare.app.ui.citydetail

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ForecastInsightsSectionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun first_insight_is_highlighted_in_the_single_section() {
        val firstPoint = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-26T15:00:00Z"),
            precipitationPercent = 60,
            precipitationModelCount = 3,
            precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
            hasMultiModelEvidence = true,
        )
        val secondPoint = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-26T19:00:00Z"),
            windKmh = 35.0,
            windModelCount = 3,
            hasMultiModelEvidence = true,
        )

        composeRule.setContent {
            MeteoCompareTheme {
                Surface {
                    ForecastInsightsSection(
                        insights = listOf(
                            ForecastInsight(
                                kind = ForecastInsightKind.RAIN_UNCERTAIN,
                                point = firstPoint,
                                value = 60,
                                precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY
                            ),
                            ForecastInsight(
                                kind = ForecastInsightKind.WIND_RISING,
                                point = secondPoint,
                                secondaryValue = 35
                            )
                        ),
                        timezone = "Europe/Paris"
                    )
                }
            }
        }

        composeRule.onNodeWithTag(TAG_FORECAST_INSIGHTS_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_FORECAST_INSIGHT_PRIMARY).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_FORECAST_INSIGHT_SECONDARY).assertIsDisplayed()

        val primaryHeight = composeRule
            .onNodeWithTag(TAG_FORECAST_INSIGHT_PRIMARY)
            .fetchSemanticsNode()
            .boundsInRoot.height
        val secondaryHeight = composeRule
            .onNodeWithTag(TAG_FORECAST_INSIGHT_SECONDARY)
            .fetchSemanticsNode()
            .boundsInRoot.height

        assertTrue(primaryHeight > secondaryHeight)
    }
}
