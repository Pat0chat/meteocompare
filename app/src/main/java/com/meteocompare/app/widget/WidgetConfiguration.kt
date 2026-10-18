package com.meteocompare.app.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.data.repository.CurrentLocationStore

/** Préférences persistées d'une seule instance de widget. */
internal data class WidgetConfiguration(
    val cityId: String? = null,
    val opacityPct: Int = WidgetPreferences.DEFAULT_OPACITY_PCT,
    val forecastMode: ForecastMode = WidgetPreferences.DEFAULT_FORECAST_MODE,
    val backgroundColorArgb: Int? = null,
    val textColorArgb: Int? = null,
    val cornerStyle: WidgetCornerStyle = WidgetCornerStyle.ROUNDED,
    val cornerRadiusDp: Int = WidgetPreferences.DEFAULT_CORNER_RADIUS_DP,
    val contentSpacing: WidgetContentSpacing = WidgetContentSpacing.NORMAL
) {
    /** N'écrit QUE les options de présentation : les ticks et états de refresh
     * restent exactement tels que WorkManager les a enregistrés. */
    fun writeTo(prefs: MutablePreferences) {
        if (cityId != null) prefs[WidgetPreferences.CityIdKey] = cityId
        else prefs.remove(WidgetPreferences.CityIdKey)
        prefs[WidgetPreferences.OpacityPctKey] = opacityPct.coerceIn(0, 100)
        prefs[WidgetPreferences.ForecastModeKey] = forecastMode.name
        prefs[WidgetPreferences.CornerStyleKey] = cornerStyle.name
        prefs[WidgetPreferences.CornerRadiusDpKey] = cornerRadiusDp.coerceIn(0, WidgetPreferences.MAX_CORNER_RADIUS_DP)
        prefs[WidgetPreferences.ContentSpacingKey] = contentSpacing.name
        if (backgroundColorArgb != null) prefs[WidgetPreferences.BackgroundColorKey] = backgroundColorArgb
        else prefs.remove(WidgetPreferences.BackgroundColorKey)
        if (textColorArgb != null) prefs[WidgetPreferences.TextColorKey] = textColorArgb
        else prefs.remove(WidgetPreferences.TextColorKey)
    }

    fun selectableCityId(favorites: List<City>): String? =
        if (cityId == null) favorites.firstOrNull()?.let {
            if (CurrentLocationStore.isCurrentCity(it.id)) CurrentLocationStore.WIDGET_CITY_ID else it.id
        } else if (cityId == CurrentLocationStore.WIDGET_CITY_ID) {
            cityId.takeIf { favorites.any { city -> CurrentLocationStore.isCurrentCity(city.id) } }
        } else favorites.firstOrNull { it.id == cityId }?.id

    companion object {
        fun fromPreferences(prefs: Preferences): WidgetConfiguration = WidgetConfiguration(
            cityId = prefs[WidgetPreferences.CityIdKey],
            opacityPct = (prefs[WidgetPreferences.OpacityPctKey]
                ?: WidgetPreferences.DEFAULT_OPACITY_PCT).coerceIn(0, 100),
            forecastMode = prefs[WidgetPreferences.ForecastModeKey]?.let { stored ->
                runCatching { ForecastMode.valueOf(stored).normalized() }.getOrNull()
            } ?: WidgetPreferences.DEFAULT_FORECAST_MODE,
            backgroundColorArgb = prefs[WidgetPreferences.BackgroundColorKey],
            textColorArgb = prefs[WidgetPreferences.TextColorKey],
            cornerStyle = WidgetCornerStyle.fromStored(prefs[WidgetPreferences.CornerStyleKey]),
            cornerRadiusDp = (prefs[WidgetPreferences.CornerRadiusDpKey]
                ?: WidgetPreferences.DEFAULT_CORNER_RADIUS_DP).coerceIn(0, WidgetPreferences.MAX_CORNER_RADIUS_DP),
            contentSpacing = WidgetContentSpacing.fromStored(prefs[WidgetPreferences.ContentSpacingKey])
        )
    }
}
