package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastConsensusTest {

    @Test
    fun `les trois ICON partagent exactement une voix face a GFS`() {
        val weights = ForecastConsensus.familyBalancedWeights(
            listOf(
                WeatherModel.ICON_D2,
                WeatherModel.ICON_EU,
                WeatherModel.ICON_GLOBAL,
                WeatherModel.GFS
            )
        )

        val iconMass = listOf(
            WeatherModel.ICON_D2,
            WeatherModel.ICON_EU,
            WeatherModel.ICON_GLOBAL
        ).sumOf { weights.getValue(it) }

        assertEquals(1.0, iconMass, 1e-9)
        assertEquals(1.0, weights.getValue(WeatherModel.GFS), 1e-9)
    }

    @Test
    fun `la mediane ponderee resiste a un modele aberrant`() {
        val result = ForecastConsensus.continuous(
            entries = listOf(
                ForecastConsensus.Entry(WeatherModel.GFS, 20.0),
                ForecastConsensus.Entry(WeatherModel.ECMWF, 21.0),
                ForecastConsensus.Entry(WeatherModel.ICON_GLOBAL, 22.0),
                ForecastConsensus.Entry(WeatherModel.ARPEGE_EUROPE, 60.0)
            ),
            tightStdDev = 0.5,
            wideStdDev = 3.0
        )

        assertEquals(21.5, result.central ?: error("central missing"), 1e-9)
        assertEquals(4, result.familyCount)
    }

    @Test
    fun `pluie separe occurrence quantite conditionnelle et esperance`() {
        val result = ForecastConsensus.precipitation(
            rows = listOf(
                ForecastConsensus.PrecipitationRow(WeatherModel.GFS, amountMm = 0.0, probabilityPercent = 20),
                ForecastConsensus.PrecipitationRow(WeatherModel.ECMWF, amountMm = 8.0, probabilityPercent = 80),
                ForecastConsensus.PrecipitationRow(WeatherModel.ICON_GLOBAL, amountMm = 10.0, probabilityPercent = 70),
                ForecastConsensus.PrecipitationRow(WeatherModel.ARPEGE_EUROPE, amountMm = 0.0, probabilityPercent = 30)
            ),
            thresholdMm = 1.0,
            amountTightStdDev = 1.0,
            amountWideStdDev = 8.0
        )

        assertEquals(50, result.probabilityPercent)
        assertEquals(9.0, result.conditionalAmountMm ?: error("conditional missing"), 1e-9)
        assertEquals(9.0, result.centralAmountMm ?: error("central missing"), 1e-9)
        assertEquals(4.5, result.expectedAmountMm ?: error("expected missing"), 1e-9)
        assertEquals(ForecastConsensus.PrecipitationSource.PROBABILITY, result.source)
    }

    @Test
    fun `une ponderation locale extreme reste bornee au niveau famille`() {
        val weights = ForecastConsensus.familyBalancedWeights(
            models = listOf(WeatherModel.AROME_FRANCE_HD, WeatherModel.GFS),
            localWeights = mapOf(WeatherModel.AROME_FRANCE_HD to 100.0, WeatherModel.GFS to 1.0)
        )

        assertTrue(weights.getValue(WeatherModel.AROME_FRANCE_HD) <= 1.25 + 1e-9)
        assertEquals(1.0, weights.getValue(WeatherModel.GFS), 1e-9)
    }
    @Test
    fun `nouveaux modeles europeens conservent les lignees du web v1 16`() {
        assertEquals(
            ForecastConsensus.groupFor(WeatherModel.KNMI_HARMONIE_EU),
            ForecastConsensus.groupFor(WeatherModel.DMI_HARMONIE_EU)
        )
        assertEquals(
            ForecastConsensus.groupFor(WeatherModel.ICON_EU),
            ForecastConsensus.groupFor(WeatherModel.METEOSWISS_ICON_CH2)
        )
    }

    @Test
    fun `ciel sec ne fragmente plus le vote entre quatre libelles`() {
        val entries = listOf(
            ForecastConsensus.Entry(WeatherModel.AROME_FRANCE_HD, WeatherCondition.OVERCAST),
            ForecastConsensus.Entry(WeatherModel.ARPEGE_EUROPE, WeatherCondition.OVERCAST),
            ForecastConsensus.Entry(WeatherModel.GFS, WeatherCondition.OVERCAST),
            ForecastConsensus.Entry(WeatherModel.ECMWF, WeatherCondition.PARTLY_CLOUDY),
            ForecastConsensus.Entry(WeatherModel.UKMO_GLOBAL, WeatherCondition.PARTLY_CLOUDY),
            ForecastConsensus.Entry(WeatherModel.GEM_GLOBAL, WeatherCondition.MAINLY_CLEAR),
            ForecastConsensus.Entry(WeatherModel.METNO_NORDIC, WeatherCondition.MAINLY_CLEAR),
            ForecastConsensus.Entry(WeatherModel.KNMI_HARMONIE_EU, WeatherCondition.CLEAR),
            ForecastConsensus.Entry(WeatherModel.BOM_ACCESS, WeatherCondition.CLEAR)
        )

        val result = ForecastConsensus.conditionHybrid(entries, cloudCoverPercent = 62.0)

        assertEquals(WeatherCondition.PARTLY_CLOUDY, result.value)
        // La convergence reste le vote brut : OVERCAST est seulement 3/9.
        assertEquals(33, result.percent)
    }

    @Test
    fun `phenomene significatif majoritaire reste prioritaire sur la nebulosite`() {
        val entries = listOf(
            ForecastConsensus.Entry(WeatherModel.GFS, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.ECMWF, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.ICON_GLOBAL, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.ARPEGE_EUROPE, WeatherCondition.CLEAR),
            ForecastConsensus.Entry(WeatherModel.UKMO_GLOBAL, WeatherCondition.MAINLY_CLEAR)
        )

        val result = ForecastConsensus.conditionHybrid(entries, cloudCoverPercent = 10.0)

        assertEquals(WeatherCondition.RAIN, result.value)
    }

    @Test
    fun `egalite ciel sec pluie reste prudente et choisit pluie`() {
        val entries = listOf(
            ForecastConsensus.Entry(WeatherModel.GFS, WeatherCondition.CLEAR),
            ForecastConsensus.Entry(WeatherModel.ECMWF, WeatherCondition.RAIN)
        )

        val result = ForecastConsensus.conditionHybrid(entries, cloudCoverPercent = 5.0)

        assertEquals(WeatherCondition.RAIN, result.value)
    }

    @Test
    fun `nebulosite seule peut resoudre un ciel sec sans code WMO`() {
        val result = ForecastConsensus.conditionHybrid(emptyList(), cloudCoverPercent = 82.0)

        assertEquals(WeatherCondition.PARTLY_CLOUDY, result.value)
        assertEquals(null, result.percent)
    }

}
