package com.meteocompare.app.domain.radar

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private fun clamp(value: Double, min: Double, max: Double): Double = kotlin.math.max(min, kotlin.math.min(max, value))
private fun clampLat(value: Double): Double = clamp(value, -85.05112878, 85.05112878)

private fun rgbToHue(r: Int, g: Int, b: Int): Double? {
    val rn = r / 255.0
    val gn = g / 255.0
    val bn = b / 255.0
    val max = max(rn, max(gn, bn))
    val min = min(rn, min(gn, bn))
    val delta = max - min
    if (delta <= 1e-6) return null
    val hue = when (max) {
        rn -> ((gn - bn) / delta) % 6.0
        gn -> (bn - rn) / delta + 2.0
        else -> (rn - gn) / delta + 4.0
    }
    val degrees = hue * 60.0
    return if (degrees < 0) degrees + 360.0 else degrees
}

fun isRainRadarPixel(
    r: Int,
    g: Int,
    b: Int,
    a: Int,
    alphaMin: Int = 36,
    chromaMin: Int = 26,
    saturationMin: Double = .24,
    valueMin: Double = .20
): Boolean {
    if (a < alphaMin) return false
    val max = max(r, max(g, b))
    val min = min(r, min(g, b))
    val chroma = max - min
    val value = max / 255.0
    if (value < valueMin || chroma < chromaMin) return false
    val saturation = if (max != 0) chroma.toDouble() / max else 0.0
    if (saturation < saturationMin) return false
    val hue = rgbToHue(r, g, b) ?: return false
    return hue in 175.0..260.0 || hue in 85.0..<175.0 || hue in 0.0..70.0 || hue in 260.0..330.0
}

fun radarMaskFromArgb(image: RadarImage): ByteArray {
    val mask = ByteArray(image.width * image.height)
    image.argb.forEachIndexed { index, color ->
        val a = color ushr 24 and 0xff
        val r = color ushr 16 and 0xff
        val g = color ushr 8 and 0xff
        val b = color and 0xff
        if (isRainRadarPixel(r, g, b, a)) mask[index] = 1
    }
    return refineRainMask(mask, image.width, image.height)
}

fun refineRainMask(mask: ByteArray, width: Int, height: Int, minNeighbors: Int = 2): ByteArray {
    if (mask.size != width * height) return ByteArray(0)
    val result = mask.copyOf()
    val threshold = min(8, max(0, minNeighbors))
    if (threshold == 0) return result
    for (y in 0 until height) for (x in 0 until width) {
        val index = y * width + x
        if (mask[index].toInt() == 0) continue
        var neighbors = 0
        for (oy in -1..1) for (ox in -1..1) {
            if (ox == 0 && oy == 0) continue
            val nx = x + ox
            val ny = y + oy
            if (nx !in 0 until width || ny !in 0 until height) continue
            if (mask[ny * width + nx].toInt() != 0) neighbors++
        }
        if (neighbors < threshold) result[index] = 0
    }
    return result
}

fun extractRainCells(
    mask: ByteArray,
    width: Int = RADAR_ANALYSIS_SIZE,
    height: Int = RADAR_ANALYSIS_SIZE,
    minPixels: Int = RADAR_MIN_CELL_PIXELS,
    maxCells: Int = 16
): List<RadarRainCell> {
    if (mask.size != width * height) return emptyList()
    val visited = BooleanArray(mask.size)
    val cells = mutableListOf<RadarRainCell>()
    // One reusable BFS buffer avoids allocating a 512×512 IntArray for every
    // small connected component in a noisy radar frame.
    val queue = IntArray(mask.size)
    val ox = intArrayOf(0, -1, 1, 0)
    val oy = intArrayOf(-1, 0, 0, 1)
    for (start in mask.indices) {
        if (mask[start].toInt() == 0 || visited[start]) continue
        var head = 0
        var tail = 0
        queue[tail++] = start
        visited[start] = true
        val pixels = ArrayList<Int>()
        var sx = 0.0
        var sy = 0.0
        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0
        while (head < tail) {
            val index = queue[head++]
            val x = index % width
            val y = index / width
            pixels += index
            sx += x
            sy += y
            minX = min(minX, x)
            maxX = max(maxX, x)
            minY = min(minY, y)
            maxY = max(maxY, y)
            for (n in 0..3) {
                val nx = x + ox[n]
                val ny = y + oy[n]
                if (nx !in 0 until width || ny !in 0 until height) continue
                val ni = ny * width + nx
                if (mask[ni].toInt() != 0 && !visited[ni]) {
                    visited[ni] = true
                    queue[tail++] = ni
                }
            }
        }
        if (pixels.size < minPixels) continue
        val boundary = pixels.filter { index ->
            val x = index % width
            val y = index / width
            x == 0 || y == 0 || x == width - 1 || y == height - 1 ||
                mask[index - 1].toInt() == 0 || mask[index + 1].toInt() == 0 ||
                mask[index - width].toInt() == 0 || mask[index + width].toInt() == 0
        }.toIntArray()
        cells += RadarRainCell(
            id = "cell-${cells.size + 1}",
            pixels = pixels.toIntArray(),
            boundary = boundary,
            count = pixels.size,
            width = width,
            height = height,
            centroid = RadarPoint(sx / pixels.size, sy / pixels.size),
            bbox = RadarBoundingBox(minX, minY, maxX, maxY, maxX - minX + 1, maxY - minY + 1)
        )
    }
    return cells.sortedByDescending { it.count }.take(maxCells)
}

