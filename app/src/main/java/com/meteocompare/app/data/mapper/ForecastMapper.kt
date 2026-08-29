package com.meteocompare.app.data.mapper

import com.meteocompare.app.core.util.parseOpenMeteoDate
import com.meteocompare.app.core.util.parseOpenMeteoTime
import com.meteocompare.app.core.util.parseOpenMeteoTimeline
import com.meteocompare.app.data.remote.dto.ForecastResponseDto
import com.meteocompare.app.data.remote.dto.GeocodingResultDto
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.util.FranceDepartmentResolver
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForecastMapper @Inject constructor() {

    /**
     * Transforme la réponse brute en domain model, pour le modèle [model] donné.
     *
     * Stratégie d'alignement :
     * - Si un timestamp est non parsable, on conserve une valeur null à la place
     *   plutôt que de tout décaler — la longueur des listes reste cohérente.
     * - Si une variable est absente du modèle, on remplit avec une liste de nulls
     *   de la bonne longueur, ce qui garantit `temperature.size == timestamps.size`.
     */
    fun toSeries(model: WeatherModel, dto: ForecastResponseDto): ForecastSeries {
        val tz = dto.timezone

        val hourly = dto.hourly?.let { h ->
            val times = parseOpenMeteoTimeline(h.time, tz)
            HourlyForecast(
                // On filtre les timestamps null tout en gardant l'alignement avec les valeurs
                timestamps = times.filterNotNull(),
                temperature2m = alignNonNullTimes(times, h.temperature2m).finite(),
                precipitation = alignNonNullTimes(times, h.precipitation).nonNegative(),
                windSpeed10m = alignNonNullTimes(times, h.windSpeed10m).nonNegative(),
                weatherCode = alignNonNullTimesInt(times, h.weatherCode),
                windDirection10m = alignNonNullTimesInt(times, h.windDirection10m).directions(),
                precipitationProbability = alignNonNullTimesInt(times, h.precipitationProbability).percentages(),
                cloudCover = alignNonNullTimesInt(times, h.cloudCover).percentages(),
                windGusts10m = alignNonNullTimes(times, h.windGusts10m).nonNegative()
            )
        } ?: HourlyForecast(
            timestamps = emptyList(),
            temperature2m = emptyList(),
            precipitation = emptyList(),
            windSpeed10m = emptyList(),
            weatherCode = emptyList(),
            windDirection10m = emptyList(),
            precipitationProbability = emptyList(),
            cloudCover = emptyList(),
            windGusts10m = emptyList()
        )

        val daily = dto.daily?.let { d ->
            val dates = d.time.map { parseOpenMeteoDate(it) }
            DailyForecast(
                dates = dates.filterNotNull(),
                tempMax = alignNonNullDates(dates, d.temperature2mMax).finite(),
                tempMin = alignNonNullDates(dates, d.temperature2mMin).finite(),
                precipitationSum = alignNonNullDates(dates, d.precipitationSum).nonNegative(),
                windSpeedMax = alignNonNullDates(dates, d.windSpeed10mMax).nonNegative(),
                weatherCode = alignNonNullDatesInt(dates, d.weatherCode),
                windDirection10mDominant = alignNonNullDatesInt(dates, d.windDirection10mDominant).directions(),
                precipitationProbabilityMax = alignNonNullDatesInt(dates, d.precipitationProbabilityMax).percentages(),
                windGustsMax = alignNonNullDates(dates, d.windGusts10mMax).nonNegative(),
                sunrise = alignNonNullDatesInstants(dates, d.sunrise, tz),
                sunset = alignNonNullDatesInstants(dates, d.sunset, tz)
            )
        } ?: DailyForecast(
            dates = emptyList(),
            tempMax = emptyList(),
            tempMin = emptyList(),
            precipitationSum = emptyList(),
            windSpeedMax = emptyList(),
            weatherCode = emptyList(),
            windDirection10mDominant = emptyList(),
            precipitationProbabilityMax = emptyList(),
            windGustsMax = emptyList(),
            sunrise = emptyList(),
            sunset = emptyList()
        )

        return ForecastSeries(model = model, hourly = hourly, daily = daily)
    }

    /**
     * Garde uniquement les valeurs dont le timestamp correspondant est non null.
     * Si [values] est null, retourne une liste de nulls de la même taille que les
     * timestamps valides.
     */
    private fun alignNonNullTimes(
        times: List<Instant?>,
        values: List<Double?>?
    ): List<Double?> {
        if (values == null) {
            return List(times.count { it != null }) { null }
        }
        return times.indices
            .filter { index -> times[index] != null }
            .map { index -> values.getOrNull(index) }
    }

    private fun alignNonNullDates(
        dates: List<LocalDate?>,
        values: List<Double?>?
    ): List<Double?> {
        if (values == null) {
            return List(dates.count { it != null }) { null }
        }
        return dates.indices
            .filter { index -> dates[index] != null }
            .map { index -> values.getOrNull(index) }
    }

    // Variantes Int? : les codes WMO sont des entiers, pas des doubles. On
    // duplique le squelette plutôt que d'introduire un generic <T> sur les
    // helpers — la sur-abstraction génère plus de bruit que les 2 lignes
    // qu'elle économise, et brouille l'intent à la lecture.
    private fun alignNonNullTimesInt(
        times: List<Instant?>,
        values: List<Int?>?
    ): List<Int?> {
        if (values == null) {
            return List(times.count { it != null }) { null }
        }
        return times.indices
            .filter { index -> times[index] != null }
            .map { index -> values.getOrNull(index) }
    }

    private fun alignNonNullDatesInt(
        dates: List<LocalDate?>,
        values: List<Int?>?
    ): List<Int?> {
        if (values == null) {
            return List(dates.count { it != null }) { null }
        }
        return dates.indices
            .filter { index -> dates[index] != null }
            .map { index -> values.getOrNull(index) }
    }

    /** Valeurs non finies / physiques impossibles rejetées plutôt que propagées jusqu'à l'UI. */
    private fun List<Double?>.finite(): List<Double?> =
        map { value -> value?.takeIf(Double::isFinite) }

    private fun List<Double?>.nonNegative(): List<Double?> =
        map { value -> value?.takeIf { it.isFinite() && it >= 0.0 } }

    private fun List<Int?>.percentages(): List<Int?> =
        map { value -> value?.takeIf { it in 0..100 } }

    private fun List<Int?>.directions(): List<Int?> =
        map { value -> value?.takeIf { it in 0..360 } }

    private fun alignNonNullDatesInstants(
        dates: List<LocalDate?>,
        values: List<String?>?,
        timezone: String
    ): List<Instant?> {
        if (values == null) {
            return List(dates.count { it != null }) { null }
        }
        return dates.indices
            .filter { index -> dates[index] != null }
            .map { index ->
                values.getOrNull(index)?.let { parseOpenMeteoTime(it, timezone) }
            }
    }
}

/** Mapping DTO geocoding → domain. Pas une classe car stateless / pas d'injection nécessaire. */
fun GeocodingResultDto.toDomain(): City = City(
    id = id.toString(),
    name = name,
    admin1 = admin1,
    country = country ?: "",
    latitude = latitude,
    longitude = longitude,
    timezone = timezone,
    countryCode = countryCode,
    departmentName = admin2,
    departmentCode = if (countryCode.equals("FR", ignoreCase = true) || country.equals("France", ignoreCase = true)) {
        FranceDepartmentResolver.resolve(admin2, postcodes)
    } else {
        null
    }
)
