package com.meteocompare.app.ui.accessibility

import com.meteocompare.app.core.units.WeatherUnit
import com.meteocompare.app.core.units.WeatherUnits

import com.meteocompare.app.core.units.weatherString
import com.meteocompare.app.core.units.spokenUnit

import android.content.res.Resources
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.weatherConditionLabelRes
import com.meteocompare.app.domain.model.ConfidenceLevel
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.HourlyConfidenceBand
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.ui.citylist.CityCardState
import com.meteocompare.app.ui.citylist.ForecastState
import java.time.Duration
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
 * Chaque fonction prend des [Resources] pour résoudre les ressources string
 * — ça permet la localisation (FR/EN) sans dupliquer la logique de formatage.
 * On garde `object` (pas `class`) pour éviter d'injecter ce formatter partout.
 * Les Resources sont obtenues via LocalResources.current en Compose, ce qui
 * invalide correctement la composition lors des changements de configuration.
 */
object A11yFormatter {

    fun confidenceLevelLabel(resources: Resources, level: ConfidenceLevel): String = when (level) {
        ConfidenceLevel.HIGH -> resources.getString(R.string.a11y_confidence_high)
        ConfidenceLevel.MEDIUM -> resources.getString(R.string.a11y_confidence_medium)
        ConfidenceLevel.LOW -> resources.getString(R.string.a11y_confidence_low)
    }

    fun temperatureDescription(resources: Resources, score: ConfidenceScore, units: WeatherUnits = WeatherUnits()): String =
        measureDescription(resources, score, WeatherUnit.TEMPERATURE, units)

    private fun measureDescription(resources: Resources, score: ConfidenceScore, metricUnit: WeatherUnit,
                                   units: WeatherUnits): String {
        val locale = resources.configuration.locales[0]
        val unitLabel = spokenUnit(resources, units, metricUnit)
        val main = if (score.spread <= 1.0) {
            resources.getString(R.string.a11y_temp_single,
                units.value(score.meanValue, metricUnit, locale = locale), unitLabel)
        } else {
            resources.getString(R.string.a11y_temp_range,
                units.value(score.minValue, metricUnit, locale = locale),
                units.value(score.maxValue, metricUnit, locale = locale), unitLabel)
        }
        val convergence = score.convergencePercent
        return if (convergence != null) resources.getString(R.string.a11y_temp_with_confidence,
            main, confidenceLevelLabel(resources, score.level).lowercase(locale), convergence) else main
    }

    fun precipitationDescription(resources: Resources, precip: PrecipitationConfidence, units: WeatherUnits = WeatherUnits()): String = when (precip) {
        is PrecipitationConfidence.NoRain -> {
            val convergence = precip.convergencePercent
            if (convergence != null) resources.getString(R.string.a11y_no_rain, convergence)
            else resources.getString(R.string.a11y_no_rain_no_convergence)
        }
        is PrecipitationConfidence.Rain -> {
            val convergence = precip.convergencePercent
            if (convergence != null) {
                weatherString(resources, units,
                    R.string.a11y_rain,
                    precip.minMm,
                    precip.maxMm,
                    convergence
                )
            } else {
                weatherString(resources, units,
                    R.string.a11y_rain_no_convergence,
                    precip.minMm,
                    precip.maxMm
                )
            }
        }
        is PrecipitationConfidence.Divided ->
            weatherString(resources, units,
                R.string.a11y_models_divided,
                precip.modelsForRain,
                precip.modelCount,
                precip.rainMinMm,
                precip.rainMaxMm
            )
    }

