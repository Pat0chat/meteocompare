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
}
