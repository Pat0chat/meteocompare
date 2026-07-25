package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
    val rowDivider: Color,
    val frozenDivider: Color,
    val highlightedText: Color
)

internal val DetailTableShape = RoundedCornerShape(14.dp)

@Composable
internal fun detailTablePalette(): DetailTablePalette {
    val scheme = MaterialTheme.colorScheme
    val tableSurface = scheme.surfaceContainerLow
    val frozenSurface = scheme.surfaceContainer

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

/** Nombre maximal de modèles visibles avant défilement vertical interne. */
private const val MAX_VISIBLE_MODEL_ROWS = 9.0f

/**
 * Structure commune des tableaux détaillés.
 *
 * La ligne des dates/heures reste fixe pendant le défilement vertical des
 * modèles. La colonne des modèles reste fixe pendant le défilement horizontal
 * des échéances. Les deux axes sont indépendants et synchronisés visuellement.
 */
@Composable
internal fun FrozenDetailTableLayout(
    modelColumnWidth: Dp,
    temporalColumnCount: Int,
    headerHeight: Dp,
    rowHeight: Dp,
    rowCount: Int,
    palette: DetailTablePalette,
    modifier: Modifier = Modifier,
    cornerHeader: @Composable () -> Unit,
    temporalHeaders: @Composable RowScope.() -> Unit,
    modelRows: @Composable ColumnScope.() -> Unit,
    temporalColumns: @Composable RowScope.() -> Unit
) {
    if (rowCount <= 0 || temporalColumnCount <= 0) return

    val horizontalState = rememberScrollState()
    val verticalState = rememberScrollState()
    // La largeur temporelle reste naturelle : les en-têtes et le corps sont
    // composés avec les mêmes cellules de largeur fixe. Ne pas multiplier des
    // valeurs Dp ici évite les écarts cumulés d'arrondi en pixels selon la
    // densité (cause d'un décalage visible après plusieurs colonnes).
    val bodyContentHeight = (rowHeight.value * rowCount).dp
    val bodyViewportHeight = minOf(
        bodyContentHeight.value,
        rowHeight.value * MAX_VISIBLE_MODEL_ROWS
    ).dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .detailTableFrame(palette)
    ) {
        Row(modifier = Modifier.height(headerHeight)) {
            Box(
                modifier = Modifier
                    .width(modelColumnWidth)
                    .height(headerHeight)
            ) {
                cornerHeader()
            }

            VerticalDivider(
                modifier = Modifier.height(headerHeight),
                color = palette.frozenDivider
            )

            SyncedTemporalHeaderViewport(
                scrollOffsetPx = horizontalState.value,
                modifier = Modifier
                    .weight(1f)
                    .height(headerHeight)
                    .clipToBounds(),
                content = temporalHeaders
            )
        }

        Row(
            modifier = Modifier
                .height(bodyViewportHeight)
                .verticalScroll(verticalState)
        ) {
            Column(
                modifier = Modifier
                    .width(modelColumnWidth)
                    .height(bodyContentHeight),
                content = modelRows
            )

            VerticalDivider(
                modifier = Modifier.height(bodyContentHeight),
                color = palette.frozenDivider
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(bodyContentHeight)
                    .clipToBounds()
            ) {
                Row(
                    modifier = Modifier
                        .height(bodyContentHeight)
                        .horizontalScroll(horizontalState),
                    content = temporalColumns
                )
            }
        }
    }
}

/**
 * Viewport d'en-tete synchronise avec le scroll du corps du tableau.
 *
 * `requiredWidth` ne convient pas ici : lorsqu'un enfant force une largeur
 * supérieure aux contraintes de son parent, Compose masque cette violation et
 * centre par défaut l'enfant sur la largeur accordée. L'en-tete se retrouvait
 * alors décalé par rapport aux colonnes du corps, surtout après un scroll.
 *
 * Ce layout mesure explicitement la rangée sans borne horizontale puis la place
 * à l'origine du viewport avec exactement le même décalage en pixels que le
 * corps. En-têtes et valeurs gardent leur largeur naturelle, somme des cellules
 * réellement mesurées : aucun arrondi Dp global ne peut dériver de l'arrondi de
 * chaque colonne.
 */
@Composable
private fun SyncedTemporalHeaderViewport(
    scrollOffsetPx: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Layout(
        modifier = modifier,
        content = { Row(content = content) }
    ) { measurables, constraints ->
        val row = measurables.single().measure(
            constraints.copy(
                minWidth = 0,
                maxWidth = Constraints.Infinity
            )
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            row.placeRelative(x = -scrollOffsetPx, y = 0)
        }
    }
}

/** Encadrement arrondi et fin, sans modifier les dimensions internes. */
internal fun Modifier.detailTableFrame(palette: DetailTablePalette): Modifier =
    clip(DetailTableShape)
        .background(palette.tableSurface)

/**
 * Fond et séparateur horizontal communs à toutes les cellules.
 * [accentColor] dessine un indicateur court et discret au bas des en-têtes de modèle.
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
                // Accent volontairement court et translucide : il permet
                // d'identifier le modèle sans transformer tout l'en-tête en
                // bande colorée dominante.
                val accentHeight = 2.dp.toPx()
                val horizontalInset = 10.dp.toPx()
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.52f),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        horizontalInset,
                        size.height - accentHeight - 1.dp.toPx()
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        (size.width - horizontalInset * 2f).coerceAtLeast(0f),
                        accentHeight
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        accentHeight / 2f,
                        accentHeight / 2f
                    )
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
