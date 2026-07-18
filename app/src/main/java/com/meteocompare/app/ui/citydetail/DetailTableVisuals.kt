package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * Palette visuelle commune aux tableaux de la page détail.
 *
 * La colonne des modèles est figée à gauche et les échéances défilent
 * horizontalement. La palette reste commune aux orientations daily/hourly. Cette
 * palette modernise uniquement leur rendu avec des surfaces plus douces,
 * une alternance très légère, des séparateurs horizontaux discrets et un
 * accent stable sous chaque en-tête de modèle.
 */
@Immutable
internal data class DetailTablePalette(
    val tableSurface: Color,
    val alternateSurface: Color,
    val frozenSurface: Color,
    val frozenAlternateSurface: Color,
    val headerSurface: Color,
    val frozenHeaderSurface: Color,
    val highlightedSurface: Color,
    val highlightedFrozenSurface: Color,
    val border: Color,
    val rowDivider: Color,
    val frozenDivider: Color,
    val highlightedText: Color
)

internal val DetailTableShape = RoundedCornerShape(14.dp)

@Composable
internal fun detailTablePalette(): DetailTablePalette {
    val scheme = MaterialTheme.colorScheme
    val tableSurface = scheme.surfaceContainerLowest
    val frozenSurface = scheme.surfaceContainerLow

    return DetailTablePalette(
        tableSurface = tableSurface,
        alternateSurface = scheme.onSurface.copy(alpha = 0.035f).compositeOver(tableSurface),
        frozenSurface = frozenSurface,
        frozenAlternateSurface = scheme.onSurface.copy(alpha = 0.035f).compositeOver(frozenSurface),
        headerSurface = scheme.primary.copy(alpha = 0.09f)
            .compositeOver(scheme.surfaceContainerHigh),
        frozenHeaderSurface = scheme.primary.copy(alpha = 0.15f)
            .compositeOver(scheme.surfaceContainerHigh),
        highlightedSurface = scheme.primary.copy(alpha = 0.13f).compositeOver(tableSurface),
        highlightedFrozenSurface = scheme.primary.copy(alpha = 0.18f)
            .compositeOver(frozenSurface),
        border = scheme.outlineVariant.copy(alpha = 0.78f),
        rowDivider = scheme.outlineVariant.copy(alpha = 0.38f),
        frozenDivider = scheme.outline.copy(alpha = 0.42f),
        highlightedText = scheme.primary
    )
}

internal fun DetailTablePalette.dataRowBackground(index: Int, highlighted: Boolean): Color =
    when {
        highlighted -> highlightedSurface
        index % 2 == 1 -> alternateSurface
        else -> tableSurface
    }

internal fun DetailTablePalette.labelRowBackground(index: Int, highlighted: Boolean): Color =
    when {
        highlighted -> highlightedFrozenSurface
        index % 2 == 1 -> frozenAlternateSurface
        else -> frozenSurface
    }

/** Encadrement arrondi et fin, sans modifier les dimensions internes. */
internal fun Modifier.detailTableFrame(palette: DetailTablePalette): Modifier =
    clip(DetailTableShape)
        .background(palette.tableSurface)
        .border(1.dp, palette.border, DetailTableShape)

/**
 * Fond et séparateur horizontal communs à toutes les cellules.
 * [accentColor] dessine un trait de 3 dp au bas des en-têtes de modèle.
 */
internal fun Modifier.detailTableCell(
    backgroundColor: Color,
    palette: DetailTablePalette,
    accentColor: Color? = null
): Modifier =
    background(backgroundColor)
        .drawBehind {
            val dividerY = size.height - 0.5.dp.toPx()
            drawLine(
                color = palette.rowDivider,
                start = androidx.compose.ui.geometry.Offset(0f, dividerY),
                end = androidx.compose.ui.geometry.Offset(size.width, dividerY),
                strokeWidth = 0.5.dp.toPx()
            )
            if (accentColor != null) {
                val accentHeight = 3.dp.toPx()
                drawRect(
                    color = accentColor,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - accentHeight),
                    size = androidx.compose.ui.geometry.Size(size.width, accentHeight)
                )
            }
        }

/**
 * Adoucit les anciennes heatmaps saturées en les fondant dans la surface du
 * tableau. L'échelle et les seuils météorologiques restent inchangés.
 */
internal fun DetailTablePalette.modernHeatmapBackground(source: Color): Color {
    val alpha = if (tableSurface.luminance() < 0.35f) 0.58f else 0.72f
    return source.copy(alpha = alpha).compositeOver(tableSurface)
}
