package com.meteocompare.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherModelOrderingTest {

    @Test
    fun `models from the same family are consecutive`() {
        val shuffled = listOf(
            WeatherModel.ECMWF_AIFS,
            WeatherModel.GFS,
            WeatherModel.ARPEGE_WORLD,
            WeatherModel.ICON_GLOBAL,
            WeatherModel.AROME_FRANCE_HD,
            WeatherModel.ECMWF,
            WeatherModel.ICON_D2,
            WeatherModel.HRRR_CONUS,
            WeatherModel.ARPEGE_EUROPE,
            WeatherModel.ICON_EU
        )

        val sorted = shuffled.sortedByFamily()
        val families = sorted.map { it.family }

        families.distinct().forEach { family ->
            val positions = families.indices.filter { families[it] == family }
            assertEquals(
                "Les modèles de $family doivent former un bloc continu",
                positions.size,
                positions.last() - positions.first() + 1
            )
        }
    }

    @Test
    fun `models are ordered by increasing resolution inside each family`() {
        val sorted = WeatherModel.entries.sortedByFamily()

        sorted.groupBy { it.family }.values.forEach { familyModels ->
            val resolutions = familyModels.map { it.resolutionKm }
            assertTrue(
                "Résolutions non croissantes pour ${familyModels.first().family}: $resolutions",
                resolutions.zipWithNext().all { (a, b) -> a <= b }
            )
        }
    }

    @Test
    fun `expected main family order is stable`() {
        val sorted = WeatherModel.entries.sortedByFamily()
        assertEquals(
            listOf(
                ModelFamily.METEO_FRANCE,
                ModelFamily.DWD,
                ModelFamily.NOAA,
                ModelFamily.ECMWF,
                ModelFamily.UKMO,
                ModelFamily.ECCC,
                ModelFamily.METNO,
                ModelFamily.KNMI,
                ModelFamily.BOM,
                ModelFamily.CMA
            ),
            sorted.map { it.family }.distinct()
        )
    }
}
