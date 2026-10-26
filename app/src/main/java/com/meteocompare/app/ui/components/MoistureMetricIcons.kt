package com.meteocompare.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Hygrometer gauge with three vapour dots: never confused with the rain drop. */
val HumidityMetricIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "HumidityGauge", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(7.2f, 4.4f)
            curveTo(9.9f, 2.7f, 14.4f, 2.9f, 17f, 4.8f)
            moveTo(19.4f, 8f)
            curveTo(21f, 11f, 20.5f, 15.5f, 18f, 18.2f)
            curveTo(15.5f, 20.9f, 10f, 21.6f, 6.5f, 19f)
            curveTo(3.4f, 16.7f, 2.5f, 11.6f, 4.3f, 8.2f)
            moveTo(8f, 12f)
            curveTo(9.9f, 10.5f, 14.1f, 10.5f, 16f, 12f)
            moveTo(9.5f, 16f)
            curveTo(11.2f, 16.8f, 13f, 16.8f, 14.5f, 16f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 7f); curveTo(8f, 8.2f, 6.2f, 8.2f, 6.2f, 7f)
            curveTo(6.2f, 5.8f, 8f, 5.8f, 8f, 7f)
            moveTo(13f, 7f); curveTo(13f, 8.2f, 11.2f, 8.2f, 11.2f, 7f)
            curveTo(11.2f, 5.8f, 13f, 5.8f, 13f, 7f)
            moveTo(18f, 7f); curveTo(18f, 8.2f, 16.2f, 8.2f, 16.2f, 7f)
            curveTo(16.2f, 5.8f, 18f, 5.8f, 18f, 7f)
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
