package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.meteocompare.app.domain.model.MarineDaily
import com.meteocompare.app.domain.model.MarineForecast
import com.meteocompare.app.domain.model.MarineGrid
import com.meteocompare.app.domain.model.MarineHourly
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Rule
import org.junit.Test

class MarineSectionRenderTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun loaded_dashboard_displays_modern_current_wave_and_tide_panels() {
        composeRule.setContent {
            MeteoCompareTheme {
                LazyColumn {
                    item {
                        MarineSection(
                            state = MarineUiState.Loaded(forecast()),
                            onRefresh = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(TAG_MARINE_CURRENT_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_MARINE_WAVE_CHART)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_MARINE_WAVE_AXES)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_MARINE_TIDE_AXES)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_MARINE_TIDE_PANEL)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun loaded_dashboard_renders_in_dark_theme() {
        composeRule.setContent {
            MeteoCompareTheme(themePreference = ThemePreference.DARK, dynamicColor = false) {
                LazyColumn {
                    item {
                        MarineSection(
                            state = MarineUiState.Loaded(forecast()),
                            onRefresh = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(TAG_MARINE_CURRENT_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_MARINE_WAVE_AXES)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_MARINE_TIDE_AXES)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_MARINE_TIDE_PANEL)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun forecast(): MarineForecast {
        val now = System.currentTimeMillis()
        val zone = ZoneId.of("Europe/Paris")
        val hours = 80
        val epochs = List(hours) { index -> now + index * 3_600_000L }
        val timestamps = epochs.map { epoch ->
            Instant.ofEpochMilli(epoch)
                .atZone(zone)
                .toLocalDateTime()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }
        val waveHeight = List(hours) { index -> 0.9 + (index % 8) * 0.08 }
        val seaLevel = List(hours) { index ->
            0.55 + kotlin.math.sin(index * Math.PI / 6.0) * 0.65
        }
        val dates = List(7) { LocalDate.now(zone).plusDays(it.toLong()).toString() }

        return MarineForecast(
            fetchedAtEpochMs = now,
            timezone = zone.id,
            grid = MarineGrid(latitude = 48.0, longitude = -4.0, distanceKm = 7.2),
            hourly = MarineHourly(
                timestamps = timestamps,
                timestampEpochMs = epochs.map { it as Long? },
                waveHeight = waveHeight.map { it as Double? },
                waveDirection = List(hours) { 245.0 },
                wavePeriod = List(hours) { 7.4 },
                swellHeight = List(hours) { 0.7 },
                swellDirection = List(hours) { 255.0 },
                swellPeriod = List(hours) { 10.2 },
                seaSurfaceTemperature = List(hours) { 18.4 },
                seaLevelHeightMsl = seaLevel.map { it as Double? }
            ),
            daily = MarineDaily(
                dates = dates,
                waveHeightMax = List(7) { 1.6 },
                waveDirectionDominant = List(7) { 250.0 },
                wavePeriodMax = List(7) { 8.1 },
                swellHeightMax = List(7) { 1.0 },
                swellDirectionDominant = List(7) { 260.0 },
                swellPeriodMax = List(7) { 11.0 }
            ),
            usablePoints = hours,
            coastal = true
        )
    }
}