private fun cellMatchScore(reference: RadarRainCell, candidate: RadarRainCell, dtMinutes: Double): Double {
    val distance = hypot(reference.centroid.x - candidate.centroid.x, reference.centroid.y - candidate.centroid.y)
    val areaRatio = min(reference.count, candidate.count).toDouble() / max(reference.count, candidate.count)
    val referenceRadius = .5 * hypot(reference.bbox.width.toDouble(), reference.bbox.height.toDouble())
    val candidateRadius = .5 * hypot(candidate.bbox.width.toDouble(), candidate.bbox.height.toDouble())
    val maxDistance = max(8.0, (referenceRadius + candidateRadius) * .72 + max(1.0, dtMinutes) * 1.8)
    if (distance > maxDistance || areaRatio < .16) return Double.NEGATIVE_INFINITY
    val distanceScore = 1 - clamp(distance / maxDistance, 0.0, 1.0)
    val widthRatio = min(reference.bbox.width, candidate.bbox.width).toDouble() / max(reference.bbox.width, candidate.bbox.width)
    val heightRatio = min(reference.bbox.height, candidate.bbox.height).toDouble() / max(reference.bbox.height, candidate.bbox.height)
    val shapeScore = (widthRatio + heightRatio) / 2
    return distanceScore * .52 + areaRatio * .30 + shapeScore * .18
}

private fun mean(values: List<Double>): Double? = values.filter(Double::isFinite).takeIf { it.isNotEmpty() }?.average()
private fun median(values: List<Double>): Double? {
    val rows = values.filter(Double::isFinite).sorted()
    if (rows.isEmpty()) return null
    val middle = rows.size / 2
    return if (rows.size % 2 == 1) rows[middle] else (rows[middle - 1] + rows[middle]) / 2
}

private fun regressionVelocity(points: List<RadarTrackPoint>, value: (RadarTrackPoint) -> Double): Double {
    if (points.size < 2) return 0.0
    val base = points.last().time
    val ts = points.map { (it.time - base) / 60.0 }
    val meanT = ts.average()
    val values = points.map(value)
    val meanV = values.average()
    var num = 0.0
    var den = 0.0
    for (i in points.indices) {
        val dt = ts[i] - meanT
        num += dt * (values[i] - meanV)
        den += dt * dt
    }
    return if (den != 0.0) num / den else 0.0
}

private fun trackPoint(cell: RadarRainCell, time: Long) = RadarTrackPoint(
    time = time,
    x = cell.centroid.x,
    y = cell.centroid.y,
    count = cell.count,
    bboxWidth = cell.bbox.width,
    bboxHeight = cell.bbox.height,
    logCount = ln(max(1, cell.count).toDouble()),
    logWidth = ln(max(1, cell.bbox.width).toDouble()),
    logHeight = ln(max(1, cell.bbox.height).toDouble())
)

data class RadarTranslation(val dx: Int, val dy: Int, val score: Double, val overlap: Double)

