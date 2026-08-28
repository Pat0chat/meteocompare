package com.meteocompare.app.data.repository

import com.meteocompare.app.data.local.BiasSampleDao
import com.meteocompare.app.data.local.ForecastSampleEntity
import com.meteocompare.app.data.local.ObservationSampleEntity
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.ForecastBiasRecord
import com.meteocompare.app.domain.repository.ObservationBiasRecord
import com.meteocompare.app.domain.usecase.selectPreviousDaySamples
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation Room de [BiasSampleRepository].
 *
 * Rôle : traduire entre l'API domain (types Java time, WeatherModel/BiasVariable
 * enums) et l'API Room (primitives Long, String). Aucune logique métier ici —
 * la sémantique du biais est dans [ComputeBiasUseCase], le fetch est ailleurs.
 *
 * ## Choix implémentation
 *
 * **Reads sans withContext explicite** : Room retourne déjà un [Flow] qui
 * émet sur le dispatcher I/O interne à Room. On chaîne juste un `.map` de
 * conversion, qui est trivial et peut vivre sur n'importe quel thread —
 * pas besoin de `flowOn(io)`.
 *
 * **Writes avec withContext(io)** : les `@Insert` suspend de Room dépendent
 * du dispatcher courant. On force le dispatcher I/O partagé du projet (via
 * `@IoDispatcher`) pour éviter qu'un appelant qui aurait oublié n'exécute
 * l'insertion sur Main.
 *
 * **Fenêtre d'observation explicite** : l'appelant fournit la date civile
 * `asOf` de la ville. Room et le calcul statistique travaillent ainsi sur la
 * même fenêtre, sans dépendre du fuseau du terminal.
 */
@Singleton
class BiasSampleRepositoryImpl @Inject constructor(
    private val dao: BiasSampleDao,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    private val clock: Clock
) : BiasSampleRepository {

    override fun observeSamples(
        cityId: String,
        model: WeatherModel,
        variable: BiasVariable,
        asOf: LocalDate,
        timezone: String?,
        windowDays: Int
    ): Flow<List<BiasSample>> {
        require(windowDays > 0) { "windowDays must be positive, got $windowDays" }
        val end = asOf.toEpochDay()
        val start = end - windowDays
        return dao.observeJoinedSamples(
            cityId = cityId,
            modelKey = model.name,
            variable = variable.name,
            startEpochDay = start,
            endEpochDay = end
        ).map { rows ->
            rows.map { row ->
                BiasSample(
                    targetDate = LocalDate.ofEpochDay(row.targetDateEpochDay),
                    forecast = row.forecast,
                    observation = row.observation,
                    issuedAt = Instant.ofEpochMilli(row.issuedAtEpochMs)
                )
            }.let { samples ->
                if (timezone == null) samples else selectPreviousDaySamples(samples, timezone)
            }
        }
    }

    override suspend fun recordForecast(
        cityId: String,
        model: WeatherModel,
        variable: BiasVariable,
        targetDate: LocalDate,
        issuedAt: Instant,
        value: Double
    ) = withContext(io) {
        dao.insertForecast(
            ForecastSampleEntity(
                cityId = cityId,
                modelKey = model.name,
                variable = variable.name,
                targetDateEpochDay = targetDate.toEpochDay(),
                issuedAtEpochMs = issuedAt.toEpochMilli(),
                value = value,
                sourceApiKey = model.apiKey,
                resolutionKm = model.resolutionKm
            )
        )
    }

    override suspend fun recordForecasts(records: List<ForecastBiasRecord>) = withContext(io) {
        if (records.isEmpty()) return@withContext
        dao.insertForecasts(records.map { record ->
            ForecastSampleEntity(
                cityId = record.cityId,
                modelKey = record.model.name,
                variable = record.variable.name,
                targetDateEpochDay = record.targetDate.toEpochDay(),
                issuedAtEpochMs = record.issuedAt.toEpochMilli(),
                value = record.value,
                sourceApiKey = record.model.apiKey,
                resolutionKm = record.model.resolutionKm
            )
        })
    }

    override suspend fun recordObservation(
        cityId: String,
        variable: BiasVariable,
        targetDate: LocalDate,
        value: Double
    ) = withContext(io) {
        dao.insertObservation(
            ObservationSampleEntity(
                cityId = cityId,
                variable = variable.name,
                targetDateEpochDay = targetDate.toEpochDay(),
                value = value,
                fetchedAtEpochMs = clock.instant().toEpochMilli()
            )
        )
    }

    override suspend fun recordObservations(records: List<ObservationBiasRecord>) = withContext(io) {
        if (records.isEmpty()) return@withContext
        dao.insertObservations(records.map { record ->
            ObservationSampleEntity(
                cityId = record.cityId,
                variable = record.variable.name,
                targetDateEpochDay = record.targetDate.toEpochDay(),
                value = record.value,
                fetchedAtEpochMs = record.fetchedAt.toEpochMilli()
            )
        })
    }

    override suspend fun earliestMissingReferenceDate(
        cityId: String,
        upToDate: LocalDate
    ): LocalDate? = withContext(io) {
        dao.getEarliestMissingReferenceEpochDay(
            cityId = cityId,
            upToEpochDay = upToDate.toEpochDay()
        )?.let(LocalDate::ofEpochDay)
    }

    override suspend fun purgeOlderThan(beforeDate: LocalDate) = withContext(io) {
        val epochDay = beforeDate.toEpochDay()
        dao.purgeForecastsBefore(epochDay)
        dao.purgeObservationsBefore(epochDay)
    }
}
