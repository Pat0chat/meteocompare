package com.meteocompare.app.domain.model

/**
 * Modèles météorologiques exposés par l'API Open-Meteo.
 *
 * @property apiKey Identifiant du modèle dans le paramètre `&models=` de l'API.
 * @property displayName Nom court affiché dans l'UI.
 * @property resolutionKm Résolution horizontale native du modèle (en kilomètres).
 *           Utilisé pour pondérer le calcul d'indice de confiance — plus la résolution
 *           est fine, plus le modèle est fiable à courte échéance sur sa zone de couverture.
 * @property maxForecastDays Horizon de prévision typique du modèle.
 * @property coverage Zone de couverture (utile pour filtrer selon la position de la ville).
 */
enum class WeatherModel(
    val apiKey: String,
    val displayName: String,
    val resolutionKm: Double,
    val maxForecastDays: Int,
    val coverage: Coverage
) {
    AROME_FRANCE_HD(
        apiKey = "meteofrance_arome_france_hd",
        displayName = "AROME HD",
        resolutionKm = 1.5,
        maxForecastDays = 2,
        coverage = Coverage.FRANCE
    ),
    AROME_FRANCE(
        apiKey = "meteofrance_arome_france",
        displayName = "AROME",
        resolutionKm = 2.5,
        maxForecastDays = 2,
        coverage = Coverage.FRANCE
    ),
    ARPEGE_EUROPE(
        apiKey = "meteofrance_arpege_europe",
        displayName = "ARPEGE EU",
        resolutionKm = 11.0,
        maxForecastDays = 4,
        coverage = Coverage.EUROPE
    ),
    ARPEGE_WORLD(
        apiKey = "meteofrance_arpege_world",
        displayName = "ARPEGE",
        resolutionKm = 25.0,
        maxForecastDays = 4,
        coverage = Coverage.GLOBAL
    ),
    ICON_EU(
        apiKey = "icon_eu",
        displayName = "ICON-EU",
        resolutionKm = 7.0,
        maxForecastDays = 5,
        coverage = Coverage.EUROPE
    ),
    ICON_GLOBAL(
        apiKey = "icon_seamless",
        displayName = "ICON",
        resolutionKm = 13.0,
        maxForecastDays = 7,
        coverage = Coverage.GLOBAL
    ),
    GFS(
        apiKey = "gfs_seamless",
        displayName = "GFS",
        resolutionKm = 13.0,
        maxForecastDays = 16,
        coverage = Coverage.GLOBAL
    ),
    ECMWF(
        apiKey = "ecmwf_ifs025",
        displayName = "ECMWF",
        resolutionKm = 25.0,
        maxForecastDays = 10,
        coverage = Coverage.GLOBAL
    );

    companion object {
        /** Modèles activés par défaut pour une comparaison MVP. */
        val MVP_SELECTION: List<WeatherModel> = listOf(
            AROME_FRANCE_HD,
            ARPEGE_EUROPE,
            ICON_EU,
            GFS,
            ECMWF
        )
    }
}

enum class Coverage { FRANCE, EUROPE, GLOBAL }
