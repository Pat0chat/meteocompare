package com.meteocompare.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.ui.preview.MeteoComponentPreview
import com.meteocompare.app.ui.preview.MeteoPreviewSurface

/** Preview of the real localized sunshine formatter used across the UI. */
@MeteoComponentPreview
@Composable
private fun SunshineDurationPreview() {
    MeteoPreviewSurface {
        val formats = localizedSunshineDurationFormats()
        val locale = LocalLocale.current.platformLocale
        Column(modifier = Modifier.padding(16.dp)) {
            Text(formatSunshineDuration(0.3, true, WeatherUnits(), locale, formats))
            Text(formatSunshineDuration(1.2, false, WeatherUnits(), locale, formats))
        }
    }
}
