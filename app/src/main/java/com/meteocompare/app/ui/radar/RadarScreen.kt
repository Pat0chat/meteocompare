package com.meteocompare.app.ui.radar

import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.domain.radar.RADAR_PROJECTION_HORIZONS
import com.meteocompare.app.domain.radar.RadarImpactKind
import com.meteocompare.app.domain.radar.RadarImage
import com.meteocompare.app.domain.radar.RadarMode
import com.meteocompare.app.domain.radar.RadarRainCell
import com.meteocompare.app.domain.radar.RadarRange
import com.meteocompare.app.domain.radar.mapResolutionKm
import com.meteocompare.app.domain.radar.projectRainCell
import com.meteocompare.app.domain.radar.radarNowcastDisplayGeometry
import com.meteocompare.app.domain.radar.rainCellContours
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.roundToInt

internal const val TAG_RADAR_STAGE = "radar_stage"
internal const val TAG_RADAR_OBSERVATION = "radar_mode_observation"
internal const val TAG_RADAR_PROJECTION = "radar_mode_projection"
internal const val TAG_RADAR_RECALCULATE = "radar_recalculate"
internal const val RADAR_OBSERVATION_ALPHA = .80f
internal const val RADAR_PROJECTION_OBSERVATION_ALPHA = .38f
internal fun radarHorizonTag(minutes: Int) = "radar_horizon_$minutes"
internal fun radarRangeTag(range: RadarRange) = "radar_range_${range.name.lowercase()}"

