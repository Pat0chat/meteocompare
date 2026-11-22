package com.meteocompare.app.ui.theme

import com.meteocompare.app.domain.model.WeatherModel
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie que tous les modèles ont une couleur EXPLICITE assignée et que
 * ces couleurs sont uniques deux à deux.
 *
 * Cas qu'on attrape :
 *   - Un modèle sans couleur explicite utilise la couleur de secours grise.
 *   - Deux modèles ne doivent pas partager la même couleur.
 */
class ModelColorsTest {

    @Test
    fun `every WeatherModel has an explicit color`() {
        val missing = WeatherModel.entries.filter { it.color() == Color(0xFF9E9E9E) }
        assertTrue(
            "Ces modèles n'ont pas de couleur explicite (fallback gris) : $missing. " +
                "Ajoute une entrée dans ModelColorMap dans ModelColors.kt.",
            missing.isEmpty()
        )
    }

    @Test
    fun `all model colors are pairwise distinct`() {
        val duplicates = WeatherModel.entries.groupBy { it.color() }
            .values.filter { it.size > 1 }
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
            WeatherModel.ECMWF_AIFS,
            WeatherModel.JMA_GSM,
            WeatherModel.NCEP_AIGFS
        )
        assertEquals(expected, WeatherModel.MVP_SELECTION.toSet())
    }
}
