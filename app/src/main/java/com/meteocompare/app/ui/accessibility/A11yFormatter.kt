package com.meteocompare.app.ui.accessibility

import android.content.Context
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.ConfidenceLevel
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.HourlyConfidenceBand
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.ui.citylist.CityCardState
import com.meteocompare.app.ui.citylist.ForecastState
import kotlin.math.roundToInt

/**
 * Helpers pour générer des descriptions accessibles cohérentes.
 *
 * Le principe : TalkBack lit ces strings telles quelles. Donc on évite
 * les abréviations ("°" → "degrés"), on linéarise les ranges en phrases
 * naturelles, et on annonce le niveau qualitatif de confiance plutôt
 * que juste le pourcentage isolé.
 *
 * Avant : "85" lu comme "quatre-vingt cinq" (sans contexte)
 * Après : "Confiance haute, 85 pourcent" (lisible et informatif)
 *
 * Chaque fonction prend un [Context] pour résoudre les ressources string
 * — ça permet la localisation (FR/EN) sans dupliquer la logique de formatage.
 * On garde `object` (pas `class`) pour éviter d'injecter ça partout : un
 * Context est facile à obtenir au call site (LocalContext.current en Compose,
 * ApplicationContext via Hilt sinon).
 */
object A11yFormatter {

    fun confidenceLevelLabel(context: Context, level: ConfidenceLevel): String = when (level) {
        ConfidenceLevel.HIGH -> context.getString(R.string.a11y_confidence_high)
        ConfidenceLevel.MEDIUM -> context.getString(R.string.a11y_confidence_medium)
        ConfidenceLevel.LOW -> context.getString(R.string.a11y_confidence_low)
    }

    fun temperatureDescription(context: Context, score: ConfidenceScore): String {
        val main = if (score.spread <= 1.0) {
            context.getString(R.string.a11y_temp_single, score.meanValue.roundToInt())
        } else {
            context.getString(
                R.string.a11y_temp_range,
                score.minValue.roundToInt(),
                score.maxValue.roundToInt()
            )
        }
        return context.getString(
            R.string.a11y_temp_with_confidence,
            main,
            confidenceLevelLabel(context, score.level).lowercase(),
            score.percent
        )
    }

    fun precipitationDescription(context: Context, precip: PrecipitationConfidence): String = when (precip) {
        is PrecipitationConfidence.NoRain ->
            context.getString(R.string.a11y_no_rain, precip.percent)
        is PrecipitationConfidence.Rain ->
            context.getString(
                R.string.a11y_rain,
                precip.minMm.roundToInt(),
                precip.maxMm.roundToInt(),
                precip.percent
            )
        is PrecipitationConfidence.Divided ->
            context.getString(R.string.a11y_models_divided, precip.modelsForRain, precip.modelCount)
    }

    fun cityCardDescription(context: Context, state: CityCardState): String {
        val city = state.city
        val base = "Ville ${city.name}${city.admin1?.let { ", $it" } ?: ""}"
        return when (val f = state.forecast) {
            ForecastState.Loading -> "$base. ${context.getString(R.string.a11y_city_loading)}"
            is ForecastState.Error -> "$base. ${context.getString(R.string.a11y_city_error, f.message)}"
            is ForecastState.Loaded -> {
                val parts = mutableListOf<String>()
                // Condition actuelle en premier : "ensoleillé" + "20°" se
                // suivent dans la lecture TalkBack, ce qui mime la perception
                // visuelle de l'icône + température côte à côte.
                f.currentCondition?.let {
                    parts += context.getString(weatherConditionStringRes(it))
                }
                f.currentTemp?.let { parts += context.getString(R.string.a11y_now_temp, it.roundToInt()) }
                f.today.tempMax?.let {
                    parts += context.getString(R.string.a11y_temperature_prefix) + " " +
                        temperatureDescription(context, it)
                }
                f.today.precipitation?.let { parts += precipitationDescription(context, it) }
                f.today.overallPercent?.let { parts += context.getString(R.string.a11y_overall_confidence, it) }
                "$base. " + parts.joinToString(". ") + "."
            }
        }
    }