fun estimateRainCellTranslation(previous: RadarRainCell, next: RadarRainCell, searchRadius: Int = 6): RadarTranslation? {
    if (previous.pixels.isEmpty() || next.pixels.isEmpty() || previous.width != next.width || previous.height != next.height) return null
    val width = previous.width
    val height = previous.height
    val nextMask = ByteArray(width * height)
    next.pixels.forEach { if (it in nextMask.indices) nextMask[it] = 1 }
    val expectedDx = (next.centroid.x - previous.centroid.x).roundToInt()
    val expectedDy = (next.centroid.y - previous.centroid.y).roundToInt()
    val radius = max(2, min(10, searchRadius))
    val stride = max(1, previous.pixels.size / 5000)
    var best: RadarTranslation? = null
    for (dy in expectedDy - radius..expectedDy + radius) for (dx in expectedDx - radius..expectedDx + radius) {
        var intersection = 0
        var sampled = 0
        var pos = 0
        while (pos < previous.pixels.size) {
            val index = previous.pixels[pos]
            val x = index % width
            val y = index / width
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until width && ny in 0 until height) {
                sampled++
                if (nextMask[ny * width + nx].toInt() != 0) intersection++
            }
            pos += stride
        }
        if (sampled == 0) continue
        val overlap = intersection.toDouble() / sampled
        val centroidPenalty = hypot((dx - expectedDx).toDouble(), (dy - expectedDy).toDouble()) / (radius * 2 + 1)
        val score = overlap - centroidPenalty * .05
        val currentBest = best
        if (currentBest == null || score > currentBest.score) best = RadarTranslation(dx, dy, score, overlap)
    }
    return best?.takeIf { it.overlap >= .14 }
}

private fun weightedMotion(vectors: List<RadarMotionVector>, maxSpeedPxPerMinute: Double): RadarAdvection? {
    val eligible = vectors.filter { hypot(it.vx, it.vy) <= maxSpeedPxPerMinute }
    if (eligible.isEmpty()) return null
    val medianVx = median(eligible.map { it.vx }) ?: 0.0
    val medianVy = median(eligible.map { it.vy }) ?: 0.0
    val residuals = eligible.map { hypot(it.vx - medianVx, it.vy - medianVy) }
    val medianResidual = median(residuals) ?: 0.0
    val mad = median(residuals.map { abs(it - medianResidual) }) ?: 0.0
    val limit = max(.08, medianResidual + max(.08, mad * 3))
    val inliers = if (eligible.size >= 3) eligible.filterIndexed { index, _ -> residuals[index] <= limit } else eligible
    if (inliers.isEmpty()) return null
    val weights = inliers.mapIndexed { index, row ->
        max(.05, row.score) * (.58 + .42 * (index + 1) / inliers.size) * clamp(20 / max(5.0, row.dtMinutes), .45, 1.0)
    }
    val total = weights.sum()
    val vx = inliers.mapIndexed { i, row -> row.vx * weights[i] }.sum() / total
    val vy = inliers.mapIndexed { i, row -> row.vy * weights[i] }.sum() / total
    val speed = hypot(vx, vy)
    val variation = mean(inliers.map { hypot(it.vx - vx, it.vy - vy) }) ?: 0.0
    val consistency = clamp(1 - variation / (speed * 1.5 + .08), 0.0, 1.0)
    val overlapConfidence = mean(inliers.map { it.score }) ?: 0.0
    val inlierShare = inliers.size.toDouble() / max(1, vectors.size)
    val confidence = clamp(overlapConfidence * .68 + consistency * .22 + inlierShare * .10, 0.0, 1.0)
    return RadarAdvection(vx, vy, confidence, inliers, vectors.filterNot(inliers::contains))
}

private fun observedTrackFromAdvection(history: List<Pair<RadarRainCell, Long>>, vectors: List<RadarMotionVector>): List<RadarTrackPoint> {
    if (history.size < 2 || vectors.isEmpty()) return emptyList()
    val byPair = vectors.associateBy { "${it.fromTime}:${it.toTime}" }
    val latest = history.last()
    var x = latest.first.centroid.x
    var y = latest.first.centroid.y
    val points = mutableListOf(RadarTrackPoint(latest.second, x, y))
    for (index in history.lastIndex downTo 1) {
        val previous = history[index - 1]
        val next = history[index]
        val vector = byPair["${previous.second}:${next.second}"] ?: break
        x -= vector.dx
        y -= vector.dy
        points.add(0, RadarTrackPoint(previous.second, x, y))
    }
    return points.takeIf { it.size >= 2 } ?: emptyList()
}

