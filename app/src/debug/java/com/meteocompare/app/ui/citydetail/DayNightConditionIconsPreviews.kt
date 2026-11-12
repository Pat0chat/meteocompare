package com.meteocompare.app.ui.citydetail

import androidx.compose.runtime.Composable
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.preview.MeteoComponentPreview
import com.meteocompare.app.ui.preview.MeteoPreviewSurface

/** Debug-only visual coverage of daytime / nighttime daily conditions. */
@MeteoComponentPreview
@Composable
private fun DayNightConditionIconsPreview() {
    MeteoPreviewSurface {
        DayNightConditionIcons(
            day = WeatherCondition.PARTLY_CLOUDY,
            night = WeatherCondition.CLEAR
        )
    }
}
