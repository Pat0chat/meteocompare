package com.meteocompare.app.domain.model

/**
 * Modèles météorologiques exposés par l'API Open-Meteo.
 *
 * @property apiKey Identifiant du modèle dans le paramètre `&models=` de l'API.
 * @property displayName Nom court affiché dans l'UI.
 * @property resolutionKm Résolution horizontale native du modèle (en kilomètres),
 *           affichée comme métadonnée. Elle ne sert pas de score de qualité : une
 *           maille plus fine ne garantit pas à elle seule un meilleur forecast.
 * @property maxForecastDays Nombre entier de jours utilisé pour borner la
 *           requête Forecast API. Pour un horizon natif partiel (ex. ~2,5 j),
 *           cette valeur est le plafond entier permettant de récupérer toute
 *           la série ; elle n'est pas une promesse d'heures complètes.
 * @property forecastHorizonHours Horizon natif indicatif documenté, utilisé uniquement
 *           pour l'affichage. [maxForecastDays] reste le plafond de requête entier.
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
    val family: ModelFamily,
    /** Anciennes clés/alias acceptés uniquement pour relire prefs et caches existants. */
    val apiKeyAliases: Set<String> = emptySet(),
    /** Horizon natif indicatif, distinct du plafond entier utilisé par `forecast_days`. */
    val forecastHorizonHours: Int = maxForecastDays * 24
) {
    AROME_FRANCE_HD(
        apiKey = "meteofrance_arome_france_hd",
        displayName = "AROME HD",
        resolutionKm = 1.5,
        maxForecastDays = 3,
        coverage = Coverage.FRANCE,
        family = ModelFamily.METEO_FRANCE,
        forecastHorizonHours = 51
    ),
    AROME_FRANCE(
        apiKey = "meteofrance_arome_france",
        displayName = "AROME",
        resolutionKm = 2.5,
        maxForecastDays = 3,
        coverage = Coverage.FRANCE,
        family = ModelFamily.METEO_FRANCE,
        forecastHorizonHours = 51
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
        apiKey = "icon_global",
        displayName = "ICON",
        resolutionKm = 11.0,
        maxForecastDays = 8,
        coverage = Coverage.GLOBAL,
        family = ModelFamily.DWD,
        forecastHorizonHours = 180,
        // Ancienne version de l'app demandait le "seamless" DWD, qui peut
        // basculer vers ICON-EU / ICON-D2 selon la position. Le modèle nommé
        // ICON Global doit rester une source globale distincte d'ICON-EU.
        apiKeyAliases = setOf("icon_seamless")
    ),
    GFS(
        apiKey = "ncep_gfs_seamless",
        displayName = "GFS",
        resolutionKm = 13.0,
        maxForecastDays = 16,
        coverage = Coverage.GLOBAL,
        family = ModelFamily.NOAA,
        apiKeyAliases = setOf("gfs_seamless")
    ),
    ECMWF(
        apiKey = "ecmwf_ifs025",
        displayName = "ECMWF",
        resolutionKm = 25.0,
        maxForecastDays = 15,
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
     * Ajoute une source indépendante aux côtés de GFS et ECMWF. L'application
     * le traite comme un scénario supplémentaire, sans lui attribuer a priori
     * un rôle d'arbitre ni un skill supérieur.
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
     * Modèle de prévision fondé sur l'apprentissage automatique, proposé comme
     * scénario distinct de l'IFS physique. La comparaison ne lui attribue pas
     * de poids supérieur sans backtest local vérifié.
     */
    ECMWF_AIFS(
        apiKey = "ecmwf_aifs025_single",
        displayName = "AIFS",
        resolutionKm = 25.0,
        maxForecastDays = 15,
        coverage = Coverage.GLOBAL,
        family = ModelFamily.ECMWF
    ),

    /**
     * Environnement et Changement climatique Canada — GEM Global.
     * Diversifie les sources ; profil de biais distinct des modèles européens.
     */
    GEM_GLOBAL(
        apiKey = "cmc_gem_gdps",
        displayName = "GEM",
        resolutionKm = 15.0,
        maxForecastDays = 10,
        coverage = Coverage.GLOBAL,
        family = ModelFamily.ECCC,
        apiKeyAliases = setOf("gem_global")
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
     * Modèle régional haute résolution limité aux États-Unis continentaux.
     * Hors zone, Open-Meteo ne renvoie pas de série exploitable et le splitter
     * l'exclut des modèles disponibles.
     */
    HRRR_CONUS(
        apiKey = "ncep_hrrr_conus",
        displayName = "HRRR",
        resolutionKm = 3.0,
        maxForecastDays = 2,
        coverage = Coverage.UNITED_STATES,
        family = ModelFamily.NOAA
    ),

    /**
     * MET Norway Nordic — modèle 1 km sur la Scandinavie et l'Arctique.
     *
     * Modèle régional 1 km pour la Norvège, la Suède, le Danemark et la
     * Finlande. Il complète les modèles européens sans présumer d'un avantage
     * systématique de skill.
     */
    METNO_NORDIC(
        apiKey = "metno_nordic",
        displayName = "MET Nordic",
        resolutionKm = 1.0,
        maxForecastDays = 3,
        coverage = Coverage.EUROPE,
        family = ModelFamily.METNO,
        forecastHorizonHours = 60
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
        maxForecastDays = 3,
        coverage = Coverage.EUROPE,
        family = ModelFamily.KNMI,
        forecastHorizonHours = 60
    ),

    /**
     * BOM ACCESS-G — modèle global du Bureau of Meteorology australien (15 km).
     * Ajoute une source institutionnelle indépendante à la comparaison globale.
     */
    BOM_ACCESS(
        apiKey = "bom_access_global",
        displayName = "BOM",
        resolutionKm = 15.0,
        maxForecastDays = 10,
        coverage = Coverage.GLOBAL,
        family = ModelFamily.BOM
    ),

    /**
     * CMA GRAPES Global — modèle global du China Meteorological Administration.
     *
     * Diversifie les sources avec un scénario global d'environ 13 km. Aucun
     * avantage régional n'est supposé sans mesure de vérification dédiée.
     */
    CMA_GRAPES(
        apiKey = "cma_grapes_global",
        displayName = "CMA",
        resolutionKm = 13.0,
        maxForecastDays = 10,
        coverage = Coverage.GLOBAL,
        family = ModelFamily.CMA
    );

    /** Clé courante ou alias historique/canonique accepté en lecture. */
    fun matchesApiKey(key: String): Boolean = key == apiKey || key in apiKeyAliases

    /** Toutes les clés reconnues pour relire une réponse/cache existant. */
    val compatibleApiKeys: Set<String> get() = apiKeyAliases + apiKey

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

        /** Résout une clé stockée/cache vers le modèle correspondant. */
        fun fromApiKey(key: String): WeatherModel? =
            entries.firstOrNull { it.matchesApiKey(key) }
    }
}

/**
 * Zone géographique couverte par un modèle.
 *
 * Utile pour filtrer et regrouper l'affichage dans la page Settings.
 * L'ordre déclaré correspond à un tri de "plus local" → "plus étendu" —
 * exploité par [WeatherModel.entries.sortedBy { it.coverage.ordinal }].
 */
enum class Coverage { FRANCE, EUROPE, UNITED_STATES, GLOBAL }

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
