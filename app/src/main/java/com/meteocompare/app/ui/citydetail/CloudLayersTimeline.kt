package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.ForecastConsensus
import java.time.Instant
import kotlin.math.roundToInt

/** One physical forecast hour. Cloud layers are independent sky fractions, NOT additive. */
internal data class CloudLayerModelReading(
    val model: WeatherModel,
    val total: Int?,
    val low: Int?,
    val mid: Int?,
    val high: Int?,
    /** Duration forecast for [hour, hour + 1h), labelled by the API at hour + 1h. */
    val sunshineMinutes: Int?,
    /** Hour-mean DNI for the same preceding hour. */
    val directNormalWm2: Int?
) {
    val hasCloudData: Boolean get() = listOf(total, low, mid, high).any { it != null }
}

internal data class CloudLayerHour(
    val hour: Instant,
    val total: Int?,
    val low: Int?,
    val mid: Int?,
    val high: Int?,
    val readings: List<CloudLayerModelReading>
) {
    val hasCloudData: Boolean get() = listOf(total, low, mid, high).any { it != null }
}

/**
 * Hour-aligned family-balanced medians, each layer aggregated separately. A missing layer
 * remains missing; an opaque or high cloud must never be inferred from total-cloud cover.
 * There is no summation of percentages (layers overlap along the same sight line).
 */
internal fun buildCloudLayersTimeline(
    forecast: CityForecast,
    firstHour: Instant,
    count: Int = 9
): List<CloudLayerHour> {
    if (count !in 1..24) return emptyList()
    val hours = List(count) { offset -> firstHour.plusSeconds(offset * 3600L) }
    val output = hours.map { hour ->
        val modelRows = forecast.seriesByModel.mapNotNull { (model, series) ->
            // A merged cache may contain duplicate timestamps. Prefer the most
            // complete valid reading; never let an earlier null hide a later value.
            val hourly = series.hourly
            val index = hourly.timestamps.indices.filter { hourly.timestamps[it] == hour }
                .maxWithOrNull(compareBy<Int> { i ->
                    listOf(hourly.cloudCover.getOrNull(i), hourly.cloudCoverLow.getOrNull(i),
                        hourly.cloudCoverMid.getOrNull(i), hourly.cloudCoverHigh.getOrNull(i))
                        .count { it in 0..100 }
                }.thenBy { -it }) ?: return@mapNotNull null
            val next = hourly.timestamps.indices
                .filter { hourly.timestamps[it] == hour.plusSeconds(3600L) }
                .maxWithOrNull(compareBy<Int> { i ->
                    (if (hourly.sunshineDuration.getOrNull(i)?.let { it.isFinite() && it in 0.0..3600.0 } == true) 2 else 0) +
                        (if (hourly.directNormalIrradiance.getOrNull(i)?.let { it.isFinite() && it in 0.0..1500.0 } == true) 1 else 0)
                }.thenBy { -it })
            val sunshine = next?.let { series.hourly.sunshineDuration.getOrNull(it) }
                ?.takeIf { it.isFinite() && it in 0.0..3600.0 }
                ?.div(60.0)?.roundToInt()
            val dni = next?.let { series.hourly.directNormalIrradiance.getOrNull(it) }
                ?.takeIf { it.isFinite() && it in 0.0..1500.0 }
                ?.roundToInt()
            CloudLayerModelReading(
                model = model,
                total = series.hourly.cloudCover.getOrNull(index).validCloudPercentage(),
                low = series.hourly.cloudCoverLow.getOrNull(index).validCloudPercentage(),
                mid = series.hourly.cloudCoverMid.getOrNull(index).validCloudPercentage(),
                high = series.hourly.cloudCoverHigh.getOrNull(index).validCloudPercentage(),
                sunshineMinutes = sunshine,
                directNormalWm2 = dni
            )
        }.sortedBy { it.model.displayName }
        fun medianFor(extract: (CloudLayerModelReading) -> Int?): Int? {
            val values = modelRows.mapNotNull { row ->
                extract(row)?.let { ForecastConsensus.Entry(row.model, it.toDouble()) }
            }
            return ForecastConsensus.continuous(
                values, tightStdDev = 8.0, wideStdDev = 45.0
            ).central?.roundToInt()?.coerceIn(0, 100)
        }
        CloudLayerHour(hour, medianFor { it.total }, medianFor { it.low },
            medianFor { it.mid }, medianFor { it.high }, modelRows)
    }
    return output.takeIf { result -> result.any(CloudLayerHour::hasCloudData) } ?: emptyList()
}

private fun Int?.validCloudPercentage(): Int? = this?.takeIf { it in 0..100 }
