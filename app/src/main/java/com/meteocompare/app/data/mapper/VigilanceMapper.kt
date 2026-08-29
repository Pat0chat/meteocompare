package com.meteocompare.app.data.mapper

import com.meteocompare.app.data.remote.dto.VigilanceResponseDto
import com.meteocompare.app.domain.model.VigilanceColor
import com.meteocompare.app.domain.model.VigilanceForecast
import com.meteocompare.app.domain.model.VigilanceInterval
import com.meteocompare.app.domain.model.VigilancePeriod
import com.meteocompare.app.domain.model.VigilancePhenomenon
import com.meteocompare.app.domain.model.VigilancePhenomenonAlert
import com.meteocompare.app.domain.model.VigilanceScope
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

fun VigilanceResponseDto.toDomain(
    fetchedAt: Instant,
    stale: Boolean = false
): VigilanceForecast? {
    if (!configured || unavailable || department.isBlank()) return null

    return VigilanceForecast(
        source = source,
        department = department,
        includeCoast = includeCoast,
        updateTime = parseVigilanceInstant(updateTime),
        productDatetime = parseVigilanceInstant(productDatetime),
        generationTimestamp = parseVigilanceInstant(generationTimestamp),
        periods = periods.mapNotNull periodLoop@{ period ->
            val maxColor = VigilanceColor.fromId(period.maxColorId) ?: return@periodLoop null
            VigilancePeriod(
                term = period.term,
                begin = parseVigilanceInstant(period.beginTime),
                end = parseVigilanceInstant(period.endTime),
                maxColor = maxColor,
                departmentMaxColor = VigilanceColor.fromId(period.departmentMaxColorId),
                coastMaxColor = VigilanceColor.fromId(period.coastMaxColorId),
                phenomena = period.phenomena.mapNotNull phenomenonLoop@{ phenomenon ->
                    val phenomenonColor = VigilanceColor.fromId(phenomenon.maxColorId)
                        ?: return@phenomenonLoop null
                    VigilancePhenomenonAlert(
                        phenomenon = VigilancePhenomenon.fromId(phenomenon.id),
                        maxColor = phenomenonColor,
                        intervals = phenomenon.intervals.mapNotNull intervalLoop@{ interval ->
                            val color = VigilanceColor.fromId(interval.colorId)
                                ?: return@intervalLoop null
                            VigilanceInterval(
                                begin = parseVigilanceInstant(interval.beginTime),
                                end = parseVigilanceInstant(interval.endTime),
                                color = color,
                                scope = when (interval.scope.lowercase()) {
                                    "department" -> VigilanceScope.DEPARTMENT
                                    "coast", "coastal", "littoral" -> VigilanceScope.COAST
                                    else -> VigilanceScope.UNKNOWN
                                },
                                timingApproximate = interval.timingApproximate
                            )
                        }
                    )
                }
            )
        },
        fetchedAt = fetchedAt,
        isStale = stale
    )
}

/** Le Worker émet normalement un ISO avec offset ; les fallbacks rendent le client tolérant. */
internal fun parseVigilanceInstant(value: String?): Instant? {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        ?: runCatching { ZonedDateTime.parse(raw).toInstant() }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(raw).atZone(ZoneId.of("Europe/Paris")).toInstant()
        }.getOrNull()
}