fun estimateRainCellMotions(
    samples: List<RadarMaskSample>,
    width: Int = RADAR_ANALYSIS_SIZE,
    height: Int = RADAR_ANALYSIS_SIZE,
    minPixels: Int = RADAR_MIN_CELL_PIXELS,
    maxCells: Int = 8,
    maxTrackGapMinutes: Double = 25.0,
    maxSpeedPxPerMinute: Double = 3.2
): List<RadarRainCell> {
    data class Row(val time: Long, val cells: List<RadarRainCell>)
    data class State(
        val cell: RadarRainCell,
        var reference: RadarRainCell,
        var referenceTime: Long,
        val history: MutableList<Pair<RadarRainCell, Long>>,
        val used: MutableList<Double>
    )
    val rows = samples.filter { it.mask.size == width * height }.takeLast(9).map {
        Row(it.timeEpochSeconds, extractRainCells(it.mask, width, height, minPixels, maxCells * 2))
    }
    if (rows.size < 2) return emptyList()
    val latest = rows.last()
    val states = latest.cells.take(maxCells).map { State(it, it, latest.time, mutableListOf(it to latest.time), mutableListOf()) }
    for (rowIndex in rows.lastIndex - 1 downTo 0) {
        data class Proposal(val stateIndex: Int, val candidateIndex: Int, val score: Double)
        val candidates = rows[rowIndex].cells
        val proposals = mutableListOf<Proposal>()
        states.forEachIndexed { stateIndex, state ->
            val dtMinutes = (state.referenceTime - rows[rowIndex].time) / 60.0
            if (dtMinutes <= 0 || dtMinutes > maxTrackGapMinutes) return@forEachIndexed
            candidates.forEachIndexed { candidateIndex, candidate ->
                val score = cellMatchScore(state.reference, candidate, dtMinutes)
                if (score >= .42) proposals += Proposal(stateIndex, candidateIndex, score)
            }
        }
        val claimedStates = mutableSetOf<Int>()
        val claimedCandidates = mutableSetOf<Int>()
        proposals.sortedByDescending { it.score }.forEach { proposal ->
            if (!claimedStates.add(proposal.stateIndex) || !claimedCandidates.add(proposal.candidateIndex)) return@forEach
            val state = states[proposal.stateIndex]
            val candidate = candidates[proposal.candidateIndex]
            state.reference = candidate
            state.referenceTime = rows[rowIndex].time
            state.used += proposal.score
            state.history += candidate to rows[rowIndex].time
        }
    }
    val result = mutableListOf<RadarRainCell>()
    states.forEach { state ->
        val cell = state.cell
        val history = state.history.sortedBy { it.second }
        val track = history.map { (rainCell, time) -> trackPoint(rainCell, time) }
        if (track.size < 2) return@forEach
        val centroidVx = regressionVelocity(track) { it.x }
        val centroidVy = regressionVelocity(track) { it.y }
        val spanMinutes = (track.last().time - track.first().time) / 60.0
        if (spanMinutes <= 0) return@forEach
        val vectors = mutableListOf<RadarMotionVector>()
        for (index in 1..history.lastIndex) {
            val previous = history[index - 1]
            val next = history[index]
            val dtMinutes = (next.second - previous.second) / 60.0
            if (dtMinutes <= 0 || dtMinutes > maxTrackGapMinutes) continue
            val translation = estimateRainCellTranslation(previous.first, next.first, min(10, max(6, ceil(dtMinutes / 3).toInt()))) ?: continue
            val vx = translation.dx / dtMinutes
            val vy = translation.dy / dtMinutes
            if (hypot(vx, vy) > maxSpeedPxPerMinute) continue
            vectors += RadarMotionVector(vx, vy, translation.overlap, dtMinutes, translation.dx.toDouble(), translation.dy.toDouble(), previous.second, next.second)
        }
        val advection = weightedMotion(vectors, maxSpeedPxPerMinute)
        val matchConfidence = mean(state.used) ?: .45
        val historyConfidence = clamp((track.size - 1) / 4.0, 0.0, 1.0)
        var vx = centroidVx
        var vy = centroidVy
        var method = "centroid"
        if (advection != null && advection.confidence >= .18) {
            val weight = clamp(.66 + advection.confidence * .28, .70, .94)
            vx = centroidVx * (1 - weight) + advection.vx * weight
            vy = centroidVy * (1 - weight) + advection.vy * weight
            method = "cell-advection"
        }
        val speed = hypot(vx, vy)
        if (speed > maxSpeedPxPerMinute) {
            val scale = maxSpeedPxPerMinute / speed
            vx *= scale
            vy *= scale
        }
        val observed = observedTrackFromAdvection(history, advection?.vectors.orEmpty())
        val fitTrack = if (observed.size >= 2) observed else track
        val residuals = fitTrack.map { point ->
            val dt = (point.time - fitTrack.last().time) / 60.0
            hypot(point.x - (cell.centroid.x + vx * dt), point.y - (cell.centroid.y + vy * dt))
        }
        val finalSpeed = hypot(vx, vy)
        val residual = mean(residuals) ?: 0.0
        val residualConfidence = clamp(1 - residual / (2.2 + finalSpeed * 7), 0.0, 1.0)
        val advectionConfidence = advection?.confidence ?: 0.0
        val confidence = clamp(matchConfidence * .28 + historyConfidence * .20 + residualConfidence * .20 + advectionConfidence * .32, 0.0, 1.0)
        val logWidthRate = regressionVelocity(track) { it.logWidth }
        val logHeightRate = regressionVelocity(track) { it.logHeight }
        val logAreaRate = regressionVelocity(track) { it.logCount }
        val evolutionVariation = mean(track.drop(1).mapIndexed { index, point ->
            abs((point.logCount - track[index].logCount) / max(1.0, (point.time - track[index].time) / 60.0) - logAreaRate)
        }) ?: 0.0
        val evolutionConfidence = clamp(historyConfidence * (1 - evolutionVariation / (abs(logAreaRate) + .035)), .2, 1.0)
        result += cell.copy(
            motion = RadarCellMotion(
                vx, vy, confidence, track.size, spanMinutes, method, centroidVx, centroidVy, advection,
                RadarEvolution(logWidthRate, logHeightRate, logAreaRate, evolutionConfidence)
            ),
            track = track,
            observedTrack = observed
        )
    }
    return result
}

