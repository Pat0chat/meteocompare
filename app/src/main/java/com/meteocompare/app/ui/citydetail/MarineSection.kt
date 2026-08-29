package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.MarineForecast
import com.meteocompare.app.domain.model.VigilancePhenomenonAlert
import com.meteocompare.app.domain.model.TideEvent
import com.meteocompare.app.domain.model.TideEventType
import com.meteocompare.app.domain.util.detectTideEvents
import com.meteocompare.app.domain.util.nearestMarineIndex
import com.meteocompare.app.domain.util.tideRangeNext24h
import com.meteocompare.app.ui.components.CollapsibleSectionHeader
import com.meteocompare.app.ui.components.MarineCoastalVigilanceBanner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.max

internal const val TAG_MARINE_CURRENT_PANEL = "marine-current-panel"
internal const val TAG_MARINE_WAVE_CHART = "marine-wave-chart"
internal const val TAG_MARINE_TIDE_PANEL = "marine-tide-panel"
internal const val TAG_MARINE_WAVE_AXES = "marine-wave-axes"
internal const val TAG_MARINE_TIDE_AXES = "marine-tide-axes"

/**
 * La partie marine suit volontairement le même contrat visuel que les autres
 * sections de CityDetail : conteneur 20 dp, Surface tonale légère, groupes en
 * 14 dp, accents portés par une petite icône plutôt que par des bordures ou des
 * cartes "hero" spécifiques.
 */
private val MarineSectionShape = RoundedCornerShape(20.dp)
private val MarineContentShape = RoundedCornerShape(14.dp)

