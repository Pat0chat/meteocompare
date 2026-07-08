package com.meteocompare.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests du Saver de [ModelSortMode]. Même pattern que [ConfidenceMetric.Saver] :
 *   1. Round-trip pour chaque valeur
 *   2. Fallback sur ZONE (défaut) pour une clé invalide
 */
class ModelSortModeSaverTest {

    @Test
    fun `Saver - round-trip pour chaque valeur`() {
        ModelSortMode.entries.forEach { mode ->
            val saved = with(ModelSortMode.Saver) {
                requireNotNull(fakeScope.save(mode))
            }
            val restored = ModelSortMode.Saver.restore(saved)
            assertEquals(mode, restored)
        }
    }

    @Test
    fun `Saver - clé inconnue tombe sur ZONE`() {
        val restored = ModelSortMode.Saver.restore("MYSTERY_MODE")
        assertEquals(
            "Une clé inconnue doit fallback sur ZONE (défaut safe)",
            ModelSortMode.ZONE,
            restored
        )
    }

    private val fakeScope = object : androidx.compose.runtime.saveable.SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }
}
