package com.meteocompare.app.ui.theme

import com.meteocompare.app.domain.model.WeatherModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie que tous les modèles ont une couleur EXPLICITE assignée et que
 * ces couleurs sont uniques deux à deux.
 *
 * Cas qu'on attrape :
 *   - Ajout d'un nouveau modèle dans l'enum sans entrée dans la map → le
 *     fallback gris se trahit via [modelsWithoutExplicitColor].
 *   - Copier-coller raté qui crée un doublon de couleur → deux modèles
 *     deviendraient indiscernables sur les charts superposés, capturé par
 *     [duplicateModelColors].
 *
 * Le test passe par les helpers internes plutôt que d'instancier `Color`
 * directement, pour ne pas créer une dépendance Compose sur le sourceSet
 * `test` (JVM pur).
 */
class ModelColorsTest {

    @Test
    fun `every WeatherModel has an explicit color`() {
        val missing = modelsWithoutExplicitColor()
        assertTrue(
            "Ces modèles n'ont pas de couleur explicite (fallback gris) : $missing. " +
                "Ajoute une entrée dans ModelColorMap dans ModelColors.kt.",
            missing.isEmpty()
        )
    }

    @Test
    fun `all model colors are pairwise distinct`() {
        val duplicates = duplicateModelColors()
        assertTrue(
            "Modèles partageant la même couleur — indistinguables en superposition : " +
                "$duplicates. Choisir des teintes différentes dans ModelColorMap.",
            duplicates.isEmpty()
        )
    }

    @Test
    fun `MVP_SELECTION contains every model that should be shown by default`() {
        // Garde-fou pour ne pas qu'un refactor enlève silencieusement un
        // modèle de la sélection par défaut sans qu'un test le signale.
        // La liste explicite suivante est la "vérité éditoriale" — si on
        // change le défaut, ce test doit être adapté aussi, ce qui force
        // une revue.
        val expected = setOf(
            WeatherModel.AROME_FRANCE_HD,
            WeatherModel.ARPEGE_EUROPE,
            WeatherModel.ICON_EU,
            WeatherModel.GFS,
            WeatherModel.ECMWF,
            WeatherModel.UKMO_GLOBAL,
            WeatherModel.ECMWF_AIFS
        )
        assertEquals(expected, WeatherModel.MVP_SELECTION.toSet())
    }
}
