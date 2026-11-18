package com.meteocompare.app.domain.model

import com.meteocompare.app.core.util.haversineKm

/** Station METAR embarquée (liste NOAA AWC, voir `scripts/generate_metar_stations.py`). */
data class MetarStation(
    val icao: String,
    val latitude: Double,
    val longitude: Double,
    val elevationM: Double
)

/**
 * Point où l'historique de calibration d'une ville est mesuré.
 *
 * Avec une station, les prévisions Previous Runs et la réanalyse sont prises
 * aux coordonnées de la station, et la Tmax de référence vient de ses METAR :
 * prévision et observation portent ainsi sur le même lieu, comme dans le banc
 * d'essai (`scripts/backtest`). Le biais mesuré là est ensuite appliqué à la
 * ville. Sans station proche, tout reste aux coordonnées de la ville, sur la
 * réanalyse.
 */
data class CalibrationPoint(
    val latitude: Double,
    val longitude: Double,
    val station: MetarStation?
) {
    /** Identifie la référence : un changement impose de repartir d'un historique vide. */
    val key: String get() = station?.let { "metar:${it.icao}" } ?: REANALYSIS_KEY

    /**
     * Vrai quand l'historique construit sur [storedKey] doit être effacé puis
     * reconstruit. Sans clé enregistrée, l'historique existant date d'avant
     * cette fonctionnalité et repose donc sur la réanalyse.
     */
    fun requiresRebuild(storedKey: String?): Boolean = (storedKey ?: REANALYSIS_KEY) != key

    companion object {
        const val REANALYSIS_KEY = "reanalysis"

        fun reanalysis(city: City) = CalibrationPoint(city.latitude, city.longitude, null)

        fun of(city: City, station: MetarStation?) =
            station?.let { CalibrationPoint(it.latitude, it.longitude, it) } ?: reanalysis(city)
    }
}

/**
 * Choix de la station de calibration : la plus proche à moins de
 * [MAX_DISTANCE_KM] et à moins de [MAX_ELEVATION_GAP_M] de dénivelé. Au-delà,
 * la station ne représente plus le climat local et la ville garde la réanalyse.
 */
object MetarStationSelector {
    const val MAX_DISTANCE_KM = 25.0
    const val MAX_ELEVATION_GAP_M = 150.0

    fun select(
        latitude: Double,
        longitude: Double,
        elevationM: Double,
        stations: List<MetarStation>
    ): MetarStation? = stations
        .asSequence()
        .filter { kotlin.math.abs(it.elevationM - elevationM) <= MAX_ELEVATION_GAP_M }
        .map { it to haversineKm(latitude, longitude, it.latitude, it.longitude) }
        .filter { (_, distance) -> distance <= MAX_DISTANCE_KM }
        .minWithOrNull(compareBy<Pair<MetarStation, Double>> { it.second }.thenBy { it.first.icao })
        ?.first
}