private val CELL_COLORS = listOf(
    Color(0xFF0EA5E9), Color(0xFF8B5CF6), Color(0xFFF97316), Color(0xFF10B981),
    Color(0xFFE11D48), Color(0xFFEAB308), Color(0xFF14B8A6), Color(0xFF6366F1)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    onBack: () -> Unit,
    viewModel: RadarViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ready = state as? RadarUiState.Ready
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.radar_title), fontWeight = FontWeight.Bold)
                        ready?.city?.name?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    if (ready != null) {
                        IconButton(onClick = viewModel::toggleFullscreen) {
                            Icon(
                                if (ready.isFullscreen) Icons.Default.CloseFullscreen else Icons.Default.Fullscreen,
                                contentDescription = stringResource(
                                    if (ready.isFullscreen) R.string.radar_exit_fullscreen else R.string.radar_enter_fullscreen
                                )
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { padding ->
        RadarContent(
            state = state,
            onRetry = viewModel::retry,
            onModeChange = viewModel::setMode,
            onRangeChange = viewModel::setRange,
            onHorizonChange = viewModel::setHorizon,
            onFrameChange = viewModel::selectFrame,
            onPlayPause = viewModel::togglePlayback,
            onRecalculate = viewModel::recalculateProjection,
            onViewportSize = viewModel::setViewportSize,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
internal fun RadarContent(
    state: RadarUiState,
    onRetry: () -> Unit = {},
    onModeChange: (RadarMode) -> Unit = {},
    onRangeChange: (RadarRange) -> Unit = {},
    onHorizonChange: (Int) -> Unit = {},
    onFrameChange: (Int) -> Unit = {},
    onPlayPause: () -> Unit = {},
    onRecalculate: () -> Unit = {},
    onViewportSize: (width: Int, height: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    when (state) {
        RadarUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is RadarUiState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(40.dp))
                Text(
                    stringResource(if (state.reason == RadarErrorReason.CITY_NOT_FOUND) R.string.city_not_found_in_favorites else R.string.radar_unavailable),
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
        is RadarUiState.Ready -> RadarReadyContent(
            state, onModeChange, onRangeChange, onHorizonChange, onFrameChange, onPlayPause, onRecalculate,
            onViewportSize, modifier
        )
    }
}

@Composable
private fun RadarReadyContent(
    state: RadarUiState.Ready,
    onModeChange: (RadarMode) -> Unit,
    onRangeChange: (RadarRange) -> Unit,
    onHorizonChange: (Int) -> Unit,
    onFrameChange: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onRecalculate: () -> Unit,
    onViewportSize: (width: Int, height: Int) -> Unit,
    modifier: Modifier
) {
    val contentModifier = modifier
        .fillMaxSize()
        .padding(horizontal = if (state.isFullscreen) 0.dp else 16.dp)
        .let { base -> if (state.isFullscreen) base else base.verticalScroll(rememberScrollState()) }
    Column(
        modifier = contentModifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!state.isFullscreen) {
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.radar_mode), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.mode == RadarMode.OBSERVATION,
                    onClick = { onModeChange(RadarMode.OBSERVATION) },
                    label = { Text(stringResource(R.string.radar_observation)) },
                    modifier = Modifier.testTag(TAG_RADAR_OBSERVATION)
                )
                FilterChip(
                    selected = state.mode == RadarMode.PROJECTION,
                    onClick = { onModeChange(RadarMode.PROJECTION) },
                    label = { Text(stringResource(R.string.radar_projection)) },
                    modifier = Modifier.testTag(TAG_RADAR_PROJECTION)
                )
            }
            Text(stringResource(R.string.radar_range), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadarRange.entries.forEach { range ->
                    FilterChip(
                        selected = state.range == range,
                        onClick = { onRangeChange(range) },
                        label = { Text(stringResource(range.labelResource())) },
                        modifier = Modifier.testTag(radarRangeTag(range))
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().then(if (state.isFullscreen) Modifier.weight(1f) else Modifier.height(360.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE2E8F0)),
            shape = if (state.isFullscreen) RoundedCornerShape(0.dp) else MaterialTheme.shapes.large
        ) {
            Box(Modifier.fillMaxSize()) {
                RadarStage(
                    state = state,
                    onViewportSize = onViewportSize,
                    modifier = Modifier.fillMaxSize().testTag(TAG_RADAR_STAGE)
                )
                if (state.isImageLoading || state.isBaseLoading) {
                    CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .88f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = formatRadarTime(state),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
                if (state.mode == RadarMode.PROJECTION) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .88f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "${stringResource(R.string.radar_projection)} · +${state.horizonMinutes} min",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
                RadarAttribution(Modifier.align(Alignment.BottomEnd).padding(6.dp))
                if (state.isFullscreen) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CompactModeControls(state, onModeChange, onRangeChange, onHorizonChange, onRecalculate)
                            if (state.mode == RadarMode.OBSERVATION) ObservationControls(state, onFrameChange, onPlayPause)
                        }
                    }
                }
            }
        }

        if (!state.isFullscreen) {
            RadarLegend(state.mode)
            Text(
                text = when {
                    state.isAnalyzing -> stringResource(R.string.radar_nowcast_analyzing)
                    state.mode == RadarMode.OBSERVATION -> stringResource(R.string.radar_observed_window)
                    state.nowcast != null -> stringResource(R.string.radar_projection_ready)
                    state.nowcastReason == RadarNowcastReason.UNCERTAIN -> stringResource(R.string.radar_nowcast_uncertain)
                    else -> stringResource(R.string.radar_projection_waiting)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.recalculationFailed) {
                Text(
                    stringResource(R.string.radar_recalculation_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (state.mode == RadarMode.OBSERVATION) {
                ObservationControls(state, onFrameChange, onPlayPause)
            } else {
                ProjectionControls(state, onHorizonChange, onRecalculate)
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.radar_reading_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.radar_reading_body), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.radar_privacy_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RadarAttribution(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = .82f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = stringResource(R.string.radar_attribution_osm),
                style = MaterialTheme.typography.labelSmall,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://www.openstreetmap.org/copyright")
                }
            )
            Text(
                text = stringResource(R.string.radar_attribution_rainviewer),
                style = MaterialTheme.typography.labelSmall,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://www.rainviewer.com/")
                }
            )
        }
    }
}

@Composable
private fun RadarLegend(mode: RadarMode) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.radar_intensity), style = MaterialTheme.typography.labelLarge)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF1BAEE2), Color(0xFF005588), Color(0xFFFFEE00),
                                Color(0xFFFF8100), Color(0xFFC10000), Color(0xFFFF77FF)
                            )
                        ),
                        RoundedCornerShape(999.dp)
                    )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.radar_intensity_light), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.radar_intensity_moderate), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.radar_intensity_heavy), style = MaterialTheme.typography.labelSmall)
            }
            if (mode == RadarMode.PROJECTION) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        RadarLegendKey(stringResource(R.string.radar_probable_zone), dashed = true, strong = false)
                        RadarLegendKey(stringResource(R.string.radar_observed_trajectory), dashed = true, strong = true)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        RadarLegendKey(stringResource(R.string.radar_forecast_zone), dashed = false, strong = true)
                        RadarLegendKey(stringResource(R.string.radar_projected_trajectory), dashed = false, strong = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarLegendKey(label: String, dashed: Boolean, strong: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Canvas(Modifier.width(28.dp).height(10.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = if (strong) 2.2.dp.toPx() else 1.5.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CompactModeControls(
    state: RadarUiState.Ready,
    onModeChange: (RadarMode) -> Unit,
    onRangeChange: (RadarRange) -> Unit,
    onHorizonChange: (Int) -> Unit,
    onRecalculate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(state.mode == RadarMode.OBSERVATION, { onModeChange(RadarMode.OBSERVATION) }, { Text(stringResource(R.string.radar_observation)) })
            FilterChip(state.mode == RadarMode.PROJECTION, { onModeChange(RadarMode.PROJECTION) }, { Text(stringResource(R.string.radar_projection)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            RadarRange.entries.forEach { range ->
                FilterChip(
                    selected = state.range == range,
                    onClick = { onRangeChange(range) },
                    label = { Text(stringResource(range.labelResource())) },
                    modifier = Modifier.testTag(radarRangeTag(range))
                )
            }
        }
        if (state.mode == RadarMode.PROJECTION) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                RADAR_PROJECTION_HORIZONS.forEach { horizon ->
                    FilterChip(state.horizonMinutes == horizon, { onHorizonChange(horizon) }, { Text("+$horizon") })
                }
                IconButton(
                    onClick = onRecalculate,
                    enabled = !state.isAnalyzing && !state.isRecalculating,
                    modifier = Modifier.testTag(TAG_RADAR_RECALCULATE)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.radar_projection_recalculate))
                }
            }
        }
    }
}

@Composable
private fun ObservationControls(state: RadarUiState.Ready, onFrameChange: (Int) -> Unit, onPlayPause: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPlayPause, enabled = state.frames.size > 1) {
            Icon(
                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(if (state.isPlaying) R.string.radar_pause else R.string.radar_play)
            )
        }
        if (state.frames.size > 1) {
            Slider(
                value = state.selectedFrameIndex.toFloat(),
                onValueChange = { onFrameChange(it.roundToInt()) },
                valueRange = 0f..state.frames.lastIndex.toFloat(),
                steps = (state.frames.size - 2).coerceAtLeast(0),
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProjectionControls(state: RadarUiState.Ready, onHorizonChange: (Int) -> Unit, onRecalculate: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RADAR_PROJECTION_HORIZONS.forEach { horizon ->
            FilterChip(
                selected = state.horizonMinutes == horizon,
                onClick = { onHorizonChange(horizon) },
                label = { Text("+$horizon min") },
                modifier = Modifier.testTag(radarHorizonTag(horizon))
            )
        }
    }
    Button(
        onClick = onRecalculate,
        enabled = !state.isAnalyzing && !state.isRecalculating,
        modifier = Modifier.testTag(TAG_RADAR_RECALCULATE)
    ) {
        if (state.isRecalculating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Icon(Icons.Default.Refresh, contentDescription = null)
        Spacer(Modifier.size(6.dp))
        Text(stringResource(if (state.isRecalculating) R.string.radar_projection_recalculating else R.string.radar_projection_recalculate))
    }
    RadarProjectionSummary(state)
}

@Composable
private fun RadarProjectionSummary(state: RadarUiState.Ready) {
    val nowcast = state.nowcast
    if (nowcast == null) {
        Text(
            stringResource(if (state.nowcastReason == RadarNowcastReason.UNCERTAIN) R.string.radar_nowcast_uncertain else R.string.radar_nowcast_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val speeds = nowcast.cells.mapNotNull { cell ->
        cell.motion?.let { hypot(it.vx, it.vy) * mapResolutionKm(state.city.latitude, cell.analysisZoom) * 60 }
    }.sorted()
    val medianSpeed = speeds.getOrNull(speeds.size / 2)?.roundToInt() ?: 0
    val confidence = nowcast.cells.mapNotNull { it.motion?.confidence }.average().takeIf { !it.isNaN() }?.times(100)?.roundToInt() ?: 0
    val relevant = nowcast.cells.maxByOrNull { it.impact?.relevanceScore ?: 0.0 }
    val impactText = when (val impact = relevant?.impact) {
        null -> stringResource(R.string.radar_impact_none)
        else -> when (impact.kind) {
            RadarImpactKind.CURRENT -> stringResource(R.string.radar_impact_current, relevant.stableLabel ?: "", impact.windowEnd ?: 60)
            RadarImpactKind.CURRENT_LEAVING -> stringResource(R.string.radar_impact_current_leaving, relevant.stableLabel ?: "")
            RadarImpactKind.IMPACT -> if (impact.windowStart == impact.windowEnd) {
                stringResource(R.string.radar_impact_at, relevant.stableLabel ?: "", impact.windowStart ?: state.horizonMinutes)
            } else stringResource(R.string.radar_impact_window, relevant.stableLabel ?: "", impact.windowStart ?: 15, impact.windowEnd ?: 60)
            RadarImpactKind.APPROACHING -> stringResource(R.string.radar_impact_approaching, relevant.stableLabel ?: "")
            else -> stringResource(R.string.radar_impact_none)
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.radar_projection_cells, nowcast.cells.size), fontWeight = FontWeight.SemiBold)
            Text(impactText, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.radar_projection_detail, medianSpeed, confidence), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RadarStage(
    state: RadarUiState.Ready,
    onViewportSize: (width: Int, height: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val tileImages = remember(state.baseTiles) { state.baseTiles.map { it to it.image.toImageBitmap() } }
    val radar = remember(state.displayImage) { state.displayImage?.toImageBitmap() }
    val background = MaterialTheme.colorScheme.surfaceVariant
    val markerColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .background(background)
            .onSizeChanged { size ->
                onViewportSize(
                    (size.width / density).roundToInt(),
                    (size.height / density).roundToInt()
                )
            }
    ) {
        val cx = size.width / 2
        val cy = size.height / 2
        val tilePx = 256f * density
        tileImages.forEach { (tile, image) ->
            drawImage(
                image = image,
                dstOffset = androidx.compose.ui.unit.IntOffset(
                    (cx + tile.leftFromCenter * density).roundToInt(),
                    (cy + tile.topFromCenter * density).roundToInt()
                ),
                dstSize = androidx.compose.ui.unit.IntSize(tilePx.roundToInt(), tilePx.roundToInt())
            )
        }
        radar?.let { image ->
            val imageSize = 512f * state.range.radarScale * density
            drawImage(
                image = image,
                dstOffset = androidx.compose.ui.unit.IntOffset((cx - imageSize / 2).roundToInt(), (cy - imageSize / 2).roundToInt()),
                dstSize = androidx.compose.ui.unit.IntSize(imageSize.roundToInt(), imageSize.roundToInt()),
                alpha = if (state.mode == RadarMode.PROJECTION) RADAR_PROJECTION_OBSERVATION_ALPHA else RADAR_OBSERVATION_ALPHA,
                filterQuality = FilterQuality.None
            )
        }
        if (state.projectionVisible) drawProjectionOverlay(state, density)
        drawCircle(Color.White.copy(alpha = .95f), radius = 7f * density, center = Offset(cx, cy))
        drawCircle(markerColor, radius = 4f * density, center = Offset(cx, cy))
    }
}

private fun RadarImage.toImageBitmap(): ImageBitmap =
    Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

private fun DrawScope.drawProjectionOverlay(state: RadarUiState.Ready, density: Float) {
    val cells = state.nowcast?.cells.orEmpty().sortedBy { it.impact?.relevanceScore ?: .0 }
    cells.forEachIndexed { index, cell ->
        val projection = projectRainCell(cell, state.horizonMinutes.toDouble()) ?: return@forEachIndexed
        val geometry = radarNowcastDisplayGeometry(state.range, (size.width / density).toDouble(), (size.height / density).toDouble(), cell.analysisZoom)
        val color = CELL_COLORS[(cell.colorIndex ?: index) % CELL_COLORS.size]
        val relevance = (cell.impact?.relevanceScore ?: .35).coerceIn(0.0, 1.0)
        val priority = index >= (cells.size - 4).coerceAtLeast(0) || relevance >= .62
        val emphasis = if (priority) (.72 + .28 * relevance).toFloat() else (.42 + .28 * relevance).toFloat()
        val envelope = cellPath(cell, geometry, projection.dx, projection.dy, projection.scaleX, projection.scaleY, projection.uncertaintyPx, density)
        drawPath(envelope, color.copy(alpha = .035f + .035f * emphasis), style = androidx.compose.ui.graphics.drawscope.Fill)
        drawPath(
            envelope,
            color.copy(alpha = .56f + .34f * emphasis),
            style = Stroke(
                width = (1.7f + .5f * emphasis) * density,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * density, 5f * density))
            )
        )
        val contour = cellPath(cell, geometry, projection.dx, projection.dy, projection.scaleX, projection.scaleY, 0.0, density)
        drawPath(contour, color.copy(alpha = .045f + .045f * emphasis), style = androidx.compose.ui.graphics.drawscope.Fill)
        drawPath(contour, color.copy(alpha = .76f + .22f * emphasis), style = Stroke(width = (2.1f + .6f * emphasis) * density))

        if (priority) drawObservedMotion(cell, geometry, color, density, .34f + .30f * emphasis)
        val displacement = hypot(projection.dx, projection.dy) * geometry.sourceScale
        if (displacement >= 18.0) drawTrajectory(cell, geometry, state.horizonMinutes, color, density, .20f + .68f * emphasis)
        drawCellLabels(cell, projection.x, projection.y, geometry, color, state.horizonMinutes, density, priority)
    }
}

private fun cellPath(
    cell: RadarRainCell,
    geometry: com.meteocompare.app.domain.radar.RadarDisplayGeometry,
    dx: Double,
    dy: Double,
    scaleX: Double,
    scaleY: Double,
    expandPx: Double,
    density: Float
): Path {
    val path = Path().apply { fillType = PathFillType.EvenOdd }
    val cx = cell.centroid.x
    val cy = cell.centroid.y
    rainCellContours(cell, expandPx).forEach { loop ->
        if (loop.isEmpty()) return@forEach
        fun mapped(point: com.meteocompare.app.domain.radar.RadarPoint): Offset {
            val x = cx + (point.x - cx) * scaleX + dx
            val y = cy + (point.y - cy) * scaleY + dy
            return Offset(
                ((geometry.sourceLeft + x * geometry.sourceScale) * density).toFloat(),
                ((geometry.sourceTop + y * geometry.sourceScale) * density).toFloat()
            )
        }
        val first = mapped(loop.first())
        path.moveTo(first.x, first.y)
        loop.drop(1).forEach { point ->
            val next = mapped(point)
            path.lineTo(next.x, next.y)
        }
        path.close()
    }
    return path
}

private fun DrawScope.drawObservedMotion(
    cell: RadarRainCell,
    geometry: com.meteocompare.app.domain.radar.RadarDisplayGeometry,
    color: Color,
    density: Float,
    alpha: Float
) {
    val source = if (cell.motion?.advection != null) cell.observedTrack else cell.track
    val track = source.takeLast(5)
    if (track.size < 2) return
    val points = track.map { point ->
        Offset(
            ((geometry.sourceLeft + point.x * geometry.sourceScale) * density).toFloat(),
            ((geometry.sourceTop + point.y * geometry.sourceScale) * density).toFloat()
        )
    }
    if (hypot((points.last().x - points.first().x).toDouble(), (points.last().y - points.first().y).toDouble()) < 4 * density) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    val dash = PathEffect.dashPathEffect(floatArrayOf(4f * density, 4f * density))
    drawPath(path, Color(0xFF0F172A).copy(alpha = .66f * alpha), style = Stroke(3.8f * density, pathEffect = dash))
    drawPath(path, color.copy(alpha = alpha), style = Stroke(1.7f * density, pathEffect = dash))
    points.forEachIndexed { index, point ->
        drawCircle(color.copy(alpha = alpha * (.42f + .10f * index)), 2.2f * density, point)
    }
}

private fun DrawScope.drawTrajectory(
    cell: RadarRainCell,
    geometry: com.meteocompare.app.domain.radar.RadarDisplayGeometry,
    horizon: Int,
    color: Color,
    density: Float,
    alpha: Float
) {
    val points = listOf(0.0, .33, .66, 1.0).map { fraction ->
        val projection = if (fraction == 0.0) null else projectRainCell(cell, horizon * fraction)
        val x = projection?.x ?: cell.centroid.x
        val y = projection?.y ?: cell.centroid.y
        Offset(
            ((geometry.sourceLeft + x * geometry.sourceScale) * density).toFloat(),
            ((geometry.sourceTop + y * geometry.sourceScale) * density).toFloat()
        )
    }
    if (hypot((points.last().x - points.first().x).toDouble(), (points.last().y - points.first().y).toDouble()) < 8 * density) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, Color(0xFF0F172A).copy(alpha = .72f * alpha), style = Stroke(5f * density))
    drawPath(path, color.copy(alpha = alpha), style = Stroke(2.4f * density))
    points.drop(1).dropLast(1).forEach { drawCircle(color.copy(alpha = alpha), 2.6f * density, it) }
    drawArrowHead(points[points.lastIndex - 1], points.last(), color, density, alpha)
}

private fun DrawScope.drawArrowHead(from: Offset, to: Offset, color: Color, density: Float, alpha: Float) {
    val distance = hypot((to.x - from.x).toDouble(), (to.y - from.y).toDouble())
    if (distance < 5 * density) return
    val angle = atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
    val head = 8f * density
    fun wing(delta: Double) = Offset(
        (to.x - head * cos(angle + delta)).toFloat(),
        (to.y - head * sin(angle + delta)).toFloat()
    )
    val left = wing(-Math.PI / 6)
    val right = wing(Math.PI / 6)
    val arrow = Path().apply {
        moveTo(left.x, left.y)
        lineTo(to.x, to.y)
        lineTo(right.x, right.y)
    }
    drawPath(arrow, Color(0xFF0F172A).copy(alpha = .78f * alpha), style = Stroke(4f * density))
    drawPath(arrow, color.copy(alpha = alpha), style = Stroke(2f * density))
}

private fun DrawScope.drawCellLabels(
    cell: RadarRainCell,
    projectedX: Double,
    projectedY: Double,
    geometry: com.meteocompare.app.domain.radar.RadarDisplayGeometry,
    color: Color,
    horizon: Int,
    density: Float,
    priority: Boolean
) {
    val projected = Offset(
        ((geometry.sourceLeft + projectedX * geometry.sourceScale) * density).toFloat(),
        ((geometry.sourceTop + projectedY * geometry.sourceScale) * density).toFloat()
    )
    val current = Offset(
        ((geometry.sourceLeft + cell.centroid.x * geometry.sourceScale) * density).toFloat(),
        ((geometry.sourceTop + cell.centroid.y * geometry.sourceScale) * density).toFloat()
    )
    drawOutlinedRadarText("+$horizon", projected.x, projected.y - 12f * density, color, density)
    if (priority) cell.stableLabel?.let { label ->
        drawOutlinedRadarText(label, current.x, current.y + 16f * density, color, density)
    }
}

private fun DrawScope.drawOutlinedRadarText(text: String, x: Float, y: Float, color: Color, density: Float) {
    if (x < -20 || x > size.width + 20 || y < -20 || y > size.height + 20) return
    drawContext.canvas.nativeCanvas.apply {
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            textAlign = AndroidPaint.Align.CENTER
            textSize = 10f * density
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            style = AndroidPaint.Style.STROKE
            strokeWidth = 3.5f * density
            this.color = android.graphics.Color.argb(220, 15, 23, 42)
        }
        drawText(text, x, y, paint)
        paint.style = AndroidPaint.Style.FILL
        paint.color = android.graphics.Color.argb(
            (255 * .95f).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        )
        drawText(text, x, y, paint)
    }
}

@Composable
private fun formatRadarTime(state: RadarUiState.Ready): String {
    val zone = remember(state.city.timezone) { runCatching { ZoneId.of(state.city.timezone ?: "UTC") }.getOrDefault(ZoneId.of("UTC")) }
    val formatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    return Instant.ofEpochSecond(state.selectedFrame.timeEpochSeconds).atZone(zone).format(formatter)
}

private fun RadarRange.labelResource(): Int = when (this) {
    RadarRange.NEAR -> R.string.radar_range_near
    RadarRange.REGIONAL -> R.string.radar_range_regional
    RadarRange.WIDE -> R.string.radar_range_wide
}
