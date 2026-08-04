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
import org.junit.Rule
import org.junit.Test

class ForecastInsightsSectionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun alert_is_emphasized_even_when_it_is_not_the_first_message() {
        val watch = insight(
            kind = ForecastInsightKind.WIND_EVENT,
            hour = 2,
            level = ForecastInsightLevel.WATCH,
            value = 12,
            secondary = 45
        )
        val alert = insight(
            kind = ForecastInsightKind.RAIN_LIKELY,
            hour = 6,
            level = ForecastInsightLevel.ALERT,
            value = 90,
            secondary = 4,
            condition = WeatherCondition.THUNDERSTORM
        )

        setContent(listOf(watch, alert))

        composeRule.onAllNodesWithTag(TAG_FORECAST_INSIGHT_PRIMARY).assertCountEquals(1)
        composeRule.onAllNodesWithTag(TAG_FORECAST_INSIGHT_SECONDARY).assertCountEquals(1)
        composeRule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(
                R.string.forecast_insight_title_weather_thunderstorm
            )
        ).assertIsDisplayed()
    }

    @Test
    fun stable_state_uses_the_lightweight_row() {
        val stable = insight(
            kind = ForecastInsightKind.HIGH_AGREEMENT,
            hour = 3,
            level = ForecastInsightLevel.POSITIVE,
            value = 88
        )

        setContent(listOf(stable))

        composeRule.onNodeWithTag(TAG_FORECAST_INSIGHT_STABLE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_FORECAST_INSIGHT_PRIMARY).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_FORECAST_INSIGHTS_TIMELINE_HINT).assertDoesNotExist()
    }

    @Test
    fun disagreement_metric_pills_keep_identical_dimensions() {
        val point = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-26T15:00:00Z"),
            modelCount = 4,
            hasMultiModelEvidence = true,
            consensusPercent = 35,
            divergenceReasons = setOf(DivergenceReason.PRECIPITATION, DivergenceReason.WIND)
        )
        val disagreement = ForecastInsight(
            kind = ForecastInsightKind.DISAGREEMENT,
            level = ForecastInsightLevel.WATCH,
            point = point,
            divergenceReasons = point.divergenceReasons
        )

        setContent(listOf(disagreement), clickable = false)

        val reasonBounds = composeRule
            .onNodeWithTag("${TAG_FORECAST_INSIGHT_REASON_PREFIX}precipitation")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val consensusBounds = composeRule
            .onNodeWithTag(TAG_FORECAST_INSIGHT_CONSENSUS)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        assertEquals(consensusBounds.height, reasonBounds.height, 0.5f)
        assertEquals(reasonBounds.height, reasonBounds.width, 0.5f)
    }

    @Test
    fun severe_precipitation_uses_specific_wording() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alert = insight(
            kind = ForecastInsightKind.RAIN_LIKELY,
            hour = 7,
            level = ForecastInsightLevel.ALERT,
            value = 80,
            secondary = 4,
            condition = WeatherCondition.THUNDERSTORM
        )

        setContent(listOf(alert), clickable = false)

        composeRule.onNodeWithText(
            context.getString(R.string.forecast_insight_title_weather_thunderstorm)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.forecast_insight_title_rain_likely)
        ).assertDoesNotExist()
    }

    private fun setContent(insights: List<ForecastInsight>, clickable: Boolean = true) {
        composeRule.setContent {
            MeteoCompareTheme {
                Surface {
                    ForecastInsightsSection(
                        insights = insights,
                        timezone = "Europe/Paris",
                        onInsightClick = if (clickable) ({}) else null
                    )
                }
            }
        }
    }

    private fun insight(
        kind: ForecastInsightKind,
        hour: Int,
        level: ForecastInsightLevel,
        value: Int? = null,
        secondary: Int? = null,
        condition: WeatherCondition? = null
    ): ForecastInsight {
        val point = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-26T10:00:00Z").plusSeconds(hour * 3_600L),
            precipitationPercent = value,
            precipitationSource = if (kind in setOf(
                    ForecastInsightKind.RAIN_LIKELY,
                    ForecastInsightKind.RAIN_UNCERTAIN
                )) PrecipitationSignalSource.MODEL_PROBABILITY else null,
            precipitationModelCount = secondary ?: 0,
            windKmh = secondary?.toDouble(),
            condition = condition,
            modelCount = 4,
            hasMultiModelEvidence = true
        )
        return ForecastInsight(
            kind = kind,
            level = level,
            point = point,
            value = value,
            secondaryValue = secondary,
            targetValue = value,
            targetCondition = condition,
            precipitationSource = point.precipitationSource
        )
    }
}
