package com.meteocompare.app.ui.radar

import androidx.lifecycle.SavedStateHandle
import com.meteocompare.app.data.radar.RadarRepository
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.radar.RADAR_ANALYSIS_ZOOM
import com.meteocompare.app.domain.radar.RadarBaseTile
import com.meteocompare.app.domain.radar.RadarFrame
import com.meteocompare.app.domain.radar.RadarImage
import com.meteocompare.app.domain.radar.RadarMetadata
import com.meteocompare.app.domain.radar.RadarMode
import com.meteocompare.app.domain.radar.RadarRange
import com.meteocompare.app.ui.navigation.Destinations
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val city = City(
        id = "radar-city",
        name = "Radar City",
        country = "France",
        latitude = 48.8566,
        longitude = 2.3522,
        timezone = "Europe/Paris"
    )

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `initial load exposes observation animation and projection can select latest frame`() = runTest(dispatcher) {
        val repo = FakeRadarRepository()
        val vm = viewModel(repo)
        runCurrent()

        val initial = vm.state.value as RadarUiState.Ready
        assertEquals(RadarMode.OBSERVATION, initial.mode)
        assertTrue(initial.isPlaying)
        assertEquals(0, initial.selectedFrameIndex)

        vm.setMode(RadarMode.PROJECTION)
        runCurrent()
        val projection = vm.state.value as RadarUiState.Ready
        assertEquals(RadarMode.PROJECTION, projection.mode)
        assertFalse(projection.isPlaying)
        assertEquals(projection.frames.lastIndex, projection.selectedFrameIndex)
    }

    @Test
    fun `only supported projection horizons are accepted`() = runTest(dispatcher) {
        val vm = viewModel(FakeRadarRepository())
        runCurrent()
        vm.setHorizon(45)
        assertEquals(45, (vm.state.value as RadarUiState.Ready).horizonMinutes)
        vm.setHorizon(20)
        assertEquals(45, (vm.state.value as RadarUiState.Ready).horizonMinutes)
    }

    @Test
    fun `range change uses matching map and radar zooms`() = runTest(dispatcher) {
        val repo = FakeRadarRepository()
        val vm = viewModel(repo)
        runCurrent()
        vm.setViewportSize(360, 360)
        runCurrent()
        vm.setRange(RadarRange.WIDE)
        runCurrent()

        val state = vm.state.value as RadarUiState.Ready
        assertEquals(RadarRange.WIDE, state.range)
        assertTrue(6 in repo.baseZooms)
        assertTrue(5 in repo.radarZooms)
    }


    @Test
    fun `nowcast frame downloads stay sequential like the web implementation`() = runTest(dispatcher) {
        val repo = SequentialAnalysisRepository()
        val handle = SavedStateHandle(
            mapOf(
                Destinations.CITY_DETAIL_ARG to city.id,
                "radar.mode" to RadarMode.PROJECTION.name,
                "radar.range" to RadarRange.WIDE.name
            )
        )
        viewModel(repo, handle)
        advanceUntilIdle()

        assertEquals(7, repo.analysisDownloads)
        assertEquals(1, repo.maxConcurrentAnalysisDownloads)
    }

    @Test
    fun `base tiles wait for the real viewport and identical viewport is not reloaded`() = runTest(dispatcher) {
        val repo = FakeRadarRepository()
        val vm = viewModel(repo)
        runCurrent()

        assertTrue(repo.baseZooms.isEmpty())
        vm.setViewportSize(360, 360)
        runCurrent()
        assertEquals(listOf(9), repo.baseZooms)
        assertEquals(listOf(360 to 360), repo.baseViewports)

        vm.setViewportSize(360, 360)
        runCurrent()
        assertEquals(1, repo.baseZooms.size)

        vm.setViewportSize(1200, 800)
        runCurrent()
        assertEquals(listOf(360 to 360, 1200 to 800), repo.baseViewports)
    }

    @Test
    fun `changing projection horizon is local and triggers no repository call`() = runTest(dispatcher) {
        val repo = FakeRadarRepository()
        val vm = viewModel(repo)
        runCurrent()
        val metadataCalls = repo.metadataForceFlags.size
        val radarCalls = repo.radarZooms.size
        val baseCalls = repo.baseZooms.size

        vm.setHorizon(15)
        vm.setHorizon(60)
        runCurrent()

        assertEquals(metadataCalls, repo.metadataForceFlags.size)
        assertEquals(radarCalls, repo.radarZooms.size)
        assertEquals(baseCalls, repo.baseZooms.size)
        assertEquals(60, (vm.state.value as RadarUiState.Ready).horizonMinutes)
    }

    @Test
    fun `saved radar controls are restored across recreation`() = runTest(dispatcher) {
        val handle = SavedStateHandle(
            mapOf(
                Destinations.CITY_DETAIL_ARG to city.id,
                "radar.mode" to RadarMode.PROJECTION.name,
                "radar.range" to RadarRange.WIDE.name,
                "radar.horizon" to 60,
                "radar.fullscreen" to true
            )
        )
        val vm = viewModel(FakeRadarRepository(), handle)
        runCurrent()

        val restored = vm.state.value as RadarUiState.Ready
        assertEquals(RadarMode.PROJECTION, restored.mode)
        assertEquals(RadarRange.WIDE, restored.range)
        assertEquals(60, restored.horizonMinutes)
        assertTrue(restored.isFullscreen)
        assertFalse(restored.isPlaying)
        assertEquals(restored.frames.lastIndex, restored.selectedFrameIndex)

        vm.setRange(RadarRange.REGIONAL)
        vm.setHorizon(45)
        vm.toggleFullscreen()
        assertEquals(RadarRange.REGIONAL.name, handle.get<String>("radar.range"))
        assertEquals(45, handle.get<Int>("radar.horizon"))
        assertEquals(false, handle.get<Boolean>("radar.fullscreen"))
    }

    @Test
    fun `failed manual recalculation preserves the last usable radar state`() = runTest(dispatcher) {
        val repo = FakeRadarRepository(failForcedRefresh = true)
        val vm = viewModel(repo)
        runCurrent()
        vm.setMode(RadarMode.PROJECTION)
        val before = vm.state.value as RadarUiState.Ready

        vm.recalculateProjection()
        runCurrent()

        val after = vm.state.value as RadarUiState.Ready
        assertEquals(RadarMode.PROJECTION, after.mode)
        assertEquals(before.frames, after.frames)
        assertEquals(before.metadata, after.metadata)
        assertTrue(after.recalculationFailed)
        assertFalse(after.isRecalculating)
        assertFalse(after.isAnalyzing)
    }

    @Test
    fun `manual projection recalculation refreshes metadata`() = runTest(dispatcher) {
        val repo = FakeRadarRepository()
        val vm = viewModel(repo)
        runCurrent()
        vm.recalculateProjection()
        runCurrent()
        assertTrue(repo.metadataForceFlags.contains(true))
        assertFalse((vm.state.value as RadarUiState.Ready).isRecalculating)
    }

    private fun viewModel(
        repository: RadarRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf(Destinations.CITY_DETAIL_ARG to city.id))
    ): RadarViewModel {
        val cityRepository = mockk<CityRepository> {
            every { observeFavorites() } returns flowOf(listOf(city))
        }
        return RadarViewModel(
            savedStateHandle = savedStateHandle,
            cityRepository = cityRepository,
            radarRepository = repository,
            computationDispatcher = dispatcher
        )
    }

    private class SequentialAnalysisRepository : RadarRepository {
        private val frames = List(7) { i -> RadarFrame(1_700_000_000L + i * 600, "/v2/radar/frame$i") }
        private val image = RadarImage(8, 8, IntArray(64))
        var analysisDownloads = 0
        var maxConcurrentAnalysisDownloads = 0
        private var activeAnalysisDownloads = 0

        override suspend fun metadata(forceRefresh: Boolean): RadarMetadata =
            RadarMetadata("https://tilecache.rainviewer.com", frames)

        override suspend fun radarImage(
            metadata: RadarMetadata,
            frame: RadarFrame,
            city: City,
            zoom: Int
        ): RadarImage {
            if (zoom == RADAR_ANALYSIS_ZOOM) {
                analysisDownloads++
                activeAnalysisDownloads++
                maxConcurrentAnalysisDownloads = maxOf(maxConcurrentAnalysisDownloads, activeAnalysisDownloads)
                try {
                    delay(1)
                } finally {
                    activeAnalysisDownloads--
                }
            }
            return image
        }

        override suspend fun baseTiles(
            city: City,
            zoom: Int,
            viewportWidth: Int,
            viewportHeight: Int
        ): List<RadarBaseTile> = emptyList()
    }

    private class FakeRadarRepository(
        private val failForcedRefresh: Boolean = false
    ) : RadarRepository {
        val metadataForceFlags = mutableListOf<Boolean>()
        val radarZooms = mutableListOf<Int>()
        val baseZooms = mutableListOf<Int>()
        val baseViewports = mutableListOf<Pair<Int, Int>>()
        private val image = RadarImage(8, 8, IntArray(64))
        private val frames = List(7) { i -> RadarFrame(1_700_000_000L + i * 600, "/v2/radar/frame$i") }

        override suspend fun metadata(forceRefresh: Boolean): RadarMetadata {
            metadataForceFlags += forceRefresh
            if (forceRefresh && failForcedRefresh) error("forced refresh failed")
            return RadarMetadata("https://tilecache.rainviewer.com", frames)
        }

        override suspend fun radarImage(metadata: RadarMetadata, frame: RadarFrame, city: City, zoom: Int): RadarImage {
            radarZooms += zoom
            return image
        }

        override suspend fun baseTiles(city: City, zoom: Int, viewportWidth: Int, viewportHeight: Int): List<RadarBaseTile> {
            baseZooms += zoom
            baseViewports += viewportWidth to viewportHeight
            return emptyList()
        }
    }
}
