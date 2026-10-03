package com.meteocompare.app.ui.citydetail

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.core.units.LocalWeatherUnits
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.data.worker.BiasHistoryRefreshState
import com.meteocompare.app.domain.model.CityDetailContentTab
import com.meteocompare.app.domain.model.UnitSystem
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DisplayRegressionTest {
    @get:Rule val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun daily_table_converts_gusts_when_units_change() = checkWindTable(DisplayMode.DAILY)
    @Test fun hourly_table_converts_gusts_when_units_change() = checkWindTable(DisplayMode.HOURLY)

    private fun checkWindTable(mode: DisplayMode) {
        val units = mutableStateOf(UnitSystem.METRIC)
        val initial = TestFixtures.forecast(models = listOf(WeatherModel.GFS))
        val forecast = initial.copy(seriesByModel = initial.seriesByModel.mapValues { (_, series) ->
            series.copy(
                daily = series.daily.copy(
                    windSpeedMax = series.daily.dates.map { 16.09344 },
                    windGustsMax = series.daily.dates.map { 32.18688 }
                ),
                hourly = series.hourly.copy(
                    windSpeed10m = series.hourly.timestamps.map { 16.09344 },
                    windGusts10m = series.hourly.timestamps.map { 32.18688 }
                )
            )
        })
        composeRule.setContent {
            CompositionLocalProvider(LocalWeatherUnits provides WeatherUnits(units.value)) {
                MeteoCompareTheme {
                    Surface {
                        DetailedComparisonContent(
                            mode = mode, tab = CityDetailContentTab.WIND,
                            forecast = forecast, dailyConditions = emptyList(), normals = null,
                            presentationNow = TestFixtures.now, cityToday = TestFixtures.today
                        )
                    }
                }
            }
        }
        val gust = context.getString(R.string.wind_gust_abbreviation)
        composeRule.onAllNodesWithText("$gust 32", useUnmergedTree = true)[0].assertExists()
        composeRule.runOnIdle { units.value = UnitSystem.IMPERIAL }
        composeRule.onAllNodesWithText("$gust 20", useUnmergedTree = true)[0].assertExists()
        assertEquals(0, composeRule.onAllNodesWithText("$gust 32", useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.runOnIdle { units.value = UnitSystem.METRIC }
        composeRule.onAllNodesWithText("$gust 32", useUnmergedTree = true)[0].assertExists()
        assertEquals(32.18688, forecast.seriesByModel.getValue(WeatherModel.GFS).hourly.windGusts10m.first()!!, 0.0)
    }

    @Test fun bias_action_tracks_waiting_running_failure_and_partial_success() {
        val state = mutableStateOf(BiasHistoryRefreshState.IDLE)
        var requests = 0
        composeRule.setContent {
            MeteoCompareTheme {
                BiasHistoryHint(
                    progress = BiasHistoryProgress(0, 1, 2, 5),
                    refreshState = state.value,
                    onRequestHistory = {
                        requests++
                        state.value = BiasHistoryRefreshState.QUEUED
                    }
                )
            }
        }
        composeRule.onNodeWithTag(TAG_BIAS_HISTORY_FETCH).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(TAG_BIAS_HISTORY_FETCH).assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.bias_history_fetch_queued)).assertExists()
        composeRule.runOnIdle { state.value = BiasHistoryRefreshState.RUNNING }
        composeRule.onNodeWithText(context.getString(R.string.bias_history_fetch_requested)).assertExists()
        composeRule.runOnIdle { state.value = BiasHistoryRefreshState.FAILED }
        composeRule.onNodeWithTag(TAG_BIAS_HISTORY_FETCH).assertIsEnabled().performClick()
        assertEquals(2, requests)
        composeRule.runOnIdle { state.value = BiasHistoryRefreshState.SUCCEEDED }
        composeRule.onNodeWithTag(TAG_BIAS_HISTORY_FETCH).assertIsEnabled().performClick()
        assertEquals(3, requests)
    }
}