fun projectRainCell(cell: RadarRainCell, horizonMinutes: Double): RadarProjection? {
    val motion = cell.motion ?: return null
    if (!horizonMinutes.isFinite()) return null
    val evolution = motion.evolution
    val resolutionScale = cell.width / 320.0
    val evolutionConfidence = clamp(evolution.confidence, 0.0, 1.0)
    val evolutionWeight = clamp(motion.confidence * .6 + evolutionConfidence * .4, 0.0, 1.0)
    val cap = min(1.0, max(0.0, horizonMinutes) / 60)
    val rawScaleX = exp(clamp(evolution.logWidthRate * horizonMinutes * evolutionWeight, -.5, .55))
    val rawScaleY = exp(clamp(evolution.logHeightRate * horizonMinutes * evolutionWeight, -.5, .55))
    val rawAreaFactor = exp(clamp(evolution.logAreaRate * horizonMinutes * evolutionWeight, -1.15, .8))
    val targetAreaFactor = clamp(rawAreaFactor, .32, 2.1)
    val shapeArea = max(.01, rawScaleX * rawScaleY)
    val areaCorrection = sqrt(targetAreaFactor / shapeArea)
    val scaleX = clamp(rawScaleX * areaCorrection, .58, 1.55)
    val scaleY = clamp(rawScaleY * areaCorrection, .58, 1.55)
    val areaFactor = clamp(scaleX * scaleY, .34, 2.2)
    val survivalProbability = clamp(if (areaFactor < 1) .35 + .65 * areaFactor else 1.0, 0.0, 1.0)
    val dissipating = areaFactor < .72 && evolution.logAreaRate < -.004
    val developing = areaFactor > 1.22 && evolution.logAreaRate > .003
    val projectionDamping = clamp(1 - (1 - motion.confidence) * .18 * cap, .82, 1.0)
    val dx = motion.vx * horizonMinutes * projectionDamping
    val dy = motion.vy * horizonMinutes * projectionDamping
    val uncertainty = resolutionScale * (
        1.25 + (horizonMinutes / 15) * (1.15 + (1 - motion.confidence) * 2.15) +
            cap * abs(scaleX - scaleY) * 2.2 + hypot(motion.vx, motion.vy) * horizonMinutes * (1 - projectionDamping) * .16
        )
    return RadarProjection(horizonMinutes, dx, dy, cell.centroid.x + dx, cell.centroid.y + dy, uncertainty, motion.confidence, scaleX, scaleY, areaFactor, survivalProbability, dissipating, developing, projectionDamping)
}

private data class Edge(val sx: Int, val sy: Int, val ex: Int, val ey: Int, val dir: Int, var used: Boolean = false)
private fun edgeDirection(sx: Int, sy: Int, ex: Int, ey: Int) = when {
    ex > sx -> 0
    ey > sy -> 1
    ex < sx -> 2
    else -> 3
}

private fun maskForCell(cell: RadarRainCell, expandPx: Double = 0.0): ByteArray {
    val mask = ByteArray(cell.width * cell.height)
    cell.pixels.forEach { if (it in mask.indices) mask[it] = 1 }
    val radius = max(0, ceil(expandPx).toInt())
    if (radius == 0) return mask
    val seeds = if (cell.boundary.isNotEmpty()) cell.boundary else cell.pixels
    val radiusSq = radius * radius
    seeds.forEach { index ->
        val cx = index % cell.width
        val cy = index / cell.width
        for (oy in -radius..radius) {
            val y = cy + oy
            if (y !in 0 until cell.height) continue
            val span = floor(sqrt(max(0, radiusSq - oy * oy).toDouble())).toInt()
            for (ox in -span..span) {
                val x = cx + ox
                if (x in 0 until cell.width) mask[y * cell.width + x] = 1
            }
        }
    }
    return mask
}

