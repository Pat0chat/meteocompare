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
    fun `isConfidenceBand - MINI_FORECAST_12H renvoie false`() {
        // Le mini forecast est un rendu Bitmap dédié, PAS une bande de confiance.
        // Il est traité par sa propre branche dans ExtraLargeLayout.
        assertFalse(ForecastMode.MINI_FORECAST_12H.isConfidenceBand())
        assertFalse(ForecastMode.HEATMAP_CHART_12H.isConfidenceBand())
    }

    @Test
    fun `isConfidenceBand - le mode combiné et les valeurs historiques renvoient true`() {
        assertTrue(ForecastMode.CONFIDENCE_ALL.isConfidenceBand())
        assertTrue(ForecastMode.CONFIDENCE_TEMPERATURE.isConfidenceBand())
        assertTrue(ForecastMode.CONFIDENCE_PRECIPITATION.isConfidenceBand())
        assertTrue(ForecastMode.CONFIDENCE_WIND.isConfidenceBand())
    }

    @Test
    fun `normalized - migre les anciens modes vers la vue combinée`() {
        assertEquals(ForecastMode.CONFIDENCE_ALL, ForecastMode.CONFIDENCE_ALL.normalized())
        assertEquals(ForecastMode.CONFIDENCE_ALL, ForecastMode.CONFIDENCE_TEMPERATURE.normalized())
        assertEquals(ForecastMode.CONFIDENCE_ALL, ForecastMode.CONFIDENCE_PRECIPITATION.normalized())
        assertEquals(ForecastMode.CONFIDENCE_ALL, ForecastMode.CONFIDENCE_WIND.normalized())
        assertEquals(ForecastMode.HOURLY, ForecastMode.HOURLY.normalized())
        assertEquals(ForecastMode.DAILY, ForecastMode.DAILY.normalized())
    }

    @Test
    fun `isMiniForecast - reconnaît uniquement le mode dédié`() {
        // Le helper doit être STRICTEMENT vrai pour MINI_FORECAST_12H et faux
        // pour tous les autres. Sert de guard dans loadWidgetData pour aiguiller
        // vers le pipeline de rendu bitmap.
        assertTrue(ForecastMode.MINI_FORECAST_12H.isMiniForecast())
        assertFalse(ForecastMode.HOURLY.isMiniForecast())
        assertFalse(ForecastMode.DAILY.isMiniForecast())
        assertFalse(ForecastMode.CONFIDENCE_ALL.isMiniForecast())
        assertFalse(ForecastMode.CONFIDENCE_TEMPERATURE.isMiniForecast())
        assertFalse(ForecastMode.CONFIDENCE_PRECIPITATION.isMiniForecast())
        assertFalse(ForecastMode.CONFIDENCE_WIND.isMiniForecast())
        assertFalse(ForecastMode.HEATMAP_CHART_12H.isMiniForecast())
        assertFalse(ForecastMode.HEATMAP_TREND_12H.isMiniForecast())
    }

    @Test
    fun `helpers 12h bitmap reconnaissent mini et heatmap`() {
        assertTrue(ForecastMode.MINI_FORECAST_12H.usesTwelveHourBitmapForecast())
        assertTrue(ForecastMode.HEATMAP_CHART_12H.usesTwelveHourBitmapForecast())
        assertTrue(ForecastMode.HEATMAP_TREND_12H.usesTwelveHourBitmapForecast())
        assertTrue(ForecastMode.HEATMAP_CHART_12H.isHeatmapChartForecast())
        assertTrue(ForecastMode.HEATMAP_TREND_12H.isHeatmapChartForecast())
        assertTrue(ForecastMode.HEATMAP_TREND_12H.isModernHeatmapChartForecast())
        assertFalse(ForecastMode.HOURLY.usesTwelveHourBitmapForecast())
        assertFalse(ForecastMode.MINI_FORECAST_12H.isHeatmapChartForecast())
    }

    @Test
    fun `partition exhaustive de l'enum en groupes disjoints`() {
        // Verrouille l'invariant "chaque mode appartient à EXACTEMENT UN groupe" :
        //   - CONFIDENCE_ALL + 3 valeurs historiques : 4 modes
        //   - mini forecast : 1 mode
        //   - heatmaps 12 h : 2 modes
        //   - Forecast discret (HOURLY, DAILY) : 2 modes
        // Un futur ajout d'enum sans classification tombera ici — soit dans
        // le total, soit dans "aucun groupe / plusieurs groupes".
        val confidenceCount = ForecastMode.entries.count { it.isConfidenceBand() }
        val miniCount = ForecastMode.entries.count { it.isMiniForecast() }
        val heatmapCount = ForecastMode.entries.count { it.isHeatmapChartForecast() }
        val discreteCount = ForecastMode.entries.count {
            !it.isConfidenceBand() && !it.usesTwelveHourBitmapForecast()
        }
        // Exhaustivité : la somme = total
        assertEquals(
            ForecastMode.entries.size,
            confidenceCount + miniCount + heatmapCount + discreteCount
        )
        // Disjonction : aucun mode ne peut être à la fois confidence ET vue 12 h bitmap
        val overlap = ForecastMode.entries.count {
            it.isConfidenceBand() && it.usesTwelveHourBitmapForecast()
        }
        assertEquals("Un mode ne peut pas être à la fois confidence et mini", 0, overlap)
        // Comptages spécifiques
        assertEquals("Confidence : 1 mode combiné + 3 valeurs historiques", 4, confidenceCount)
        assertEquals("Mini forecast : 1 (MINI_FORECAST_12H)", 1, miniCount)
        assertEquals("Heatmap charts : 2", 2, heatmapCount)
        assertEquals("Forecast discret : 2 (HOURLY, DAILY)", 2, discreteCount)
    }
}