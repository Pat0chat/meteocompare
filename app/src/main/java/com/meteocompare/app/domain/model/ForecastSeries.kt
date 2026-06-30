package com.meteocompare.app.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Série temporelle de prévisions issues d'UN modèle météo pour UNE ville.
 *
 * Les listes `hourly` et `daily` sont alignées avec leurs timestamps respectifs.
 * Les valeurs sont nullables : un modèle peut ne pas fournir une variable donnée,
 * ou une heure isolée peut manquer (interpolation Open-Meteo).
 */
data class ForecastSeries(
    val model: WeatherModel,
    val hourly: HourlyForecast,
    val daily: DailyForecast
)

data class HourlyForecast(
    val timestamps: List<Instant>,
    /** Température à 2m en °C. */
    val temperature2m: List<Double?>,
    /** Précipitations en mm (somme sur l'heure écoulée). */
    val precipitation: List<Double?>,
    /** Vitesse du vent à 10m en km/h. */
    val windSpeed10m: List<Double?>,
    /**
     * Code météo WMO 4677 (0=clair, 3=couvert, 61=pluie, 95=orage, etc.).
     * Vide si le modèle ne fournit pas la variable ou si le cache provient
     * d'une version antérieure de l'app — l'UI traite ce cas en n'affichant
     * simplement pas d'icône, sans erreur.
     */
    val weatherCode: List<Int?> = emptyList()
) {
    val size: Int get() = timestamps.size
}

data class DailyForecast(
    val dates: List<LocalDate>,
    /** Température maximale du jour en °C. */
    val tempMax: List<Double?>,
    /** Température minimale du jour en °C. */
    val tempMin: List<Double?>,
    /** Cumul de précipitations journalier en mm. */
    val precipitationSum: List<Double?>,
    /** Vitesse de vent maximale du jour en km/h. */
    val windSpeedMax: List<Double?>,
    /** Code météo WMO 4677 — défaut empty pour les caches antérieurs. */
    val weatherCode: List<Int?> = emptyList()
) {
    val size: Int get() = dates.size
}