@Composable
internal fun MarineSection(
    state: MarineUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {},
    coastalVigilance: VigilancePhenomenonAlert? = null,
    vigilanceTimezone: String? = null
) {
    val loaded = state as? MarineUiState.Loaded
    val distance = loaded?.data?.grid?.distanceKm

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MarineSectionShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            CollapsibleSectionHeader(
                text = stringResource(R.string.marine_title),
                subtitle = if (distance != null) {
                    stringResource(R.string.marine_distance, distance)
                } else {
                    stringResource(R.string.marine_intro)
                },
                expanded = expanded,
                onToggle = { onExpandedChange(!expanded) },
                trailingContent = {
                    if (loaded?.isRefreshing == true || state is MarineUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            )

            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    when (state) {
                        MarineUiState.Idle, MarineUiState.Loading -> Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }

                        is MarineUiState.Error -> {
                            MarineInlineNotice(
                                primary = state.message,
                                secondary = null,
                                accent = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = onRefresh) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }

                        is MarineUiState.Loaded -> MarineDashboard(
                            data = state.data,
                            coastalVigilance = coastalVigilance,
                            vigilanceTimezone = vigilanceTimezone
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarineDashboard(
    data: MarineForecast,
    coastalVigilance: VigilancePhenomenonAlert?,
    vigilanceTimezone: String?
) {
    val locale = LocalLocale.current.platformLocale
    val now by produceState(System.currentTimeMillis(), data) {
        while (true) {
            val current = System.currentTimeMillis()
            value = current
            delay(60_000L - (current % 60_000L).coerceAtMost(59_999L))
        }
    }
    val index = remember(data, now / 60_000L) { data.nearestMarineIndex(now) }
    val h = data.hourly
    fun value(values: List<Double?>): Double? = values.getOrNull(index)

    val marinePalette = marinePalette()
    val currentAccent = marinePalette.current
    val waveAccent = marinePalette.waves
    val tideAccent = marinePalette.tides

    coastalVigilance?.let { alert ->
        MarineCoastalVigilanceBanner(
            alert = alert,
            timezone = vigilanceTimezone ?: data.timezone
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MarineGroupHeader(
            title = stringResource(R.string.marine_current_conditions),
            trailing = stringResource(R.string.marine_intro),
            accent = currentAccent,
            kind = MarineGroupKind.CURRENT
        )
        MarineCurrentSummary(
            waveHeight = formatMarine(value(h.waveHeight), "m", locale = locale),
            wavePeriod = formatMarine(value(h.wavePeriod), "s", locale = locale),
            waveDirection = value(h.waveDirection)?.let { "${it.toInt()}° ${compass(it)}" } ?: "—",
            swellHeight = formatMarine(value(h.swellHeight), "m", locale = locale),
            seaTemperature = formatMarine(value(h.seaSurfaceTemperature), "°C", locale = locale),
            accent = currentAccent,
            modifier = Modifier.testTag(TAG_MARINE_CURRENT_PANEL)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MarineGroupHeader(
            title = stringResource(R.string.marine_wave_evolution),
            trailing = stringResource(R.string.marine_next_48h),
            accent = waveAccent,
            kind = MarineGroupKind.WAVES
        )
        MarineChartSurface(modifier = Modifier.testTag(TAG_MARINE_WAVE_CHART)) {
            MarineLineChart(
                epochs = h.timestampEpochMs,
                values = h.waveHeight,
                nowEpochMs = now,
                hours = 48,
                accent = waveAccent,
                yUnit = "m",
                yDigits = 1,
                timezone = data.timezone,
                allowNegative = false,
                axisTag = TAG_MARINE_WAVE_AXES,
                modifier = Modifier.fillMaxWidth().height(208.dp)
            )
        }
        Text(
            text = stringResource(R.string.marine_daily_outlook),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            data.daily.dates.take(7).forEachIndexed { i, date ->
                MarineDayCard(data, date, i, waveAccent)
            }
        }
    }

    MarineGroupDivider()

    val tideEvents = remember(data, now / 60_000L) {
        data.detectTideEvents(hours = 72, nowEpochMs = now).take(6)
    }
    val tideRange = remember(data, now / 60_000L) { data.tideRangeNext24h(now) }
    val currentLevel = value(h.seaLevelHeightMsl)
    val nextLevel = h.seaLevelHeightMsl.getOrNull(index + 1)
    val trend = when {
        currentLevel == null || nextLevel == null -> stringResource(R.string.marine_unavailable)
        nextLevel - currentLevel > 0.015 -> stringResource(R.string.marine_trend_rising)
        currentLevel - nextLevel > 0.015 -> stringResource(R.string.marine_trend_falling)
        else -> stringResource(R.string.marine_trend_steady)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MarineGroupHeader(
            title = stringResource(R.string.marine_tides),
            trailing = stringResource(R.string.marine_tides_intro),
            accent = tideAccent,
            kind = MarineGroupKind.TIDES
        )
        MarineChartSurface {
            MarineLineChart(
                epochs = h.timestampEpochMs,
                values = h.seaLevelHeightMsl,
                nowEpochMs = now,
                hours = 72,
                events = tideEvents,
                accent = tideAccent,
                yUnit = "m",
                yDigits = 2,
                timezone = data.timezone,
                allowNegative = true,
                axisTag = TAG_MARINE_TIDE_AXES,
                modifier = Modifier.fillMaxWidth().height(226.dp)
            )
        }

        MarineTideSummary(
            level = formatMarine(currentLevel, "m", digits = 2, locale = locale),
            trend = trend,
            nextExtremum = tideEvents.firstOrNull()?.let {
                stringResource(
                    if (it.type == TideEventType.HIGH) R.string.marine_high_water
                    else R.string.marine_low_water
                )
            } ?: "—",
            nextExtremumDetail = tideEvents.firstOrNull()?.let {
                "${eventTime(it)} · ${formatMarine(it.value, "m", 2, locale)}"
            } ?: stringResource(R.string.marine_tide_unavailable),
            tideRange = tideRange?.let { formatMarine(it.range, "m", 2, locale) } ?: "—",
            accent = tideAccent,
            modifier = Modifier.testTag(TAG_MARINE_TIDE_PANEL)
        )

        if (tideEvents.isNotEmpty()) {
            Surface(
                shape = MarineContentShape,
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.52f)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)) {
                    tideEvents.take(4).forEachIndexed { eventIndex, event ->
                        if (eventIndex > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
                            )
                        }
                        TideRow(event, tideAccent)
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.marine_tide_datum_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    MarineGroupDivider()

    MarineInlineNotice(
        primary = stringResource(R.string.marine_disclaimer),
        secondary = stringResource(R.string.marine_source),
        accent = MaterialTheme.colorScheme.error
    )
}

private data class MarinePalette(
    val current: Color,
    val waves: Color,
    val tides: Color
)

@Composable
private fun marinePalette(): MarinePalette {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) {
        MarinePalette(
            current = Color(0xFF59E0D0),
            waves = Color(0xFF70B7FF),
            tides = Color(0xFFB0A7FF)
        )
    } else {
        MarinePalette(
            current = Color(0xFF009E91),
            waves = Color(0xFF147FE3),
            tides = Color(0xFF6658D3)
        )
    }
}

private enum class MarineGroupKind { CURRENT, WAVES, TIDES }

@Composable
private fun MarineGroupHeader(
    title: String,
    trailing: String?,
    accent: Color,
    kind: MarineGroupKind
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (kind) {
                MarineGroupKind.CURRENT -> Icons.Outlined.Explore
                MarineGroupKind.WAVES -> Icons.Outlined.Waves
                MarineGroupKind.TIDES -> Icons.Outlined.SwapVert
            },
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        trailing?.let {
            Spacer(Modifier.width(10.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun MarineGroupDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
    )
}

@Composable
private fun MarineCurrentSummary(
    waveHeight: String,
    wavePeriod: String,
    waveDirection: String,
    swellHeight: String,
    seaTemperature: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MarineContentShape,
        color = accent.copy(alpha = 0.105f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.marine_wave_height),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = waveHeight,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = waveDirection,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                MarineMetricCell(
                    label = stringResource(R.string.marine_wave_period),
                    value = wavePeriod,
                    modifier = Modifier.weight(1f)
                )
                MarineMetricCell(
                    label = stringResource(R.string.marine_swell_height),
                    value = swellHeight,
                    modifier = Modifier.weight(1f)
                )
                MarineMetricCell(
                    label = stringResource(R.string.marine_sea_temp),
                    value = seaTemperature,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MarineMetricCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(end = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MarineChartSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MarineContentShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.52f)
    ) {
        Box(Modifier.fillMaxWidth().padding(8.dp)) { content() }
    }
}

@Composable
private fun MarineDayCard(data: MarineForecast, date: String, index: Int, accent: Color) {
    val locale = LocalLocale.current.platformLocale
    val dayLabel = runCatching {
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    }.getOrDefault(date)
    Surface(
        modifier = Modifier.width(116.dp),
        shape = MarineContentShape,
        color = accent.copy(alpha = 0.105f)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                dayLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                formatMarine(data.daily.waveHeightMax.getOrNull(index), "m", locale = locale),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${formatMarine(data.daily.wavePeriodMax.getOrNull(index), "s", locale = locale)} · " +
                    formatMarine(data.daily.swellHeightMax.getOrNull(index), "m", locale = locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val direction = data.daily.waveDirectionDominant.getOrNull(index)
            Text(
                direction?.let { "${it.toInt()}° ${compass(it)}" } ?: "—",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MarineTideSummary(
    level: String,
    trend: String,
    nextExtremum: String,
    nextExtremumDetail: String,
    tideRange: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MarineContentShape,
        color = accent.copy(alpha = 0.105f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.marine_current_level),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(level, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.marine_reference_msl),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = trend,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.marine_next_extremum),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        nextExtremum,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        nextExtremumDetail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.marine_tide_range_short),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        tideRange,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.marine_next_24h),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TideRow(event: TideEvent, accent: Color) {
    val locale = LocalLocale.current.platformLocale
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (event.type == TideEventType.HIGH) "↥" else "↧",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    if (event.type == TideEventType.HIGH) R.string.marine_high_water
                    else R.string.marine_low_water
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                eventDateTime(event),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            formatMarine(event.value, "m", 2, locale),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MarineInlineNotice(primary: String, secondary: String?, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .background(accent.copy(alpha = 0.82f), RoundedCornerShape(99.dp))
        )
        Spacer(Modifier.width(9.dp))
        Column {
            Text(
                text = primary,
                style = MaterialTheme.typography.bodySmall,
                color = if (secondary == null) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (secondary == null) FontWeight.Medium else FontWeight.Normal
            )
            secondary?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun eventDateTime(event: TideEvent): String {
    val locale = LocalLocale.current.platformLocale
    return runCatching {
        LocalDateTime.parse(event.timestamp)
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
    }.getOrDefault(event.timestamp)
}

private fun eventTime(event: TideEvent): String = runCatching {
    LocalDateTime.parse(event.timestamp).format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault(event.timestamp.takeLast(5))

@Composable
private fun MarineLineChart(
    epochs: List<Long?>,
    values: List<Double?>,
    nowEpochMs: Long,
    hours: Int,
    accent: Color,
    yUnit: String,
    yDigits: Int,
    timezone: String,
    allowNegative: Boolean,
    axisTag: String,
    modifier: Modifier = Modifier,
    events: List<TideEvent> = emptyList()
) {
    val locale = LocalLocale.current.platformLocale
    val points = remember(epochs, values, nowEpochMs / 60_000L, hours) {
        buildList<Pair<Long, Double>> {
            val end = nowEpochMs + hours * 3_600_000L
            for (i in 0 until minOf(epochs.size, values.size)) {
                val epoch = epochs[i] ?: continue
                val value = values[i] ?: continue
                if (value.isFinite() && epoch >= nowEpochMs - 3_600_000L && epoch <= end) add(epoch to value)
            }
        }
    }
    if (points.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.marine_chart_unavailable),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val rawMinY = points.minOf { it.second }
    val rawMaxY = points.maxOf { it.second }
    val yPadding = max((rawMaxY - rawMinY) * 0.10, if (yDigits >= 2) 0.03 else 0.05)
    val minY = if (allowNegative) rawMinY - yPadding else max(0.0, rawMinY - yPadding)
    val maxY = rawMaxY + yPadding
    val minX = points.first().first.toDouble()
    val maxX = points.last().first.toDouble()
    val yTicks = remember(minY, maxY) {
        listOf(maxY, (maxY + minY) / 2.0, minY)
    }
    val xTicks = remember(minX, maxX) {
        listOf(0.0, 1.0 / 3.0, 2.0 / 3.0, 1.0).map { fraction ->
            (minX + (maxX - minX) * fraction).toLong()
        }
    }
    val timeFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE\nHH:mm", locale) }
    val chartZone = remember(timezone) { runCatching { java.time.ZoneId.of(timezone) }.getOrElse { java.time.ZoneId.systemDefault() } }

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
    val nowColor = accent.copy(alpha = 0.42f)
    val highColor = MaterialTheme.colorScheme.tertiary
    val lowColor = MaterialTheme.colorScheme.secondary

    Column(modifier = modifier.testTag(axisTag)) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier.width(48.dp).fillMaxHeight().padding(vertical = 3.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                yTicks.forEach { tick ->
                    Text(
                        text = formatAxisValue(tick, yUnit, yDigits, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.End
                    )
                }
            }
            Spacer(Modifier.width(7.dp))
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val left = 1.dp.toPx()
                val right = size.width - 1.dp.toPx()
                val top = 5.dp.toPx()
                val bottom = size.height - 5.dp.toPx()

                repeat(3) { i ->
                    val y = top + (bottom - top) * i / 2f
                    drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                }
                listOf(0f, 1f / 3f, 2f / 3f, 1f).forEach { fraction ->
                    val x = left + (right - left) * fraction
                    drawLine(
                        gridColor.copy(alpha = 0.55f),
                        Offset(x, top),
                        Offset(x, bottom),
                        strokeWidth = 0.75.dp.toPx()
                    )
                }

                fun x(epoch: Long): Float = if (maxX == minX) {
                    left
                } else {
                    (left + (epoch - minX) / (maxX - minX) * (right - left)).toFloat()
                }
                fun y(value: Double): Float = if (maxY == minY) {
                    (top + bottom) / 2
                } else {
                    (bottom - (value - minY) / (maxY - minY) * (bottom - top)).toFloat()
                }

                if (nowEpochMs.toDouble() in minX..maxX) {
                    val nowX = x(nowEpochMs)
                    drawLine(nowColor, Offset(nowX, top), Offset(nowX, bottom), strokeWidth = 1.5.dp.toPx())
                    drawCircle(accent, radius = 2.6.dp.toPx(), center = Offset(nowX, bottom))
                }

                val linePath = Path()
                val fillPath = Path()
                points.forEachIndexed { i, (epoch, value) ->
                    val px = x(epoch)
                    val py = y(value)
                    if (i == 0) {
                        linePath.moveTo(px, py)
                        fillPath.moveTo(px, py)
                    } else {
                        linePath.lineTo(px, py)
                        fillPath.lineTo(px, py)
                    }
                }
                fillPath.lineTo(x(points.last().first), bottom)
                fillPath.lineTo(x(points.first().first), bottom)
                fillPath.close()

                drawPath(fillPath, color = accent.copy(alpha = 0.13f))
                drawPath(linePath, color = accent, style = Stroke(width = 2.4.dp.toPx()))

                events.forEach { event ->
                    if (event.epochMs.toDouble() in minX..maxX && event.value in minY..maxY) {
                        val eventColor = if (event.type == TideEventType.HIGH) highColor else lowColor
                        val center = Offset(x(event.epochMs), y(event.value))
                        drawCircle(color = eventColor.copy(alpha = 0.18f), radius = 7.dp.toPx(), center = center)
                        drawCircle(color = eventColor, radius = 3.6.dp.toPx(), center = center)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 55.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            xTicks.forEach { epoch ->
                val label = Instant.ofEpochMilli(epoch)
                    .atZone(chartZone)
                    .toLocalDateTime()
                    .format(timeFormatter)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.labelSmall.lineHeight
                )
            }
        }
    }
}

private fun formatAxisValue(value: Double, unit: String, digits: Int, locale: Locale): String =
    String.format(locale, "%.${digits}f", value) + " $unit"

private fun formatMarine(value: Double?, unit: String, digits: Int = 1, locale: Locale): String =
    value?.takeIf { it.isFinite() }
        ?.let { String.format(locale, "%.${digits}f", it) + " $unit" }
        ?: "—"

private fun compass(degrees: Double): String {
    val labels = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val normalized = ((degrees % 360) + 360) % 360
    return labels[((normalized + 22.5) / 45.0).toInt() % 8]
}
