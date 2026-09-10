package com.meteocompare.app.core.locale

import androidx.annotation.StringRes
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.WeatherCondition

/**
 * Source unique du libellé localisé associé à une condition météo.
 *
 * Ce mapping est partagé par Compose, l'accessibilité et les widgets Glance.
 * Une condition absente est traitée comme inconnue, ce qui couvre aussi les
 * caches antérieurs à l'ajout de la condition courante.
 */
@StringRes
internal fun weatherConditionLabelRes(condition: WeatherCondition?): Int = when (condition) {
    WeatherCondition.CLEAR -> R.string.weather_clear
    WeatherCondition.MAINLY_CLEAR -> R.string.weather_mainly_clear
    WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
    WeatherCondition.OVERCAST -> R.string.weather_overcast
    WeatherCondition.FOG -> R.string.weather_fog
    WeatherCondition.DRIZZLE -> R.string.weather_drizzle
    WeatherCondition.RAIN -> R.string.weather_rain
    WeatherCondition.FREEZING_RAIN -> R.string.weather_freezing_rain
    WeatherCondition.SNOW -> R.string.weather_snow
    WeatherCondition.RAIN_SHOWERS -> R.string.weather_rain_showers
    WeatherCondition.SNOW_SHOWERS -> R.string.weather_snow_showers
    WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
    WeatherCondition.UNKNOWN,
    null -> R.string.weather_unknown
}
