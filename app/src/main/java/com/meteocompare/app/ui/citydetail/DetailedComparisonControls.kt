package com.meteocompare.app.ui.citydetail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.CityDetailContentTab
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.temperatureMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent

private const val CONTROL_ANIMATION_MS = 160

/**
 * Navigation contextuelle des prévisions détaillées.
 *
 * La granularité est volontairement traitée comme un réglage secondaire :
 * un simple libellé ouvre un menu ponctuel. La variable est la seule vraie
 * navigation persistante. Les accents météo restent distincts, mais ne sont
 * utilisés que sur l'onglet actif afin de conserver une interface légère.
 *
 * Le composant sert de stickyHeader ; sa couleur est donc identique au fond de
 * la page pour qu'il paraisse intégré au contenu avant de devenir fonctionnel
 * au scroll.
 */
@Composable
internal fun DetailedComparisonControls(
    mode: DisplayMode,
    selectedTab: CityDetailContentTab,
    onModeChange: (DisplayMode) -> Unit,
    onTabChange: (CityDetailContentTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = scheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.forecast_tables_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1
                )

                DisplayModeMenu(
                    mode = mode,
                    onModeChange = onModeChange
                )
            }

            DetailContentTabs(
                selected = selectedTab,
                onSelected = onTabChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )

            HorizontalDivider(
                thickness = 1.dp,
                color = scheme.outlineVariant.copy(alpha = 0.28f)
            )
        }
    }
}

/**
 * Réglage discret de granularité. Aucun rail ni capsule permanente : le mode
 * courant est simplement présenté comme une action contextuelle.
 */
@Composable
private fun DisplayModeMenu(
    mode: DisplayMode,
    onModeChange: (DisplayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    role = Role.Button,
                    onClick = { expanded = true }
                )
                .padding(start = 8.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
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
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DisplayMode.entries.forEach { option ->
                val selected = option == mode
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expanded = false
                        if (!selected) onModeChange(option)
                    },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = scheme.primary
                            )
                        }
                    } else {
                        null
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
    val contentColor by animateColorAsState(
        targetValue = if (selected) accent else scheme.onSurfaceVariant,
        animationSpec = tween(CONTROL_ANIMATION_MS)
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) accent else Color.Transparent,
        animationSpec = tween(CONTROL_ANIMATION_MS)
    )

    Box(
        modifier = modifier
            .height(42.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(tab.labelRes),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 3.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(30.dp)
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

private val CityDetailContentTab.labelRes: Int
    get() = when (this) {
        CityDetailContentTab.CONDITIONS -> R.string.detail_tab_conditions
        CityDetailContentTab.TEMPERATURE -> R.string.detail_tab_temperature
        CityDetailContentTab.PRECIPITATION -> R.string.detail_tab_precipitation
        CityDetailContentTab.WIND -> R.string.detail_tab_wind
    }
