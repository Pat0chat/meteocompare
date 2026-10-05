package com.meteocompare.app.domain.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RainRadarNowcastTest {
    @Test
    fun `rain colour filter rejects neutral artifacts and transparent pixels`() {
        assertFalse(isRainRadarPixel(120, 120, 120, 255))
        assertFalse(isRainRadarPixel(180, 180, 180, 255))
        assertTrue(isRainRadarPixel(55, 145, 255, 255))
        assertTrue(isRainRadarPixel(245, 210, 35, 255))
        assertTrue(isRainRadarPixel(235, 60, 45, 255))
        assertFalse(isRainRadarPixel(40, 120, 60, 22))
    }

    @Test
    fun `mask refinement removes isolated speckles`() {
        val mask = ByteArray(25)
        mask[0] = 1
        for (y in 2..3) for (x in 2..3) mask[y * 5 + x] = 1
        val refined = refineRainMask(mask, 5, 5, minNeighbors = 2)
        assertEquals(0, refined[0].toInt())
        assertEquals(1, refined[2 * 5 + 2].toInt())
    }

    @Test
    fun `distinct rain areas keep separate contours`() {
        val width = 48
        val height = 36
        val mask = paint(width, height, listOf(Rect(8, 7, 5, 4), Rect(32, 19, 4, 5)))
        val cells = extractRainCells(mask, width, height, minPixels = 5)
        assertEquals(2, cells.size)
        assertTrue(cells.all { it.boundary.isNotEmpty() })
        assertTrue(cells.all { rainCellContours(it).isNotEmpty() })
        assertTrue(rainCellContours(cells.first(), 2.0).isNotEmpty())
    }

    @Test
    fun `overlap translation recovers motion despite footprint deformation`() {
        val width = 120
        val height = 80
        val previous = extractRainCells(
            paint(width, height, listOf(Rect(10, 30, 16, 8), Rect(14, 26, 6, 4))),
            width, height, minPixels = 1
        ).first()
        val next = extractRainCells(
            paint(width, height, listOf(Rect(14, 30, 16, 8), Rect(18, 26, 6, 4), Rect(10, 32, 5, 3))),
            width, height, minPixels = 1
        ).first()
        val translation = estimateRainCellTranslation(previous, next)
        assertNotNull(translation)
        assertEquals(4, translation!!.dx)
        assertEquals(0, translation.dy)
        assertTrue(translation.overlap > .8)
    }

    @Test
    fun `recent observations drive eastward advection and all four horizons`() {
        val width = 120
        val height = 80
        val samples = List(7) { i ->
            val x = 12 + i * 4
            val rects = mutableListOf(Rect(x, 32, 22, 10), Rect(x + 5, 27, 7, 5))
            if (i % 2 == 1) rects += Rect(x - 5, 35, 5, 5)
            RadarMaskSample(paint(width, height, rects), i * 600L)
        }
        val cell = estimateRainCellMotions(samples, width, height, minPixels = 20, maxCells = 4).first()
        assertEquals("cell-advection", cell.motion!!.method)
        assertTrue(cell.motion.vx > .32)
        assertTrue(abs(cell.motion.vy) < .03)
        assertTrue((cell.motion.advection?.confidence ?: 0.0) > .7)

        val projections = RADAR_PROJECTION_HORIZONS.map { projectRainCell(cell, it.toDouble())!! }
        assertEquals(listOf(15.0, 30.0, 45.0, 60.0), projections.map { it.horizon })
        assertTrue(projections.last().uncertaintyPx > projections.first().uncertaintyPx)
        assertTrue(projections[1].dx > 9)
        assertTrue(projections.last().dx > projections[1].dx * 1.8)
    }

    @Test
    fun `one inconsistent frame is rejected instead of creating a sixty minute jump`() {
        val width = 120
        val height = 80
        val xs = listOf(10, 14, 18, 30, 26, 30, 34)
        val samples = xs.mapIndexed { i, x ->
            RadarMaskSample(
                paint(width, height, listOf(Rect(x, 30, 18, 10), Rect(x + 4, 26, 6, 4))),
                i * 600L
            )
        }
        val cell = estimateRainCellMotions(samples, width, height, minPixels = 20, maxCells = 4).first()
        assertTrue(abs(cell.motion!!.vx - .4) < .08)
        assertTrue(cell.motion.advection!!.rejected.isNotEmpty())
        assertTrue(cell.observedTrack.size >= 2)
        assertTrue(projectRainCell(cell, 60.0)!!.dx < 30)
    }

    @Test
    fun `large observation gap does not fabricate a track`() {
        val width = 120
        val height = 80
        val sparse = listOf(
            RadarMaskSample(paint(width, height, listOf(Rect(10, 30, 18, 10))), 0),
            RadarMaskSample(paint(width, height, listOf(Rect(14, 30, 18, 10))), 600),
            RadarMaskSample(paint(width, height, listOf(Rect(26, 30, 18, 10))), 2400)
        )
        assertTrue(estimateRainCellMotions(sparse, width, height, minPixels = 20, maxCells = 4, maxTrackGapMinutes = 25.0).isEmpty())
    }


    @Test
    fun `diagonally touching rain areas stay separate`() {
        val mask = ByteArray(36)
        listOf(1 to 1, 1 to 2, 2 to 1, 3 to 3, 3 to 4, 4 to 3).forEach { (x, y) ->
            mask[y * 6 + x] = 1
        }
        assertEquals(2, extractRainCells(mask, 6, 6, minPixels = 1).size)
    }

    @Test
    fun `growing and shrinking cells evolve their projected footprint`() {
        val width = 80
        val height = 64
        val growing = listOf(
            RadarMaskSample(paint(width, height, listOf(Rect(8, 26, 8, 8))), 0),
            RadarMaskSample(paint(width, height, listOf(Rect(12, 25, 10, 9))), 600),
            RadarMaskSample(paint(width, height, listOf(Rect(16, 24, 13, 10))), 1200)
        )
        val growingCell = estimateRainCellMotions(growing, width, height, minPixels = 10).first()
        val growingProjection = projectRainCell(growingCell, 60.0)!!
        assertTrue(growingProjection.scaleX > 1 && growingProjection.scaleY > 1)
        assertTrue(growingProjection.areaFactor > 1.2)
        assertTrue(growingProjection.developing)
        assertTrue(abs(growingProjection.scaleX - growingProjection.scaleY) > .05)

        val shrinking = listOf(
            RadarMaskSample(paint(width, height, listOf(Rect(12, 23, 16, 14))), 0),
            RadarMaskSample(paint(width, height, listOf(Rect(16, 25, 12, 11))), 600),
            RadarMaskSample(paint(width, height, listOf(Rect(20, 27, 8, 7))), 1200)
        )
        val shrinkingCell = estimateRainCellMotions(shrinking, width, height, minPixels = 10).first()
        val shrinkingProjection = projectRainCell(shrinkingCell, 60.0)!!
        assertTrue(shrinkingProjection.areaFactor < .75)
        assertTrue(shrinkingProjection.dissipating)
        assertTrue(shrinkingProjection.survivalProbability < .9)
    }

    @Test
    fun `locality impact distinguishes approaching impact from movement away`() {
        val width = 100
        val height = 80
        val samples = listOf(
            RadarMaskSample(paint(width, height, listOf(Rect(5, 35, 10, 10))), 0),
            RadarMaskSample(paint(width, height, listOf(Rect(10, 35, 10, 10))), 600),
            RadarMaskSample(paint(width, height, listOf(Rect(15, 35, 10, 10))), 1200)
        )
        val approaching = estimateRainCellMotions(samples, width, height, minPixels = 10).first()
        val impact = evaluateRainCellLocalityImpact(approaching)
        assertEquals(RadarImpactKind.IMPACT, impact.kind)
        assertTrue(impact.windowStart?.let { it in listOf(30, 45, 60) } == true)
        assertTrue((impact.windowEnd ?: 0) >= (impact.windowStart ?: 0))
        assertTrue(impact.relevanceScore > .75)

        val movingAway = approaching.copy(
            motion = approaching.motion!!.copy(vx = -kotlin.math.abs(approaching.motion.vx))
        )
        val away = evaluateRainCellLocalityImpact(movingAway)
        assertEquals(RadarImpactKind.AWAY, away.kind)
        assertTrue(away.relevanceScore < impact.relevanceScore)
    }

    @Test
    fun `exclusive historical matching prevents split cells sharing one parent`() {
        val width = 64
        val height = 48
        val samples = listOf(
            RadarMaskSample(paint(width, height, listOf(Rect(20, 18, 22, 10))), 0),
            RadarMaskSample(paint(width, height, listOf(Rect(18, 18, 8, 10), Rect(36, 18, 8, 10))), 600)
        )
        val tracked = estimateRainCellMotions(samples, width, height, minPixels = 10, maxCells = 4)
        assertEquals(1, tracked.size)
    }

    @Test
    fun `stable identity is preserved when radar analysis zoom changes`() {
        fun cell(x: Int, y: Int, w: Int, h: Int): RadarRainCell =
            extractRainCells(paint(512, 512, listOf(Rect(x, y, w, h))), 512, 512, minPixels = 1).first()

        val nearCell = cell(300, 240, 20, 16).copy(
            motion = RadarCellMotion(
                vx = .1, vy = .02, confidence = .8, history = 2, spanMinutes = 10.0,
                method = "centroid", centroidVx = .1, centroidVy = .02, advection = null,
                evolution = RadarEvolution(0.0, 0.0, 0.0, .8)
            )
        )
        val first = stabilizeRainCellIdentities(
            listOf(nearCell), latitude = 48.85, radarZoom = 7, time = 1200, nextId = 1
        )
        assertEquals("Z1", first.cells.first().stableLabel)

        val wideCell = cell(267, 252, 5, 4).copy(
            motion = nearCell.motion!!.copy(vx = .025, vy = .005, centroidVx = .025, centroidVy = .005)
        )
        val second = stabilizeRainCellIdentities(
            listOf(wideCell), first.registry, latitude = 48.85, radarZoom = 5,
            time = 1200, nextId = first.nextId
        )
        assertEquals("Z1", second.cells.first().stableLabel)
        assertEquals(first.cells.first().colorIndex, second.cells.first().colorIndex)
    }

    @Test
    fun `wide supplement excludes cells overlapping canonical high resolution core`() {
        fun cellAt(x: Int, y: Int, w: Int = 30, h: Int = 30): RadarRainCell =
            extractRainCells(paint(512, 512, listOf(Rect(x - w / 2, y - h / 2, w, h))), 512, 512, minPixels = 1).first()

        val peripheral = filterPeripheralRainCells(
            listOf(cellAt(256, 256, 40, 40), cellAt(470, 256, 20, 20)),
            latitude = 48.8,
            radarZoom = 5
        )
        assertEquals(1, peripheral.size)
        assertTrue(peripheral.first().centroid.x > 450)
    }

    @Test
    fun `range geometry keeps RainViewer and base map at same displayed zoom`() {
        RadarRange.entries.forEach { range ->
            var scale = range.radarScale
            var power = 0
            while (scale > 1 && scale % 2 == 0) {
                scale /= 2
                power++
            }
            assertEquals(1, scale)
            assertEquals(range.mapZoom, range.radarZoom + power)
        }
        assertTrue(RadarRange.NEAR.mapZoom > RadarRange.REGIONAL.mapZoom)
        assertTrue(RadarRange.REGIONAL.mapZoom > RadarRange.WIDE.mapZoom)
    }

    @Test
    fun `canonical display geometry matches web zoom rules`() {
        val near = radarNowcastDisplayGeometry(RadarRange.NEAR, 1000.0, 620.0)
        val regional = radarNowcastDisplayGeometry(RadarRange.REGIONAL, 1000.0, 620.0)
        val wide = radarNowcastDisplayGeometry(RadarRange.WIDE, 1000.0, 620.0)
        assertEquals(4.0, near.sourceScale, 0.0)
        assertEquals(2.0, regional.sourceScale, 0.0)
        assertEquals(.5, wide.sourceScale, 0.0)
        assertEquals(RADAR_ANALYSIS_ZOOM, wide.analysisZoom)
        assertEquals(listOf(6, 7, 8, 9, 10, 11, 12), recentRadarFrameIndices(13, 7))
        assertEquals(listOf(0, 1, 2, 3, 4), recentRadarFrameIndices(5, 7))
    }

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int)

    private fun paint(width: Int, height: Int, rects: List<Rect>): ByteArray {
        val mask = ByteArray(width * height)
        rects.forEach { rect ->
            for (y in rect.y until rect.y + rect.height) for (x in rect.x until rect.x + rect.width) {
                if (x in 0 until width && y in 0 until height) mask[y * width + x] = 1
            }
        }
        return mask
    }
}
