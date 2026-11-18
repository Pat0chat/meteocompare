package com.meteocompare.app.domain.util

/**
 * Stable Home order independent of GPS grid cell changes. Kept separate from
 * the cities JSON so upgrades and forecast caches never invalidate the order.
 */
object FavoriteCityOrder {
    const val CURRENT_LOCATION = "current"

    fun stableId(id: String): String =
        if (LocationPrivacyGrid.isCellId(id)) CURRENT_LOCATION else id

    /** Unknown and duplicated IDs have no influence; newly added cities stay last. */
    fun <T> apply(items: List<T>, savedIds: List<String>, idOf: (T) -> String): List<T> {
        val positions = savedIds.map(::stableId).distinct().withIndex()
            .associate { (index, id) -> id to index }
        return items.withIndex().sortedWith(
            compareBy<IndexedValue<T>> { positions[stableId(idOf(it.value))] ?: Int.MAX_VALUE }
                .thenBy { it.index }
        ).map { it.value }
    }

    /** Save a complete order; append concurrent additions and drop deleted cities. */
    fun normalize(requested: List<String>, available: List<String>): List<String> {
        val active = available.map(::stableId).toSet()
        return (requested.map(::stableId).filter { it in active } + available.map(::stableId))
            .distinct()
    }

    /** Returns unchanged order for invalid moves, used by drag and a11y buttons. */
    fun move(ids: List<String>, id: String, by: Int): List<String> {
        if (by !in -1..1 || by == 0) return ids
        val from = ids.indexOf(id)
        val to = from + by
        if (from == -1 || to !in ids.indices) return ids
        return ids.toMutableList().apply { add(to, removeAt(from)) }
    }
}
