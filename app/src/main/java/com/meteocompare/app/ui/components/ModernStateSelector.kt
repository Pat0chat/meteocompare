package com.meteocompare.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

/**
 * Sélecteur à état moderne commun à l'application.
 *
 * Contrairement au segmented button Material classique, il évite les contours
 * accolés et utilise une capsule sélectionnée, légèrement teintée par la
 * variable active. Le fond commun reste discret et les éléments conservent une
 * cible tactile d'au moins 44 dp.
 */
@Composable
internal fun <T> ModernStateSelector(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: @Composable (T) -> String,
    accent: Color,
    modifier: Modifier = Modifier,
    itemModifier: (T) -> Modifier = { Modifier }
) {
    if (options.isEmpty()) return

    val outerShape = RoundedCornerShape(18.dp)
    val itemShape = RoundedCornerShape(14.dp)
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .clip(outerShape)
            .background(scheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = scheme.outlineVariant.copy(alpha = 0.65f),
                shape = outerShape
            )
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val selectedContainer = accent.copy(alpha = 0.16f)
                .compositeOver(scheme.surfaceContainerHighest)
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) selectedContainer else Color.Transparent
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) accent else scheme.onSurfaceVariant
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) accent.copy(alpha = 0.34f) else Color.Transparent
            )

            Box(
                modifier = Modifier
                    .then(itemModifier(option))
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(itemShape)
                    .background(containerColor)
                    .border(1.dp, borderColor, itemShape)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelected(option) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
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

/** Variante compacte et wrappable pour les listes de choix plus longues. */
@Composable
internal fun ModernStateChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val selectedContainer = accent.copy(alpha = 0.15f)
        .compositeOver(scheme.surfaceContainerHigh)
    val containerColor by animateColorAsState(
        targetValue = if (selected) selectedContainer else scheme.surfaceContainerLow
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) accent else scheme.onSurfaceVariant
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.38f) else scheme.outlineVariant
    )

    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
