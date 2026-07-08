package com.meteocompare.app.ui.citydetail

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests du Saver de [ConfidenceMetric]. Le Saver survit à la rotation via
 * rememberSaveable ; on veut garantir qu'il :
 *   1. Round-trip correctement pour chaque valeur de l'enum
 *   2. Fallback proprement sur TEMPERATURE si la clé sérialisée est invalide
 *      (ex. Bundle produit par une version antérieure de l'app avec un enum
 *      différent — le seul cas où on peut avoir une string incompatible).
 */
class ConfidenceMetricSaverTest {

    @Test
    fun `Saver - round-trip pour chaque valeur de l'enum`() {
        ConfidenceMetric.entries.forEach { metric ->
            val saved = with(ConfidenceMetric.Saver) {
                // Le contexte du Saver n'est pas utilisé — on passe un stub
                // minimal via reflection interne. En pratique on utilise
                // directement les lambdas save/restore.
                requireNotNull(fakeScope.save(metric))
            }
            val restored = ConfidenceMetric.Saver.restore(saved)
            assertEquals(
                "Round-trip pour $metric doit rendre la même valeur",
                metric,
                restored
            )
        }
    }

    @Test
    fun `Saver - clé inconnue tombe silencieusement sur TEMPERATURE`() {
        val restored = ConfidenceMetric.Saver.restore("SOMETHING_UNKNOWN")
        assertEquals(
            "Une clé inconnue doit fallback sur TEMPERATURE (défaut safe)",
            ConfidenceMetric.TEMPERATURE,
            restored
        )
    }

    @Test
    fun `Saver - chaîne vide tombe sur TEMPERATURE`() {
        val restored = ConfidenceMetric.Saver.restore("")
        assertEquals(ConfidenceMetric.TEMPERATURE, restored)
    }

    // Compose Saver expose save via un SaverScope receiver. On stubbe le
    // minimum requis pour appeler save() en test JVM pur — save() n'utilise
    // pas de méthodes du scope dans le cas simple des Savers de type "String".
    private val fakeScope = object : androidx.compose.runtime.saveable.SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }
}
