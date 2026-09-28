package com.meteocompare.app.data.repository

import com.meteocompare.app.data.local.ForecastEvolutionEntity
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.WeatherModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WeatherNext 2 n'a pas d'archive Previous Runs : sa fiabilité locale est
 * construite à partir des instantanés enregistrés par l'application.
 */
class LocalLeadOneForecastBackfillTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val city = City(
        id = "lyon", name = "Lyon", country = "France",
        latitude = 45.76, longitude = 4.84, timezone = "Europe/Paris"
    )
    private val target = LocalDate.of(2026, 9, 28)
    private val wn2 = WeatherModel.GOOGLE_WEATHERNEXT2

    @Test
    fun `la derniere prevision capturee la veille devient un echantillon J+1`() {
        val rows = listOf(
            row(wn2, ForecastEvolutionVariable.TEMPERATURE, capturedAt = target.minusDays(1).atTime(8, 0), value = 20.0),
            row(wn2, ForecastEvolutionVariable.TEMPERATURE, capturedAt = target.minusDays(1).atTime(21, 0), value = 21.5)
        )

        val records = selectLocalLeadOneRecords(city, rows, listOf(wn2))

        assertEquals(1, records.size)
        val record = records.single()
        assertEquals(BiasVariable.TEMPERATURE, record.variable)
        assertEquals(target, record.targetDate)
        assertEquals(21.5, record.value, 0.0)
        assertEquals(1, record.leadDay)
        assertEquals(target.minusDays(1).atTime(21, 0).atZone(zone).toInstant(), record.issuedAt)
    }

    @Test
    fun `instantanes du jour meme ou de l'avant-veille ignores`() {
        val rows = listOf(
            row(wn2, ForecastEvolutionVariable.PRECIPITATION, capturedAt = target.atTime(6, 0), value = 3.0),
            row(wn2, ForecastEvolutionVariable.PRECIPITATION, capturedAt = target.minusDays(2).atTime(18, 0), value = 5.0)
        )

        assertTrue(selectLocalLeadOneRecords(city, rows, listOf(wn2)).isEmpty())
    }

    @Test
    fun `les trois metriques d'evolution alimentent les trois variables de biais`() {
        val veille = target.minusDays(1).atTime(12, 0)
        val rows = listOf(
            row(wn2, ForecastEvolutionVariable.TEMPERATURE, veille, 22.0),
            row(wn2, ForecastEvolutionVariable.PRECIPITATION, veille, 4.2),
            row(wn2, ForecastEvolutionVariable.WIND, veille, 31.0)
        )

        val variables = selectLocalLeadOneRecords(city, rows, listOf(wn2)).map { it.variable }.toSet()

        assertEquals(setOf(BiasVariable.TEMPERATURE, BiasVariable.PRECIPITATION, BiasVariable.WIND_SPEED), variables)
    }

    @Test
    fun `modeles non demandes et autre source ignores`() {
        val veille = target.minusDays(1).atTime(12, 0)
        val rows = listOf(
            row(WeatherModel.GFS, ForecastEvolutionVariable.TEMPERATURE, veille, 19.0),
            row(wn2, ForecastEvolutionVariable.TEMPERATURE, veille, 20.0, sourceApiKey = "google_weathernext_legacy")
        )

        assertTrue(selectLocalLeadOneRecords(city, rows, listOf(wn2)).isEmpty())
    }

    private fun row(
        model: WeatherModel,
        variable: ForecastEvolutionVariable,
        capturedAt: LocalDateTime,
        value: Double,
        sourceApiKey: String? = model.apiKey
    ): ForecastEvolutionEntity {
        val capturedMs = capturedAt.atZone(zone).toInstant().toEpochMilli()
        return ForecastEvolutionEntity(
            cityId = city.id,
            modelKey = model.name,
            variable = variable.name,
            targetDateEpochDay = target.toEpochDay(),
            snapshotBucket = capturedMs / ForecastEvolutionRecorder.SNAPSHOT_BUCKET_MS,
            snapshotAtEpochMs = capturedMs,
            value = value,
            sourceApiKey = sourceApiKey,
            resolutionKm = model.resolutionKm
        )
    }
}
