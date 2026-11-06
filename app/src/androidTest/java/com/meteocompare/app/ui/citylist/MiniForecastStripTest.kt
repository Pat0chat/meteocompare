package com.meteocompare.app.ui.citylist

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.WeatherCondition
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MiniForecastStripTest {
    @get:Rule val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun nominal_data_exposes_min_max_and_rain_in_accessibility_label() {
        composeRule.setContent {
            MiniForecastStrip(
                hourlyTemps = listOf(15.0, 16.0, 17.0, 18.0, 20.0, 22.0, 23.0, 22.0, 20.0, 18.0, 16.0, 15.0),
                hourlyPrecipProb = listOf(0, 10, 20, 40, 60, 50, 30, 10, 0, 0, 0, 0)
            )
        }
        composeRule.onNodeWithTag(TAG_MINI_FORECAST_STRIP).assertIsDisplayed()
            .assertContentDescriptionContains("15", substring = true)
            .assertContentDescriptionContains("23", substring = true)
    }

    @Test
    fun empty_or_all_null_data_uses_no_data_accessibility_label() {
        composeRule.setContent {
            MiniForecastStrip(
                hourlyTemps = List<Double?>(12) { null },
                hourlyPrecipProb = List<Int?>(12) { null }
            )
        }
        composeRule.onNodeWithTag(TAG_MINI_FORECAST_STRIP)
            .assertContentDescriptionContains(
                context.getString(R.string.mini_forecast_a11y_no_data),
                substring = false
            )
    }

    @Test
    fun partial_and_constant_data_do_not_crash() {
        composeRule.setContent {
            MiniForecastStrip(
                hourlyTemps = listOf(20.0, 20.0, 20.0),
                hourlyPrecipProb = listOf(0, null, 80)
            )
        }
        composeRule.onNodeWithTag(TAG_MINI_FORECAST_STRIP).assertIsDisplayed()
    }

    @Test
    fun weather_condition_icon_is_rendered_for_each_available_hour() {
        composeRule.setContent {
            MiniForecastStrip(
                hourlyTemps = List(12) { 20.0 },
                hourlyPrecipProb = List(12) { 0 },
                hourlyConditions = listOf(
                    WeatherCondition.CLEAR,
                    WeatherCondition.RAIN
                ) + List(10) { null }
            )
        }

        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_CONDITION_PREFIX}0").assertIsDisplayed()
        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_CONDITION_PREFIX}1").assertIsDisplayed()
        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_CONDITION_PREFIX}2").assertDoesNotExist()
        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_TEMPERATURE_PREFIX}0").assertIsDisplayed()
    }

    @Test
    fun shared_colorful_icons_are_kept_for_day_and_night_hours() {
        composeRule.setContent {
            MiniForecastStrip(
                hourlyTemps = List(12) { 12.0 },
                hourlyPrecipProb = List(12) { 0 },
                hourlyConditions = listOf(
                    WeatherCondition.MAINLY_CLEAR,
                    WeatherCondition.PARTLY_CLOUDY,
                    WeatherCondition.RAIN_SHOWERS
                ) + List(9) { null },
                nightFlags = listOf(false, true, true)
            )
        }

        (0..2).forEach { index ->
            composeRule.onNodeWithTag("$TAG_MINI_FORECAST_CONDITION_PREFIX$index")
                .assertIsDisplayed()
        }
        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_CONDITION_PREFIX}3").assertDoesNotExist()
    }

    @Test
    fun icon_and_temperature_capsule_keep_their_compact_dimensions() {
        composeRule.setContent {
            MiniForecastStrip(
                hourlyTemps = List(12) { 15.0 },
                hourlyPrecipProb = List(12) { 0 },
                hourlyConditions = List(12) { WeatherCondition.CLEAR }
            )
        }

        val icon = composeRule.onNodeWithTag("${TAG_MINI_FORECAST_CONDITION_PREFIX}0")
            .getUnclippedBoundsInRoot()
        val capsule = composeRule.onNodeWithTag("${TAG_MINI_FORECAST_TEMPERATURE_PREFIX}0")
            .getUnclippedBoundsInRoot()

        assertEquals(27f, (icon.right - icon.left).value, 0.5f)
        assertEquals(27f, (icon.bottom - icon.top).value, 0.5f)
        assertEquals(40f, (capsule.right - capsule.left).value, 0.5f)
        assertEquals(18f, (capsule.bottom - capsule.top).value, 0.5f)
    }

    @Test
    fun current_hour_marker_uses_theme_accent_and_updates_when_accent_changes() {
        val accent = mutableStateOf(Color(0xFF2456B8))
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = accent.value)) {
                MiniForecastStrip(
                    hourlyTemps = List(12) { 15.0 },
                    hourlyPrecipProb = List(12) { 0 },
                    startTime = LocalDateTime.of(2026, 7, 15, 15, 0)
                )
            }
        }

        fun assertMarkerMatches(expected: Color) {
            val pixels = composeRule.onNodeWithTag(TAG_MINI_FORECAST_CURRENT_MARKER)
                .captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            assertEquals(expected.red, center.red, 0.08f)
            assertEquals(expected.green, center.green, 0.08f)
            assertEquals(expected.blue, center.blue, 0.08f)
        }

        assertMarkerMatches(Color(0xFF2456B8))
        composeRule.runOnIdle { accent.value = Color(0xFFCA582F) }
        composeRule.waitForIdle()
        assertMarkerMatches(Color(0xFFCA582F))
    }

    @Test
    fun anchors_are_absent_without_start_time() {
        composeRule.setContent {
            MiniForecastStrip(List(12) { 20.0 }, List(12) { 0 })
        }
        composeRule.onNodeWithTag(TAG_MINI_FORECAST_ANCHORS).assertDoesNotExist()
    }

    @Test
    fun anchors_are_rendered_with_start_time() {
        composeRule.setContent {
            MiniForecastStrip(
                List(12) { 20.0 },
                List(12) { 0 },
                startTime = LocalDateTime.of(2026, 7, 15, 15, 0)
            )
        }
        composeRule.onNodeWithTag(TAG_MINI_FORECAST_ANCHORS).assertIsDisplayed()
    }
    @Test
    fun six_hours_are_visible_and_later_hours_are_reached_by_horizontal_swipe() {
        composeRule.setContent {
            MiniForecastStrip(
                hourlyTemps = List(12) { 10.0 + it },
                hourlyPrecipProb = List(12) { 0 },
                hourlyConditions = List(12) { WeatherCondition.CLEAR },
                startTime = LocalDateTime.of(2026, 8, 28, 9, 0)
            )
        }

        val firstHour = composeRule.onNodeWithTag("${TAG_MINI_FORECAST_CONDITION_PREFIX}0")
        firstHour.assertIsDisplayed()
        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_CONDITION_PREFIX}5").assertIsDisplayed()
        val laterHour = composeRule.onNodeWithTag("${TAG_MINI_FORECAST_CONDITION_PREFIX}10")
        laterHour.assertIsNotDisplayed()

        // A swipe does move the actual horizontal strip. Do not assume a
        // single swipe covers five hours on every screen / emulator density.
        val initialLeft = firstHour.getUnclippedBoundsInRoot().left
        composeRule.onNodeWithTag(TAG_MINI_FORECAST_SCROLL).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        val scrolledLeft = firstHour.getUnclippedBoundsInRoot().left
        assertTrue("A horizontal swipe must move the timeline", scrolledLeft < initialLeft)

        // Scroll the real content until the requested hour is in the viewport.
        laterHour.performScrollTo().assertIsDisplayed()
    }

    @Test
    fun full_day_has_twenty_four_distinct_cells_and_last_hour_is_scrollable() {
        composeRule.setContent {
            MiniForecastStrip(
                hourlyTemps = List(24) { 10.0 + it },
                hourlyPrecipProb = List(24) { 0 },
                hourlyConditions = List(24) { WeatherCondition.CLEAR },
                startTime = LocalDateTime.of(2026, 8, 28, 9, 0)
            )
        }
        val lastHour = composeRule.onNodeWithTag("${TAG_MINI_FORECAST_CONDITION_PREFIX}23")
        lastHour.assertIsNotDisplayed()
        // Request the last hour explicitly: a fixed number of swipes is not
        // portable across screen widths and animation / gesture durations.
        lastHour.performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_CONDITION_PREFIX}0")
            .assertIsNotDisplayed()
        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_TEMPERATURE_PREFIX}23")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_TEMPERATURE_PREFIX}24")
            .assertDoesNotExist()
    }


    @Test
    fun sunshine_minutes_are_part_of_each_hour_not_a_separate_carousel() {
        composeRule.setContent {
            MiniForecastStrip(
                hourlyTemps = List(24) { 18.0 },
                hourlyPrecipProb = List(24) { 0 },
                hourlyConditions = List(24) { WeatherCondition.CLEAR },
                hourlySunshineHours = listOf(0.3, 0.0, null, Double.NaN) + List(20) { 0.5 },
                startTime = LocalDateTime.of(2026, 7, 15, 12, 0)
            )
        }
        val sunshine0 = composeRule.onNodeWithTag("${TAG_MINI_FORECAST_SUNSHINE_PREFIX}0", useUnmergedTree = true)
        sunshine0.assertIsDisplayed()
            .assertContentDescriptionContains(context.getString(R.string.compare_sunshine), substring = true)
            .assertContentDescriptionContains("18", substring = true)
        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_SUNSHINE_PREFIX}1", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertContentDescriptionContains("0", substring = true)
        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_SUNSHINE_PREFIX}2", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_SUNSHINE_PREFIX}3", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_CONDITION_PREFIX}0").assertIsDisplayed()
        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_TEMPERATURE_PREFIX}0").assertIsDisplayed()
    }

    @Test
    fun sunshine_follows_same_scroll_as_24_hour_conditions_and_stops_at_hour_23() {
        composeRule.setContent {
            MiniForecastStrip(
                hourlyTemps = List(24) { 20.0 },
                hourlyPrecipProb = List(24) { 0 },
                hourlyConditions = List(24) { WeatherCondition.CLEAR },
                hourlySunshineHours = List(26) { 0.5 },
                startTime = LocalDateTime.of(2026, 7, 15, 12, 0)
            )
        }
        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_SUNSHINE_PREFIX}0", useUnmergedTree = true)
            .assertIsDisplayed()
        val last = composeRule.onNodeWithTag("${TAG_MINI_FORECAST_SUNSHINE_PREFIX}23", useUnmergedTree = true)
        last.assertIsNotDisplayed()
        last.performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_TEMPERATURE_PREFIX}23").assertIsDisplayed()
        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_SUNSHINE_PREFIX}24", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun sunshine_is_absent_and_strip_keeps_its_original_height_when_data_missing() {
        composeRule.setContent {
            MiniForecastStrip(
                hourlyTemps = List(24) { 20.0 },
                hourlyPrecipProb = List(24) { 0 },
                hourlySunshineHours = List(24) { null } + listOf(0.3)
            )
        }
        composeRule.onNodeWithTag("${TAG_MINI_FORECAST_SUNSHINE_PREFIX}0", useUnmergedTree = true)
            .assertDoesNotExist()
        val strip = composeRule.onNodeWithTag(TAG_MINI_FORECAST_STRIP)
        assertEquals(MINI_TIMELINE_HEIGHT_DP.toFloat(), strip.getUnclippedBoundsInRoot().height.value, 0.5f)
    }

    @Test
    fun valid_sunshine_extends_only_the_timeline_not_the_card_with_a_second_panel() {
        composeRule.setContent {
            MiniForecastStrip(
                hourlyTemps = List(24) { 20.0 },
                hourlyPrecipProb = List(24) { 0 },
                hourlySunshineHours = List(24) { 0.3 }
            )
        }
        val strip = composeRule.onNodeWithTag(TAG_MINI_FORECAST_STRIP)
        assertEquals(
            (MINI_TIMELINE_HEIGHT_DP + MINI_TIMELINE_SUNSHINE_EXTRA_HEIGHT_DP).toFloat(),
            strip.getUnclippedBoundsInRoot().height.value, 0.5f
        )
    }


    @Test
    fun sunshine_minutes_follow_the_app_language_inside_the_mini_timeline() {
        val language = mutableStateOf("fr")
        composeRule.setContent {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language.value))
            }
            val localeContext = context.createConfigurationContext(configuration)
            CompositionLocalProvider(
                LocalContext provides localeContext,
                LocalConfiguration provides configuration
            ) {
                MiniForecastStrip(
                    hourlyTemps = List(24) { 20.0 },
                    hourlyPrecipProb = List(24) { 0 },
                    hourlySunshineHours = List(24) { 0.3 }
                )
            }
        }
        for ((locale, label) in mapOf(
            "fr" to "18 min", "en" to "18 min", "de" to "18 Min.",
            "es" to "18 min", "it" to "18 min"
        )) {
            composeRule.runOnIdle { language.value = locale }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("${TAG_MINI_FORECAST_SUNSHINE_PREFIX}0", useUnmergedTree = true)
                .assertIsDisplayed()
                .assertContentDescriptionContains(label, substring = true)
        }
    }

}
