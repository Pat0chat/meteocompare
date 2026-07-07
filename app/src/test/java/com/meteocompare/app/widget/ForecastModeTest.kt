package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires purs sur l'enum ForecastMode et son helper
 * [isConfidenceBand]. Sans dépendance Glance/Android → JVM test standard.
 *
 * Objectif : verrouiller la sémantique du helper — s'assurer qu'un futur
 * ajout de mode n'oublie pas de mettre à jour la fonction (Kotlin exigera
 * un `else ->` explicite si on ajoute un case sans le classer).
 */
class ForecastModeTest {

    @Test
    fun `isConfidenceBand - HOURLY et DAILY renvoient false`() {
        assertFalse(ForecastMode.HOURLY.isConfidenceBand())
        assertFalse(ForecastMode.DAILY.isConfidenceBand())
    }

    @Test
    fun `isConfidenceBand - les trois modes CONFIDENCE renvoient true`() {
        assertTrue(ForecastMode.CONFIDENCE_TEMPERATURE.isConfidenceBand())
        assertTrue(ForecastMode.CONFIDENCE_PRECIPITATION.isConfidenceBand())
        assertTrue(ForecastMode.CONFIDENCE_WIND.isConfidenceBand())
    }

    @Test
    fun `isConfidenceBand - partition exhaustive de l'enum`() {
        // Verrouille l'invariant "chaque mode est soit une bande, soit un
        // forecast discret, pas les deux". Un futur ajout d'enum sans
        // classification tomberait ici.
        val confidenceModes = ForecastMode.entries.count { it.isConfidenceBand() }
        val nonConfidenceModes = ForecastMode.entries.count { !it.isConfidenceBand() }
        assertEquals(ForecastMode.entries.size, confidenceModes + nonConfidenceModes)
        assertEquals(3, confidenceModes) // TEMPERATURE, PRECIPITATION, WIND
        assertEquals(2, nonConfidenceModes) // HOURLY, DAILY
    }
}