    /**
     * Mapping famille WeatherCondition → string res. Volontairement dupliqué
     * du composant `WeatherIcon` parce que l'A11yFormatter est dans une couche
     * différente (a11y/) qui ne devrait pas dépendre de l'UI components, et
     * inversement. Coût de la duplication = 13 lignes, gain = aucun couplage
     * entre les deux.
     */
    private fun weatherConditionStringRes(c: com.meteocompare.app.domain.model.WeatherCondition): Int = when (c) {
        com.meteocompare.app.domain.model.WeatherCondition.CLEAR -> R.string.weather_clear
        com.meteocompare.app.domain.model.WeatherCondition.MAINLY_CLEAR -> R.string.weather_mainly_clear
        com.meteocompare.app.domain.model.WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
        com.meteocompare.app.domain.model.WeatherCondition.OVERCAST -> R.string.weather_overcast
        com.meteocompare.app.domain.model.WeatherCondition.FOG -> R.string.weather_fog
        com.meteocompare.app.domain.model.WeatherCondition.DRIZZLE -> R.string.weather_drizzle
        com.meteocompare.app.domain.model.WeatherCondition.RAIN -> R.string.weather_rain
        com.meteocompare.app.domain.model.WeatherCondition.FREEZING_RAIN -> R.string.weather_freezing_rain
        com.meteocompare.app.domain.model.WeatherCondition.SNOW -> R.string.weather_snow
        com.meteocompare.app.domain.model.WeatherCondition.RAIN_SHOWERS -> R.string.weather_rain_showers
        com.meteocompare.app.domain.model.WeatherCondition.SNOW_SHOWERS -> R.string.weather_snow_showers
        com.meteocompare.app.domain.model.WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
        com.meteocompare.app.domain.model.WeatherCondition.UNKNOWN -> R.string.weather_unknown
    }

    fun hourlyChartDescription(context: Context, bands: List<HourlyConfidenceBand>): String {
        if (bands.size < 2) return context.getString(R.string.a11y_hourly_empty)
        val first = bands.first()
        val last = bands.last()
        val daysAhead = java.time.Duration
            .between(first.timestamp, last.timestamp).toDays().toInt()
        val firstTemp = first.meanValue.roundToInt()
        val lastTemp = last.meanValue.roundToInt()
        val spreadStart = first.maxValue - first.minValue
        val spreadEnd = last.maxValue - last.minValue
        val divergence = when {
            spreadEnd > spreadStart * 2 -> context.getString(R.string.a11y_divergence_strong)
            spreadEnd > spreadStart * 1.3 -> context.getString(R.string.a11y_divergence_increasing)
            else -> context.getString(R.string.a11y_divergence_stable)
        }
        return context.getString(
            R.string.a11y_hourly_template,
            daysAhead, firstTemp, lastTemp, divergence, first.percent, last.percent
        )
    }

    fun multiModelChartDescription(context: Context, modelCount: Int, daysCovered: Int): String =
        if (modelCount > 1)
            context.getString(R.string.a11y_multi_model_chart_many, daysCovered, modelCount)
        else
            context.getString(R.string.a11y_multi_model_chart_one, daysCovered, modelCount)

    fun todaySummaryDescription(context: Context, today: DayConfidence, modelCount: Int): String {
        val header = if (modelCount > 1)
            context.getString(R.string.a11y_today_summary_many, modelCount)
        else
            context.getString(R.string.a11y_today_summary_one, modelCount)
        val parts = mutableListOf(header)
        today.tempMax?.let {
            parts += context.getString(R.string.a11y_temp_max_label) + " " + temperatureDescription(context, it)
        }
        today.tempMin?.let {
            parts += context.getString(R.string.a11y_temp_min_label) + " " + temperatureDescription(context, it)
        }
        today.precipitation?.let { parts += precipitationDescription(context, it) }
        today.windMax?.let {
            // Vent : on remplace "degrés" par "kilomètres par heure" dans la description.
            // Hack utilitaire pour ne pas dupliquer toute la logique de format pour une seule unité.
            val degUnit = context.getString(R.string.a11y_temp_single, 0).removePrefix("0 ")
            val kmhUnit = context.getString(R.string.a11y_kmh_unit)
            parts += context.getString(R.string.a11y_wind_max_label) + " " +
                temperatureDescription(context, it).replace(degUnit, kmhUnit)
        }
        return parts.joinToString(". ") + "."
    }
}
