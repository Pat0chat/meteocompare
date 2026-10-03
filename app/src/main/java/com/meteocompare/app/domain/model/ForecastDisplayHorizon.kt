package com.meteocompare.app.domain.model

/**
 * Horizons de référence pour le téléchargement et l’affichage des prévisions.
 *
 * Les vues calendaires couvrent [DAYS] jours. Les vues « Par heure » de la
 * page détail restent volontairement limitées aux [HOURLY_HOURS] prochaines
 * heures pour conserver une lecture utile et compacte. La Chart View, elle,
 * conserve une timeline complète de [GRAPHIC_HOURS] heures (10 jours).
 */
object ForecastDisplayHorizon {
    const val DAYS: Int = 10
    const val HOURLY_HOURS: Int = 24
    const val GRAPHIC_HOURS: Int = DAYS * 24

    /**
     * Horizon réseau commun à tous les consommateurs du cache météo.
     * Un jour civil supplémentaire permet de couvrir les 240 h de la Chart View
     * dès le chargement de la liste, sans étendre le cache à chaque navigation.
     * Le repository borne cet horizon à la disponibilité des modèles choisis.
     */
    const val REQUEST_DAYS: Int = DAYS + 1

    const val DETAIL_REQUEST_DAYS: Int = REQUEST_DAYS
    const val GRAPHIC_REQUEST_DAYS: Int = REQUEST_DAYS
}
