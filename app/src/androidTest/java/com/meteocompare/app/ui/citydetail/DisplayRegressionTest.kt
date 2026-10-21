package com.meteocompare.app.ui.citydetail

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.core.units.WeatherUnit
import com.meteocompare.app.core.units.LocalWeatherUnits
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.data.worker.BiasHistoryRefreshState
import com.meteocompare.app.domain.model.CityDetailContentTab
import com.meteocompare.app.domain.model.UnitSystem
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DisplayRegressionTest {
    @get:Rule val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun daily_table_converts_gusts_when_units_change() = checkWindTable(DisplayMode.DAILY)
    @Test fun hourly_table_converts_gusts_when_units_change() = checkWindTable(DisplayMode.HOURLY)

    @Test fun daily_wind_table_keeps_values_in_their_day() = checkWindLayout(DisplayMode.DAILY)
    @Test fun hourly_wind_table_keeps_values_in_their_hour() = checkWindLayout(DisplayMode.HOURLY)

    private fun checkWindLayout(mode: DisplayMode) {
        val unitSystem = mutableStateOf(UnitSystem.METRIC)
        val forecast = TestFixtures.forecast(models = listOf(WeatherModel.GFS)).let { initial ->
            initial.copy(seriesByModel = initial.seriesByModel.mapValues { (_, series) ->
                series.copy(
                    daily = series.daily.copy(
                        windSpeedMax = series.daily.dates.map { 128.0 },
                        windGustsMax = series.daily.dates.map { 160.0 },
                        windDirection10mDominant = series.daily.dates.map { 45 }
                    ),
                    hourly = series.hourly.copy(
                        windSpeed10m = series.hourly.timestamps.map { 128.0 },
                        windGusts10m = series.hourly.timestamps.map { 160.0 },
                        windDirection10m = series.hourly.timestamps.map { 45 }
                    )
                )
            })
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalWeatherUnits provides WeatherUnits(unitSystem.value)) {
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

        // Valeurs suffisamment grandes pour reproduire le retour à la ligne de la V7.
        // Contrôler aussi les unités impériales, sans changer la largeur des colonnes.
        listOf(UnitSystem.METRIC, UnitSystem.IMPERIAL).forEach { system ->
            composeRule.runOnIdle { unitSystem.value = system }
            composeRule.waitForIdle()
            val units = WeatherUnits(system)
            val cell = composeRule.onAllNodesWithTag(
                TAG_WIND_DETAIL_VALUE_CELL, useUnmergedTree = true
            )[0].getUnclippedBoundsInRoot()
            val direction = composeRule.onAllNodesWithText("NE", useUnmergedTree = true)[0]
                .getUnclippedBoundsInRoot()
            val speed = composeRule.onAllNodesWithText(
                units.speed(128.0), useUnmergedTree = true
            )[0].getUnclippedBoundsInRoot()
            val gust = composeRule.onAllNodesWithText(
                "${context.getString(R.string.wind_gust_abbreviation)} ${units.value(160.0, WeatherUnit.WIND_SPEED)}",
                useUnmergedTree = true
            )[0].getUnclippedBoundsInRoot()

            assertTrue("Direction must be above the speed", direction.bottom <= speed.top + 1.dp)
            assertTrue("Gust must be below the speed", speed.bottom <= gust.top + 1.dp)
            assertTrue("Direction must remain inside its date/hour column",
                direction.left >= cell.left && direction.right <= cell.right)
            assertTrue("Speed must remain inside its date/hour column",
                speed.left >= cell.left && speed.right <= cell.right)
            assertTrue("Gust must remain inside its date/hour column",
                gust.left >= cell.left && gust.right <= cell.right)
            assertTrue("Speed must use only one line", speed.bottom - speed.top <= 22.dp)
            assertTrue("Gust must use only one line", gust.bottom - gust.top <= 18.dp)
            assertTrue("Column width must remain the shared 80 dp", kotlin.math.abs((cell.right - cell.left).value - 80f) <= 1f)
        }
    }

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

    @Test fun hourly_temperature_shows_felt_value_below_air_temp_in_the_same_cell() {
        val unitSystem = mutableStateOf(UnitSystem.METRIC)
        val initial = TestFixtures.forecast(models = listOf(WeatherModel.GFS))
        val forecast = initial.copy(seriesByModel = initial.seriesByModel.mapValues { (_, series) ->
            series.copy(hourly = series.hourly.copy(
                apparentTemperature = series.hourly.timestamps.map { 16.0 }
            ))
        })
        composeRule.setContent {
            CompositionLocalProvider(LocalWeatherUnits provides WeatherUnits(unitSystem.value)) {
                MeteoCompareTheme {
                    Surface {
                        DetailedComparisonContent(
                            mode = DisplayMode.HOURLY,
                            tab = CityDetailContentTab.TEMPERATURE,
                            forecast = forecast,
                            dailyConditions = emptyList(),
                            normals = null,
                            presentationNow = TestFixtures.now,
                            cityToday = TestFixtures.today
                        )
                    }
                }
            }
        }

        val firstTemp = composeRule.onAllNodesWithText("20°", useUnmergedTree = true)[0]
            .getUnclippedBoundsInRoot()
        val feels = composeRule.onAllNodesWithText(
            "${context.getString(R.string.apparent_temperature_widget_prefix)} 16°",
            useUnmergedTree = true
        )[0].getUnclippedBoundsInRoot()
        assertTrue("Feels-like is under temperature", feels.top >= firstTemp.bottom)
        assertTrue("Both values share the same model and hour cell", kotlin.math.abs(
            (feels.left.value + feels.right.value) / 2f -
                (firstTemp.left.value + firstTemp.right.value) / 2f
        ) <= 8f)
        composeRule.runOnIdle { unitSystem.value = UnitSystem.IMPERIAL }
        composeRule.onAllNodesWithText("68°F", useUnmergedTree = true)[0].assertExists()
        composeRule.onAllNodesWithText(
            "${context.getString(R.string.apparent_temperature_widget_prefix)} 61°F",
            useUnmergedTree = true
        )[0].assertExists()
    }

    @Test fun hourly_temperature_does_not_invent_missing_felt_values() {
        val initial = TestFixtures.forecast(models = listOf(WeatherModel.GFS))
        val forecast = initial.copy(seriesByModel = initial.seriesByModel.mapValues { (_, series) ->
            series.copy(hourly = series.hourly.copy(
                apparentTemperature = series.hourly.timestamps.map { Double.NaN }
            ))
        })
        composeRule.setContent {
            MeteoCompareTheme {
                Surface {
                    DetailedComparisonContent(
                        mode = DisplayMode.HOURLY,
                        tab = CityDetailContentTab.TEMPERATURE,
                        forecast = forecast,
                        dailyConditions = emptyList(), normals = null,
                        presentationNow = TestFixtures.now, cityToday = TestFixtures.today
                    )
                }
            }
        }
        composeRule.onAllNodesWithText(
            "${context.getString(R.string.apparent_temperature_widget_prefix)} NaN°",
            useUnmergedTree = true
        ).assertCountEquals(0)
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
