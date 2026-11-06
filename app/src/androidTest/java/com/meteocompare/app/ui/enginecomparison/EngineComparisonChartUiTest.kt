package com.meteocompare.app.ui.enginecomparison

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class EngineComparisonChartUiTest {
    @get:Rule val composeRule = createComposeRule()

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

    private fun render() {
        val loaded = state()
        composeRule.setContent {
            MeteoCompareTheme { EngineComparisonContent(loaded) }
        }
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
