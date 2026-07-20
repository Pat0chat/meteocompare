package com.meteocompare.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val SELECTOR_ANIMATION_MS = 180

/**
 * Sélecteur compact à indicateur tonal coulissant.
 *
 * Il est destiné aux changements de mode structurants (Par heure / Par jour)
 * et aux réglages à choix unique. Le contrôle n'utilise ni contour, ni ombre :
 * un seul indicateur animé porte l'état actif dans un conteneur très discret.
 * La hauteur visuelle reste compacte, mais chaque option conserve une cible
 * tactile de 44 dp et une sémantique de bouton radio.
 */
@Composable
internal fun <T> ModernSlidingSelector(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: @Composable (T) -> String,
    accent: Color,
    modifier: Modifier = Modifier,
    itemModifier: (T) -> Modifier = { Modifier }
) {
    if (options.isEmpty()) return

    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val scheme = MaterialTheme.colorScheme
    val outerShape = RoundedCornerShape(14.dp)
    val indicatorShape = RoundedCornerShape(11.dp)
    val indicatorColor = accent.copy(alpha = 0.11f)
        .compositeOver(scheme.surfaceContainerLow)

    Box(
        modifier = modifier
            .clip(outerShape)
            .background(scheme.surfaceContainerLow.copy(alpha = 0.72f))
            .selectableGroup()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            val itemWidth = maxWidth / options.size.toFloat()
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex.toFloat(),
                animationSpec = tween(durationMillis = SELECTOR_ANIMATION_MS)
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .padding(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .clip(indicatorShape)
                        .background(indicatorColor)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEach { option ->
                    val isSelected = option == selected
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) accent else scheme.onSurfaceVariant,
                        animationSpec = tween(durationMillis = SELECTOR_ANIMATION_MS)
                    )

                    Box(
                        modifier = Modifier
                            .then(itemModifier(option))
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(indicatorShape)
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelected(option) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}


/**
 * Choix exclusif très léger sans conteneur commun.
 *
 * L'option inactive reste un simple libellé, tandis que l'option active reçoit
 * une petite capsule tonale. Ce rendu correspond à « Par heure   [ Par jour ] »
 * et évite l'effet de barre segmentée massive pour un choix binaire.
 */
@Composable
internal fun <T> ModernInlineSelector(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: @Composable (T) -> String,
    accent: Color,
    modifier: Modifier = Modifier,
    itemModifier: (T) -> Modifier = { Modifier }
) {
    if (options.isEmpty()) return

    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) {
                    accent.copy(alpha = 0.11f).compositeOver(scheme.surfaceContainerLow)
                } else {
                    Color.Transparent
                },
                animationSpec = tween(durationMillis = SELECTOR_ANIMATION_MS)
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) accent else scheme.onSurfaceVariant,
                animationSpec = tween(durationMillis = SELECTOR_ANIMATION_MS)
            )

            Box(
                modifier = Modifier
                    .then(itemModifier(option))
                    .heightIn(min = 44.dp)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelected(option) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(containerColor)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Onglets texte légers avec indicateur inférieur.
 *
 * À utiliser pour naviguer entre des métriques sœurs (température, pluie,
 * vent). Il n'y a pas de gros conteneur commun : le texte actif et une ligne
 * courte suffisent à exprimer la sélection. Le contrôle reste accessible avec
 * une cible tactile de 44 dp par onglet.
 */
@Composable
internal fun <T> ModernTextTabs(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: @Composable (T) -> String,
    accent: Color,
    modifier: Modifier = Modifier,
    itemModifier: (T) -> Modifier = { Modifier }
) {
    if (options.isEmpty()) return

    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) accent else scheme.onSurfaceVariant,
                animationSpec = tween(durationMillis = SELECTOR_ANIMATION_MS)
            )
            val indicatorColor by animateColorAsState(
                targetValue = if (isSelected) accent else Color.Transparent,
                animationSpec = tween(durationMillis = SELECTOR_ANIMATION_MS)
            )

            Box(
                modifier = Modifier
                    .then(itemModifier(option))
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelected(option) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.dp)
                        .width(28.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(indicatorColor)
                )
            }
        }
    }
}

/**
 * Petite capsule autonome pour les filtres pouvant revenir à la ligne.
 *
 * Les options inactives n'ont plus de bordure ni de fond de carte. Seule
 * l'option active reçoit un fond tonal léger, ce qui réduit nettement la masse
 * visuelle des groupes de six choix ou plus.
 */
@Composable
internal fun ModernStateChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val selectedContainer = accent.copy(alpha = 0.11f)
        .compositeOver(scheme.surfaceContainerLow)
    val containerColor by animateColorAsState(
        targetValue = if (selected) selectedContainer else Color.Transparent,
        animationSpec = tween(durationMillis = SELECTOR_ANIMATION_MS)
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) accent else scheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = SELECTOR_ANIMATION_MS)
    )

    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(containerColor)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
