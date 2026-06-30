package com.meteocompare.app.ui.navigation

/**
 * Définit les destinations de navigation et leur format de route.
 *
 * On reste sur les routes string-based (vs type-safe nav) pour le MVP :
 * peu de destinations, peu d'arguments, pas de risque de typo difficile à debug.
 */
object Destinations {
    const val CITY_LIST = "cities"
    const val SETTINGS = "settings"

    private const val CITY_DETAIL_BASE = "city"
    const val CITY_DETAIL_ARG = "cityId"
    const val CITY_DETAIL = "$CITY_DETAIL_BASE/{$CITY_DETAIL_ARG}"

    fun cityDetail(cityId: String): String = "$CITY_DETAIL_BASE/$cityId"

    // ─── "Pourquoi cette confiance ?" ──────────────────────────────────────
    //
    // Deux arguments : cityId + date (ISO yyyy-MM-dd). On garde la date dans
    // la route plutôt que dans un side-channel ViewModel-shared parce que :
    //   - la nav est restaurable au pixel près après recréation de process
    //   - le deep link "ouvre la confiance d'un jour précis" devient trivial
    //   - aujourd'hui on n'ouvre que via le badge "Aujourd'hui", mais demain
    //     on voudra brancher chaque badge des jours suivants au même écran
    private const val CONFIDENCE_BASE = "confidence"
    const val CONFIDENCE_DATE_ARG = "date"
    const val CONFIDENCE =
        "$CONFIDENCE_BASE/{$CITY_DETAIL_ARG}/{$CONFIDENCE_DATE_ARG}"

    fun confidence(cityId: String, isoDate: String): String =
        "$CONFIDENCE_BASE/$cityId/$isoDate"
}
