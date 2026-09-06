package com.meteocompare.app.ui.citylist

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CityCardSelectionTest {

    @Test
    fun smartphone_card_keeps_the_standard_home_presentation() {
        val visuals = cityCardSelectionVisuals(
            selectionEnabled = false,
            isSelected = false
        )

        assertEquals(CityCardVisualEmphasis.STANDARD, visuals.emphasis)
        assertEquals(1f, visuals.targetAlpha)
        assertFalse(visuals.usesWeatherTint)
    }

    @Test
    fun tablet_selected_card_uses_its_weather_color_at_full_emphasis() {
        val visuals = cityCardSelectionVisuals(
            selectionEnabled = true,
            isSelected = true
        )

        assertEquals(CityCardVisualEmphasis.WEATHER_COLORED, visuals.emphasis)
        assertEquals(1f, visuals.targetAlpha)
        assertTrue(visuals.usesWeatherTint)
    }

    @Test
    fun tablet_unselected_card_is_deemphasized() {
        val visuals = cityCardSelectionVisuals(
            selectionEnabled = true,
            isSelected = false
        )

        assertEquals(CityCardVisualEmphasis.DEEMPHASIZED, visuals.emphasis)
        assertEquals(CITY_CARD_DEEMPHASIZED_ALPHA, visuals.targetAlpha)
        assertFalse(visuals.usesWeatherTint)
    }

    @Test
    fun selected_surface_color_depends_on_weather_instead_of_a_global_blue() {
        val surface = Color(0xFFF8F8F8)
        val sunny = weatherTintedCardColor(
            baseColor = surface,
            weatherAccent = Color(0xFFFFA726),
            isDark = false
        )
        val rainy = weatherTintedCardColor(
            baseColor = surface,
            weatherAccent = Color(0xFF3F51B5),
            isDark = false
        )

        assertNotEquals(sunny, rainy)
        assertNotEquals(surface, sunny)
        assertNotEquals(surface, rainy)
        assertTrue(sunny.red > rainy.red)
        assertTrue(sunny.blue < rainy.blue)
    }

    @Test
    fun selected_surface_remains_weather_specific_in_dark_theme() {
        val surface = Color(0xFF181818)
        val sunny = weatherTintedCardColor(
            baseColor = surface,
            weatherAccent = Color(0xFFFFB74D),
            isDark = true
        )
        val rainy = weatherTintedCardColor(
            baseColor = surface,
            weatherAccent = Color(0xFF5C6BC0),
            isDark = true
        )

        assertNotEquals(sunny, rainy)
        assertNotEquals(surface, sunny)
        assertNotEquals(surface, rainy)
        assertTrue(sunny.red > rainy.red)
        assertTrue(sunny.blue < rainy.blue)
    }

    @Test
    fun tablet_unselected_colors_are_neutral_grays_in_both_themes() {
        val colors = listOf(
            deemphasizedCardContainerColor(isDark = false),
            deemphasizedCardAccentColor(isDark = false),
            deemphasizedCardContainerColor(isDark = true),
            deemphasizedCardAccentColor(isDark = true)
        )

        colors.forEach { color ->
            assertEquals(color.red, color.green, 0.001f)
            assertEquals(color.green, color.blue, 0.001f)
        }
    }
}
