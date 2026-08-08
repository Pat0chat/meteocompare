package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.CityDetailContentTab
import com.meteocompare.app.ui.components.ModernTextTabs
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.temperatureMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent

/** Contrôles de la comparaison détaillée. */
@Composable
internal fun DetailedComparisonControls(
    mode: DisplayMode,
    selectedTab: CityDetailContentTab,
    onModeChange: (DisplayMode) -> Unit,
    onTabChange: (CityDetailContentTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 2.dp)
        ) {
            DisplayModeToggle(
                mode = mode,
                onModeChange = onModeChange
            )
            Spacer(Modifier.height(2.dp))
            DetailContentTabs(
                selected = selectedTab,
                onSelected = onTabChange,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
private fun DetailContentTabs(
    selected: CityDetailContentTab,
    onSelected: (CityDetailContentTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent: Color = when (selected) {
        CityDetailContentTab.CONDITIONS -> MaterialTheme.colorScheme.primary
        CityDetailContentTab.TEMPERATURE -> temperatureMetricAccent()
        CityDetailContentTab.PRECIPITATION -> precipitationMetricAccent()
        CityDetailContentTab.WIND -> windMetricAccent()
    }

    ModernTextTabs(
        options = CityDetailContentTab.entries,
        selected = selected,
        onSelected = onSelected,
        label = { tab ->
            stringResource(
                when (tab) {
                    CityDetailContentTab.CONDITIONS -> R.string.detail_tab_conditions
                    CityDetailContentTab.TEMPERATURE -> R.string.detail_tab_temperature
                    CityDetailContentTab.PRECIPITATION -> R.string.detail_tab_precipitation
                    CityDetailContentTab.WIND -> R.string.detail_tab_wind
                }
            )
        },
        accent = accent,
        modifier = modifier.fillMaxWidth()
    )
}
