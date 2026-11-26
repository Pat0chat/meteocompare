package com.meteocompare.app.domain.usecase

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetarDailyMaxTemperatureTest {

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("metar/$name")) { name }.readText()

    @Test
    fun `la fixture partagee donne la meme Tmax que le banc d'essai`() {
        val expected = resource("lfpo_hourly_expected.tsv").lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line ->
                val (day, value) = line.split('\t')
                LocalDate.parse(day) to value.toDouble()
            }

        val actual = MetarDailyMaxTemperature.parse(resource("lfpo_hourly.csv"), ZoneId.of("Europe/Paris"))

        assertEquals(expected, actual)
    }

    @Test
    fun `une reponse qui n'est pas un CSV METAR ne donne aucune observation`() {
        val actual = MetarDailyMaxTemperature.parse(
            "Too many requests from your IP address, slow down.",
            ZoneId.of("Europe/Paris")
        )

        assertTrue(actual.isEmpty())
    }
}
