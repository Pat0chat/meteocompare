package com.meteocompare.app.ui.meteogram

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.core.units.LocalWeatherUnits
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.UnitSystem
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import com.meteocompare.app.ui.graphicview.GraphicForecastUiState
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class MeteogramContentTest {
    @get:Rule val rule = createComposeRule()

    @Test fun consensus_shows_all_aligned_graph_panels_without_network_calls() {
        val start = Instant.parse("2026-10-28T00:00:00Z")
        val state = GraphicForecastUiState.Loaded(
            city = City("test", "Paris", country = "France", latitude = 48.8,
                longitude = 2.3, timezone = "Europe/Paris"),
            points = List(48) { index -> SimplifiedTimelinePoint(
                instant = start.plusSeconds(index * 3600L),
                temperatureC = 12.0 + index % 6,
                cloudCoverPercent = 30 + index % 30,
                windKmh = 11.0,
                windGustKmh = 17.0, windDirectionDeg = 270,
                humidityPercent = 70,
                dewPointC = 8.0,
                pressureHpa = 1015.0,
                precipitationMm = 0.4
            ) },
            solarByDate = emptyMap(), modelValuesByInstant = emptyMap(), calculatedAt = start
        )
        rule.setContent { MeteoCompareTheme { MeteogramContent(state) } }
        listOf(TAG_METEOGRAM_CONTENT, TAG_METEOGRAM_WEATHER,
            TAG_METEOGRAM_TEMPERATURE, TAG_METEOGRAM_WIND).forEach { tag ->
            rule.onNodeWithTag(tag, useUnmergedTree = true).assertExists()
        }
        // Cloud/rain has a prominent dedicated legend; wind remains its own card.
        rule.onNodeWithTag(TAG_METEOGRAM_CLOUD_RAIN_LEGEND,
            useUnmergedTree = true).assertExists()
        // Three equally styled cloud, rain and pressure keys use the same legend component.
        listOf(TAG_METEOGRAM_CLOUD_LEGEND, TAG_METEOGRAM_RAIN_LEGEND,
            TAG_METEOGRAM_PRESSURE_LEGEND).forEach { tag ->
            rule.onNodeWithTag(tag, useUnmergedTree = true).assertExists()
        }
        // Three independent right-hand axes: rain + pressure + 3 cloud strata.
        rule.onNodeWithTag(TAG_METEOGRAM_CLOUD_AXIS, useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(TAG_METEOGRAM_CLOUD_MID_LEGEND, useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(TAG_METEOGRAM_CLOUD_HIGH_LEGEND, useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag(TAG_METEOGRAM_SELECTED_GRID, useUnmergedTree = true).assertCountEquals(3)
        rule.onAllNodesWithTag(TAG_METEOGRAM_SELECTED_TIME_TRACK, useUnmergedTree = true).assertCountEquals(3)
        rule.onAllNodesWithTag(TAG_METEOGRAM_SELECTED_TIME_BADGE, useUnmergedTree = true).assertCountEquals(3)
        // Three complementary charts: humidity belongs to temperature,
        // pressure belongs to clouds/rain/wind; daily range was removed.
        rule.onAllNodesWithTag(TAG_METEOGRAM_TIME, useUnmergedTree = true).assertCountEquals(3)
        MeteogramPresets.forEach { kind ->
            rule.onNodeWithTag(TAG_METEOGRAM_CARD_PREFIX + kind.name.lowercase(),
                useUnmergedTree = true).assertExists()
            rule.onNodeWithTag(TAG_METEOGRAM_CARD_SELECTED_PREFIX + kind.name.lowercase(),
                useUnmergedTree = true).assertExists()
        }
        // The per-chart values replace the duplicated global panel in separate mode.
        rule.onNodeWithTag(TAG_METEOGRAM_SELECTED, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun combined_mode_uses_one_real_plot_not_stacked_sections() {
        val start = Instant.parse("2026-10-28T00:00:00Z")
        val state = GraphicForecastUiState.Loaded(
            city = City("test", "Paris", country = "France", latitude = 48.8,
                longitude = 2.3, timezone = "Europe/Paris"),
            points = List(48) { i -> SimplifiedTimelinePoint(
                instant = start.plusSeconds(i * 3600L), temperatureC = 12.0 + i % 6,
                cloudCoverPercent = 40, windKmh = 13.0, windGustKmh = 21.0,
                windDirectionDeg = 270,
                humidityPercent = 75, dewPointC = 8.0,
                pressureHpa = 1016.0, precipitationMm = 0.4
            ) },
            solarByDate = emptyMap(), modelValuesByInstant = emptyMap(), calculatedAt = start
        )
        rule.setContent { MeteoCompareTheme { MeteogramContent(state) } }
        rule.onNodeWithTag(TAG_METEOGRAM_LAYOUT_COMBINED).performClick()
        rule.onNodeWithTag(TAG_METEOGRAM_COMBINED, useUnmergedTree = true).assertExists()
        val strings = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(
            "${strings.getString(R.string.meteogram_temperature)} (°C)",
            "${strings.getString(R.string.apparent_temperature_widget_prefix)} (°C)",
            "${strings.getString(R.string.metric_dew_point_short)} (°C)",
            "${strings.getString(R.string.meteogram_humidity)} (%)",
            "${strings.getString(R.string.meteogram_pressure)} (hPa)"
        ).forEach { legend ->
            rule.onNodeWithText(legend, useUnmergedTree = true).assertExists()
        }
        rule.onNodeWithTag(TAG_METEOGRAM_SELECTED, useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag(TAG_METEOGRAM_SELECTED_GRID, useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag(TAG_METEOGRAM_SELECTED_TIME_TRACK, useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithText("270°", substring = true, useUnmergedTree = true).assertExists()
        // Exactly one ruler; selecting it does not recreate the shared Canvas.
        rule.onNodeWithTag(TAG_METEOGRAM_ACTIVE_AXIS, useUnmergedTree = true)
            .assertContentDescriptionEquals("temperature")
        rule.onNodeWithTag(TAG_METEOGRAM_SCALE_PICKER).performClick()
        rule.onNodeWithTag(TAG_METEOGRAM_SCALE_OPTION_PREFIX + "pressure")
            .performClick()
        rule.onNodeWithTag(TAG_METEOGRAM_ACTIVE_AXIS, useUnmergedTree = true)
            .assertContentDescriptionEquals("pressure")
        rule.onNodeWithTag(TAG_METEOGRAM_COMBINED, useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(TAG_METEOGRAM_SCALE_PICKER).performClick()
        rule.onNodeWithTag(TAG_METEOGRAM_SCALE_OPTION_PREFIX + "clouds")
            .performClick()
        rule.onNodeWithTag(TAG_METEOGRAM_ACTIVE_AXIS, useUnmergedTree = true)
            .assertContentDescriptionEquals("clouds")
        rule.onAllNodesWithTag(TAG_METEOGRAM_TIME, useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithTag(TAG_METEOGRAM_TIME, useUnmergedTree = true)
            .assertHeightIsEqualTo(49.dp)
        rule.onNodeWithTag(TAG_METEOGRAM_TEMPERATURE, useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag(TAG_METEOGRAM_WEATHER, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun combined_legend_uses_imperial_units_for_temperature_and_pressure() {
        val start = Instant.parse("2026-10-28T00:00:00Z")
        val state = GraphicForecastUiState.Loaded(
            city = City("test", "Paris", country = "France", latitude = 48.8,
                longitude = 2.3, timezone = "Europe/Paris"),
            points = List(12) { i -> SimplifiedTimelinePoint(
                instant = start.plusSeconds(i * 3600L), temperatureC = 12.0,
                apparentTemperatureC = 10.0, dewPointC = 7.0,
                humidityPercent = 75, pressureHpa = 1015.0) },
            solarByDate = emptyMap(), modelValuesByInstant = emptyMap(), calculatedAt = start
        )
        rule.setContent {
            CompositionLocalProvider(LocalWeatherUnits provides WeatherUnits(UnitSystem.IMPERIAL)) {
                MeteoCompareTheme { MeteogramContent(state) }
            }
        }
        rule.onNodeWithTag(TAG_METEOGRAM_LAYOUT_COMBINED).performClick()
        val strings = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(
            "${strings.getString(R.string.meteogram_temperature)} (°F)",
            "${strings.getString(R.string.apparent_temperature_widget_prefix)} (°F)",
            "${strings.getString(R.string.metric_dew_point_short)} (°F)",
            "${strings.getString(R.string.meteogram_humidity)} (%)",
            "${strings.getString(R.string.meteogram_pressure)} (inHg)"
        ).forEach { legend ->
            rule.onNodeWithText(legend, useUnmergedTree = true).assertExists()
        }
    }

    @Test fun compare_mode_displays_one_everyday_meteogram_per_model() {
        val start = Instant.parse("2026-10-28T00:00:00Z")
        val modelNames = listOf("Alpha", "Bravo")
        val state = GraphicForecastUiState.Loaded(
            city = City("test", "Paris", country = "France", latitude = 48.8,
                longitude = 2.3, timezone = "Europe/Paris"),
            points = List(24) { i -> SimplifiedTimelinePoint(
                instant = start.plusSeconds(i * 3600L), temperatureC = 12.0,
                windKmh = 14.0, precipitationMm = 0.7) },
            solarByDate = emptyMap(),
            modelValuesByInstant = mapOf(start to modelNames.map { name ->
                com.meteocompare.app.ui.graphicview.GraphicModelValue(
                    modelName = name, temperatureC = 12.0, precipitationMm = 0.7,
                    precipitationProbabilityPercent = null, windKmh = 14.0,
                    windGustKmh = 18.0, windDirectionDeg = null,
                    cloudPercent = 40, pressureHpa = 1012.0)
            }),
            calculatedAt = start
        )
        rule.setContent { MeteoCompareTheme { MeteogramContent(state) } }
        rule.onNodeWithTag(TAG_METEOGRAM_COMPARE).performClick()
        rule.onAllNodesWithTag(TAG_METEOGRAM_CARD_PREFIX + "everyday", useUnmergedTree = true)
            .assertCountEquals(2)
    }
}
