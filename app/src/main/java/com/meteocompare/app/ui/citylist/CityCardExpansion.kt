package com.meteocompare.app.ui.citylist

import androidx.compose.ui.semantics.SemanticsPropertyKey

/**
 * A single city is expanded on Home. Favorites are stored in insertion order;
 * this reducer only reacts to ID changes, never to forecast refreshes.
 */
internal fun resolveExpandedCityId(
    expandedCityId: String?,
    previousCityIds: List<String>,
    cityIds: List<String>
): String? {
    if (cityIds.isEmpty()) return null
    // At startup callers pass the already-loaded IDs as previousCityIds so a
    // persisted expandedCityId wins. Newly appended favorites still take focus;
    // without a valid selection the last favorite is the fallback.
    val previous = previousCityIds.toSet()
    return cityIds.lastOrNull { it !in previous }
        ?: expandedCityId?.takeIf { it in cityIds }
        ?: cityIds.last()
}

internal enum class CityCardTapAction { EXPAND, OPEN_DETAILS }

internal fun cityCardTapAction(cityId: String, expandedCityId: String?): CityCardTapAction =
    if (cityId == expandedCityId) CityCardTapAction.OPEN_DETAILS else CityCardTapAction.EXPAND

internal val CityCardExpandedKey = SemanticsPropertyKey<Boolean>("CityCardExpanded")
internal const val TAG_CITY_CARD_COMPACT_METRICS = "city_card_compact_metrics_"
internal const val TAG_CITY_CARD_COMPACT_AGREEMENT = "city_card_compact_agreement_"
internal const val TAG_CITY_CARD_COMPACT_CONDITION = "city_card_compact_condition_"
internal const val TAG_CITY_CARD_COMPACT_TEMPERATURES = "city_card_compact_temperatures_"
internal const val TAG_CITY_CARD_COMPACT_CONDITION_LABEL = "city_card_compact_condition_label_"
internal const val TAG_CITY_CARD_COMPACT_RAIN = "city_card_compact_rain_"
internal const val TAG_CITY_CARD_COMPACT_WIND = "city_card_compact_wind_"
internal const val TAG_CITY_CARD_COMPACT_CLOUD = "city_card_compact_cloud_"
internal const val TAG_CITY_CARD_COMPACT_VIGILANCE = "city_card_compact_vigilance_"
