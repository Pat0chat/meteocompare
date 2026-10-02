package com.meteocompare.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.meteocompare.app.ui.citydetail.ChronoTimelineView
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import com.meteocompare.app.ui.citydetail.DisplayMode
import java.time.Instant
import java.util.Locale
import androidx.compose.ui.test.performClick
import com.meteocompare.app.core.units.LocalWeatherUnits
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.UnitSystem
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.citydetail.TodaySummaryCard
import com.meteocompare.app.ui.citydetail.TAG_TODAY_SUMMARY_TEMP_MAX_CENTRAL
import com.meteocompare.app.ui.citydetail.TAG_TODAY_SUMMARY_WIND_CENTRAL
import com.meteocompare.app.ui.citydetail.TAG_TODAY_SUMMARY_TEMP_MAX_CONVERGENCE
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class UnitSystemDisplayTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun chronology_converts_wind_and_gusts_without_changing_the_hour() {
        val selection = mutableStateOf(UnitSystem.METRIC)
        val now = Instant.parse("2026-09-16T06:00:00Z")
        val points = listOf(SimplifiedTimelinePoint(instant = now, temperatureC = -10.0,
            windKmh = 16.09344, windGustKmh = 32.18688, precipitationMm = 0.05))
        composeRule.setContent {
            CompositionLocalProvider(LocalWeatherUnits provides WeatherUnits(selection.value)) {
                MeteoCompareTheme { ChronoTimelineView(points, DisplayMode.HOURLY, "UTC", now) }
            }
        }
        composeRule.onAllNodesWithText("16 km/h", substring = true, useUnmergedTree = true)[0].assertExists()
        composeRule.runOnIdle { selection.value = UnitSystem.IMPERIAL }
        composeRule.onAllNodesWithText("10 mph", substring = true, useUnmergedTree = true)[0].assertExists()
        composeRule.onAllNodesWithText("20 mph", substring = true, useUnmergedTree = true)[0].assertExists()
        org.junit.Assert.assertEquals(now, points[0].instant)
        org.junit.Assert.assertEquals(32.18688, points[0].windGustKmh!!, 0.0)
    }

    @Test fun changing_units_recomposes_existing_values_and_retains_convergence() {
        val selection = mutableStateOf(UnitSystem.METRIC)
        val today = DayConfidence(
            date = TestFixtures.today,
            tempMax = ConfidenceScore(85, 20.0, 20.0, 20.0, 0.0, 5),
            tempMin = null, precipitation = null,
            windMax = ConfidenceScore(90, 16.09344, 16.09344, 16.09344, 0.0, 5)
        )
        var displayLocale = Locale.getDefault()
        composeRule.setContent {
            displayLocale = LocalLocale.current.platformLocale
            CompositionLocalProvider(LocalWeatherUnits provides WeatherUnits(selection.value)) {
                MeteoCompareTheme {
                    Surface {
                        Column {
                            UnitSystemSelector(selection.value) { selection.value = it }
                            TodaySummaryCard(today, 5, currentTemp = null)
                        }
                    }
                }
            }
        }
        fun assertSummary(temperature: String, wind: String) {
            composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_TEMP_MAX_CENTRAL, useUnmergedTree = true)
                .assertTextEquals(temperature)
            composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_WIND_CENTRAL, useUnmergedTree = true)
                .assertTextEquals(wind)
            composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_TEMP_MAX_CONVERGENCE, useUnmergedTree = true)
                .assertTextEquals("85%")
        }
        composeRule.onNodeWithTag("settings_units_METRIC").assertIsSelected()
        val metricTemperature = String.format(displayLocale, "%.1f°", 20.0)
        assertSummary(metricTemperature, "16 km/h")
        composeRule.onNodeWithTag("settings_units_IMPERIAL").performClick().assertIsSelected()
        assertSummary(String.format(displayLocale, "%.1f°F", 68.0), "10 mph")
        composeRule.onNodeWithTag("settings_units_METRIC").performClick().assertIsSelected()
        assertSummary(metricTemperature, "16 km/h")
        assertEquals(20.0, today.tempMax!!.centralValue, 0.0)
        assertEquals(16.09344, today.windMax!!.centralValue, 0.0)
    }
}
