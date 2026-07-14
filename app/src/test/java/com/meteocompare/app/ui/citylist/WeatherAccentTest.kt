package com.meteocompare.app.ui.citylist

import androidx.compose.ui.graphics.Color
import com.meteocompare.app.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la palette [WeatherAccent].
 *
 * Objectifs :
 *   1. Chaque valeur de [WeatherCondition] renvoie une couleur (pas de crash,
 *      pas de valeur par défaut oubliée) — évite qu'ajouter une condition
 *      future casse silencieusement l'affichage.
 *   2. Les paires light/dark sont bien distinctes (permutation involontaire
 *      difficile à détecter à l'œil dans le code source).
 *   3. Les conditions de sévérité contrastée ont bien des teintes contrastées
 *      (soleil ≠ orage ≠ pluie ≠ brouillard).
 *   4. Le fallback null (cache pré-feature) retombe sur du neutre, jamais sur
 *      une couleur d'alerte comme l'orage.
 */
class WeatherAccentTest {

    @Test
    fun `chaque condition retourne une couleur non-transparente en clair`() {
        WeatherCondition.entries.forEach { condition ->
            val color = WeatherAccent.of(condition, isDark = false)
            assertTrue(
                "Condition $condition renvoie une couleur transparente",
                color.alpha > 0f
            )
        }
    }

    @Test
    fun `chaque condition retourne une couleur non-transparente en sombre`() {
        WeatherCondition.entries.forEach { condition ->
            val color = WeatherAccent.of(condition, isDark = true)
            assertTrue(
                "Condition $condition en dark renvoie une couleur transparente",
                color.alpha > 0f
            )
        }
    }

    @Test
    fun `condition null retombe sur du neutre pas sur une alerte`() {
        val nullColor = WeatherAccent.of(condition = null, isDark = false)
        val stormColor = WeatherAccent.of(WeatherCondition.THUNDERSTORM, isDark = false)
        val sunnyColor = WeatherAccent.of(WeatherCondition.CLEAR, isDark = false)
        // Le fallback ne doit pas se confondre avec une couleur signifiante
        // — sinon un cache pré-feature ferait croire à un orage.
        assertNotEquals("null ne doit pas ressembler à un orage", stormColor, nullColor)
        assertNotEquals("null ne doit pas ressembler à un beau temps", sunnyColor, nullColor)
    }

    @Test
    fun `unknown retombe sur du neutre comme null`() {
        val unknownColor = WeatherAccent.of(WeatherCondition.UNKNOWN, isDark = false)
        val nullColor = WeatherAccent.of(condition = null, isDark = false)
        assertEquals("UNKNOWN et null doivent partager le neutre", nullColor, unknownColor)
    }

    @Test
    fun `light et dark sont distinctes pour toutes les conditions`() {
        // Un copier-coller "same color light == dark" est une erreur qu'on veut
        // détecter au CI plutôt qu'à l'œil.
        WeatherCondition.entries.forEach { condition ->
            val light = WeatherAccent.of(condition, isDark = false)
            val dark = WeatherAccent.of(condition, isDark = true)
            assertNotEquals(
                "Condition $condition : light et dark identiques",
                light,
                dark
            )
        }
    }

    @Test
    fun `beau temps et orage sont fortement contrastes`() {
        val sunny = WeatherAccent.of(WeatherCondition.CLEAR, isDark = false)
        val storm = WeatherAccent.of(WeatherCondition.THUNDERSTORM, isDark = false)
        // On mesure la distance euclidienne en RGB. Un contraste "fort" =
        // au moins 30% de distance sur la diagonale du cube RGB (√3).
        val distance = colorDistance(sunny, storm)
        assertTrue(
            "Soleil et orage doivent être visuellement distincts (distance=$distance)",
            distance > 0.3f
        )
    }

    @Test
    fun `pluie et neige sont distincts malgre le bleu commun`() {
        // Les deux sont dans la famille bleue — il faut quand même pouvoir les
        // distinguer. La neige est un bleu clair, la pluie un indigo profond.
        val rain = WeatherAccent.of(WeatherCondition.RAIN, isDark = false)
        val snow = WeatherAccent.of(WeatherCondition.SNOW, isDark = false)
        val distance = colorDistance(rain, snow)
        assertTrue(
            "Pluie et neige doivent être distincts (distance=$distance)",
            distance > 0.3f
        )
    }

    @Test
    fun `DRIZZLE RAIN et RAIN_SHOWERS partagent la meme couleur`() {
        // Choix produit : ces 3 variantes sont "de la pluie" pour un utilisateur
        // qui scrolle. Les différencier visuellement rajouterait du bruit sans
        // valeur ajoutée. On pin le comportement ici pour éviter une divergence
        // future qui casserait la cohérence.
        val drizzle = WeatherAccent.of(WeatherCondition.DRIZZLE, isDark = false)
        val rain = WeatherAccent.of(WeatherCondition.RAIN, isDark = false)
        val showers = WeatherAccent.of(WeatherCondition.RAIN_SHOWERS, isDark = false)
        assertEquals(drizzle, rain)
        assertEquals(rain, showers)
    }

    @Test
    fun `CLEAR et MAINLY_CLEAR partagent la meme couleur`() {
        // Idem : "clair" et "principalement clair" c'est du beau temps pour
        // l'utilisateur — pas de distinction visuelle utile.
        val clear = WeatherAccent.of(WeatherCondition.CLEAR, isDark = false)
        val mainly = WeatherAccent.of(WeatherCondition.MAINLY_CLEAR, isDark = false)
        assertEquals(clear, mainly)
    }

    /**
     * Distance euclidienne dans l'espace RGB normalisé [0, 1]. Racine de
     * (Δr² + Δg² + Δb²). Distance max ≈ 1.73 (√3, coins opposés du cube).
     * On considère qu'au-delà de 0.3, les couleurs sont "clairement" distinctes.
     */
    private fun colorDistance(a: Color, b: Color): Float {
        val dr = a.red - b.red
        val dg = a.green - b.green
        val db = a.blue - b.blue
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
    }
}
