package com.meteocompare.app.ui.citydetail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.CityDetailContentTab
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.temperatureMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent

private const val CONTROL_ANIMATION_MS = 160

/**
 * En-tête de navigation de la surface « prévisions détaillées ».
 *
 * La granularité (heure/jour) reste un réglage secondaire sous la forme d'un
 * petit bouton. La variable reste la navigation principale. Les icônes sont
 * volontairement neutres au repos et prennent uniquement la couleur d'accent
 * de leur variable lorsque l'onglet correspondant est sélectionné.
 *
 * Le composant ne dessine pas sa propre Surface : il est intégré à la Surface
 * englobante de la section détaillée, au même niveau que le tableau et sa
 * légende. Cela évite l'effet « widget dans le widget ».
 */
@Composable
internal fun DetailedComparisonControls(
    mode: DisplayMode,
    selectedTab: CityDetailContentTab,
    onModeChange: (DisplayMode) -> Unit,
    onTabChange: (CityDetailContentTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.forecast_tables_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            DisplayModeMenu(
                mode = mode,
                onModeChange = onModeChange
            )
        }

        Spacer(Modifier.height(8.dp))

        DetailContentTabs(
            selected = selectedTab,
            onSelected = onTabChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )
    }
}

/**
 * Contrôle de granularité volontairement discret. Il reste identifiable comme
 * un bouton, mais se fond dans la Surface de section. Le menu reprend les tons
 * Material 3 de l'application et réserve l'accent à l'option active.
 */
@Composable
internal fun DisplayModeMenu(
    mode: DisplayMode,
    onModeChange: (DisplayMode) -> Unit,
    modifier: Modifier = Modifier,
    availableModes: Set<DisplayMode> = DisplayMode.entries.toSet()
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val buttonShape = RoundedCornerShape(9.dp)
    val menuShape = RoundedCornerShape(14.dp)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .height(30.dp)
                .clip(buttonShape)
                .background(scheme.surfaceContainerHigh.copy(alpha = 0.38f))
                .clickable(
                    role = Role.Button,
                    onClick = { expanded = true }
                )
                .padding(start = 9.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(mode.labelRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurfaceVariant,
                maxLines = 1
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = scheme.onSurfaceVariant.copy(alpha = 0.78f),
                modifier = Modifier.size(15.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, 6.dp),
            modifier = Modifier
                .width(148.dp)
                .clip(menuShape)
                .background(scheme.surfaceContainerHigh)
                .padding(vertical = 4.dp)
        ) {
            DisplayMode.entries.filter { it in availableModes }.forEach { option ->
                val selected = option == mode
                val itemShape = RoundedCornerShape(10.dp)
                DropdownMenuItem(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(itemShape)
                        .background(
                            if (selected) scheme.primaryContainer.copy(alpha = 0.52f)
                            else Color.Transparent
                        ),
                    text = {
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface
                        )
                    },
                    onClick = {
                        expanded = false
                        if (!selected) onModeChange(option)
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier.size(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

private val DisplayMode.labelRes: Int
    get() = when (this) {
        DisplayMode.HOURLY -> R.string.display_mode_hourly
        DisplayMode.DAILY -> R.string.display_mode_daily
    }

@Composable
private fun DetailContentTabs(
    selected: CityDetailContentTab,
    onSelected: (CityDetailContentTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CityDetailContentTab.entries.forEach { tab ->
            DetailContentTab(
                tab = tab,
                selected = selected == tab,
                onClick = { onSelected(tab) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DetailContentTab(
    tab: CityDetailContentTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val accent = tabAccent(tab)
    val textColor by animateColorAsState(
        targetValue = if (selected) accent else scheme.onSurfaceVariant,
        animationSpec = tween(CONTROL_ANIMATION_MS)
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) accent else scheme.onSurfaceVariant.copy(alpha = 0.58f),
        animationSpec = tween(CONTROL_ANIMATION_MS)
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) accent else Color.Transparent,
        animationSpec = tween(CONTROL_ANIMATION_MS)
    )

    Box(
        modifier = modifier
            .height(44.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(tab.labelRes),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(28.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(indicatorColor)
        )
    }
}

@Composable
private fun tabAccent(tab: CityDetailContentTab): Color = when (tab) {
    CityDetailContentTab.CONDITIONS -> MaterialTheme.colorScheme.primary
    CityDetailContentTab.TEMPERATURE -> temperatureMetricAccent()
    CityDetailContentTab.PRECIPITATION -> precipitationMetricAccent()
    CityDetailContentTab.WIND -> windMetricAccent()
}

private val CityDetailContentTab.icon: ImageVector
    get() = when (this) {
        CityDetailContentTab.CONDITIONS -> Icons.Outlined.Cloud
        CityDetailContentTab.TEMPERATURE -> Icons.Outlined.Thermostat
        CityDetailContentTab.PRECIPITATION -> Icons.Outlined.WaterDrop
        CityDetailContentTab.WIND -> Icons.Outlined.Air
    }

private val CityDetailContentTab.labelRes: Int
    get() = when (this) {
        CityDetailContentTab.CONDITIONS -> R.string.detail_tab_conditions
        CityDetailContentTab.TEMPERATURE -> R.string.detail_tab_temperature
        CityDetailContentTab.PRECIPITATION -> R.string.detail_tab_precipitation
        CityDetailContentTab.WIND -> R.string.detail_tab_wind
    }
