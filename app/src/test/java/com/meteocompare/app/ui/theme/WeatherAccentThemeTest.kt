package com.meteocompare.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.meteocompare.app.domain.model.WeatherCondition
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherAccentThemeTest {

    @Test
    fun `les accents de texte respectent le contraste AA en thème clair`() {
        assertReadableAccents(
            surface = Color.White,
            isDark = false
        )
    }

    @Test
    fun `les accents de texte respectent le contraste AA en thème sombre`() {
        assertReadableAccents(
            surface = Color(0xFF121212),
            isDark = true
        )
    }

    @Test
    fun `les contenus de conteneur restent lisibles`() {
        listOf(false, true).forEach { isDark ->
            val surface = if (isDark) Color(0xFF121212) else Color.White
            activeConditions.forEach { condition ->
                val raw = requireNotNull(WeatherAccent.activeOrNull(condition, isDark))
                val primary = readableAccent(raw, surface)
                val container = weatherAccentContainer(raw, surface, isDark)
                val onContainer = readableAccent(primary, container)

                assertTrue(
                    "$condition manque de contraste sur son conteneur",
                    accentContrastRatio(onContainer, container) >= 4.5f
                )
            }
        }
    }

    @Test
    fun `une condition absente ou inconnue conserve le thème de base`() {
        assertNull(WeatherAccent.activeOrNull(null, isDark = false))
        assertNull(WeatherAccent.activeOrNull(WeatherCondition.UNKNOWN, isDark = false))
    }

    private fun assertReadableAccents(surface: Color, isDark: Boolean) {
        activeConditions.forEach { condition ->
            val raw = requireNotNull(WeatherAccent.activeOrNull(condition, isDark))
            val primary = readableAccent(raw, surface)
            assertTrue(
                "$condition manque de contraste",
                accentContrastRatio(primary, surface) >= 4.5f
            )
        }
    }

    private val activeConditions = WeatherCondition.entries
        .filterNot { it == WeatherCondition.UNKNOWN }
}
