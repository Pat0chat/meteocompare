package com.meteocompare.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Relative humidity: a recognisable percent sign inside a round gauge.
 * No drop shape: that pictogram is reserved for precipitation throughout the app.
 * Vector strokes, not font glyphs, keep the symbol readable at 13–18 dp.
 */
val HumidityMetricIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "HumidityGauge", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            // Circular gauge border, visually distinct from the rain icon.
            moveTo(12f, 2.5f)
            curveTo(6.75f, 2.5f, 2.5f, 6.75f, 2.5f, 12f)
            curveTo(2.5f, 17.25f, 6.75f, 21.5f, 12f, 21.5f)
            curveTo(17.25f, 21.5f, 21.5f, 17.25f, 21.5f, 12f)
            curveTo(21.5f, 6.75f, 17.25f, 2.5f, 12f, 2.5f)
            close()
            // Percent sign: slash and the two circular counters.
            moveTo(8.25f, 15.9f)
            lineTo(15.75f, 8.1f)
            moveTo(10.4f, 8.7f)
            curveTo(10.4f, 10.9f, 7.1f, 10.9f, 7.1f, 8.7f)
            curveTo(7.1f, 6.5f, 10.4f, 6.5f, 10.4f, 8.7f)
            moveTo(16.9f, 15.3f)
            curveTo(16.9f, 17.5f, 13.6f, 17.5f, 13.6f, 15.3f)
            curveTo(13.6f, 13.1f, 16.9f, 13.1f, 16.9f, 15.3f)
        }
    }.build()
}

/** Condensation beads over a cool surface: separate from rain AND air temperature. */
val DewPointMetricIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "DewCondensation", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(3f, 17f); curveTo(8f, 16f, 16f, 18f, 21f, 17f)
            moveTo(3f, 20f); curveTo(9f, 19f, 15f, 21f, 21f, 20f)
            moveTo(7f, 12f); curveTo(5f, 11f, 6f, 8f, 8f, 6.5f)
            curveTo(10f, 8f, 11f, 11f, 9f, 12f)
            curveTo(8.3f, 12.4f, 7.7f, 12.4f, 7f, 12f)
            moveTo(15f, 13f); curveTo(13f, 12f, 14f, 9f, 16f, 7.5f)
            curveTo(18f, 9f, 19f, 12f, 17f, 13f)
            curveTo(16.3f, 13.4f, 15.7f, 13.4f, 15f, 13f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 5f); curveTo(13.4f, 5f, 13.4f, 7f, 12f, 7f)
            curveTo(10.6f, 7f, 10.6f, 5f, 12f, 5f)
        }
    }.build()
}
