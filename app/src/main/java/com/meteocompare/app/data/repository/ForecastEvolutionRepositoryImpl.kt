package com.meteocompare.app.data.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.util.apiTimezoneOrAuto
import com.meteocompare.app.data.local.ForecastEvolutionDao
import com.meteocompare.app.data.local.ForecastEvolutionEntity
import com.meteocompare.app.data.remote.PreviousRunsApi
import com.meteocompare.app.data.remote.dto.PreviousRunsResponseDto
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ForecastEvolutionSample
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.ForecastEvolutionRepository
import com.meteocompare.app.domain.repository.PreviousForecastEvolutionData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Charge en une requête les séries `_previous_day1/2/3` de l'API Previous
 * Runs, puis ne conserve dans Room que les agrégats quotidiens utiles.
 *
 * La sémantique est volontairement "ce qui était prévu 24/48/72 h avant
 * l'échéance". Elle est comparable entre fournisseurs même lorsque leurs
 * heures d'initialisation et leurs cadences de runs diffèrent.
 */
@Singleton
class ForecastEvolutionRepositoryImpl @Inject constructor(
    private val api: PreviousRunsApi,
    private val dao: ForecastEvolutionDao,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    private val clock: Clock
) : ForecastEvolutionRepository {

    /**
     * Évite une tempête de retries lorsqu'une requête vient de réussir sans
     * données ou vient d'échouer avant qu'un cache Room n'ait pu être écrit.
     * Le cache persistant reste Room ; ce garde-fou n'a besoin de vivre que
     * pendant le processus courant.
     */
    private val recentAttempts = ConcurrentHashMap<RequestKey, Long>()

    override suspend fun getPreviousForecasts(
        city: City,
        models: List<WeatherModel>,
        startDate: LocalDate,
        endDate: LocalDate,
        forceRefresh: Boolean
    ): ApiResult<PreviousForecastEvolutionData> = withContext(io) {
        if (models.isEmpty() || endDate < startDate) {
            return@withContext ApiResult.Success(PreviousForecastEvolutionData(emptyList(), null, true))
        }

        val modelKeys = models.map(WeatherModel::name)
        val requestKey = RequestKey(
            cityId = city.id,
            modelKeys = modelKeys.sorted(),
            startEpochDay = startDate.toEpochDay(),
            endEpochDay = endDate.toEpochDay()
        )
        val cached = dao.getForWindow(
            cityId = city.id,
            modelKeys = modelKeys,
            startEpochDay = startDate.toEpochDay(),
            endEpochDay = endDate.toEpochDay()
        )
        val latestFetch = dao.latestFetchForWindow(
            cityId = city.id,
            modelKeys = modelKeys,
            startEpochDay = startDate.toEpochDay(),
            endEpochDay = endDate.toEpochDay()
        )
        val nowMs = clock.millis()

        // Les modèles régionaux n'ont pas tous assez d'horizon pour couvrir
        // chaque date et chaque offset. Un cache partiel récent est donc un
        // cache valide : exiger un rectangle modèle × date × offset complet
        // provoquerait des refreshs permanents par conception.
        if (!forceRefresh && cached.isNotEmpty() && latestFetch != null &&
            nowMs - latestFetch <= CACHE_TTL_MS
        ) {
            return@withContext ApiResult.Success(
                PreviousForecastEvolutionData(
                    samples = cached.mapNotNull(::toDomain),
                    fetchedAt = Instant.ofEpochMilli(latestFetch),
                    fromCache = true
                )
            )
        }

        val recentAttempt = recentAttempts[requestKey]
        if (!forceRefresh && recentAttempt != null &&
            nowMs - recentAttempt <= EMPTY_RETRY_COOLDOWN_MS
        ) {
            // Après un échec récent, ne martèle pas l'API : s'il existe un
            // cache ancien on le conserve, sinon on garde temporairement un
            // état vide. Un forceRefresh explicite contourne ce cooldown.
            return@withContext ApiResult.Success(
                PreviousForecastEvolutionData(
                    samples = cached.mapNotNull(::toDomain),
                    fetchedAt = latestFetch?.let(Instant::ofEpochMilli)
                        ?: Instant.ofEpochMilli(recentAttempt),
                    fromCache = true
                )
            )
        }

        recentAttempts[requestKey] = nowMs

        try {
            val fetchedAt = clock.instant()
            val freshSamples = fetchEvolutionResilient(
                city = city,
                models = models,
                startDate = startDate,
                endDate = endDate
            )
            val entities = freshSamples.map { sample -> sample.toEntity(city.id, fetchedAt) }
            dao.replaceWindow(
                cityId = city.id,
                modelKeys = modelKeys,
                startEpochDay = startDate.toEpochDay(),
                endEpochDay = endDate.toEpochDay(),
                samples = entities
            )
            dao.purgeFetchedBefore(nowMs - RETENTION_MS)
            ApiResult.Success(
                PreviousForecastEvolutionData(
                    samples = freshSamples,
                    fetchedAt = fetchedAt,
                    fromCache = false
                )
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            // Le run-to-run est secondaire : un cache même ancien vaut mieux
            // qu'une carte vide lorsque le téléphone est hors ligne.
            if (cached.isNotEmpty()) {
                ApiResult.Success(
                    PreviousForecastEvolutionData(
                        samples = cached.mapNotNull(::toDomain),
                        fetchedAt = latestFetch?.let(Instant::ofEpochMilli),
                        fromCache = true
                    )
                )
            } else {
                ApiResult.Error(t, t.message ?: "Previous Runs unavailable")
            }
        }
    }

    /**
     * Previous Runs sait suffixer les variables par modèle dans une réponse
     * batchée. Si un fournisseur rend malgré tout le lot invalide, on isole le
     * sous-groupe fautif sans perdre les autres modèles.
     */
    private suspend fun fetchEvolutionResilient(
        city: City,
        models: List<WeatherModel>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ForecastEvolutionSample> {
        if (models.isEmpty()) return emptyList()
        return try {
            val response = api.getForecastEvolution(
                latitude = city.latitude,
                longitude = city.longitude,
                models = models.joinToString(",", transform = WeatherModel::apiKey),
                timezone = apiTimezoneOrAuto(city.timezone),
                startDate = startDate.format(ISO_DATE),
                endDate = endDate.format(ISO_DATE)
            )
            parseResponse(response, models, startDate, endDate)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t

            // Un 4xx peut être propre à un modèle / domaine indisponible : on
            // isole alors le fautif. Les erreurs réseau et 5xx doivent au
            // contraire remonter pour permettre au caller de conserver le
            // cache Room existant au lieu de l'écraser par une liste vide.
            val modelSpecificFailure = t is HttpException && t.code() in 400..499
            if (!modelSpecificFailure) throw t
            if (models.size == 1) return emptyList()

            val half = models.size / 2
            coroutineScope {
                listOf(models.take(half), models.drop(half)).map { group ->
                    async { fetchEvolutionResilient(city, group, startDate, endDate) }
                }.awaitAll().flatten()
            }
        }
    }

    private fun parseResponse(
        response: PreviousRunsResponseDto,
        models: List<WeatherModel>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ForecastEvolutionSample> {
        val hourly = response.hourly ?: return emptyList()
        val timeline = hourly[KEY_TIME]
            .asStringList()
            .mapIndexedNotNull { index, raw ->
                val date = parseDate(raw) ?: return@mapIndexedNotNull null
                if (date < startDate || date > endDate) return@mapIndexedNotNull null
                TimelineEntry(index, date)
            }
        if (timeline.isEmpty()) return emptyList()

        val expectedHoursByDate = timeline.groupingBy(TimelineEntry::date).eachCount()
        val singleModelMode = models.size == 1

        return buildList {
            for (model in models) {
                for (daysAgo in 1..HISTORY_DAYS) {
                    val temperature = hourly.lookupSeries(
                        baseKey = "temperature_2m",
                        model = model,
                        daysAgo = daysAgo,
                        singleModelMode = singleModelMode
                    )
                    val precipitation = hourly.lookupSeries(
                        baseKey = "precipitation",
                        model = model,
                        daysAgo = daysAgo,
                        singleModelMode = singleModelMode
                    )
                    val wind = hourly.lookupSeries(
                        baseKey = "wind_speed_10m",
                        model = model,
                        daysAgo = daysAgo,
                        singleModelMode = singleModelMode
                    )

                    if (temperature.isEmpty() && precipitation.isEmpty() && wind.isEmpty()) continue

                    val accumulators = linkedMapOf<LocalDate, DailyAccumulator>()
                    for (entry in timeline) {
                        val accumulator = accumulators.getOrPut(entry.date, ::DailyAccumulator)
                        temperature.getOrNull(entry.sourceIndex)?.finiteOrNull()
                            ?.let(accumulator::addTemperature)
                        precipitation.getOrNull(entry.sourceIndex)?.nonNegativeFiniteOrNull()
                            ?.let(accumulator::addPrecipitation)
                        wind.getOrNull(entry.sourceIndex)?.nonNegativeFiniteOrNull()
                            ?.let(accumulator::addWind)
                    }

                    for ((date, values) in accumulators) {
                        val expectedHours = expectedHoursByDate[date] ?: continue
                        values.temperatureMax(expectedHours)?.let { value ->
                            add(sample(model, ForecastEvolutionVariable.TEMPERATURE, date, daysAgo, value))
                        }
                        values.precipitationSum(expectedHours)?.let { value ->
                            add(sample(model, ForecastEvolutionVariable.PRECIPITATION, date, daysAgo, value))
                        }
                        values.windMax(expectedHours)?.let { value ->
                            add(sample(model, ForecastEvolutionVariable.WIND, date, daysAgo, value))
                        }
                    }
                }
            }
        }
    }

    private fun parseDate(raw: String): LocalDate? = runCatching {
        LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate()
    }.recoverCatching {
        LocalDate.parse(raw.take(10), ISO_DATE)
    }.getOrNull()

    private fun sample(
        model: WeatherModel,
        variable: ForecastEvolutionVariable,
        date: LocalDate,
        daysAgo: Int,
        value: Double
    ) = ForecastEvolutionSample(model, variable, date, daysAgo, value)

    private fun ForecastEvolutionSample.toEntity(
        cityId: String,
        fetchedAt: Instant
    ) = ForecastEvolutionEntity(
        cityId = cityId,
        modelKey = model.name,
        variable = variable.name,
        targetDateEpochDay = targetDate.toEpochDay(),
        daysAgo = daysAgo,
        value = value,
        fetchedAtEpochMs = fetchedAt.toEpochMilli()
    )

    private fun toDomain(entity: ForecastEvolutionEntity): ForecastEvolutionSample? {
        val model = runCatching { WeatherModel.valueOf(entity.modelKey) }.getOrNull() ?: return null
        val variable = runCatching { ForecastEvolutionVariable.valueOf(entity.variable) }.getOrNull() ?: return null
        return ForecastEvolutionSample(
            model = model,
            variable = variable,
            targetDate = LocalDate.ofEpochDay(entity.targetDateEpochDay),
            daysAgo = entity.daysAgo,
            value = entity.value
        )
    }

    private data class TimelineEntry(val sourceIndex: Int, val date: LocalDate)

    private data class RequestKey(
        val cityId: String,
        val modelKeys: List<String>,
        val startEpochDay: Long,
        val endEpochDay: Long
    )

    private class DailyAccumulator {
        private var temperatureCount = 0
        private var temperatureMax: Double? = null
        private var precipitationCount = 0
        private var precipitationSum = 0.0
        private var windCount = 0
        private var windMax: Double? = null

        fun addTemperature(value: Double) {
            temperatureCount++
            temperatureMax = maxOf(temperatureMax ?: value, value)
        }

        fun addPrecipitation(value: Double) {
            precipitationCount++
            precipitationSum += value
        }

        fun addWind(value: Double) {
            windCount++
            windMax = maxOf(windMax ?: value, value)
        }

        fun temperatureMax(expectedHours: Int): Double? =
            temperatureMax.takeIf { coverageIsSufficient(temperatureCount, expectedHours) }

        fun precipitationSum(expectedHours: Int): Double? =
            precipitationSum.takeIf { coverageIsSufficient(precipitationCount, expectedHours) }

        fun windMax(expectedHours: Int): Double? =
            windMax.takeIf { coverageIsSufficient(windCount, expectedHours) }
    }

    companion object {
        private const val HISTORY_DAYS = 3
        private const val CACHE_TTL_MS = 6L * 60L * 60L * 1000L
        private const val EMPTY_RETRY_COOLDOWN_MS = 15L * 60L * 1000L
        private const val RETENTION_MS = 14L * 24L * 60L * 60L * 1000L
        private const val KEY_TIME = "time"
        private const val MIN_EXPECTED_HOURS = 18
        private const val MIN_COVERAGE_RATIO = 0.75
        private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        private fun coverageIsSufficient(validCount: Int, expectedCount: Int): Boolean =
            expectedCount > 0 &&
                validCount >= MIN_EXPECTED_HOURS &&
                validCount.toDouble() / expectedCount >= MIN_COVERAGE_RATIO
    }
}

private fun JsonObject.lookupSeries(
    baseKey: String,
    model: WeatherModel,
    daysAgo: Int,
    singleModelMode: Boolean
): List<Double?> {
    val leadKey = "${baseKey}_previous_day$daysAgo"
    val candidates = buildList {
        (listOf(model.apiKey) + model.apiKeyAliases).forEach { apiKey ->
            // Forme courante des réponses batchées.
            add("${leadKey}_${apiKey}")
            // Forme rencontrée sur certaines archives historiques.
            add("${baseKey}_${apiKey}_previous_day$daysAgo")
        }
        if (singleModelMode) add(leadKey)
    }
    return candidates.firstNotNullOfOrNull { key -> this[key]?.asNullableDoubles() }
        .orEmpty()
}

private fun JsonElement?.asStringList(): List<String> =
    (this as? JsonArray)?.map { element ->
        (element as? JsonPrimitive)
            ?.takeIf(JsonPrimitive::isString)
            ?.content
            .orEmpty()
    }.orEmpty()

private fun JsonElement.asNullableDoubles(): List<Double?> =
    (this as? JsonArray)?.map { element ->
        when (element) {
            is JsonNull -> null
            is JsonPrimitive -> element.doubleOrNull
            else -> null
        }
    }.orEmpty()

private fun Double?.finiteOrNull(): Double? = this?.takeIf(Double::isFinite)
private fun Double?.nonNegativeFiniteOrNull(): Double? =
    this?.takeIf { it.isFinite() && it >= 0.0 }
