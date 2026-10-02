package com.meteocompare.app.domain.model

/**
 * Horizons de référence partagés par les vues de prévision approfondies.
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

    /** Jours civils nécessaires aux vues quotidiennes de la page détail. */
    const val DETAIL_REQUEST_DAYS: Int = DAYS

    /**
     * Un jour civil supplémentaire garantit 240 h futures complètes dans la
     * Chart View même lorsqu'elle est ouverte en fin de journée.
     */
    const val GRAPHIC_REQUEST_DAYS: Int = DAYS + 1
}