    fun cityCardDescription(resources: Resources, state: CityCardState, units: WeatherUnits = WeatherUnits()): String {
        val city = state.city
        val marine = if (city.marineEnabled) ". ${resources.getString(R.string.marine_enabled)}" else ""
        val base = "Ville ${city.name}${city.admin1?.let { ", $it" } ?: ""}$marine"
        return when (val f = state.forecast) {
            ForecastState.Loading -> "$base. ${resources.getString(R.string.a11y_city_loading)}"
            is ForecastState.Error -> {
                val message = f.message
                    ?: f.messageRes?.let(resources::getString)
                    ?: resources.getString(R.string.error_unknown)
                "$base. ${resources.getString(R.string.a11y_city_error, message)}"
            }
            is ForecastState.Loaded -> {
                val parts = mutableListOf<String>()
                // Condition actuelle en premier : "ensoleillé" + "20°" se
                // suivent dans la lecture TalkBack, ce qui mime la perception
                // visuelle de l'icône + température côte à côte.
                f.currentCondition?.let {
                    parts += resources.getString(weatherConditionLabelRes(it))
                }
                f.currentTemp?.let { parts += weatherString(resources, units, R.string.a11y_now_temp, it) }
                f.today.tempMax?.let {
                    parts += resources.getString(R.string.a11y_temperature_prefix) + " " +
                        temperatureDescription(resources, it, units = units)
                }
                f.today.precipitation?.let { parts += precipitationDescription(resources, it, units = units) }
                f.today.windMax?.let {
                    parts += weatherString(resources, units, R.string.a11y_wind_max_label) + " " +
                        windDescription(resources, it, units = units)
                }
                f.today.windGustMax?.let {
                    parts += weatherString(resources, units, R.string.var_wind_gust_max) + " " +
                        windDescription(resources, it, units = units)
                }
                f.today.overallPercent?.let { parts += resources.getString(R.string.a11y_overall_confidence, it) }
                "$base. " + parts.joinToString(". ") + "."
            }
        }
    }

    fun hourlyChartDescription(resources: Resources, bands: List<HourlyConfidenceBand>, units: WeatherUnits = WeatherUnits(), metricUnit: WeatherUnit = WeatherUnit.TEMPERATURE): String {
        if (bands.size < 2) return resources.getString(R.string.a11y_hourly_empty)
        val first = bands.first()
        val last = bands.last()
        val daysAhead = Duration
            .between(first.timestamp, last.timestamp).toDays().toInt()
        val firstTemp = first.meanValue
        val lastTemp = last.meanValue
        val spreadStart = first.maxValue - first.minValue
        val spreadEnd = last.maxValue - last.minValue
        val divergence = when {
            spreadEnd > spreadStart * 2 -> resources.getString(R.string.a11y_divergence_strong)
            spreadEnd > spreadStart * 1.3 -> resources.getString(R.string.a11y_divergence_increasing)
            else -> resources.getString(R.string.a11y_divergence_stable)
        }
        if (metricUnit != WeatherUnit.TEMPERATURE) {
            val locale = resources.configuration.locales[0]
            return resources.getString(R.string.a11y_hourly_measure_template,
                resources.getString(if (metricUnit == WeatherUnit.PRECIPITATION) R.string.var_precipitation else R.string.metric_detail_wind),
                units.format(first.meanValue, metricUnit, if (metricUnit == WeatherUnit.PRECIPITATION) 1 else 0, locale),
                units.format(last.meanValue, metricUnit, if (metricUnit == WeatherUnit.PRECIPITATION) 1 else 0, locale),
                first.percent, last.percent)
        }
        return weatherString(resources, units,
            R.string.a11y_hourly_template,
            daysAhead, firstTemp, lastTemp, divergence, first.percent, last.percent
        )
    }

    fun todaySummaryDescription(resources: Resources, today: DayConfidence, modelCount: Int, units: WeatherUnits = WeatherUnits()): String {
        val header = if (modelCount > 1)
            resources.getString(R.string.a11y_today_summary_many, modelCount)
        else
            resources.getString(R.string.a11y_today_summary_one, modelCount)
        val parts = mutableListOf(header)
        today.tempMax?.let {
            parts += resources.getString(R.string.a11y_temp_max_label) + " " + temperatureDescription(resources, it, units = units)
        }
        today.tempMin?.let {
            parts += resources.getString(R.string.a11y_temp_min_label) + " " + temperatureDescription(resources, it, units = units)
        }
        today.precipitation?.let { parts += precipitationDescription(resources, it, units = units) }
        today.windMax?.let {
            parts += weatherString(resources, units, R.string.a11y_wind_max_label) + " " +
                windDescription(resources, it, units = units)
        }
        today.windGustMax?.let {
            parts += weatherString(resources, units, R.string.var_wind_gust_max) + " " +
                windDescription(resources, it, units = units)
        }
        return parts.joinToString(". ") + "."
    }

    private fun windDescription(resources: Resources, score: ConfidenceScore, units: WeatherUnits): String =
        measureDescription(resources, score, WeatherUnit.WIND_SPEED, units)
}
