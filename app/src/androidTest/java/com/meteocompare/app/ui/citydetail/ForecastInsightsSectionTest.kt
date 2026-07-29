package com.meteocompare.app.ui.citydetail

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import java.time.Instant
import org.junit.Assert.assertEquals
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
            modelCount = 3,
            hasMultiModelEvidence = true,
        )
        val secondPoint = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-26T19:00:00Z"),
            windKmh = 35.0,
            windModelCount = 3,
            modelCount = 3,
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
                                secondaryValue = 3,
                                precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY
                            ),
                            ForecastInsight(
                                kind = ForecastInsightKind.WIND_EVENT,
                                point = secondPoint,
                                value = 12,
                                secondaryValue = 35
                            )
                        ),
                        timezone = "Europe/Paris",
                        onInsightClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag(TAG_FORECAST_INSIGHTS_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_FORECAST_INSIGHTS_SUMMARY).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_FORECAST_INSIGHTS_TIMELINE_HINT).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_FORECAST_INSIGHT_PRIMARY).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_FORECAST_INSIGHT_SECONDARY).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_FORECAST_INSIGHT_METRICS).assertCountEquals(2)

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

    @Test
    fun disagreement_metrics_keep_all_affected_variables_visible() {
        val point = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-26T15:00:00Z"),
            modelCount = 3,
            hasMultiModelEvidence = true,
            consensusPercent = 35,
            divergenceReasons = setOf(
                DivergenceReason.PRECIPITATION,
                DivergenceReason.WIND
            )
        )

        composeRule.setContent {
            MeteoCompareTheme {
                Surface {
                    ForecastInsightsSection(
                        insights = listOf(
                            ForecastInsight(
                                kind = ForecastInsightKind.DISAGREEMENT,
                                level = ForecastInsightLevel.WATCH,
                                point = point,
                                divergenceReasons = point.divergenceReasons
                            )
                        ),
                        timezone = "Europe/Paris"
                    )
                }
            }
        }

        composeRule.onNodeWithTag(
            "${TAG_FORECAST_INSIGHT_REASON_PREFIX}precipitation"
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            "${TAG_FORECAST_INSIGHT_REASON_PREFIX}wind"
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_FORECAST_INSIGHT_CONSENSUS).assertIsDisplayed()

        val reasonBounds = composeRule
            .onNodeWithTag("${TAG_FORECAST_INSIGHT_REASON_PREFIX}precipitation")
            .fetchSemanticsNode()
            .boundsInRoot
        val consensusBounds = composeRule
            .onNodeWithTag(TAG_FORECAST_INSIGHT_CONSENSUS)
            .fetchSemanticsNode()
            .boundsInRoot

        assertEquals(consensusBounds.height, reasonBounds.height, 0.5f)
        assertEquals(reasonBounds.height, reasonBounds.width, 0.5f)
    }

    @Test
    fun severe_precipitation_uses_the_specific_condition_wording() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val point = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-26T18:00:00Z"),
            precipitationPercent = 80,
            precipitationModelCount = 3,
            precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
            condition = WeatherCondition.THUNDERSTORM,
            conditionModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )

        composeRule.setContent {
            MeteoCompareTheme {
                Surface {
                    ForecastInsightsSection(
                        insights = listOf(
                            ForecastInsight(
                                kind = ForecastInsightKind.RAIN_LIKELY,
                                level = ForecastInsightLevel.ALERT,
                                point = point,
                                value = 80,
                                secondaryValue = 3,
                                targetValue = 80,
                                targetCondition = WeatherCondition.THUNDERSTORM,
                                precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY
                            )
                        ),
                        timezone = "Europe/Paris"
                    )
                }
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.forecast_insight_title_weather_thunderstorm)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.forecast_insight_title_rain_likely)
        ).assertDoesNotExist()
        composeRule.onNodeWithText(
            context.getString(R.string.forecast_insight_metric_probability, 80)
        ).assertIsDisplayed()
    }

}
