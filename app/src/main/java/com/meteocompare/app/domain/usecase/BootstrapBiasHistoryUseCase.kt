package com.meteocompare.app.domain.usecase

import com.meteocompare.app.core.util.localDateIn
import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.data.remote.PreviousRunsApi
import com.meteocompare.app.data.remote.dto.PreviousRunsResponseDto
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ModelBias
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.ForecastBiasRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Résultat synthétique d'un bootstrap historique J+1. */
data class BiasHistoryBootstrapResult(
    val requestedDays: Int,
    val coveredDays: Int,
    val coveredModels: Int,
    val forecastRecords: Int,
    val coverageByModel: Map<WeatherModel, Map<BiasVariable, Int>> = emptyMap()
) {
    val hasUsableData: Boolean get() = forecastRecords > 0

    fun sampleCount(model: WeatherModel, variable: BiasVariable): Int =
        coverageByModel[model]?.get(variable) ?: 0

    /**
     * Nombre de modèles ayant au moins 14 prévisions valides pour la variable.
     * Les références et les dates communes sont vérifiées ensuite par Room et
     * le moteur de classement.
     */
    fun forecastReadyModels(variable: BiasVariable): Int = coverageByModel.count { (_, coverage) ->
        (coverage[variable] ?: 0) >= ModelBias.MIN_SAMPLES_FOR_BIAS
    }
}

/**
 * Initialise ou actualise le suivi de biais avec des prévisions historiques à
 * échéance fixe J+1.
 *
 * L'API Previous Runs fournit des séries horaires `_previous_day1`. Le use case
 * les agrège dans les mêmes grandeurs quotidiennes que le forecast courant :
 * température maximale, cumul des précipitations et vent maximal. Les lignes
 * produites utilisent le schéma Room unique du suivi J+1 ; la suite du
 * pipeline (références, JOIN, calcul des biais et classement) reste unique.
 */
