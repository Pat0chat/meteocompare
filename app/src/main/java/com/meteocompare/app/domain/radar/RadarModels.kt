package com.meteocompare.app.domain.radar

import com.meteocompare.app.domain.model.City

const val RADAR_ANALYSIS_SIZE = 512
const val RADAR_ANALYSIS_ZOOM = 7
const val RADAR_MIN_CELL_PIXELS = 180
val RADAR_PROJECTION_HORIZONS = listOf(15, 30, 45, 60)

/** Same visual ranges as the web radar. */
enum class RadarRange(val mapZoom: Int, val radarZoom: Int, val radarScale: Int) {
    NEAR(mapZoom = 9, radarZoom = 7, radarScale = 4),
    REGIONAL(mapZoom = 8, radarZoom = 7, radarScale = 2),
    WIDE(mapZoom = 6, radarZoom = 5, radarScale = 2)
}

enum class RadarMode { OBSERVATION, PROJECTION }

data class RadarFrame(val timeEpochSeconds: Long, val path: String)

data class RadarMetadata(
    val host: String,
    val past: List<RadarFrame>,
    val generatedEpochSeconds: Long? = null
)

data class RadarImage(
    val width: Int,
    val height: Int,
    val argb: IntArray
) {
    init {
        require(width > 0 && height > 0)
        require(argb.size == width * height)
    }
}

data class RadarBaseTile(
    /** Offset from the locality centre in Web-Mercator pixels (= CSS px on the web map). */
    val leftFromCenter: Double,
    val topFromCenter: Double,
    val image: RadarImage
)

data class RadarPoint(val x: Double, val y: Double)

data class RadarBoundingBox(
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
    val width: Int,
    val height: Int
)

data class RadarEvolution(
    val logWidthRate: Double,
    val logHeightRate: Double,
    val logAreaRate: Double,
    val confidence: Double
)

data class RadarMotionVector(
    val vx: Double,
    val vy: Double,
    val score: Double,
    val dtMinutes: Double,
    val dx: Double,
    val dy: Double,
    val fromTime: Long,
    val toTime: Long
)

data class RadarAdvection(
    val vx: Double,
    val vy: Double,
    val confidence: Double,
    val vectors: List<RadarMotionVector>,
    val rejected: List<RadarMotionVector>
)

data class RadarTrackPoint(
    val time: Long,
    val x: Double,
    val y: Double,
    val count: Int = 0,
    val bboxWidth: Int = 0,
    val bboxHeight: Int = 0,
    val logCount: Double = 0.0,
    val logWidth: Double = 0.0,
    val logHeight: Double = 0.0
)

data class RadarCellMotion(
    val vx: Double,
    val vy: Double,
    val confidence: Double,
    val history: Int,
    val spanMinutes: Double,
    val method: String,
    val centroidVx: Double,
    val centroidVy: Double,
    val advection: RadarAdvection?,
    val evolution: RadarEvolution
)

data class RadarImpactRow(
    val horizon: Int,
    val distance: Double,
    val threshold: Double,
    val probability: Double,
    val impact: Boolean
)

enum class RadarImpactKind { UNAVAILABLE, CURRENT, IMPACT, CURRENT_LEAVING, APPROACHING, AWAY }

data class RadarCellImpact(
    val kind: RadarImpactKind,
    val relevanceScore: Double,
    val windowStart: Int? = null,
    val windowEnd: Int? = null,
    val maxProbability: Double = 0.0,
    val minDistancePx: Double = Double.POSITIVE_INFINITY,
    val currentDistancePx: Double = Double.POSITIVE_INFINITY,
    val movingToward: Boolean = false,
    val rows: List<RadarImpactRow> = emptyList()
)

data class RadarRainCell(
    val id: String,
    val pixels: IntArray,
    val boundary: IntArray,
    val count: Int,
    val width: Int,
    val height: Int,
    val centroid: RadarPoint,
    val bbox: RadarBoundingBox,
    val motion: RadarCellMotion? = null,
    val track: List<RadarTrackPoint> = emptyList(),
    val observedTrack: List<RadarTrackPoint> = emptyList(),
    val analysisZoom: Int = RADAR_ANALYSIS_ZOOM,
    val stableId: Int? = null,
    val colorIndex: Int? = null,
    val stableLabel: String? = null,
    val impact: RadarCellImpact? = null
)

data class RadarProjection(
    val horizon: Double,
    val dx: Double,
    val dy: Double,
    val x: Double,
    val y: Double,
    val uncertaintyPx: Double,
    val confidence: Double,
    val scaleX: Double,
    val scaleY: Double,
    val areaFactor: Double,
    val survivalProbability: Double,
    val dissipating: Boolean,
    val developing: Boolean,
    val projectionDamping: Double
)

data class RadarMaskSample(val mask: ByteArray, val timeEpochSeconds: Long)

data class RadarIdentityRegistryEntry(
    val xKm: Double,
    val yKm: Double,
    val areaKm2: Double,
    val vxKm: Double,
    val vyKm: Double,
    val time: Long,
    val stableId: Int,
    val colorIndex: Int,
    val misses: Int = 0
)

data class RadarIdentityResult(
    val cells: List<RadarRainCell>,
    val registry: List<RadarIdentityRegistryEntry>,
    val nextId: Int
)

data class RadarNowcast(
    val mask: ByteArray,
    val cells: List<RadarRainCell>,
    val analysisZoom: Int = RADAR_ANALYSIS_ZOOM
)

data class RadarDisplayGeometry(
    val sourceLeft: Double,
    val sourceTop: Double,
    val sourceScale: Double,
    val sourceSize: Double,
    val analysisZoom: Int,
    val mapZoom: Int
)

data class RadarScene(
    val city: City,
    val metadata: RadarMetadata,
    val frames: List<RadarFrame>
)
