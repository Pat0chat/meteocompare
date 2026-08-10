package com.meteocompare.app.ui.citydetail

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class SimplifiedTimelineCardTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun disagreement_badge_keeps_the_affected_metric_visible() {
        val point = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-26T16:00:00Z"),
            temperatureC = 22.0,
            precipitationPercent = 50,
            precipitationModelCount = 3,
            windKmh = 18.0,
            modelCount = 3,
            temperatureModelCount = 3,
            windModelCount = 3,
            hasMultiModelEvidence = true,
            consensusPercent = 35,
            consensusLevel = ModelConsensusLevel.LOW,
            divergenceReasons = setOf(
                DivergenceReason.PRECIPITATION,
                DivergenceReason.WIND
            )
        )

        composeRule.setContent {
            MeteoCompareTheme {
                Surface {
                    SimplifiedTimelineCard(
                        points = listOf(point),
                        mode = DisplayMode.HOURLY,
                        timezone = "Europe/Paris"
                    )
                }
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rainLabel = context.getString(R.string.timeline_divergence_rain)
        val windLabel = context.getString(R.string.timeline_divergence_wind)
        val expected = context.getString(
            R.string.timeline_divergence_variables_accessibility,
            "$rainLabel, $windLabel"
        )
        composeRule.onNodeWithTag(TAG_TIMELINE_DIVERGENCE_REASON)
            .assertIsDisplayed()
            .assertContentDescriptionEquals(expected)
        composeRule.onNodeWithTag(TAG_TIMELINE_DIVERGENCE_ICON_RAIN)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_TIMELINE_DIVERGENCE_ICON_WIND)
            .assertIsDisplayed()
    }

    @Test
    fun agreement_badge_uses_a_compact_percentage_instead_of_a_localized_label() {
        val point = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-26T16:00:00Z"),
            temperatureC = 22.0,
            modelCount = 3,
            temperatureModelCount = 3,
            hasMultiModelEvidence = true,
            consensusPercent = 82,
            consensusLevel = ModelConsensusLevel.HIGH
        )

        composeRule.setContent {
            MeteoCompareTheme {
                Surface {
                    SimplifiedTimelineCard(
                        points = listOf(point),
                        mode = DisplayMode.HOURLY,
                        timezone = "Europe/Paris"
                    )
                }
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val agreementLabel = context.getString(R.string.timeline_consensus_high)
        val expected = context.getString(
            R.string.timeline_consensus_accessibility,
            agreementLabel,
            82
        )
        composeRule.onNodeWithTag(TAG_TIMELINE_CONSENSUS_BADGE)
            .assertIsDisplayed()
            .assertContentDescriptionEquals(expected)
        composeRule.onNodeWithText("82%").assertIsDisplayed()
        composeRule.onNodeWithText(agreementLabel).assertDoesNotExist()
    }

    @Test
    fun first_future_point_keeps_its_real_hour_instead_of_now() {
        val point = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-26T16:00:00Z"),
            temperatureC = 22.0,
            modelCount = 2,
            temperatureModelCount = 2,
            hasMultiModelEvidence = true
        )
        val now = Instant.parse("2026-07-26T12:20:00Z")

        composeRule.setContent {
            MeteoCompareTheme {
                Surface {
                    SimplifiedTimelineCard(
                        points = listOf(point),
                        mode = DisplayMode.HOURLY,
                        timezone = "UTC",
                        now = now
                    )
                }
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText("16h").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.timeline_now)).assertDoesNotExist()
    }

    @Test
    fun key_times_use_absolute_labels_without_relative_gap() {
        val points = listOf(
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-26T22:00:00Z"),
                temperatureC = 20.0,
                modelCount = 2,
                temperatureModelCount = 2,
                hasMultiModelEvidence = true
            ),
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-27T02:00:00Z"),
                temperatureC = 18.0,
                modelCount = 2,
                temperatureModelCount = 2,
                hasMultiModelEvidence = true
            )
        )

        composeRule.setContent {
            MeteoCompareTheme {
                Surface {
                    SimplifiedTimelineCard(
                        points = points,
                        mode = DisplayMode.HOURLY,
                        timezone = "UTC",
                        now = Instant.parse("2026-07-26T22:20:00Z")
                    )
                }
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText("+4 h").assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.timeline_tomorrow)).assertIsDisplayed()
        composeRule.onNodeWithText("02h").assertIsDisplayed()
    }

    @Test
    fun event_is_shown_on_the_ruler_without_changing_regular_points() {
        val start = Instant.parse("2026-07-26T12:00:00Z")
        val points = listOf(0L, 3L, 6L).map { offset ->
            SimplifiedTimelinePoint(
                instant = start.plusSeconds(offset * 3_600L),
                temperatureC = 20.0,
                modelCount = 3,
                temperatureModelCount = 3,
                hasMultiModelEvidence = true
            )
        }
        val eventPoint = SimplifiedTimelinePoint(
            instant = start.plusSeconds(4 * 3_600L),
            precipitationPercent = 80,
            precipitationModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val event = ForecastEvent(
            kind = ForecastEventKind.PRECIPITATION,
            impact = ForecastInsightLevel.WATCH,
            priority = 80,
            startPoint = eventPoint,
            peakPoint = eventPoint
        )

        composeRule.setContent {
            MeteoCompareTheme {
                Surface {
                    SimplifiedTimelineCard(
                        points = points,
                        events = listOf(event),
                        mode = DisplayMode.HOURLY,
                        timezone = "UTC",
                        now = start.minusSeconds(2 * 3_600L)
                    )
                }
            }
        }

        composeRule.onAllNodesWithTag(TAG_TIMELINE_EVENT_MARKER).assertCountEquals(1)
        composeRule.onNodeWithTag(TAG_TIMELINE_EVENT_MARKER).assertIsDisplayed()
        composeRule.onNodeWithText("12h").assertIsDisplayed()
        composeRule.onNodeWithText("15h").assertIsDisplayed()
        composeRule.onNodeWithText("18h").assertIsDisplayed()
    }

    @Test
    fun timeline_uses_temperature_heatmap_and_precipitation_intensity_marker() {
        val point = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-26T16:00:00Z"),
            temperatureC = 28.0,
            temperatureMinAcrossModels = 26.0,
            temperatureMaxAcrossModels = 30.0,
            precipitationPercent = 80,
            precipitationSource = PrecipitationSignalSource.MODEL_AGREEMENT,
            precipitationModelCount = 5,
            wetModelCount = 4,
            modelCount = 5,
            temperatureModelCount = 5,
            hasMultiModelEvidence = true,
            consensusPercent = 76,
            consensusLevel = ModelConsensusLevel.MEDIUM
        )

        composeRule.setContent {
            MeteoCompareTheme {
                Surface {
                    SimplifiedTimelineCard(
                        points = listOf(point),
                        mode = DisplayMode.HOURLY,
                        timezone = "UTC"
                    )
                }
            }
        }

        composeRule.onNodeWithTag(TAG_TIMELINE_HEATMAP_BAND).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_TIMELINE_PRECIP_HEAT_DOT).assertIsDisplayed()
        composeRule.onNodeWithText("28°").assertIsDisplayed()
        composeRule.onNodeWithText("26–30°").assertIsDisplayed()
    }

}
