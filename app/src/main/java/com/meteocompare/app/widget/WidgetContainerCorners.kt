package com.meteocompare.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider
import com.meteocompare.app.MainActivity

/**
 * Android 12+ launchers can clip the widget HOST, outside of Glance's RemoteViews.
 * cornerRadius(0.dp) is not sufficient to produce square visible corners there.
 * Leave the host-level background transparent in SQUARE mode and move the actual
 * coloured, rectangular panel INSIDE the host's corner clipping area.
 *
 * The inset is mostly HORIZONTAL. At a panel top inset of 6dp, its top-left
 * corner is already inside a 28dp-radius launcher mask when x >= 12dp
 * (quarter-circle geometry). The older 10/14/20dp VERTICAL insets consumed
 * up to 40dp of widget height and cut the bottom heatmap and daily/hourly cards.
 *
 * Insets are in dp per side. Clamp narrow widgets to leave a minimum content
 * width instead of accidentally reducing a 1x1 widget to a few pixels.
 */
internal fun squarePanelInsetsDp(widthDp: Float, heightDp: Float): Pair<Int, Int> {
    val horizontal = when {
        widthDp < 170f || heightDp < 96f -> 16
        widthDp < 260f || heightDp < 150f -> 20
        else -> 22
    }
    val maxInsetX = ((widthDp - 40f) / 2f).toInt().coerceAtLeast(0)
    val maxInsetY = ((heightDp - 32f) / 2f).toInt().coerceAtLeast(0)
    return minOf(horizontal, maxInsetX) to minOf(6, maxInsetY)
}

/** One source of truth for the actual coloured panel, preview and layout. */
internal data class WidgetPanelGeometry(
    val insetX: Int,
    val insetY: Int,
    val widthDp: Float,
    val heightDp: Float
)

internal fun widgetPanelGeometryDp(
    widthDp: Float,
    heightDp: Float,
    style: WidgetCornerStyle
): WidgetPanelGeometry {
    val (x, y) = if (style == WidgetCornerStyle.SQUARE) {
        squarePanelInsetsDp(widthDp, heightDp)
    } else 0 to 0
    return WidgetPanelGeometry(
        insetX = x,
        insetY = y,
        widthDp = (widthDp - 2f * x).coerceAtLeast(0f),
        heightDp = (heightDp - 2f * y).coerceAtLeast(0f)
    )
}

// Glance LocalSize is the HOST size even inside a padded nested Box. All the
// forecast/heatmap height budgets must use the coloured panel size instead.
private val LocalWidgetPanelSize = staticCompositionLocalOf<DpSize?> { null }

@Composable
internal fun widgetPanelSize(): DpSize = LocalWidgetPanelSize.current ?: LocalSize.current

internal fun widgetContainerCorners(style: WidgetCornerStyle): GlanceModifier =
    when (style) {
        WidgetCornerStyle.ROUNDED -> GlanceModifier.cornerRadius(
            android.R.dimen.system_app_widget_background_radius
        )
        WidgetCornerStyle.SQUARE -> GlanceModifier.cornerRadius(0.dp)
    }

/** Shared outer surface for forecast and insight widgets. */
@Composable
internal fun WidgetSurface(
    background: ColorProvider,
    cornerStyle: WidgetCornerStyle,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    content: @Composable () -> Unit
) {
    if (cornerStyle == WidgetCornerStyle.SQUARE) {
        val size = LocalSize.current
        val geometry = widgetPanelGeometryDp(
            size.width.value, size.height.value, cornerStyle
        )
        Box(
            modifier = GlanceModifier.fillMaxSize()
                // Do not draw the coloured background at the launcher-clipped edge.
                .background(ColorProvider(Color.Transparent))
                .appWidgetBackground()
                .then(widgetContainerCorners(WidgetCornerStyle.SQUARE))
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Box(
                modifier = GlanceModifier.fillMaxSize()
                    .padding(horizontal = geometry.insetX.dp, vertical = geometry.insetY.dp)
            ) {
                Box(
                    modifier = GlanceModifier.fillMaxSize()
                        .background(background)
                        .cornerRadius(0.dp)
                       .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                ) {
                    CompositionLocalProvider(
                        LocalWidgetPanelSize provides DpSize(geometry.widthDp.dp, geometry.heightDp.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    } else {
        Box(
            modifier = GlanceModifier.fillMaxSize()
                .background(background)
                .then(widgetContainerCorners(cornerStyle))
                .appWidgetBackground()
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Box(
                modifier = GlanceModifier.fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            ) {
                content()
            }
        }
    }
}
