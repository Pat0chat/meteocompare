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
import com.meteocompare.app.domain.model.CustomUnitPreferences
import com.meteocompare.app.domain.model.WindChoice
import com.meteocompare.app.domain.model.TemperatureChoice
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

    @Test fun custom_dialog_saves_all_choices_and_cancel_does_not_change_prefs() {
        val selection = mutableStateOf(UnitSystem.METRIC)
        val choices = mutableStateOf(CustomUnitPreferences())
        composeRule.setContent {
            MeteoCompareTheme {
                UnitSystemSelector(selection.value, { selection.value = it }, choices.value) { value ->
                    choices.value = value
                    selection.value = UnitSystem.CUSTOM
                }
            }
        }
        composeRule.onNodeWithTag("settings_units_CUSTOM").performClick()
        composeRule.onNodeWithTag("settings_custom_units_dialog").assertExists()
        composeRule.onNodeWithTag("settings_custom_wind").performClick()
        composeRule.onNodeWithTag("settings_custom_wind_KNOTS").performClick()
        composeRule.onNodeWithTag("settings_custom_units_cancel").performClick()
        composeRule.runOnIdle {
            assertEquals(UnitSystem.METRIC, selection.value)
            assertEquals(WindChoice.KMH, choices.value.wind)
        }
        composeRule.onNodeWithTag("settings_units_CUSTOM").performClick()
        composeRule.onNodeWithTag("settings_custom_temperature").performClick()
        composeRule.onNodeWithTag("settings_custom_temperature_FAHRENHEIT").performClick()
        composeRule.onNodeWithTag("settings_custom_wind").performClick()
        composeRule.onNodeWithTag("settings_custom_wind_KNOTS").performClick()
        composeRule.onNodeWithTag("settings_custom_units_save").performClick()
        composeRule.onNodeWithTag("settings_units_CUSTOM").assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(WindChoice.KNOTS, choices.value.wind)
            assertEquals(TemperatureChoice.FAHRENHEIT, choices.value.temperature)
        }
        composeRule.onNodeWithTag("settings_units_CUSTOM").performClick()
        composeRule.onNodeWithTag("settings_custom_wind").assertTextEquals("kn")
    }

    @Test fun daily_chronology_displays_independent_temperature_and_rain_ranges_after_unit_switch() {
        val selection = mutableStateOf(UnitSystem.METRIC)
        val choices = CustomUnitPreferences(
            temperature = TemperatureChoice.FAHRENHEIT,
            wind = WindChoice.KNOTS
        )
        val now = Instant.parse("2026-09-16T06:00:00Z")
        val point = SimplifiedTimelinePoint(
            date = java.time.LocalDate.of(2026, 9, 16),
            tempMaxC = 32.0, tempMinC = 15.0,
            temperatureMinAcrossModels = 25.0, temperatureMaxAcrossModels = 34.0,
            tempMinMinAcrossModels = 12.0, tempMinMaxAcrossModels = 19.0,
            temperatureModelCount = 3,
            precipitationMm = 21.0, precipitationConditionalMm = 21.0,
            precipitationMinAcrossModelsMm = 2.0, precipitationMaxAcrossModelsMm = 32.0,
            precipitationModelCount = 3
        )
        var displayLocale = Locale.getDefault()
        composeRule.setContent {
            displayLocale = LocalLocale.current.platformLocale
            CompositionLocalProvider(LocalWeatherUnits provides WeatherUnits(selection.value, choices)) {
                MeteoCompareTheme { ChronoTimelineView(listOf(point), DisplayMode.DAILY, "UTC", now) }
            }
        }
        composeRule.onNodeWithTag("timeline_daily_temp_max_range", useUnmergedTree = true)
            .assertTextEquals("(25–34)")
        composeRule.onNodeWithTag("timeline_daily_temp_min_range", useUnmergedTree = true)
            .assertTextEquals("(12–19)")
        composeRule.onNodeWithTag("timeline_daily_rain_amount", useUnmergedTree = true)
            .assertTextEquals("${WeatherUnits().rain(21.0, displayLocale)} " +
                com.meteocompare.app.ui.citydetail.dailyTimelineRange(2.0, 32.0,
                    WeatherUnits(), com.meteocompare.app.core.units.WeatherUnit.PRECIPITATION,
                    digits = 1, locale = displayLocale))
        composeRule.runOnIdle { selection.value = UnitSystem.CUSTOM }
        composeRule.onNodeWithTag("timeline_daily_temp_max_range", useUnmergedTree = true)
            .assertTextEquals("(77–93)")
        composeRule.onNodeWithTag("timeline_daily_temp_min_range", useUnmergedTree = true)
            .assertTextEquals("(54–66)")
        composeRule.onNodeWithTag("timeline_daily_rain_amount", useUnmergedTree = true)
            .assertTextEquals("${WeatherUnits().rain(21.0, displayLocale)} " +
                com.meteocompare.app.ui.citydetail.dailyTimelineRange(2.0, 32.0,
                    WeatherUnits(), com.meteocompare.app.core.units.WeatherUnit.PRECIPITATION,
                    digits = 1, locale = displayLocale))
    }

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
        composeRule.onNodeWithTag("settings_units_UK_MIXED").performClick().assertIsSelected()
        assertSummary(metricTemperature, "10 mph")
        composeRule.onNodeWithTag("settings_units_METRIC").performClick().assertIsSelected()
        assertSummary(metricTemperature, "16 km/h")
        assertEquals(20.0, today.tempMax!!.centralValue, 0.0)
        assertEquals(16.09344, today.windMax!!.centralValue, 0.0)
    }
}