@Singleton
class BootstrapBiasHistoryUseCase @Inject constructor(
    private val api: PreviousRunsApi,
    private val biasRepository: BiasSampleRepository,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    private val clock: Clock = Clock.systemUTC()
) {

    suspend operator fun invoke(
        city: City,
        models: List<WeatherModel>,
        today: LocalDate = clock.instant().localDateIn(city.timezone),
        requestedDays: Int = DEFAULT_BOOTSTRAP_LOOKBACK_DAYS
    ): BiasHistoryBootstrapResult = withContext(io) {
        require(requestedDays > 0) { "requestedDays must be positive" }
        if (models.isEmpty()) {
            return@withContext BiasHistoryBootstrapResult(requestedDays, 0, 0, 0)
        }

        val endDate = today.minusDays(1)
        val startDate = endDate.minusDays((requestedDays - 1).toLong())
        val response = api.getPreviousDayOne(
            latitude = city.latitude,
            longitude = city.longitude,
            models = models.joinToString(",", transform = WeatherModel::apiKey),
            timezone = city.timezone ?: "auto",
            startDate = startDate.format(ISO_DATE),
            endDate = endDate.format(ISO_DATE)
        )

        val hourly = response.hourly
            ?: return@withContext BiasHistoryBootstrapResult(requestedDays, 0, 0, 0)
        val timeline = parseTimeline(hourly, startDate, endDate)
        if (timeline.isEmpty()) {
            return@withContext BiasHistoryBootstrapResult(requestedDays, 0, 0, 0)
        }

        val expectedHoursByDate = timeline.groupingBy(TimelineEntry::date).eachCount()
        val records = ArrayList<ForecastBiasRecord>(
            models.size * requestedDays * BiasVariable.entries.size
        )
        val coveredDates = linkedSetOf<LocalDate>()
        val coveredModels = linkedSetOf<WeatherModel>()
        val coverageByModel = linkedMapOf<WeatherModel, MutableMap<BiasVariable, Int>>()
        val singleModelMode = models.size == 1
        val zone = resolveZoneOrUtc(city.timezone ?: response.timezone)

        for (model in models) {
            val temperature = hourly.lookupSeries(
                baseKey = "temperature_2m",
                model = model,
                singleModelMode = singleModelMode
            )
            val precipitation = hourly.lookupSeries(
                baseKey = "precipitation",
                model = model,
                singleModelMode = singleModelMode
            )
            val wind = hourly.lookupSeries(
                baseKey = "wind_speed_10m",
                model = model,
                singleModelMode = singleModelMode
            )

            val accumulators = linkedMapOf<LocalDate, DailyAccumulator>()
            for (entry in timeline) {
                val accumulator = accumulators.getOrPut(entry.date, ::DailyAccumulator)
                temperature.getOrNull(entry.sourceIndex)?.finiteOrNull()?.let(accumulator::addTemperature)
                precipitation.getOrNull(entry.sourceIndex)?.finiteOrNull()?.let(accumulator::addPrecipitation)
                wind.getOrNull(entry.sourceIndex)?.finiteOrNull()?.let(accumulator::addWind)
            }

            for ((date, values) in accumulators) {
                val expectedHours = expectedHoursByDate[date] ?: continue
                if (expectedHours < MIN_EXPECTED_HOURS) continue
                val issuedAt = date.minusDays(1).atStartOfDay(zone).toInstant()
                var addedForModelDay = false

                values.temperatureMax(expectedHours)?.let { value ->
                    records += ForecastBiasRecord(
                        city.id, model, BiasVariable.TEMPERATURE, date, issuedAt, value
                    )
                    coverageByModel.increment(model, BiasVariable.TEMPERATURE)
                    addedForModelDay = true
                }
                values.precipitationSum(expectedHours)?.let { value ->
                    records += ForecastBiasRecord(
                        city.id, model, BiasVariable.PRECIPITATION, date, issuedAt, value
                    )
                    coverageByModel.increment(model, BiasVariable.PRECIPITATION)
                    addedForModelDay = true
                }
                values.windMax(expectedHours)?.let { value ->
                    records += ForecastBiasRecord(
                        city.id, model, BiasVariable.WIND_SPEED, date, issuedAt, value
                    )
                    coverageByModel.increment(model, BiasVariable.WIND_SPEED)
                    addedForModelDay = true
                }

                if (addedForModelDay) {
                    coveredDates += date
                    coveredModels += model
                }
            }
        }

        biasRepository.recordForecasts(records)
        BiasHistoryBootstrapResult(
            requestedDays = requestedDays,
            coveredDays = coveredDates.size,
            coveredModels = coveredModels.size,
            forecastRecords = records.size,
            coverageByModel = coverageByModel.mapValues { (_, coverage) -> coverage.toMap() }
        )
    }

    private fun parseTimeline(
        hourly: JsonObject,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<TimelineEntry> = hourly[KEY_TIME]
        .asStringList()
        .mapIndexedNotNull { index, raw ->
            val date = parseDate(raw) ?: return@mapIndexedNotNull null
            if (date < startDate || date > endDate) return@mapIndexedNotNull null
            TimelineEntry(index, date)
        }

    private fun parseDate(raw: String): LocalDate? = runCatching {
        LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate()
    }.recoverCatching {
        LocalDate.parse(raw.take(10), ISO_DATE)
    }.getOrNull()

    private data class TimelineEntry(val sourceIndex: Int, val date: LocalDate)

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
            precipitationSum += value.coerceAtLeast(0.0)
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
        /**
         * Trois semaines sont demandées pour atteindre plus souvent le seuil
         * de 14 journées valides malgré quelques trous d'archives modèle.
         */
        internal const val DEFAULT_BOOTSTRAP_LOOKBACK_DAYS = 21

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


private fun MutableMap<WeatherModel, MutableMap<BiasVariable, Int>>.increment(
    model: WeatherModel,
    variable: BiasVariable
) {
    val coverage = getOrPut(model) { linkedMapOf() }
    coverage[variable] = (coverage[variable] ?: 0) + 1
}

private fun JsonObject.lookupSeries(
    baseKey: String,
    model: WeatherModel,
    singleModelMode: Boolean
): List<Double?> {
    val leadKey = "${baseKey}_previous_day1"
    val candidates = buildList {
        add("${leadKey}_${model.apiKey}")
        // Défense contre une éventuelle variante de suffixage côté serveur.
        add("${baseKey}_${model.apiKey}_previous_day1")
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
