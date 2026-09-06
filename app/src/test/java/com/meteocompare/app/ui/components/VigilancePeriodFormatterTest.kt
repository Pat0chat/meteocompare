package com.meteocompare.app.ui.components

import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VigilancePeriodFormatterTest {

    @Test
    fun `les creneaux du meme jour affichent la date une seule fois`() {
        val label = formatVigilanceWindows(
            windows = listOf(
                Instant.parse("2026-08-29T08:00:00Z") to
                    Instant.parse("2026-08-29T12:00:00Z"),
                Instant.parse("2026-08-29T16:00:00Z") to
                    Instant.parse("2026-08-29T20:00:00Z")
            ),
            timezone = "Europe/Paris",
            locale = Locale.FRENCH
        )

        assertTrue(label.contains("29"))
        assertEquals(1, Regex("\\b29\\b").findAll(label).count())
        assertTrue(label.contains("10h–14h, 18h–22h"))
        assertFalse(label.contains("→"))
    }

    @Test
    fun `un creneau de 16h a minuit affiche les deux jours`() {
        val label = formatVigilanceWindows(
            windows = listOf(
                Instant.parse("2026-08-29T14:00:00Z") to
                    Instant.parse("2026-08-29T22:00:00Z")
            ),
            timezone = "Europe/Paris",
            locale = Locale.FRENCH
        )

        val (begin, end) = label.split(" → ")
        assertTrue(begin.contains("29"))
        assertTrue(begin.endsWith("16h"))
        assertTrue(end.contains("30"))
        assertTrue(end.endsWith("00h"))
    }

    @Test
    fun `les bornes invalides ne produisent pas de periode trompeuse`() {
        val instant = Instant.parse("2026-08-29T14:00:00Z")

        assertEquals(
            "",
            formatVigilanceWindows(
                windows = listOf(instant to instant),
                timezone = "Europe/Paris",
                locale = Locale.FRENCH
            )
        )
    }
}
