package com.meteocompare.app.ui.citylist

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniForecastStripVisualsTest {

    @Test
    fun `rain dot is absent without meaningful rain signal`() {
        assertNull(miniTimelineRainDotStyle(20, 0.0, isDarkTheme = false))
        assertNull(miniTimelineRainDotStyle(null, 0.0, isDarkTheme = true))
    }

    @Test
    fun `rain dot becomes larger when hourly amount increases`() {
        val light = miniTimelineRainDotStyle(70, 0.1, isDarkTheme = false)
        val moderate = miniTimelineRainDotStyle(70, 1.0, isDarkTheme = false)
        val heavy = miniTimelineRainDotStyle(70, 5.0, isDarkTheme = false)

        assertNotNull(light)
        assertNotNull(moderate)
        assertNotNull(heavy)
        assertTrue(moderate!!.radiusDp > light!!.radiusDp)
        assertTrue(heavy!!.radiusDp > moderate.radiusDp)
    }

    @Test
    fun `rain dot blue strengthens with probability`() {
        val low = miniTimelineRainDotStyle(30, 1.0, isDarkTheme = false)!!
        val high = miniTimelineRainDotStyle(100, 1.0, isDarkTheme = false)!!

        assertEquals(low.radiusDp, high.radiusDp, 0.0001f)
        assertTrue(high.color.alpha > low.color.alpha)
        assertTrue(high.color != low.color)
    }

    @Test
    fun `dark theme keeps rain blue visible and probability sensitive`() {
        val low = miniTimelineRainDotStyle(30, 1.0, isDarkTheme = true)!!
        val high = miniTimelineRainDotStyle(100, 1.0, isDarkTheme = true)!!

        assertTrue(high.color.alpha > low.color.alpha)
        assertTrue(high.color != low.color)
    }

    @Test
    fun `theme content color is kept when it already has AA contrast`() {
        val background = Color(0xFFF5F5F5)
        val themeOnSurface = Color(0xFF1A1C1E)
        val chosen = miniTimelineContentColor(background, themeOnSurface)

        assertEquals(themeOnSurface, chosen)
        assertTrue(contrastRatio(chosen, background) >= 4.5f)
    }

    @Test
    fun `bright heat capsule falls back to black when dark theme content is too light`() {
        val background = Color(0xFFFFCC80)
        val darkThemeOnSurface = Color(0xFFE2E2E6)
        val chosen = miniTimelineContentColor(background, darkThemeOnSurface)

        assertEquals(Color.Black, chosen)
        assertTrue(contrastRatio(chosen, background) >= 4.5f)
    }

    @Test
    fun `dark heat capsule falls back to white when light theme content is too dark`() {
        val background = Color(0xFF0D47A1)
        val lightThemeOnSurface = Color(0xFF1A1C1E)
        val chosen = miniTimelineContentColor(background, lightThemeOnSurface)

        assertEquals(Color.White, chosen)
        assertTrue(contrastRatio(chosen, background) >= 4.5f)
    }

    @Test
    fun `home timeline shows six hours before horizontal scroll`() {
        assertEquals(6, MINI_TIMELINE_VISIBLE_HOURS)
    }

    @Test
    fun `home timeline is a compact neutral card with smaller icons`() {
        assertEquals(76, MINI_TIMELINE_HEIGHT_DP)
        assertEquals(10, MINI_TIMELINE_CORNER_RADIUS_DP)
        assertEquals(16, MINI_TIMELINE_CONDITION_ICON_DP)
        assertEquals(42, MINI_TIMELINE_TEMPERATURE_CAPSULE_WIDTH_DP)
        assertEquals(8, MINI_TIMELINE_TEMPERATURE_CAPSULE_RADIUS_DP)
    }

    @Test
    fun `temperature heat remains vivid but is confined to its capsule`() {
        val surface = Color(0xFFF1F1F1)
        val cool = miniTimelineTemperatureBackground(2.0, surface, isDarkTheme = false)
        val warm = miniTimelineTemperatureBackground(28.0, surface, isDarkTheme = false)
        val missing = miniTimelineTemperatureBackground(null, surface, isDarkTheme = false)

        assertTrue(MINI_TIMELINE_HEAT_STRENGTH_LIGHT > 0.80f)
        assertTrue(MINI_TIMELINE_HEAT_STRENGTH_DARK > 0.80f)
        assertNotEquals(cool, warm)
        assertEquals(surface, missing)
    }
}
