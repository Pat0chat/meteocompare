package com.meteocompare.app.data.mapper

import com.meteocompare.app.core.util.parseOpenMeteoDate
import com.meteocompare.app.core.util.parseOpenMeteoTime
import com.meteocompare.app.core.util.parseOpenMeteoTimeline
import com.meteocompare.app.data.remote.dto.ForecastResponseDto
import com.meteocompare.app.data.remote.dto.GeocodingResultDto
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastPhysicalLimits
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.util.FranceDepartmentResolver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForecastMapper @Inject constructor() {

    /**
     * Transforme la réponse brute en domain model, pour le modèle [model] donné.
     *
     * Stratégie d'alignement :
     * - Un timestamp non parsable est retiré avec toutes les mesures de sa ligne,
     *   sans décaler les valeurs des lignes suivantes.
     * - Si une variable est absente du modèle, on remplit avec une liste de nulls
     *   de la bonne longueur, ce qui garantit `temperature.size == timestamps.size`.
     */
    fun toSeries(model: WeatherModel, dto: ForecastResponseDto): ForecastSeries {
        val tz = dto.timezone

        val hourly = dto.hourly?.let { h ->
            val times = parseOpenMeteoTimeline(h.time, tz)
            val validRows = times.indices.filter { times[it] != null }
            HourlyForecast(
                // On filtre les timestamps null tout en gardant l'alignement avec les valeurs
                timestamps = times.filterNotNull(),
                temperature2m = alignValues(validRows, h.temperature2m, ForecastPhysicalLimits::temperature),
                apparentTemperature = alignValues(validRows, h.apparentTemperature, ForecastPhysicalLimits::temperature),
                relativeHumidity2m = alignValues(validRows, h.relativeHumidity2m, ForecastPhysicalLimits::percentage),
                dewPoint2m = alignValues(validRows, h.dewPoint2m, ForecastPhysicalLimits::dewPoint),
                pressureMsl = alignValues(validRows, h.pressureMsl, ForecastPhysicalLimits::pressure),
                precipitation = alignValues(validRows, h.precipitation, ForecastPhysicalLimits::precipitation),
                windSpeed10m = alignValues(validRows, h.windSpeed10m, ForecastPhysicalLimits::wind),
                weatherCode = alignValues(validRows, h.weatherCode, ForecastPhysicalLimits::weatherCode),
                windDirection10m = alignValues(validRows, h.windDirection10m, ForecastPhysicalLimits::direction),
                precipitationProbability = alignValues(validRows, h.precipitationProbability, ForecastPhysicalLimits::percentage),
                cloudCover = alignValues(validRows, h.cloudCover, ForecastPhysicalLimits::percentage),
                cloudCoverLow = alignValues(validRows, h.cloudCoverLow, ForecastPhysicalLimits::percentage),
                cloudCoverMid = alignValues(validRows, h.cloudCoverMid, ForecastPhysicalLimits::percentage),
                cloudCoverHigh = alignValues(validRows, h.cloudCoverHigh, ForecastPhysicalLimits::percentage),
                sunshineDuration = alignValues(validRows, h.sunshineDuration) {
                    it?.takeIf { seconds -> seconds.isFinite() && seconds in 0.0..3600.0 }
                },
                directNormalIrradiance = alignValues(validRows, h.directNormalIrradiance) {
                    it?.takeIf { watts -> watts.isFinite() && watts in 0.0..1500.0 }
                },
                windGusts10m = alignValues(validRows, h.windGusts10m, ForecastPhysicalLimits::gust)
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
            cloudCoverLow = emptyList(),
            cloudCoverMid = emptyList(),
            cloudCoverHigh = emptyList(),
            windGusts10m = emptyList()
        )

        val daily = dto.daily?.let { d ->
            val dates = d.time.map { parseOpenMeteoDate(it) }
            val validRows = dates.indices.filter { dates[it] != null }
            val alignedMax = alignValues(validRows, d.temperature2mMax) { it }
            val alignedMin = alignValues(validRows, d.temperature2mMin) { it }
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
                precipitationSum = alignValues(validRows, d.precipitationSum, ForecastPhysicalLimits::precipitation),
                windSpeedMax = alignValues(validRows, d.windSpeed10mMax, ForecastPhysicalLimits::wind),
                weatherCode = alignValues(validRows, d.weatherCode, ForecastPhysicalLimits::weatherCode),
                windDirection10mDominant = alignValues(validRows, d.windDirection10mDominant, ForecastPhysicalLimits::direction),
                precipitationProbabilityMax = alignValues(validRows, d.precipitationProbabilityMax, ForecastPhysicalLimits::percentage),
                windGustsMax = alignValues(validRows, d.windGusts10mMax, ForecastPhysicalLimits::gust),
                sunrise = alignValues(validRows, d.sunrise) { it?.let { time -> parseOpenMeteoTime(time, tz) } },
                sunset = alignValues(validRows, d.sunset) { it?.let { time -> parseOpenMeteoTime(time, tz) } }
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

    /** Reuse the same source rows for every column; validate while copying.
     * Missing or shorter columns retain null placeholders, never shifted values.
     */
    private inline fun <T, R> alignValues(
        validRows: List<Int>,
        values: List<T?>?,
        transform: (T?) -> R?
    ): List<R?> = if (values == null) {
        List(validRows.size) { null }
    } else {
        validRows.map { index -> transform(values.getOrNull(index)) }
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
    admin3 = admin3,
    admin4 = admin4,
    postalCode = postcodes.firstOrNull { it.isNotBlank() },
    departmentCode = if (countryCode.equals("FR", ignoreCase = true) || country.equals("France", ignoreCase = true)) {
        FranceDepartmentResolver.resolve(admin2, postcodes)
    } else {
        null
    }
)
