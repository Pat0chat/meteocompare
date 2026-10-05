package com.meteocompare.app.ui.radar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.data.radar.RadarRepository
import com.meteocompare.app.di.DefaultDispatcher
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.radar.RADAR_ANALYSIS_ZOOM
import com.meteocompare.app.domain.radar.RADAR_PROJECTION_HORIZONS
import com.meteocompare.app.domain.radar.RadarIdentityRegistryEntry
import com.meteocompare.app.domain.radar.RadarMaskSample
import com.meteocompare.app.domain.radar.RadarMode
import com.meteocompare.app.domain.radar.RadarNowcast
import com.meteocompare.app.domain.radar.RadarRainCell
import com.meteocompare.app.domain.radar.RadarRange
import com.meteocompare.app.domain.radar.estimateRainCellMotions
import com.meteocompare.app.domain.radar.evaluateRainCellLocalityImpact
import com.meteocompare.app.domain.radar.filterPeripheralRainCells
import com.meteocompare.app.domain.radar.radarMaskFromArgb
import com.meteocompare.app.domain.radar.recentRadarFrameIndices
import com.meteocompare.app.domain.radar.stabilizeRainCellIdentities
import com.meteocompare.app.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RADAR_FRAME_DELAY_MS = 700L
private const val RADAR_MODE_STATE = "radar.mode"
private const val RADAR_RANGE_STATE = "radar.range"
private const val RADAR_HORIZON_STATE = "radar.horizon"
private const val RADAR_FULLSCREEN_STATE = "radar.fullscreen"

