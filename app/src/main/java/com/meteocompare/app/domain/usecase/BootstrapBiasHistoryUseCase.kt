package com.meteocompare.app.domain.usecase

import com.meteocompare.app.core.util.apiTimezoneOrAuto
import com.meteocompare.app.core.util.localDateIn
import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.core.util.validZoneOrNull
import com.meteocompare.app.data.remote.PreviousRunsApi
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ForecastPhysicalLimits
import com.meteocompare.app.domain.model.ModelBias
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.ForecastBiasRecord
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Résultat synthétique d'un bootstrap historique J+1…J+7. */
data class BiasHistoryBootstrapResult(
    val requestedDays: Int,
    val coveredDays: Int,
    val coveredModels: Int,
    val forecastRecords: Int,
    /** Compatibilité : couverture J+1, utilisée par les écrans historiques. */
    val coverageByModel: Map<WeatherModel, Map<BiasVariable, Int>> = emptyMap(),
    /** Couverture réelle par échéance. */
    val coverageByLeadDay: Map<Int, Map<WeatherModel, Map<BiasVariable, Int>>> = emptyMap()
) {
    val hasUsableData: Boolean get() = forecastRecords > 0

    fun sampleCount(model: WeatherModel, variable: BiasVariable, leadDay: Int = 1): Int =
        if (leadDay == 1) {
            coverageByModel[model]?.get(variable) ?: 0
        } else {
            coverageByLeadDay[leadDay]?.get(model)?.get(variable) ?: 0
        }

    fun forecastReadyModels(variable: BiasVariable, leadDay: Int = 1): Int =
        (if (leadDay == 1) coverageByModel else coverageByLeadDay[leadDay].orEmpty())
            .count { (_, coverage) ->
                (coverage[variable] ?: 0) >= ModelBias.MIN_SAMPLES_FOR_BIAS
            }
}

