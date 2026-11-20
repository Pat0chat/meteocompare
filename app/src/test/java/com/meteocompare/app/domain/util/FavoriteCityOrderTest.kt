package com.meteocompare.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteCityOrderTest {
    private val favorites = listOf("paris", "lyon", "nice")

    @Test fun noPreferencePreservesAdditionOrder() {
        assertEquals(favorites, FavoriteCityOrder.apply(favorites, emptyList()) { it })
    }

    @Test fun savedOrderIsRestoredAfterRestart() {
        val saved = listOf("nice", "paris", "lyon")
        assertEquals(saved, FavoriteCityOrder.apply(favorites, saved) { it })
    }

    @Test fun unknownDuplicatesAndCorruptIdsDoNotHideCities() {
        assertEquals(listOf("nice", "paris", "lyon"),
            FavoriteCityOrder.apply(favorites, listOf("deleted", "nice", "nice", "paris")) { it })
    }

    @Test fun newlyAddedCitiesFollowExistingFavorites() {
        assertEquals(listOf("lyon", "paris", "nice"),
            FavoriteCityOrder.apply(favorites, listOf("lyon", "paris")) { it })
    }

    @Test fun gpsGridCellChangesDoNotMoveCurrentLocationCard() {
        val first = "current:42:89"
        val next = "current:43:90"
        val saved = FavoriteCityOrder.normalize(listOf("lyon", first, "paris"),
            listOf("paris", "lyon", first))
        assertEquals(listOf("lyon", "current", "paris"), saved)
        assertEquals(listOf("lyon", next, "paris"),
            FavoriteCityOrder.apply(listOf("paris", "lyon", next), saved) { it })
    }

    @Test fun disablingLocationDoesNotLoseFavoriteOrder() {
        val saved = listOf("lyon", "current", "paris")
        assertEquals(listOf("lyon", "paris"),
            FavoriteCityOrder.apply(listOf("paris", "lyon"), saved) { it })
        assertEquals(listOf("lyon", "current:11:22", "paris"),
            FavoriteCityOrder.apply(listOf("paris", "lyon", "current:11:22"), saved) { it })
    }

    @Test fun normalizePreservesConcurrentAdditionsAndExcludesRemovedCities() {
        assertEquals(listOf("nice", "paris", "lyon"),
            FavoriteCityOrder.normalize(listOf("removed", "nice", "paris", "nice"), favorites))
    }

    @Test fun moveIsPredictableAndDoesNotChangeMembership() {
        assertEquals(listOf("paris", "nice", "lyon"), FavoriteCityOrder.move(favorites, "nice", -1))
        assertEquals(listOf("lyon", "paris", "nice"), FavoriteCityOrder.move(favorites, "paris", 1))
        assertEquals(favorites, FavoriteCityOrder.move(favorites, "paris", -1))
        assertEquals(favorites, FavoriteCityOrder.move(favorites, "missing", 1))
        assertEquals(favorites, FavoriteCityOrder.move(favorites, "lyon", 0))
    }
}