fun rainCellContours(cell: RadarRainCell, expandPx: Double = 0.0): List<List<RadarPoint>> {
    val mask = maskForCell(cell, expandPx)
    val edges = mutableListOf<Edge>()
    val byStart = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
    fun push(sx: Int, sy: Int, ex: Int, ey: Int) {
        val edge = Edge(sx, sy, ex, ey, edgeDirection(sx, sy, ex, ey))
        val idx = edges.size
        edges += edge
        byStart.getOrPut(sx to sy) { mutableListOf() } += idx
    }
    for (y in 0 until cell.height) for (x in 0 until cell.width) {
        val index = y * cell.width + x
        if (mask[index].toInt() == 0) continue
        if (y == 0 || mask[index - cell.width].toInt() == 0) push(x, y, x + 1, y)
        if (x == cell.width - 1 || mask[index + 1].toInt() == 0) push(x + 1, y, x + 1, y + 1)
        if (y == cell.height - 1 || mask[index + cell.width].toInt() == 0) push(x + 1, y + 1, x, y + 1)
        if (x == 0 || mask[index - 1].toInt() == 0) push(x, y + 1, x, y)
    }
    val rank = mapOf(1 to 0, 0 to 1, 3 to 2, 2 to 3)
    val loops = mutableListOf<List<RadarPoint>>()
    edges.indices.forEach { startIndex ->
        if (edges[startIndex].used) return@forEach
        var edge = edges[startIndex]
        val startX = edge.sx
        val startY = edge.sy
        val points = mutableListOf<RadarPoint>()
        var guard = 0
        while (!edge.used && guard++ < edges.size + 4) {
            edge.used = true
            points += RadarPoint(edge.sx.toDouble(), edge.sy.toDouble())
            val endX = edge.ex
            val endY = edge.ey
            if (endX == startX && endY == startY) break
            val candidates = byStart[endX to endY].orEmpty().map { edges[it] }.filterNot { it.used }.toMutableList()
            if (candidates.isEmpty()) break
            val previousDirection = edge.dir
            candidates.sortBy { rank[(it.dir - previousDirection + 4) % 4] ?: 9 }
            edge = candidates.first()
        }
        if (points.size < 4) return@forEach
        val reduced = points.filterIndexed { index, cur ->
            val prev = points[(index - 1 + points.size) % points.size]
            val next = points[(index + 1) % points.size]
            (cur.x - prev.x) * (next.y - cur.y) - (cur.y - prev.y) * (next.x - cur.x) != 0.0
        }
        if (reduced.size < 4) return@forEach
        val smooth = mutableListOf<RadarPoint>()
        reduced.indices.forEach { i ->
            val a = reduced[i]
            val b = reduced[(i + 1) % reduced.size]
            smooth += RadarPoint(a.x * .75 + b.x * .25, a.y * .75 + b.y * .25)
            smooth += RadarPoint(a.x * .25 + b.x * .75, a.y * .25 + b.y * .75)
        }
        loops += smooth
    }
    return loops
}

