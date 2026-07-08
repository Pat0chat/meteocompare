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
 * @property family Institution qui produit le modèle. Utilisé pour regrouper l'affichage
 *           dans la page Settings ("tous les modèles de Météo-France ensemble") et pour
 *           afficher la crédit d'attribution.
 */
enum class WeatherModel(
    val apiKey: String,
    val displayName: String,
    val resolutionKm: Double,
    val maxForecastDays: Int,
    val coverage: Coverage,
    val family: ModelFamily
) {
    AROME_FRANCE_HD(
        apiKey = "meteofrance_arome_france_hd",
        displayName = "AROME HD",
        resolutionKm = 1.5,
        maxForecastDays = 2,
        coverage = Coverage.FRANCE,
        family = ModelFamily.METEO_FRANCE
    ),
    AROME_FRANCE(
        apiKey = "meteofrance_arome_france",
        displayName = "AROME",
        resolutionKm = 2.5,
        maxForecastDays = 2,
        coverage = Coverage.FRANCE,
        family = ModelFamily.METEO_FRANCE
    ),
    ARPEGE_EUROPE(
        apiKey = "meteofrance_arpege_europe",
        displayName = "ARPEGE EU",
        resolutionKm = 11.0,
        maxForecastDays = 4,
        coverage = Coverage.EUROPE,
        family = ModelFamily.METEO_FRANCE
    ),
    ARPEGE_WORLD(
        apiKey = "meteofrance_arpege_world",
        displayName = "ARPEGE",
        resolutionKm = 25.0,
        maxForecastDays = 4,
        coverage = Coverage.GLOBAL,
        family = ModelFamily.METEO_FRANCE
    ),
    ICON_EU(
        apiKey = "icon_eu",
        displayName = "ICON-EU",
        resolutionKm = 7.0,
        maxForecastDays = 5,
        coverage = Coverage.EUROPE,
        family = ModelFamily.DWD
    ),
    ICON_GLOBAL(
        apiKey = "icon_seamless",
        displayName = "ICON",
        resolutionKm = 13.0,
        maxForecastDays = 7,
        coverage = Coverage.GLOBAL,
        family = ModelFamily.DWD
    ),
    GFS(
        apiKey = "gfs_seamless",
        displayName = "GFS",
        resolutionKm = 13.0,
        maxForecastDays = 16,
        coverage = Coverage.GLOBAL,
        family = ModelFamily.NOAA
    ),
    ECMWF(
        apiKey = "ecmwf_ifs025",
        displayName = "ECMWF",
        resolutionKm = 25.0,
        maxForecastDays = 10,
        coverage = Coverage.GLOBAL,
        family = ModelFamily.ECMWF
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
     */
    UKMO_GLOBAL(
        apiKey = "ukmo_global_deterministic_10km",
        displayName = "UKMO",
        resolutionKm = 10.0,
        maxForecastDays = 7,
        coverage = Coverage.GLOBAL,
        family = ModelFamily.UKMO
    ),

    /**
     * ECMWF AIFS — modèle de prévision par intelligence artificielle.
     *
     * Ajout éditorial fort : AIFS est entraîné par graph neural network sur
     * les réanalyses ERA5, sans résoudre explicitement les équations
     * d'évolution atmosphérique. C'est la rupture méthodologique majeure de
     * la décennie en météo.
     */
    ECMWF_AIFS(
        apiKey = "ecmwf_aifs025_single",
        displayName = "AIFS",
        resolutionKm = 25.0,
        maxForecastDays = 10,
        coverage = Coverage.GLOBAL,
        family = ModelFamily.ECMWF
    ),

    /**
     * Environnement et Changement climatique Canada — GEM Global.
     * Diversifie les sources ; profil de biais distinct des modèles européens.
     */
    GEM_GLOBAL(
        apiKey = "gem_global",
        displayName = "GEM",
        resolutionKm = 15.0,
        maxForecastDays = 10,
        coverage = Coverage.GLOBAL,
        family = ModelFamily.ECCC
    ),

    /**
     * DWD ICON-D2 — modèle haute résolution centré sur l'Allemagne et
     * l'Europe centrale (2 km). Complète AROME HD sur l'est de la France,
     * Suisse, Allemagne, Autriche, nord de l'Italie.
     */
    ICON_D2(
        apiKey = "icon_d2",
        displayName = "ICON-D2",
        resolutionKm = 2.0,
        maxForecastDays = 2,
        coverage = Coverage.EUROPE,
        family = ModelFamily.DWD
    ),

    // ──────────────────────────────────────────────────────────────────────
    //  Nouveaux modèles (v2) — diversifient les zones et les familles.
    //  On les append toujours à la fin pour ne pas casser l'ordre historique
    //  utilisé comme fallback de tri (voir ordinal-based sorters).
    // ──────────────────────────────────────────────────────────────────────

    /**
     * NCEP HRRR CONUS — modèle rapid-refresh de la NOAA sur les USA (3 km).
     *
     * Pendant américain d'AROME HD : c'est LA référence haute-résolution pour
     * un utilisateur en Amérique du Nord. Rafraîchi toutes les heures (au lieu
     * de 6h pour la plupart des modèles), donc particulièrement utile pour la
     * convection estivale. Coverage limitée à la CONUS — hors zone, l'API
     * retourne des NaN et le modèle est simplement absent des cellules.
     */
    HRRR_CONUS(
        apiKey = "ncep_hrrr_conus",
        displayName = "HRRR",
        resolutionKm = 3.0,
        maxForecastDays = 2,
        coverage = Coverage.GLOBAL,
        family = ModelFamily.NOAA
    ),

    /**
     * MET Norway Nordic — modèle 1 km sur la Scandinavie et l'Arctique.
     *
     * Résolution la plus fine du catalogue Open-Meteo pour la région nord.
     * Pertinent pour les utilisateurs en Norvège, Suède, Finlande, Danemark,
     * Islande — remplace avantageusement ICON-EU dans ces zones.
     */
    METNO_NORDIC(
        apiKey = "metno_nordic",
        displayName = "MET Nordic",
        resolutionKm = 1.0,
        maxForecastDays = 3,
        coverage = Coverage.EUROPE,
        family = ModelFamily.METNO
    ),

    /**
     * KNMI HARMONIE AROME Europe — modèle 5.5 km piloté par l'IFS ECMWF.
     *
     * Cousin européen d'AROME : même moteur numérique, initialisation via
     * l'IFS plutôt que les analyses Météo-France. Utile en complément d'AROME
     * dans les Pays-Bas, Belgique, nord-ouest de la France — les deux modèles
     * peuvent diverger sur les situations de brise de mer.
     */
    KNMI_HARMONIE_EU(
        apiKey = "knmi_harmonie_arome_europe",
        displayName = "HARMONIE",
        resolutionKm = 5.5,
        maxForecastDays = 2,
        coverage = Coverage.EUROPE,
        family = ModelFamily.KNMI
    ),

    /**
     * BOM ACCESS-G — modèle global du Bureau of Meteorology australien (12 km).
     *
     * Utile pour l'hémisphère sud (Australie, Nouvelle-Zélande, Océanie) où
     * les modèles occidentaux ont un skill moindre. Ajoute aussi une 5ᵉ source
     * dans le pool global pour la comparaison inter-modèles.
     */
    BOM_ACCESS(
        apiKey = "bom_access_global",
        displayName = "BOM",
        resolutionKm = 12.0,
        maxForecastDays = 10,
        coverage = Coverage.GLOBAL,
        family = ModelFamily.BOM
    ),

    /**
     * CMA GRAPES Global — modèle global du China Meteorological Administration.
     *
     * Diversifie encore les sources : ajoute une 5ᵉ institution non-occidentale
     * au pool (avec BOM). Coverage globale mais skill particulièrement bon
     * sur l'Asie de l'Est. Résolution native ~15 km — comparable à GEM.
     */
    CMA_GRAPES(
        apiKey = "cma_grapes_global",
        displayName = "CMA",
        resolutionKm = 15.0,
        maxForecastDays = 10,
        coverage = Coverage.GLOBAL,
        family = ModelFamily.CMA
    );

    companion object {
        /**
         * Modèles activés par défaut — choix MVP équilibré.
         *
         * Composition : 1 fine-resolution local (AROME HD), 1 régional Europe
         * pour utilisateurs européens (ICON EU), 3 globaux occidentaux (GFS,
         * ECMWF, UKMO) et AIFS pour la comparaison IA vs physique. Les nouveaux
         * modèles (HRRR, MET Nordic, HARMONIE, BOM, GRAPES, GEM, ICON-D2)
         * restent opt-in via Settings — pertinents pour certains utilisateurs
         * mais surchargeraient la 1re impression pour les autres.
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

/**
 * Zone géographique couverte par un modèle.
 *
 * Utile pour filtrer et regrouper l'affichage dans la page Settings.
 * L'ordre déclaré correspond à un tri de "plus local" → "plus étendu" —
 * exploité par [WeatherModel.entries.sortedBy { it.coverage.ordinal }].
 */
enum class Coverage { FRANCE, EUROPE, GLOBAL }

/**
 * Institution productrice du modèle. Utilisé pour :
 *
 *   1. Grouper les modèles dans la page Settings (mode "par famille"),
 *      utile quand un utilisateur veut activer/désactiver "tous les modèles
 *      Météo-France" en un coup d'œil.
 *   2. Afficher un libellé cohérent dans les crédits d'attribution
 *      ("Modèles : AROME et ARPEGE par Météo-France…").
 *
 * L'ordre déclaré n'est pas neutre : il correspond grossièrement à l'ordre
 * historique d'ajout des modèles à l'app (Météo-France en premier car app
 * originalement franco-centrée). Utilisé comme tri stable secondaire.
 */
enum class ModelFamily(val displayName: String) {
    METEO_FRANCE("Météo-France"),
    DWD("DWD"),
    NOAA("NOAA"),
    ECMWF("ECMWF"),
    UKMO("UK Met Office"),
    ECCC("ECCC"),
    METNO("MET Norway"),
    KNMI("KNMI"),
    BOM("BOM"),
    CMA("CMA")
}
