package com.meteocompare.app.ui.radar

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.radar.RadarFrame
import com.meteocompare.app.domain.radar.RadarMetadata
import com.meteocompare.app.domain.radar.RadarMode
import com.meteocompare.app.domain.radar.RadarRange
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RadarContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun projection_mode_and_all_four_horizons_are_exposed() {
        var selectedMode: RadarMode? = null
        var selectedHorizon: Int? = null
        val state = readyState().copy(mode = RadarMode.PROJECTION)
        composeRule.setContent {
            MaterialTheme {
                RadarContent(
                    state = state,
                    onModeChange = { selectedMode = it },
                    onHorizonChange = { selectedHorizon = it }
                )
            }
        }

        composeRule.onNodeWithTag(TAG_RADAR_OBSERVATION).performClick()
        assertEquals(RadarMode.OBSERVATION, selectedMode)
        listOf(15, 30, 45, 60).forEach { horizon ->
            composeRule.onNodeWithTag(radarHorizonTag(horizon)).assertExists()
        }
        composeRule.onNodeWithTag(radarHorizonTag(60)).performScrollTo().performClick()
        assertEquals(60, selectedHorizon)
    }

    @Test
    fun observation_and_recalculate_actions_are_accessible() {
        var recalculated = false
        composeRule.setContent {
            MaterialTheme {
                RadarContent(
                    state = readyState().copy(mode = RadarMode.PROJECTION),
                    onRecalculate = { recalculated = true }
                )
            }
        }
        composeRule.onNodeWithTag(TAG_RADAR_STAGE).assertExists()
        composeRule.onNodeWithTag(TAG_RADAR_RECALCULATE).performScrollTo().performClick()
        assertEquals(true, recalculated)
    }


    @Test
    fun fullscreen_keeps_range_and_projection_controls_available() {
        var selectedRange: RadarRange? = null
        composeRule.setContent {
            MaterialTheme {
                RadarContent(
                    state = readyState().copy(mode = RadarMode.PROJECTION, isFullscreen = true),
                    onRangeChange = { selectedRange = it }
                )
            }
        }

        RadarRange.entries.forEach { range ->
            composeRule.onNodeWithTag(radarRangeTag(range)).assertExists()
        }
        composeRule.onNodeWithTag(radarRangeTag(RadarRange.WIDE)).performClick()
        assertEquals(RadarRange.WIDE, selectedRange)
        composeRule.onNodeWithTag(TAG_RADAR_RECALCULATE).assertExists()
    }

    @Test
    fun single_observation_frame_does_not_create_an_invalid_slider_range() {
        val singleFrame = RadarFrame(1_700_000_000, "/v2/radar/a")
        val state = readyState().copy(
            frames = listOf(singleFrame),
            metadata = RadarMetadata("https://tilecache.rainviewer.com", listOf(singleFrame)),
            selectedFrameIndex = 0
        )
        composeRule.setContent {
            MaterialTheme { RadarContent(state = state) }
        }
        composeRule.onNodeWithTag(TAG_RADAR_STAGE).assertExists()
    }

    private fun readyState() = RadarUiState.Ready(
        city = City(
            id = "paris",
            name = "Paris",
            country = "France",
            latitude = 48.8566,
            longitude = 2.3522,
            timezone = "Europe/Paris"
        ),
        metadata = RadarMetadata(
            host = "https://tilecache.rainviewer.com",
            past = listOf(RadarFrame(1_700_000_000, "/v2/radar/a"), RadarFrame(1_700_000_600, "/v2/radar/b"))
        ),
        frames = listOf(RadarFrame(1_700_000_000, "/v2/radar/a"), RadarFrame(1_700_000_600, "/v2/radar/b")),
        selectedFrameIndex = 1,
        displayImage = null,
        baseTiles = emptyList()
    )
}