/**
 * Initialise/actualise le suivi de calibration à échéance fixe J+1…J+7.
 *
 * Open-Meteo fournit les séries `_previous_day1` … `_previous_day7`. Chaque
 * série présente est agrégée séparément ; l'absence d'un lead pour un modèle
 * ne crée aucun échantillon artificiel. Le `leadDay` est persisté et l'heure
 * d'émission est normalisée au début de `targetDate - leadDay` dans le fuseau
 * local de la ville.
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
            timezone = apiTimezoneOrAuto(city.timezone),
            startDate = startDate.format(ISO_DATE),
            // Precipitation at next day's 00:00 covers the final hour of endDate.
            // Temperature/wind observations from that extra day are ignored.
            endDate = endDate.plusDays(1).format(ISO_DATE)
        )

        val hourly = response.hourly
            ?: return@withContext BiasHistoryBootstrapResult(requestedDays, 0, 0, 0)
        val zone = validZoneOrNull(city.timezone) ?: resolveZoneOrUtc(response.timezone)
        val timeline = parseTimeline(hourly, startDate, endDate, zone)
        if (timeline.isEmpty()) {
            return@withContext BiasHistoryBootstrapResult(requestedDays, 0, 0, 0)
        }

        val expectedHoursByDate = timeline.mapNotNull { entry ->
            entry.date.takeIf { it >= startDate && it <= endDate }
        }.groupingBy { it }.eachCount()
        val expectedPrecipitationHoursByDate = timeline.mapNotNull { entry ->
            entry.precipitationDate.takeIf { it >= startDate && it <= endDate }
        }.groupingBy { it }.eachCount()
        val records = ArrayList<ForecastBiasRecord>(
            models.size * requestedDays * BiasVariable.entries.size * PreviousRunsApi.MAX_LEAD_DAY
        )
        val coveredDates = linkedSetOf<LocalDate>()
        val coveredModels = linkedSetOf<WeatherModel>()
        val coverageByLead = linkedMapOf<Int, MutableMap<WeatherModel, MutableMap<BiasVariable, Int>>>()
        val singleModelMode = models.size == 1

        for (leadDay in PreviousRunsApi.MIN_LEAD_DAY..PreviousRunsApi.MAX_LEAD_DAY) {
            for (model in models) {
                val temperature = hourly.lookupSeries(
                    baseKey = "temperature_2m",
                    model = model,
                    leadDay = leadDay,
                    singleModelMode = singleModelMode
                )
                val precipitation = hourly.lookupSeries(
                    baseKey = "precipitation",
                    model = model,
                    leadDay = leadDay,
                    singleModelMode = singleModelMode
                )
                val wind = hourly.lookupSeries(
                    baseKey = "wind_speed_10m",
                    model = model,
                    leadDay = leadDay,
                    singleModelMode = singleModelMode
                )
                if (temperature.isEmpty() && precipitation.isEmpty() && wind.isEmpty()) continue

                val accumulators = linkedMapOf<LocalDate, DailyAccumulator>()
                for (entry in timeline) {
                    if (entry.date >= startDate && entry.date <= endDate) {
                        val accumulator = accumulators.getOrPut(entry.date, ::DailyAccumulator)
                        ForecastPhysicalLimits.temperature(temperature.getOrNull(entry.sourceIndex))
                            ?.let(accumulator::addTemperature)
                        ForecastPhysicalLimits.wind(wind.getOrNull(entry.sourceIndex))
                            ?.let(accumulator::addWind)
                    }
                    // The precipitation ending at H belongs to [H-1h,H):
                    // midnight H is part of the previous local civil day.
                    if (entry.precipitationDate >= startDate && entry.precipitationDate <= endDate) {
                        val accumulator = accumulators.getOrPut(entry.precipitationDate, ::DailyAccumulator)
                        ForecastPhysicalLimits.precipitation(precipitation.getOrNull(entry.sourceIndex))
                            ?.let(accumulator::addPrecipitation)
                    }
                }

                for ((date, values) in accumulators) {
                    val expectedHours = expectedHoursByDate[date] ?: 0
                    val expectedPrecipHours = expectedPrecipitationHoursByDate[date] ?: 0
                    // A normal day has 24 hours, DST days have 23 or 25.
                    // A 23-hour partial ordinary day must NOT be called complete.
                    val civilDayHours = Duration.between(
                        date.atStartOfDay(zone).toInstant(),
                        date.plusDays(1).atStartOfDay(zone).toInstant()
                    ).toHours().toInt()
                    if (expectedHours != civilDayHours) continue
                    val issuedAt = date.minusDays(leadDay.toLong()).atStartOfDay(zone).toInstant()
                    var addedForModelDay = false

                    values.temperatureMax(expectedHours)?.let { value ->
                        records += ForecastBiasRecord(
                            city.id, model, BiasVariable.TEMPERATURE, date, issuedAt, value, leadDay
                        )
                        coverageByLead.increment(leadDay, model, BiasVariable.TEMPERATURE)
                        addedForModelDay = true
                    }
                    values.precipitationSum(expectedPrecipHours)
                        ?.takeIf { expectedPrecipHours == civilDayHours }
                        ?.let { value ->
                        records += ForecastBiasRecord(
                            city.id, model, BiasVariable.PRECIPITATION, date, issuedAt, value, leadDay
                        )
                        coverageByLead.increment(leadDay, model, BiasVariable.PRECIPITATION)
                        addedForModelDay = true
                    }
                    values.windMax(expectedHours)?.let { value ->
                        records += ForecastBiasRecord(
                            city.id, model, BiasVariable.WIND_SPEED, date, issuedAt, value, leadDay
                        )
                        coverageByLead.increment(leadDay, model, BiasVariable.WIND_SPEED)
                        addedForModelDay = true
                    }

                    if (addedForModelDay) {
                        coveredDates += date
                        coveredModels += model
                    }
                }
            }
        }

        biasRepository.recordForecasts(records)
        val immutableCoverage = coverageByLead.mapValues { (_, byModel) ->
            byModel.mapValues { (_, coverage) -> coverage.toMap() }
        }
        BiasHistoryBootstrapResult(
            requestedDays = requestedDays,
            coveredDays = coveredDates.size,
            coveredModels = coveredModels.size,
            forecastRecords = records.size,
            coverageByModel = immutableCoverage[1].orEmpty(),
            coverageByLeadDay = immutableCoverage
        )
    }

    private fun parseTimeline(
        hourly: JsonObject,
        startDate: LocalDate,
        endDate: LocalDate,
        zone: ZoneId
    ): List<TimelineEntry> = (hourly[KEY_TIME] as? JsonArray).orEmpty()
        .mapIndexedNotNull { index, element ->
            val instant = parseTime(element, zone) ?: return@mapIndexedNotNull null
            val date = instant.atZone(zone).toLocalDate()
            val precipitationDate = instant.minusSeconds(3_600L).atZone(zone).toLocalDate()
            if ((date < startDate || date > endDate) &&
                (precipitationDate < startDate || precipitationDate > endDate)) return@mapIndexedNotNull null
            TimelineEntry(index, instant, date, precipitationDate)
        }.distinctBy(TimelineEntry::instant)

    /** Production requests timeformat=unixtime; ISO local timestamps are accepted
     * for legacy fixtures but cannot disambiguate an autumn repeated hour. */
    private fun parseTime(element: JsonElement, zone: ZoneId): Instant? {
        val value = element as? JsonPrimitive ?: return null
        if (!value.isString) return value.longOrNull?.let { Instant.ofEpochSecond(it) }
        return runCatching { Instant.parse(value.content) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value.content).atZone(zone).toInstant() }.getOrNull()
    }

    private data class TimelineEntry(
        val sourceIndex: Int,
        val instant: Instant,
        val date: LocalDate,
        val precipitationDate: LocalDate
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
        internal const val DEFAULT_BOOTSTRAP_LOOKBACK_DAYS = 21
        private const val KEY_TIME = "time"
        private val COMPLETE_CIVIL_DAY_HOURS = 23..25
        private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        private fun coverageIsSufficient(validCount: Int, expectedCount: Int): Boolean =
            expectedCount in COMPLETE_CIVIL_DAY_HOURS && validCount == expectedCount
    }
}

private fun MutableMap<Int, MutableMap<WeatherModel, MutableMap<BiasVariable, Int>>>.increment(
    leadDay: Int,
    model: WeatherModel,
    variable: BiasVariable
) {
    val byModel = getOrPut(leadDay) { linkedMapOf() }
    val coverage = byModel.getOrPut(model) { linkedMapOf() }
    coverage[variable] = (coverage[variable] ?: 0) + 1
}

private fun JsonObject.lookupSeries(
    baseKey: String,
    model: WeatherModel,
    leadDay: Int,
    singleModelMode: Boolean
): List<Double?> {
    val leadKey = "${baseKey}_previous_day$leadDay"
    val candidates = buildList {
        (listOf(model.apiKey) + model.apiKeyAliases).forEach { apiKey ->
            add("${leadKey}_${apiKey}")
            add("${baseKey}_${apiKey}_previous_day$leadDay")
        }
        if (singleModelMode) add(leadKey)
    }
    return candidates.firstNotNullOfOrNull { key -> this[key]?.asNullableDoubles() }
        .orEmpty()
}

private fun JsonElement.asNullableDoubles(): List<Double?> =
    (this as? JsonArray)?.map { element ->
        when (element) {
            is JsonNull -> null
            is JsonPrimitive -> element.doubleOrNull
            else -> null
        }
    }.orEmpty()
