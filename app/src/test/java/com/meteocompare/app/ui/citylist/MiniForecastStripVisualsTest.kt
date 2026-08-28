package com.meteocompare.app.ui.citylist

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
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

        // Même quantité => même taille ; seule la force du bleu change.
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
    fun `bright heat cell falls back to black when dark theme content is too light`() {
        val background = Color(0xFFFFCC80)
        val darkThemeOnSurface = Color(0xFFE2E2E6)
        val chosen = miniTimelineContentColor(background, darkThemeOnSurface)

        assertEquals(Color.Black, chosen)
        assertTrue(contrastRatio(chosen, background) >= 4.5f)
    }

    @Test
    fun `dark heat cell falls back to white when light theme content is too dark`() {
        val background = Color(0xFF0D47A1)
        val lightThemeOnSurface = Color(0xFF1A1C1E)
        val chosen = miniTimelineContentColor(background, lightThemeOnSurface)

        assertEquals(Color.White, chosen)
        assertTrue(contrastRatio(chosen, background) >= 4.5f)
    }
    @Test
    fun `home heat strip shows six hours before horizontal scroll`() {
        assertEquals(6, MINI_TIMELINE_VISIBLE_HOURS)
    }

    @Test
    fun `adjacent heatmap cell gradients join without a seam`() {
        val colors = listOf(
            Color(0xFF1976D2),
            Color(0xFF81C784),
            Color(0xFFFFB74D)
        )

        val first = miniTimelineCellGradientColors(colors, 0)
        val second = miniTimelineCellGradientColors(colors, 1)
        val third = miniTimelineCellGradientColors(colors, 2)

        assertEquals(first.last(), second.first())
        assertEquals(second.last(), third.first())
        assertEquals(colors[1], second[1])
    }

}