@HiltViewModel
class RadarViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val cityRepository: CityRepository,
    private val radarRepository: RadarRepository,
    @param:DefaultDispatcher private val computationDispatcher: CoroutineDispatcher
) : ViewModel() {
    private val cityId: String = checkNotNull(savedStateHandle[Destinations.CITY_DETAIL_ARG])
    private val persistedMode: RadarMode
        get() = savedStateHandle.get<String>(RADAR_MODE_STATE)
            ?.let { runCatching { RadarMode.valueOf(it) }.getOrNull() } ?: RadarMode.OBSERVATION
    private val persistedRange: RadarRange
        get() = savedStateHandle.get<String>(RADAR_RANGE_STATE)
            ?.let { runCatching { RadarRange.valueOf(it) }.getOrNull() } ?: RadarRange.NEAR
    private val persistedHorizon: Int
        get() = savedStateHandle.get<Int>(RADAR_HORIZON_STATE)
            ?.takeIf { it in RADAR_PROJECTION_HORIZONS } ?: 30
    private val persistedFullscreen: Boolean
        get() = savedStateHandle[RADAR_FULLSCREEN_STATE] ?: false
    private val _state = MutableStateFlow<RadarUiState>(RadarUiState.Loading)
    val state: StateFlow<RadarUiState> = _state.asStateFlow()

    private var playbackJob: Job? = null
    private var imageJob: Job? = null
    private var baseJob: Job? = null
    private var analysisJob: Job? = null
    private var coverageJob: Job? = null
    private var loadJob: Job? = null
    private var identityRegistry: List<RadarIdentityRegistryEntry> = emptyList()
    private var nextCellId = 1
    private val coverageRanges = mutableSetOf<RadarRange>()

    init { load() }

    fun retry() = load(forceRefresh = true)

    fun setMode(mode: RadarMode) {
        val current = _state.value as? RadarUiState.Ready ?: return
        if (current.mode == mode) return
        stopPlayback()
        val nextIndex = if (mode == RadarMode.PROJECTION) current.frames.lastIndex else current.selectedFrameIndex
        _state.value = current.copy(mode = mode, selectedFrameIndex = nextIndex, isPlaying = false)
        savedStateHandle[RADAR_MODE_STATE] = mode.name
        loadDisplayImage()
    }

    fun setRange(range: RadarRange) {
        val current = _state.value as? RadarUiState.Ready ?: return
        if (current.range == range) return
        _state.value = current.copy(range = range, isBaseLoading = true)
        savedStateHandle[RADAR_RANGE_STATE] = range.name
        loadBaseTiles()
        loadDisplayImage()
        if (range == RadarRange.WIDE) augmentWideCoverage()
    }

    fun setHorizon(minutes: Int) {
        if (minutes !in RADAR_PROJECTION_HORIZONS) return
        _state.update { current ->
            if (current is RadarUiState.Ready) current.copy(horizonMinutes = minutes) else current
        }
        savedStateHandle[RADAR_HORIZON_STATE] = minutes
    }

    fun selectFrame(index: Int) {
        val current = _state.value as? RadarUiState.Ready ?: return
        if (current.frames.isEmpty()) return
        stopPlayback()
        val safeIndex = index.coerceIn(current.frames.indices)
        _state.value = current.copy(
            mode = RadarMode.OBSERVATION,
            selectedFrameIndex = safeIndex,
            isPlaying = false
        )
        savedStateHandle[RADAR_MODE_STATE] = RadarMode.OBSERVATION.name
        loadDisplayImage()
    }

    fun togglePlayback() {
        val current = _state.value as? RadarUiState.Ready ?: return
        if (current.isPlaying) {
            stopPlayback()
            return
        }
        if (current.frames.size < 2) return
        val startIndex = if (current.selectedFrameIndex >= current.frames.lastIndex) 0 else current.selectedFrameIndex
        _state.value = current.copy(
            mode = RadarMode.OBSERVATION,
            selectedFrameIndex = startIndex,
            isPlaying = true
        )
        savedStateHandle[RADAR_MODE_STATE] = RadarMode.OBSERVATION.name
        loadDisplayImage()
        playbackJob = viewModelScope.launch {
            while (true) {
                delay(RADAR_FRAME_DELAY_MS)
                val ready = _state.value as? RadarUiState.Ready ?: break
                if (!ready.isPlaying || ready.mode != RadarMode.OBSERVATION) break
                if (ready.selectedFrameIndex >= ready.frames.lastIndex) {
                    stopPlayback()
                    break
                }
                _state.value = ready.copy(selectedFrameIndex = ready.selectedFrameIndex + 1)
                loadDisplayImage()
            }
        }
    }

    fun toggleFullscreen() {
        val current = _state.value as? RadarUiState.Ready ?: return
        val fullscreen = !current.isFullscreen
        _state.value = current.copy(isFullscreen = fullscreen)
        savedStateHandle[RADAR_FULLSCREEN_STATE] = fullscreen
    }

    fun recalculateProjection() {
        val current = _state.value as? RadarUiState.Ready ?: return
        if (current.isAnalyzing || current.isRecalculating) return
        stopPlayback()
        analysisJob?.cancel()
        coverageJob?.cancel()
        coverageJob = null
        savedStateHandle[RADAR_MODE_STATE] = RadarMode.PROJECTION.name
        _state.value = current.copy(
            mode = RadarMode.PROJECTION,
            selectedFrameIndex = current.frames.lastIndex,
            isRecalculating = true,
            isAnalyzing = true,
            nowcast = null,
            nowcastReason = null,
            recalculationFailed = false
        )
        identityRegistry = emptyList()
        nextCellId = 1
        coverageRanges.clear()
        analysisJob = viewModelScope.launch {
            val previous = current
            try {
                val metadata = radarRepository.metadata(forceRefresh = true)
                val ready = (_state.value as? RadarUiState.Ready) ?: return@launch
                _state.value = ready.copy(
                    metadata = metadata,
                    frames = metadata.past,
                    selectedFrameIndex = metadata.past.lastIndex,
                    displayImage = null
                )
                loadDisplayImage()
                analyzeNowcastInternal()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _state.value = previous.copy(
                    mode = RadarMode.PROJECTION,
                    selectedFrameIndex = previous.frames.lastIndex,
                    isPlaying = false,
                    isAnalyzing = false,
                    isRecalculating = false,
                    recalculationFailed = true
                )
            } finally {
                _state.update { state ->
                    if (state is RadarUiState.Ready) state.copy(isRecalculating = false) else state
                }
            }
        }
    }

    private fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        stopPlayback()
        imageJob?.cancel()
        baseJob?.cancel()
        analysisJob?.cancel()
        coverageJob?.cancel()
        coverageJob = null
        coverageRanges.clear()
        _state.value = RadarUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val city = cityRepository.observeFavorites().first().firstOrNull { it.id == cityId }
                if (city == null) {
                    _state.value = RadarUiState.Error(RadarErrorReason.CITY_NOT_FOUND)
                    return@launch
                }
                val metadata = radarRepository.metadata(forceRefresh)
                val frames = metadata.past
                val mode = persistedMode
                _state.value = RadarUiState.Ready(
                    city = city,
                    metadata = metadata,
                    frames = frames,
                    selectedFrameIndex = if (mode == RadarMode.PROJECTION) frames.lastIndex else 0,
                    displayImage = null,
                    baseTiles = emptyList(),
                    range = persistedRange,
                    mode = mode,
                    horizonMinutes = persistedHorizon,
                    isImageLoading = true,
                    isBaseLoading = true,
                    isAnalyzing = true,
                    isFullscreen = persistedFullscreen
                )
                loadBaseTiles()
                loadDisplayImage()
                analyzeNowcast()
                if (mode == RadarMode.OBSERVATION && frames.size > 1) togglePlayback()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _state.value = RadarUiState.Error(RadarErrorReason.NETWORK)
            }
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _state.update { current -> if (current is RadarUiState.Ready && current.isPlaying) current.copy(isPlaying = false) else current }
    }

    private fun loadDisplayImage() {
        imageJob?.cancel()
        val current = _state.value as? RadarUiState.Ready ?: return
        val frame = current.frames.getOrNull(current.selectedFrameIndex) ?: return
        val zoom = current.range.radarZoom
        _state.value = current.copy(isImageLoading = true)
        imageJob = viewModelScope.launch {
            try {
                val image = radarRepository.radarImage(current.metadata, frame, current.city, zoom)
                _state.update { state ->
                    if (state is RadarUiState.Ready &&
                        state.selectedFrame.timeEpochSeconds == frame.timeEpochSeconds && state.range.radarZoom == zoom
                    ) state.copy(displayImage = image, isImageLoading = false) else state
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _state.update { state -> if (state is RadarUiState.Ready) state.copy(isImageLoading = false) else state }
            }
        }
    }

    private fun loadBaseTiles() {
        baseJob?.cancel()
        val current = _state.value as? RadarUiState.Ready ?: return
        val zoom = current.range.mapZoom
        baseJob = viewModelScope.launch {
            val tiles = runCatching { radarRepository.baseTiles(current.city, zoom) }.getOrDefault(emptyList())
            _state.update { state ->
                if (state is RadarUiState.Ready && state.range.mapZoom == zoom) state.copy(baseTiles = tiles, isBaseLoading = false) else state
            }
        }
    }

    private fun analyzeNowcast() {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch { analyzeNowcastInternal() }
    }

    private suspend fun analyzeNowcastInternal() {
        val current = _state.value as? RadarUiState.Ready ?: return
        if (current.frames.size < 2) {
            _state.value = current.copy(isAnalyzing = false, nowcastReason = RadarNowcastReason.UNCERTAIN)
            return
        }
        _state.update { state -> if (state is RadarUiState.Ready) state.copy(isAnalyzing = true) else state }
        try {
            val frames = recentRadarFrameIndices(current.frames.size, 7).mapNotNull(current.frames::getOrNull)
            val images = coroutineScope {
                frames.map { frame -> async { frame to radarRepository.radarImage(current.metadata, frame, current.city, RADAR_ANALYSIS_ZOOM) } }.awaitAll()
            }
            val samples = withContext(computationDispatcher) {
                images.map { (frame, image) -> RadarMaskSample(radarMaskFromArgb(image), frame.timeEpochSeconds) }
            }
            val tracked = withContext(computationDispatcher) {
                estimateRainCellMotions(samples).map { it.copy(analysisZoom = RADAR_ANALYSIS_ZOOM) }
            }
            if (tracked.isEmpty()) {
                _state.update { state -> if (state is RadarUiState.Ready) state.copy(nowcast = null, nowcastReason = RadarNowcastReason.UNCERTAIN, isAnalyzing = false) else state }
                return
            }
            val latestTime = samples.last().timeEpochSeconds
            val identity = withContext(computationDispatcher) {
                stabilizeRainCellIdentities(tracked, identityRegistry, current.city.latitude, RADAR_ANALYSIS_ZOOM, latestTime, nextCellId)
            }
            identityRegistry = identity.registry
            nextCellId = identity.nextId
            val cells = withContext(computationDispatcher) { identity.cells.map { it.copy(impact = evaluateRainCellLocalityImpact(it)) } }
            _state.update { state ->
                if (state is RadarUiState.Ready) state.copy(
                    nowcast = RadarNowcast(samples.last().mask, cells),
                    nowcastReason = null,
                    isAnalyzing = false
                ) else state
            }
            if ((_state.value as? RadarUiState.Ready)?.range == RadarRange.WIDE) augmentWideCoverage()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            _state.update { state -> if (state is RadarUiState.Ready) state.copy(nowcast = null, nowcastReason = RadarNowcastReason.ERROR, isAnalyzing = false) else state }
        }
    }

    private fun augmentWideCoverage() {
        if (RadarRange.WIDE in coverageRanges || coverageJob?.isActive == true) return
        val current = _state.value as? RadarUiState.Ready ?: return
        if (current.range != RadarRange.WIDE || current.nowcast == null || current.isAnalyzing) return
        coverageJob = viewModelScope.launch {
            try {
                val frames = recentRadarFrameIndices(current.frames.size, 7).mapNotNull(current.frames::getOrNull)
                val images = coroutineScope {
                    frames.map { frame ->
                        async { frame to radarRepository.radarImage(current.metadata, frame, current.city, RadarRange.WIDE.radarZoom) }
                    }.awaitAll()
                }
                val samples = withContext(computationDispatcher) {
                    images.map { (frame, image) -> RadarMaskSample(radarMaskFromArgb(image), frame.timeEpochSeconds) }
                }
                val peripheral = withContext(computationDispatcher) {
                    filterPeripheralRainCells(
                        estimateRainCellMotions(samples).map { it.copy(analysisZoom = RadarRange.WIDE.radarZoom) },
                        latitude = current.city.latitude,
                        radarZoom = RadarRange.WIDE.radarZoom
                    )
                }
                if (peripheral.isNotEmpty()) {
                    val identity = withContext(computationDispatcher) {
                        stabilizeRainCellIdentities(
                            peripheral,
                            identityRegistry,
                            current.city.latitude,
                            RadarRange.WIDE.radarZoom,
                            samples.last().timeEpochSeconds,
                            nextCellId
                        )
                    }
                    identityRegistry = identity.registry
                    nextCellId = identity.nextId
                    val extra = withContext(computationDispatcher) {
                        identity.cells.map { it.copy(impact = evaluateRainCellLocalityImpact(it)) }
                    }
                    _state.update { state ->
                        if (state !is RadarUiState.Ready || state.nowcast == null) state else {
                            val existing = state.nowcast.cells.mapNotNull(RadarRainCell::stableId).toSet()
                            state.copy(
                                nowcast = state.nowcast.copy(
                                    cells = state.nowcast.cells + extra.filter { it.stableId !in existing }
                                )
                            )
                        }
                    }
                }
                coverageRanges += RadarRange.WIDE
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // The canonical high-resolution nowcast remains usable when the optional
                // wide-area supplement cannot be loaded.
            }
        }
    }
}
