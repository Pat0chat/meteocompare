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
    /** Vitesse du vent MOYEN à 10m en km/h (à ne pas confondre avec les rafales). */
    val windSpeed10m: List<Double?>,
    /**
     * Code météo WMO 4677 (0=clair, 3=couvert, 61=pluie, 95=orage, etc.).
     * Vide si le modèle ne fournit pas la variable ou si le cache provient
     * d'une version antérieure de l'app — l'UI traite ce cas en n'affichant
     * simplement pas d'icône, sans erreur.
     */
    val weatherCode: List<Int?> = emptyList(),
    /**
     * Direction d'origine du vent à 10m, en degrés météorologiques
     * (0=Nord, 90=Est, 180=Sud, 270=Ouest — la direction D'OÙ souffle le vent).
     * Vide si le modèle ne fournit pas la variable ou cache pré-feature.
     */
    val windDirection10m: List<Int?> = emptyList(),
    /**
     * Probabilité de précipitation sur l'heure, 0-100%. Peut être absente
     * selon le modèle, l'horizon ou un ancien cache ; l'UI l'omet alors.
     */
    val precipitationProbability: List<Int?> = emptyList(),
    /**
     * Couverture nuageuse totale, 0-100%. Vide si non fourni par le modèle
     * ou cache antérieur à la feature.
     */
    val cloudCover: List<Int?> = emptyList(),
    /**
     * Rafales à 10 m en km/h : maximum observé/prévu sur l'heure précédente
     * selon la sémantique Open-Meteo. Vide si absent ou cache antérieur.
     */
    val windGusts10m: List<Double?> = emptyList()
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
    /**
     * Vitesse maximale du vent MOYEN sur la journée à 10m en km/h.
     * IMPORTANT : c'est le max des vents horaires moyens, PAS les rafales
     * (`wind_gusts_10m_max` est stocké séparément dans [windGustsMax]).
     */
    val windSpeedMax: List<Double?>,
    /** Code météo WMO 4677 — défaut empty pour les caches antérieurs. */
    val weatherCode: List<Int?> = emptyList(),
    /**
     * Direction dominante du vent sur la journée, en degrés météorologiques,
     * telle que fournie par Open-Meteo.
     */
    val windDirection10mDominant: List<Int?> = emptyList(),
    /**
     * Probabilité MAX de précipitation sur la journée, 0-100%. On prend le
     * max journalier plutôt que la moyenne — le signal utile est "y a-t-il
     * un pic de risque de pluie", pas "quelle est la valeur moyenne".
     */
    val precipitationProbabilityMax: List<Int?> = emptyList(),
    /** Rafale maximale de la journée à 10 m en km/h. */
    val windGustsMax: List<Double?> = emptyList(),
    /** Lever/coucher du soleil fournis par Open-Meteo, convertis en instants absolus. */
    val sunrise: List<Instant?> = emptyList(),
    val sunset: List<Instant?> = emptyList()
    // Note : cloud_cover_mean n'est pas demandé côté Forecast API en daily —
    // l'application agrège la couverture horaire du même jour si nécessaire.
) {
    val size: Int get() = dates.size
}
