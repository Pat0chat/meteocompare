package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.ModelBias
import com.meteocompare.app.domain.model.WeatherModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Régression issue #8 : le bandeau de collecte ne doit plus disparaître dès
 * qu'un seul modèle est prêt sur une seule variable, alors que les autres
 * affichent encore « N/14 ».
 */
class BiasHistoryProgressTest {

    private val models = listOf(WeatherModel.GFS, WeatherModel.ECMWF, WeatherModel.ICON_EU)
    private val start = LocalDate.of(2026, 9, 1)

    @Test
    fun `aucun modele pret - bandeau de preparation avec le modele le plus avance`() {
        val state = state(samplesByModel = mapOf(WeatherModel.GFS to 3, WeatherModel.ECMWF to 5))

        val progress = state.historyProgress(models)

        assertTrue(progress.isPreparing)
        assertTrue(progress.shouldShowBanner)
        assertEquals(0, progress.readyModels)
        assertEquals(2, progress.collectingModels)
        assertEquals(5, progress.bestSampleCount)
    }

    @Test
    fun `premier lancement sans aucune donnee - bandeau de preparation sans progression`() {
        val progress = state(samplesByModel = emptyMap()).historyProgress(models)

        assertTrue(progress.shouldShowBanner)
        assertEquals(0, progress.bestSampleCount)
    }

    @Test
    fun `un modele pret et les autres en collecte - le bandeau reste affiche`() {
        val state = state(
            samplesByModel = mapOf(WeatherModel.GFS to 16, WeatherModel.ECMWF to 9, WeatherModel.ICON_EU to 4),
            readyModels = setOf(WeatherModel.GFS)
        )

        val progress = state.historyProgress(models)

        assertFalse(progress.isPreparing)
        assertTrue(progress.shouldShowBanner)
        assertEquals(1, progress.readyModels)
        assertEquals(2, progress.collectingModels)
        assertEquals(3, progress.totalModels)
        assertEquals(9, progress.bestSampleCount)
    }

    @Test
    fun `tous les modeles collectables prets - le bandeau disparait`() {
        // ICON-EU n'a aucune donnée (source sans archive) : il ne retient pas le bandeau.
        val state = state(
            samplesByModel = mapOf(WeatherModel.GFS to 16, WeatherModel.ECMWF to 15),
            readyModels = setOf(WeatherModel.GFS, WeatherModel.ECMWF)
        )

        assertFalse(state.historyProgress(models).shouldShowBanner)
    }

    @Test
    fun `un modele encore incomplet n'affiche jamais 14 sur 14`() {
        // Des doublons de dates peuvent porter l'historique brut à 14 lignes
        // alors que le biais (dédupliqué) n'est pas encore calculable.
        val progress = state(samplesByModel = mapOf(WeatherModel.GFS to 14)).historyProgress(models)

        assertEquals(ModelBias.MIN_SAMPLES_FOR_BIAS - 1, progress.bestSampleCount)
    }

    private fun state(
        samplesByModel: Map<WeatherModel, Int>,
        readyModels: Set<WeatherModel> = emptySet()
    ) = VariableBiasState(
        biasByModel = models.associateWith { model ->
            if (model in readyModels) {
                ModelBias(
                    variable = BiasVariable.PRECIPITATION,
                    meanBias = 0.2,
                    stdDev = 1.0,
                    sampleSize = samplesByModel.getValue(model)
                )
            } else {
                null
            }
        },
        historyByModel = samplesByModel.mapValues { (_, count) ->
            List(count) { day -> BiasSample(start.plusDays(day.toLong()), forecast = 1.0, observation = 1.0) }
        },
        yDomainMin = null,
        yDomainMax = null
    )
}
