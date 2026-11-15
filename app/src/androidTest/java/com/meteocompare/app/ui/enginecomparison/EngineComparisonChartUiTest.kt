package com.meteocompare.app.ui.enginecomparison

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.usecase.EngineComparisonDay
import com.meteocompare.app.domain.usecase.EngineComparisonMetric
import com.meteocompare.app.domain.usecase.EngineComparisonValues
import com.meteocompare.app.domain.usecase.EngineDivergence
import com.meteocompare.app.domain.usecase.EngineDivergenceLevel
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import androidx.lifecycle.Lifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class EngineComparisonChartUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun state(): EngineComparisonUiState.Loaded = EngineComparisonUiState.Loaded(
        cityName = "Chart demo",
        selectedEngine = ForecastEngine.MULTI_CONSENSUS,
        days = List(10) { day ->
            EngineComparisonDay(
                date = LocalDate.of(2026, 11, 6).plusDays(day.toLong()),
                byEngine = ForecastEngine.entries.associateWith { engine ->
                    val variance = engine.ordinal * 0.7
                    EngineComparisonValues(
                        tempMax = 15.0 + day + variance,
                        tempMin = 6.0 + day + variance,
                        precipitationAmountMm = 1.0 + variance,
                        precipitationProbabilityPercent = 50,
                        precipitationExpectedMm = 1.0 + variance,
                        windKmh = 12.0 + day + variance,
                        gustKmh = 18.0 + day + variance,
                        cloudPercent = 30.0 + day + variance,
                        condition = null
                    )
                },
                divergence = EngineDivergence(
                    score = 0.2, level = EngineDivergenceLevel.LOW,
                    temperatureDelta = 1.0, precipitationDelta = 1.0,
                    windDelta = 1.0, cloudDelta = 1.0, conditionCount = 2
                )
            )
        }
    )

    private fun render(loaded: EngineComparisonUiState.Loaded = state()) {
        // Ensure the Android host is attached before installing the composition.
        // If an AVD loses its window or services, fail at this precondition
        // rather than reporting a misleading graph/temperature assertion.
        composeRule.waitUntil(
            conditionDescription = "Engine comparison host window is attached and resumed",
            timeoutMillis = 15_000
        ) {
            val activity = composeRule.activity
            activity.window.decorView.isAttachedToWindow &&
                activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        composeRule.setContent {
            MeteoCompareTheme { EngineComparisonContent(loaded) }
        }
    }

    @Test fun missingTemperatureDoesNotShowMisleadingCurvesOrValues() {
        // Two days are sufficient to prove that the chart has no valid
        // temperature maximum. Avoid constructing a ten-day plot just to
        // assert its empty state on a resource-constrained emulator.
        val allMissing = state().let { loaded ->
            loaded.copy(days = loaded.days.take(2).map { day ->
                day.copy(byEngine = day.byEngine.mapValues { (_, values) ->
                    values.copy(tempMax = null)
                })
            })
        }
        render(allMissing)
        composeRule.onNodeWithTag(TAG_ENGINE_COMPARISON_LIST)
            .performScrollToNode(hasTestTag(TAG_ENGINE_METRIC_STRIP))
        composeRule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(
                com.meteocompare.app.R.string.engine_comparison_not_enough_data
            )
        ).assertExists()
        composeRule.onNodeWithTag(TAG_ENGINE_CHART_INSPECTOR).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_ENGINE_CHART_SCROLL).assertDoesNotExist()
    }

    @Test fun variablesLiveInOneHorizontalSelectorAndCanBeChanged() {
        render()
        composeRule.onNodeWithTag(TAG_ENGINE_COMPARISON_LIST)
            .performScrollToNode(hasTestTag(TAG_ENGINE_METRIC_STRIP))
        composeRule.onNodeWithTag(TAG_ENGINE_METRIC_STRIP).assertExists()
        EngineComparisonMetric.entries.forEach { metric ->
            composeRule.onNodeWithTag("$TAG_ENGINE_METRIC_PREFIX${metric.name}")
                .assertExists()
        }
        composeRule.onNodeWithTag("${TAG_ENGINE_METRIC_PREFIX}CLOUD")
            .performScrollTo().performClick()
        composeRule.onNodeWithTag(TAG_ENGINE_CHART_INSPECTOR).assertExists()
    }

    @Test fun chartTimelineCanScrollToTenthDayAndSelectIt() {
        render()
        composeRule.onNodeWithTag(TAG_ENGINE_COMPARISON_LIST)
            .performScrollToNode(hasTestTag(TAG_ENGINE_CHART_SCROLL))
        composeRule.onNodeWithTag(TAG_ENGINE_CHART_SCROLL).assertExists()
        composeRule.onNodeWithTag("${TAG_ENGINE_CHART_DAY_PREFIX}9")
            .performScrollTo().performClick()
        composeRule.onNodeWithTag(TAG_ENGINE_CHART_INSPECTOR).assertExists()
        val expected = LocalDate.of(2026, 11, 15)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(InstrumentationRegistry.getInstrumentation().targetContext
                    .resources.configuration.locales[0]))
        composeRule.onNodeWithTag(TAG_ENGINE_CHART_SELECTED_DAY)
            .assertTextEquals(expected)
    }
}
