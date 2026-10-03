package com.meteocompare.app.widget

import androidx.datastore.preferences.core.Preferences
import com.meteocompare.app.domain.model.City

/** Préférences persistées d'une seule instance de widget. */
internal data class WidgetConfiguration(
    val cityId: String? = null,
    val opacityPct: Int = WidgetPreferences.DEFAULT_OPACITY_PCT,
    val forecastMode: ForecastMode = WidgetPreferences.DEFAULT_FORECAST_MODE,
    val backgroundColorArgb: Int? = null,
    val textColorArgb: Int? = null
) {
    fun selectableCityId(favorites: List<City>): String? =
        if (cityId == null) favorites.firstOrNull()?.id
        else favorites.firstOrNull { it.id == cityId }?.id

    companion object {
        fun fromPreferences(prefs: Preferences): WidgetConfiguration = WidgetConfiguration(
            cityId = prefs[WidgetPreferences.CityIdKey],
            opacityPct = (prefs[WidgetPreferences.OpacityPctKey]
                ?: WidgetPreferences.DEFAULT_OPACITY_PCT).coerceIn(0, 100),
            forecastMode = prefs[WidgetPreferences.ForecastModeKey]?.let { stored ->
                runCatching { ForecastMode.valueOf(stored).normalized() }.getOrNull()
            } ?: WidgetPreferences.DEFAULT_FORECAST_MODE,
            backgroundColorArgb = prefs[WidgetPreferences.BackgroundColorKey],
            textColorArgb = prefs[WidgetPreferences.TextColorKey]
        )
    }
}
