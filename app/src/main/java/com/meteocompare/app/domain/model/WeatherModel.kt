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
    ),

    // ──────────────────────────────────────────────────────────────────────
    //  Nouveaux modèles — appendus en fin d'enum pour préserver les ordinals
    //  des modèles existants (utilisés comme clé de tri stable dans plusieurs
    //  vues, ex. ForecastTable.sortedBy { it.ordinal }).
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UK Met Office — modèle global déterministe.
     *
     * Ajout éditorial : c'est le 3ᵉ grand modèle occidental aux côtés de GFS
     * (NOAA) et ECMWF. Quand GFS et ECMWF divergent, UKMO sert souvent
     * d'arbitre. Score de vérification historiquement très bon sur l'Europe.
     * 10 km est légèrement plus fin que GFS (13 km) et ECMWF (25 km) — un
     * compromis intéressant entre granularité et horizon.
     */
    UKMO_GLOBAL(
        apiKey = "ukmo_global_deterministic_10km",
        displayName = "UKMO",
        resolutionKm = 10.0,
        maxForecastDays = 7,
        coverage = Coverage.GLOBAL
    ),

    /**
     * ECMWF AIFS — modèle de prévision par intelligence artificielle.
     *
     * Ajout éditorial fort : AIFS est entraîné par graph neural network sur
     * les réanalyses ERA5, sans résoudre explicitement les équations
     * d'évolution atmosphérique. C'est la rupture méthodologique majeure de
     * la décennie en météo. Sur le tableau Jour × Modèle, quand AIFS s'aligne
     * avec les modèles physiques classiques, la confiance grimpe en flèche.
     * Quand il s'en écarte, c'est l'occasion d'expliquer à l'utilisateur la
     * différence d'approche — partie du edge éditorial de l'app.
     */
    ECMWF_AIFS(
        apiKey = "ecmwf_aifs025_single",
        displayName = "AIFS",
        resolutionKm = 25.0,
        maxForecastDays = 10,
        coverage = Coverage.GLOBAL
    ),

    /**
     * Environnement et Changement climatique Canada — Global Environmental
     * Multiscale model (GEM).
     *
     * Diversifie les sources : NOAA (USA), ECMWF (Europe), Met Office (UK),
     * ECCC (Canada). 4 grandes institutions occidentales représentées. Le
     * GEM a un profil de biais distinct des modèles européens, utile dans
     * les situations où la circulation transatlantique pilote la prévision.
     */
    GEM_GLOBAL(
        apiKey = "gem_global",
        displayName = "GEM",
        resolutionKm = 15.0,
        maxForecastDays = 10,
        coverage = Coverage.GLOBAL
    ),

    /**
     * DWD ICON-D2 — modèle haute résolution centré sur l'Allemagne et
     * l'Europe centrale (2 km).
     *
     * Couvre l'est de la France (à partir d'environ Strasbourg), la Suisse,
     * l'Allemagne, l'Autriche, le nord de l'Italie. Pour les utilisateurs
     * dans ces zones, il complète AROME HD avec un 2ᵉ modèle 2 km — quand
     * deux modèles fine-resolution s'alignent, c'est l'un des signaux de
     * confiance les plus forts qu'on puisse avoir à courte échéance.
     *
     * PAS dans MVP_SELECTION : couverture trop limitée pour bénéficier à
     * tous les utilisateurs par défaut. À activer dans les Settings si
     * pertinent pour la localisation.
     */
    ICON_D2(
        apiKey = "icon_d2",
        displayName = "ICON-D2",
        resolutionKm = 2.0,
        maxForecastDays = 2,
        coverage = Coverage.EUROPE
    );

    companion object {
        /**
         * Modèles activés par défaut pour une comparaison MVP.
         *
         * Choix : 1 modèle fine-resolution local (AROME HD) + 1 régional Europe
         * (ARPEGE EU et ICON EU pour diversité d'institution) + 3 globaux
         * (GFS, ECMWF, UKMO). On ajoute AIFS pour exposer dès la sortie de
         * boîte la dimension "comparaison physique vs IA" qui fait partie
         * de l'angle éditorial de l'app. GEM_GLOBAL et ICON_D2 restent opt-in
         * via Settings pour ne pas surcharger la première impression et
         * éviter des cellules vides sur le tableau Jour × Modèle pour les
         * utilisateurs hors zone de couverture d'ICON-D2.
         */
        val MVP_SELECTION: List<WeatherModel> = listOf(
            AROME_FRANCE_HD,
            ARPEGE_EUROPE,
            ICON_EU,
            GFS,
            ECMWF,
            UKMO_GLOBAL,
            ECMWF_AIFS
        )
    }
}

enum class Coverage { FRANCE, EUROPE, GLOBAL }