private fun pointSegmentDistance(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax
    val dy = by - ay
    val lengthSq = dx * dx + dy * dy
    if (lengthSq == 0.0) return hypot(px - ax, py - ay)
    val t = clamp(((px - ax) * dx + (py - ay) * dy) / lengthSq, 0.0, 1.0)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

private fun pointInLoop(point: RadarPoint, loop: List<RadarPoint>): Boolean {
    var inside = false
    var j = loop.lastIndex
    for (i in loop.indices) {
        val a = loop[i]
        val b = loop[j]
        val cross = ((a.y > point.y) != (b.y > point.y)) &&
            point.x < (b.x - a.x) * (point.y - a.y) / ((b.y - a.y).takeIf { abs(it) > 1e-9 } ?: 1e-9) + a.x
        if (cross) inside = !inside
        j = i
    }
    return inside
}

private fun pointToCellDistance(cell: RadarRainCell, x: Double, y: Double): Double {
    val loops = rainCellContours(cell)
    if (loops.isEmpty()) return Double.POSITIVE_INFINITY
    if (loops.any { pointInLoop(RadarPoint(x, y), it) }) return 0.0
    var best = Double.POSITIVE_INFINITY
    loops.forEach { loop ->
        loop.indices.forEach { i ->
            val a = loop[i]
            val b = loop[(i + 1) % loop.size]
            best = min(best, pointSegmentDistance(x, y, a.x, a.y, b.x, b.y))
        }
    }
    return best
}

fun evaluateRainCellLocalityImpact(
    cell: RadarRainCell,
    cityX: Double = cell.width / 2.0,
    cityY: Double = cell.height / 2.0,
    horizons: List<Int> = RADAR_PROJECTION_HORIZONS,
    localityRadiusPx: Double = 2.0
): RadarCellImpact {
    if (cell.motion == null) return RadarCellImpact(RadarImpactKind.UNAVAILABLE, 0.0)
    val currentDistance = pointToCellDistance(cell, cityX, cityY)
    val rows = horizons.mapNotNull { horizon ->
        val projection = projectRainCell(cell, horizon.toDouble()) ?: return@mapNotNull null
        val sx = max(.01, projection.scaleX)
        val sy = max(.01, projection.scaleY)
        val sourceX = cell.centroid.x + (cityX - projection.x) / sx
        val sourceY = cell.centroid.y + (cityY - projection.y) / sy
        val distance = pointToCellDistance(cell, sourceX, sourceY) * min(sx, sy)
        val threshold = localityRadiusPx + projection.uncertaintyPx
        val closeness = clamp(1 - distance / max(1.0, threshold * 1.8), 0.0, 1.0)
        val probability = clamp(closeness * (.52 + .48 * projection.confidence) * projection.survivalProbability, 0.0, 1.0)
        RadarImpactRow(horizon, distance, threshold, probability, distance <= threshold && projection.survivalProbability >= .34)
    }
    if (rows.isEmpty()) return RadarCellImpact(RadarImpactKind.UNAVAILABLE, 0.0)
    val hits = rows.filter { it.impact }
    val minRow = rows.minBy { it.distance }
    val lastDistance = rows.last().distance
    val approachGain = currentDistance - minRow.distance
    val approachShare = if (currentDistance.isFinite() && currentDistance > 0) clamp(approachGain / currentDistance, 0.0, 1.0) else 0.0
    val movingToward = lastDistance < currentDistance
    val maxProbability = rows.maxOf { it.probability }
    if (hits.isNotEmpty()) {
        val hitProbability = hits.maxOf { it.probability }
        return RadarCellImpact(
            if (currentDistance <= localityRadiusPx) RadarImpactKind.CURRENT else RadarImpactKind.IMPACT,
            clamp(.78 + hitProbability * .22, 0.0, 1.0), hits.first().horizon, hits.last().horizon,
            hitProbability, minRow.distance, currentDistance, true, rows
        )
    }
    if (currentDistance <= localityRadiusPx) return RadarCellImpact(
        RadarImpactKind.CURRENT_LEAVING, .9, 0, 0, maxProbability, minRow.distance, currentDistance, false, rows
    )
    if (movingToward && approachShare > .08) return RadarCellImpact(
        RadarImpactKind.APPROACHING, clamp(.28 + approachShare * .5, 0.0, .76), null, null,
        maxProbability, minRow.distance, currentDistance, true, rows
    )
    return RadarCellImpact(RadarImpactKind.AWAY, .12, null, null, maxProbability, minRow.distance, currentDistance, false, rows)
}

fun mapResolutionKm(latitude: Double, zoom: Int): Double =
    156543.03392 * cos(clampLat(latitude) * Math.PI / 180) / (1 shl zoom) / 1000

private fun geoSignature(cell: RadarRainCell, latitude: Double, radarZoom: Int, time: Long): RadarIdentityRegistryEntry {
    val pixelKm = mapResolutionKm(latitude, radarZoom)
    val xKm = (cell.centroid.x - cell.width / 2.0) * pixelKm
    val yKm = (cell.centroid.y - cell.height / 2.0) * pixelKm
    return RadarIdentityRegistryEntry(
        xKm, yKm, max(.01, cell.count * pixelKm * pixelKm),
        (cell.motion?.vx ?: 0.0) * pixelKm, (cell.motion?.vy ?: 0.0) * pixelKm,
        time, cell.stableId ?: 0, cell.colorIndex ?: 0
    )
}

fun stabilizeRainCellIdentities(
    cells: List<RadarRainCell>,
    registry: List<RadarIdentityRegistryEntry> = emptyList(),
    latitude: Double = 0.0,
    radarZoom: Int = 7,
    time: Long = 0,
    nextId: Int = 1,
    maxMisses: Int = 2
): RadarIdentityResult {
    data class Current(val cell: RadarRainCell, val signature: RadarIdentityRegistryEntry)
    data class Proposal(val currentIndex: Int, val previousIndex: Int, val score: Double)
    val current = cells.map { Current(it, geoSignature(it, latitude, radarZoom, time)) }
    val previous = registry.filter { it.misses <= maxMisses }
    val proposals = mutableListOf<Proposal>()
    current.forEachIndexed { ci, row -> previous.forEachIndexed { pi, old ->
        val now = row.signature
        val dtMinutes = max(0.0, (time - old.time) / 60.0)
        val predX = old.xKm + old.vxKm * dtMinutes
        val predY = old.yKm + old.vyKm * dtMinutes
        val distance = hypot(now.xKm - predX, now.yKm - predY)
        val radiusKm = max(6.0, sqrt(max(now.areaKm2, old.areaKm2) / Math.PI) * 2.8 + hypot(old.vxKm, old.vyKm) * dtMinutes * .7)
        val distanceScore = 1 - clamp(distance / radiusKm, 0.0, 1.0)
        val areaRatio = min(now.areaKm2, old.areaKm2) / max(now.areaKm2, old.areaKm2)
        val velocityScale = max(.08, hypot(now.vxKm, now.vyKm) + hypot(old.vxKm, old.vyKm))
        val velocityDelta = hypot(now.vxKm - old.vxKm, now.vyKm - old.vyKm)
        val velocityScore = 1 - clamp(velocityDelta / (velocityScale * 1.8), 0.0, 1.0)
        val score = distanceScore * .62 + areaRatio * .25 + velocityScore * .13
        if (score >= .30) proposals += Proposal(ci, pi, score)
    } }
    val claimedCurrent = mutableSetOf<Int>()
    val claimedPrevious = mutableSetOf<Int>()
    val matches = mutableMapOf<Int, RadarIdentityRegistryEntry>()
    proposals.sortedByDescending { it.score }.forEach { proposal ->
        if (proposal.currentIndex in claimedCurrent || proposal.previousIndex in claimedPrevious) return@forEach
        claimedCurrent += proposal.currentIndex
        claimedPrevious += proposal.previousIndex
        matches[proposal.currentIndex] = previous[proposal.previousIndex]
    }
    var idCounter = max(nextId, (previous.maxOfOrNull { it.stableId } ?: 0) + 1)
    val assigned = current.mapIndexed { index, row ->
        val prior = matches[index]
        val stableId = prior?.stableId ?: idCounter++
        val colorIndex = prior?.colorIndex ?: ((stableId - 1) % 8)
        row.cell.copy(stableId = stableId, colorIndex = colorIndex, stableLabel = "Z$stableId")
    }
    val active = assigned.map { cell ->
        geoSignature(cell, latitude, radarZoom, time).copy(stableId = cell.stableId!!, colorIndex = cell.colorIndex!!, misses = 0)
    }
    val carried = previous.mapIndexedNotNull { index, row ->
        if (index in claimedPrevious) null else row.copy(misses = row.misses + 1).takeIf { it.misses <= maxMisses }
    }
    return RadarIdentityResult(assigned, active + carried, idCounter)
}

fun filterPeripheralRainCells(
    cells: List<RadarRainCell>,
    latitude: Double,
    radarZoom: Int = 5,
    primaryZoom: Int = RADAR_ANALYSIS_ZOOM,
    margin: Double = .88
): List<RadarRainCell> {
    val primaryHalfKm = RADAR_ANALYSIS_SIZE * .5 * mapResolutionKm(latitude, primaryZoom)
    return cells.filter { cell ->
        val pixelKm = mapResolutionKm(latitude, radarZoom)
        val cx = (cell.centroid.x - RADAR_ANALYSIS_SIZE / 2) * pixelKm
        val cy = (cell.centroid.y - RADAR_ANALYSIS_SIZE / 2) * pixelKm
        val radius = .58 * max(cell.bbox.width, cell.bbox.height) * pixelKm
        hypot(cx, cy) - radius > primaryHalfKm * margin
    }
}

fun radarNowcastDisplayGeometry(
    range: RadarRange,
    width: Double,
    height: Double,
    analysisZoom: Int = RADAR_ANALYSIS_ZOOM
): RadarDisplayGeometry {
    val sourceScale = 2.0.pow(range.mapZoom - analysisZoom)
    val sourceSize = RADAR_ANALYSIS_SIZE * sourceScale
    return RadarDisplayGeometry((width - sourceSize) / 2, (height - sourceSize) / 2, sourceScale, sourceSize, analysisZoom, range.mapZoom)
}

private fun Double.pow(power: Int): Double = Math.pow(this, power.toDouble())

fun recentRadarFrameIndices(frameCount: Int, limit: Int = 7): List<Int> {
    val count = max(0, frameCount)
    if (count == 0) return emptyList()
    val take = min(count, max(2, limit))
    return List(take) { count - take + it }
}
