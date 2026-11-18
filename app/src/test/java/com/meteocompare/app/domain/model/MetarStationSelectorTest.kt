package com.meteocompare.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetarStationSelectorTest {

    private val orly = MetarStation("LFPO", 48.717, 2.384, 88.0)
    private val bourget = MetarStation("LFPB", 48.969, 2.441, 66.0)
    private val mountain = MetarStation("LFXX", 48.80, 2.30, 600.0)

    @Test
    fun `la station la plus proche est retenue`() {
        // Paris-centre : Orly à ~19 km, Le Bourget à ~13 km.
        assertEquals(bourget, MetarStationSelector.select(48.857, 2.352, 35.0, listOf(orly, bourget)))
    }

    @Test
    fun `une station trop haute est ignoree meme si elle est la plus proche`() {
        assertEquals(orly, MetarStationSelector.select(48.80, 2.30, 80.0, listOf(mountain, orly)))
    }

    @Test
    fun `au dela de 25 km la ville garde la reanalyse`() {
        // Orléans : ~110 km d'Orly.
        assertNull(MetarStationSelector.select(47.90, 1.90, 100.0, listOf(orly, bourget)))
    }

    @Test
    fun `l'historique n'est reconstruit que si la reference change`() {
        val city = City(id = "1", name = "Paris", country = "France", latitude = 48.857, longitude = 2.352)
        val reanalysis = CalibrationPoint.reanalysis(city)
        val atOrly = CalibrationPoint.of(city, orly)

        assertEquals(false, reanalysis.requiresRebuild(null))
        assertEquals(true, atOrly.requiresRebuild(null))
        assertEquals(false, atOrly.requiresRebuild("metar:LFPO"))
        assertEquals(true, atOrly.requiresRebuild("metar:LFPB"))
        assertEquals(true, reanalysis.requiresRebuild("metar:LFPO"))
    }
}
