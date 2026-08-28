package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.PrecipitationThresholds
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
    fun `probabilite native reste probabiliste meme si tous les deterministes sont humides`() {
        val result = ForecastConsensus.precipitation(
            rows = listOf(
                ForecastConsensus.PrecipitationRow(WeatherModel.GFS, amountMm = 4.0, probabilityPercent = 70),
                ForecastConsensus.PrecipitationRow(WeatherModel.ECMWF, amountMm = 5.0, probabilityPercent = 80),
                ForecastConsensus.PrecipitationRow(WeatherModel.ARPEGE_EUROPE, amountMm = 6.0, probabilityPercent = 90)
            ),
            thresholdMm = PrecipitationThresholds.DAILY_OCCURRENCE_MM,
            amountTightStdDev = 1.0,
            amountWideStdDev = 8.0
        )

        assertEquals(80, result.probabilityPercent)
        assertEquals(3, result.wetModelCount)
        assertEquals(ForecastConsensus.PrecipitationSource.PROBABILITY, result.source)
    }

    @Test
    fun `accord deterministe complet sans probabilite native donne cent pour cent meme pour pluie faible`() {
        val result = ForecastConsensus.precipitation(
            rows = listOf(
                ForecastConsensus.PrecipitationRow(WeatherModel.GFS, amountMm = 0.2),
                ForecastConsensus.PrecipitationRow(WeatherModel.ECMWF, amountMm = 0.3),
                ForecastConsensus.PrecipitationRow(WeatherModel.ARPEGE_EUROPE, amountMm = 0.4)
            ),
            thresholdMm = PrecipitationThresholds.DAILY_OCCURRENCE_MM,
            amountTightStdDev = 1.0,
            amountWideStdDev = 8.0
        )

        assertEquals(100, result.probabilityPercent)
        assertEquals(3, result.wetModelCount)
        assertEquals(ForecastConsensus.PrecipitationSource.MODEL_AGREEMENT, result.source)
        assertTrue((result.centralAmountMm ?: 0.0) >= 0.2)
    }

    @Test
    fun `probabilite sans quantite ne fabrique pas zero millimetre`() {
        val result = ForecastConsensus.precipitation(
            rows = listOf(
                ForecastConsensus.PrecipitationRow(WeatherModel.GFS, probabilityPercent = 80),
                ForecastConsensus.PrecipitationRow(WeatherModel.ECMWF, probabilityPercent = 70)
            ),
            thresholdMm = PrecipitationThresholds.DAILY_OCCURRENCE_MM,
            amountTightStdDev = 1.0,
            amountWideStdDev = 8.0
        )

        assertEquals(75, result.probabilityPercent)
        assertEquals(null, result.centralAmountMm)
        assertEquals(null, result.expectedAmountMm)
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
    fun `ECMWF HRES et AIFS partagent une seule masse de famille`() {
        val weights = ForecastConsensus.familyBalancedWeights(
            listOf(WeatherModel.ECMWF, WeatherModel.ECMWF_AIFS, WeatherModel.GFS)
        )

        assertEquals(1.0, weights.getValue(WeatherModel.ECMWF) + weights.getValue(WeatherModel.ECMWF_AIFS), 1e-9)
        assertEquals(1.0, weights.getValue(WeatherModel.GFS), 1e-9)
        assertEquals(
            ForecastConsensus.groupFor(WeatherModel.ECMWF),
            ForecastConsensus.groupFor(WeatherModel.ECMWF_AIFS)
        )
    }

}
