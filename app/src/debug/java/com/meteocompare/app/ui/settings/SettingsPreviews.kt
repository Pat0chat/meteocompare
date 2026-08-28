package com.meteocompare.app.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.ui.preview.MeteoPreviewSurface
import com.meteocompare.app.ui.preview.MeteoScreenPreview
import com.meteocompare.app.ui.preview.PreviewFixtures

@MeteoScreenPreview
@Composable
private fun SettingsScreenContentPreview() {
    MeteoPreviewSurface {
        SettingsContent(
            enabledModels = PreviewFixtures.models.toSet(),
            onToggle = { _, _ -> },
            theme = ThemePreference.SYSTEM,
            onThemeSelected = {},
            language = LanguagePreference.FRENCH,
            onLanguageSelected = {},
            refreshInterval = RefreshInterval.HOUR_1,
            onRefreshIntervalSelected = {},
            forecastEngine = ForecastEngine.MULTI_CONSENSUS,
            onForecastEngineSelected = {},
            biasRefreshRequested = false,
            onBiasRefreshClick = {},
            onDonateClick = {},
            padding = PaddingValues(0.dp)
        )
    }
}

@MeteoScreenPreview
@Composable
private fun DonationDialogPreview() {
    MeteoPreviewSurface {
        DonationDialog(onDismiss = {})
    }
}
