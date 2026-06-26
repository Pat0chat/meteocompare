package com.meteocompare.app.ui.accessibility

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
 */
object A11yFormatter {

    fun confidenceLevelLabel(level: ConfidenceLevel): String = when (level) {
        ConfidenceLevel.HIGH -> "Confiance haute"
        ConfidenceLevel.MEDIUM -> "Confiance moyenne"
        ConfidenceLevel.LOW -> "Confiance faible"
    }

    fun temperatureDescription(score: ConfidenceScore): String {
        val unit = "degrés"
        val main = if (score.spread <= 1.0) {
            "${score.meanValue.roundToInt()} $unit"
        } else {
            "entre ${score.minValue.roundToInt()} et ${score.maxValue.roundToInt()} $unit"
        }
        return "$main, ${confidenceLevelLabel(score.level).lowercase()}, ${score.percent} pourcent"
    }

    fun precipitationDescription(precip: PrecipitationConfidence): String = when (precip) {
        is PrecipitationConfidence.NoRain ->
            "Pas de pluie attendue, confiance ${precip.percent} pourcent"
        is PrecipitationConfidence.Rain ->
            "Pluie attendue, entre ${precip.minMm.roundToInt()} et ${precip.maxMm.roundToInt()} millimètres, confiance ${precip.percent} pourcent"
        is PrecipitationConfidence.Divided ->
            "Modèles divisés sur la pluie, ${precip.modelsForRain} sur ${precip.modelCount} prédisent la pluie"
    }

    fun cityCardDescription(state: CityCardState): String {
        val city = state.city
        val base = "Ville ${city.name}${city.admin1?.let { ", $it" } ?: ""}"
        return when (val f = state.forecast) {
            ForecastState.Loading -> "$base. Chargement des prévisions."
            is ForecastState.Error -> "$base. Erreur : ${f.message}."
            is ForecastState.Loaded -> {
                val parts = mutableListOf<String>()
                f.currentTemp?.let { parts += "Maintenant ${it.roundToInt()} degrés" }
                f.today.tempMax?.let { parts += "Température " + temperatureDescription(it) }
                f.today.precipitation?.let { parts += precipitationDescription(it) }
                f.today.overallPercent?.let { parts += "Confiance globale $it pourcent" }
                "$base. " + parts.joinToString(". ") + "."
            }
        }
    }

    fun hourlyChartDescription(bands: List<HourlyConfidenceBand>): String {
        if (bands.size < 2) return "Graphique vide, données insuffisantes"
        val first = bands.first()
        val last = bands.last()
        val daysAhead = java.time.Duration
            .between(first.timestamp, last.timestamp).toDays()
        val firstTemp = first.meanValue.roundToInt()
        val lastTemp = last.meanValue.roundToInt()
        val spreadStart = first.maxValue - first.minValue
        val spreadEnd = last.maxValue - last.minValue
        val divergence = if (spreadEnd > spreadStart * 2) "qui augmente fortement"
                        else if (spreadEnd > spreadStart * 1.3) "qui augmente"
                        else "globalement stable"
        return "Bande de confiance horaire sur $daysAhead jours. " +
            "Température moyenne $firstTemp degrés à l'heure actuelle, " +
            "$lastTemp degrés à la fin de l'horizon. " +
            "Divergence entre modèles $divergence. " +
            "Confiance ${first.percent} pourcent maintenant, ${last.percent} pourcent à J plus $daysAhead."
    }

    fun multiModelChartDescription(modelCount: Int, daysCovered: Int): String =
        "Graphique de comparaison des températures sur $daysCovered jours, " +
            "$modelCount modèle${if (modelCount > 1) "s" else ""} affiché${if (modelCount > 1) "s" else ""}."

    fun todaySummaryDescription(today: DayConfidence, modelCount: Int): String {
        val parts = mutableListOf("Résumé d'aujourd'hui, $modelCount modèle${if (modelCount > 1) "s" else ""} analysé${if (modelCount > 1) "s" else ""}")
        today.tempMax?.let { parts += "Température max " + temperatureDescription(it) }
        today.tempMin?.let { parts += "Température min " + temperatureDescription(it) }
        today.precipitation?.let { parts += precipitationDescription(it) }
        today.windMax?.let { parts += "Vent max " + temperatureDescription(it).replace("degrés", "kilomètres par heure") }
        return parts.joinToString(". ") + "."
    }
}
