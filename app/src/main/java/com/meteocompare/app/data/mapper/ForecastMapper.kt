package com.meteocompare.app.data.mapper

import com.meteocompare.app.core.util.parseOpenMeteoDate
import com.meteocompare.app.core.util.parseOpenMeteoTime
import com.meteocompare.app.core.util.parseOpenMeteoTimeline
import com.meteocompare.app.data.remote.dto.ForecastResponseDto
import com.meteocompare.app.data.remote.dto.GeocodingResultDto
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.ForecastPhysicalLimits
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
                temperature2m = alignNonNullTimes(times, h.temperature2m).map(ForecastPhysicalLimits::temperature),
                precipitation = alignNonNullTimes(times, h.precipitation).map(ForecastPhysicalLimits::precipitation),
                windSpeed10m = alignNonNullTimes(times, h.windSpeed10m).map(ForecastPhysicalLimits::wind),
                weatherCode = alignNonNullTimesInt(times, h.weatherCode).map(ForecastPhysicalLimits::weatherCode),
                windDirection10m = alignNonNullTimesInt(times, h.windDirection10m).map(ForecastPhysicalLimits::direction),
                precipitationProbability = alignNonNullTimesInt(times, h.precipitationProbability).map(ForecastPhysicalLimits::percentage),
                cloudCover = alignNonNullTimesInt(times, h.cloudCover).map(ForecastPhysicalLimits::percentage),
                windGusts10m = alignNonNullTimes(times, h.windGusts10m).map(ForecastPhysicalLimits::gust)
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
            val alignedMax = alignNonNullDates(dates, d.temperature2mMax)
            val alignedMin = alignNonNullDates(dates, d.temperature2mMin)
            val safePairs = alignedMax.indices.map { index ->
                ForecastPhysicalLimits.dailyTemperaturePair(
                    max = alignedMax.getOrNull(index),
                    min = alignedMin.getOrNull(index)
                )
            }
            DailyForecast(
                dates = dates.filterNotNull(),
                tempMax = safePairs.map { it.first },
                tempMin = safePairs.map { it.second },
                precipitationSum = alignNonNullDates(dates, d.precipitationSum).map(ForecastPhysicalLimits::precipitation),
                windSpeedMax = alignNonNullDates(dates, d.windSpeed10mMax).map(ForecastPhysicalLimits::wind),
                weatherCode = alignNonNullDatesInt(dates, d.weatherCode).map(ForecastPhysicalLimits::weatherCode),
                windDirection10mDominant = alignNonNullDatesInt(dates, d.windDirection10mDominant).map(ForecastPhysicalLimits::direction),
                precipitationProbabilityMax = alignNonNullDatesInt(dates, d.precipitationProbabilityMax).map(ForecastPhysicalLimits::percentage),
                windGustsMax = alignNonNullDates(dates, d.windGusts10mMax).map(ForecastPhysicalLimits::gust),
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
