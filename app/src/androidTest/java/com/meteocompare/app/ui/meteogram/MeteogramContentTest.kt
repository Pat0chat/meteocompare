package com.meteocompare.app.ui.meteogram

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.meteocompare.app.domain.model.City
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
                windGustKmh = 17.0,
                humidityPercent = 70,
                dewPointC = 8.0,
                pressureHpa = 1015.0,
                precipitationMm = 0.4
            ) },
            solarByDate = emptyMap(), modelValuesByInstant = emptyMap(), calculatedAt = start
        )
        rule.setContent { MeteoCompareTheme { MeteogramContent(state) } }
        listOf(TAG_METEOGRAM_CONTENT, TAG_METEOGRAM_WEATHER,
            TAG_METEOGRAM_TEMPERATURE, TAG_METEOGRAM_WIND,
            TAG_METEOGRAM_SELECTED).forEach { tag ->
            rule.onNodeWithTag(tag, useUnmergedTree = true).assertExists()
        }
        // Three complementary charts: humidity belongs to temperature,
        // pressure belongs to clouds/rain/wind; daily range was removed.
        rule.onAllNodesWithTag(TAG_METEOGRAM_TIME, useUnmergedTree = true).assertCountEquals(3)
        MeteogramPresets.forEach { kind ->
            rule.onNodeWithTag(TAG_METEOGRAM_CARD_PREFIX + kind.name.lowercase(),
                useUnmergedTree = true).assertExists()
        }
    }

    @Test fun combined_mode_uses_one_real_plot_not_stacked_sections() {
        val start = Instant.parse("2026-10-28T00:00:00Z")
        val state = GraphicForecastUiState.Loaded(
            city = City("test", "Paris", country = "France", latitude = 48.8,
                longitude = 2.3, timezone = "Europe/Paris"),
            points = List(48) { i -> SimplifiedTimelinePoint(
                instant = start.plusSeconds(i * 3600L), temperatureC = 12.0 + i % 6,
                cloudCoverPercent = 40, windKmh = 13.0, windGustKmh = 21.0,
                humidityPercent = 75, dewPointC = 8.0,
                pressureHpa = 1016.0, precipitationMm = 0.4
            ) },
            solarByDate = emptyMap(), modelValuesByInstant = emptyMap(), calculatedAt = start
        )
        rule.setContent { MeteoCompareTheme { MeteogramContent(state) } }
        rule.onNodeWithTag(TAG_METEOGRAM_LAYOUT_COMBINED).performClick()
        rule.onNodeWithTag(TAG_METEOGRAM_COMBINED, useUnmergedTree = true).assertExists()
        // All measurement scales must be visible in the single-canvas mode.
        listOf("temperature", "cloud", "rain", "wind", "humidity", "pressure").forEach { metric ->
            rule.onNodeWithTag(TAG_METEOGRAM_SCALE_PREFIX + metric, useUnmergedTree = true)
                .assertExists()
        }
        rule.onAllNodesWithTag(TAG_METEOGRAM_TIME, useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithTag(TAG_METEOGRAM_TEMPERATURE, useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag(TAG_METEOGRAM_WEATHER, useUnmergedTree = true).assertDoesNotExist()
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
