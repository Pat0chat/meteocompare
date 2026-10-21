package com.meteocompare.app.widget

import android.os.Build

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider
import com.meteocompare.app.MainActivity
import kotlin.math.sqrt

/**
 * Corner radius for an app-controlled background (in dp).
 * The launcher may still clip the HOST on Android 12+; 0dp cannot override it.
 */
internal fun widgetCornerRadiusDp(
    style: WidgetCornerStyle,
    customRadiusDp: Int,
    widthDp: Float,
    heightDp: Float
): Float {
    val halfShortestSide = (minOf(widthDp, heightDp).coerceAtLeast(0f) / 2f)
    return when (style) {
        WidgetCornerStyle.SQUARE -> 0f
        WidgetCornerStyle.SOFT -> minOf(12f, halfShortestSide)
        WidgetCornerStyle.PILL -> halfShortestSide
        WidgetCornerStyle.CUSTOM -> minOf(
            customRadiusDp.coerceIn(0, WidgetPreferences.MAX_CORNER_RADIUS_DP).toFloat(),
            halfShortestSide
        )
        WidgetCornerStyle.ROUNDED -> minOf(28f, halfShortestSide)
    }
}

/** All inset values are INSIDE the background. External panel margins are always zero. */
internal fun widgetContentPadding(
    horizontalPadding: Dp,
    verticalPadding: Dp,
    spacing: WidgetContentSpacing
): Pair<Dp, Dp> = when (spacing) {
    WidgetContentSpacing.NORMAL -> horizontalPadding to verticalPadding
    WidgetContentSpacing.COMPACT -> (horizontalPadding.value / 2f).dp to (verticalPadding.value / 2f).dp
    WidgetContentSpacing.NONE -> 0.dp to 0.dp
}

/**
 * The pill shape has curved ENDS even when the launcher grants no margins.
 * Inscribe the content in a safe rectangular area of the rounded capsule:
 * at the first/last content row, the safe horizontal inset follows the circle.
 * The background itself stays fully edge-to-edge.
 *
 * A minimum vertical inset is necessary even for spacing=NONE, otherwise a
 * 100%-wide text row intersects the capsule near its top/bottom corners.
 */
internal fun widgetShapeSafeContentPaddingDp(
    style: WidgetCornerStyle,
    widthDp: Float,
    heightDp: Float,
    requestedHorizontalDp: Float,
    requestedVerticalDp: Float
): Pair<Float, Float> {
    val x = requestedHorizontalDp.coerceAtLeast(0f)
    val y = requestedVerticalDp.coerceAtLeast(0f)
    if (style != WidgetCornerStyle.PILL || widthDp <= 0f || heightDp <= 0f) return x to y

    val radius = minOf(widthDp, heightDp) / 2f
    // Reserve approximately one eighth of the height at the top and bottom.
    // Never reserve more than a quarter, including for tiny circular widgets.
    val safeY = maxOf(y, (heightDp * 0.12f).coerceIn(5f, 14f))
        .coerceAtMost(heightDp / 4f)
    // Circle equation, plus 2dp against host raster rounding and text ascenders.
    val distanceFromCenter = (radius - safeY).coerceIn(0f, radius)
    val safeX = radius - sqrt((radius * radius - distanceFromCenter * distanceFromCenter).coerceAtLeast(0f)) + 2f
    return maxOf(x, safeX).coerceAtMost(widthDp / 2f) to safeY
}

/** Shared true edge-to-edge surface for the weather and insight widgets. */
@Composable
internal fun WidgetSurface(
    background: ColorProvider,
    cornerStyle: WidgetCornerStyle,
    customCornerRadiusDp: Int,
    contentSpacing: WidgetContentSpacing,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    content: @Composable () -> Unit
) {
    val size = LocalSize.current
    val radius = widgetCornerRadiusDp(
        cornerStyle, customCornerRadiusDp, size.width.value, size.height.value
    )
    val (requestedX, requestedY) = widgetContentPadding(horizontalPadding, verticalPadding, contentSpacing)
    val (safeX, safeY) = widgetShapeSafeContentPaddingDp(
        style = cornerStyle,
        widthDp = size.width.value,
        heightDp = size.height.value,
        requestedHorizontalDp = requestedX.value,
        requestedVerticalDp = requestedY.value
    )
    val insetX = safeX.dp
    val insetY = safeY.dp
    // The background always fills the entire allocation. Android 12+ launcher
    // masking is outside RemoteViews and cannot be disabled by the app.
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .background(background)
            .then(
                if (cornerStyle == WidgetCornerStyle.ROUNDED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
                } else {
                    GlanceModifier.cornerRadius(radius.dp)
                }
            )
            .appWidgetBackground()
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Box(
            modifier = GlanceModifier.fillMaxSize()
                .padding(horizontal = insetX, vertical = insetY)
        ) {
            content()
        }
    }
}
