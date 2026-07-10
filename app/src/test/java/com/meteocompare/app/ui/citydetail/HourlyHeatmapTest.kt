package com.meteocompare.app.ui.citydetail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires des stylers heatmap horaires (fond de cellule + couleur de
 * texte contrastée).
 *
 * Trois axes de couverture :
 *   1. **Frontières de seuil** — chaque comparateur (`<`, `<=`, `>=`, `>`) est
 *      testé aux deux valeurs exactes du bord pour éviter les régressions de
 *      type "off-by-one" sur les paliers meteo. Une valeur pile à 20° doit
 *      tomber dans "chaud" et non "tempéré", exactement comme le prévoit le
 *      styler texte historique.
 *   2. **Contraste texte/fond** — chaque style a une [HeatmapCellStyle.contentColor]
 *      qui doit rester lisible (noir sur fond clair, blanc sur fond sombre).
 *      La règle exacte utilisée est `luminance > 0.179 → noir sinon blanc`
 *      (seuil WCAG-optimal), on valide ce contrat.
 *   3. **Comportement "neutre" (null)** — les stylers précipitation et vent
 *      renvoient `null` sous un seuil (sec, calme) pour laisser transparaître
 *      le fond de rangée. La température, elle, ne renvoie JAMAIS null : chaque
 *      valeur est colorée (principe de la heatmap "toujours pleine").
 *
 * Ces tests sont des tests JVM purs — [androidx.compose.ui.graphics.Color] est
 * un value class calculable hors contexte Android, donc pas besoin de
 * Robolectric ni d'instrumentation.
 */
class HourlyHeatmapTest {

    // ─── Helpers de vérification ──────────────────────────────────────────

    /**
     * Vérifie qu'un style a le fond attendu ET une couleur de contenu
     * cohérente (noir ou blanc, choisie via la règle de luminance).
     * On teste les deux propriétés en même temps parce qu'une régression sur
     * l'une passerait souvent inaperçue si on ne testait que l'autre.
     */
    private fun assertHeatmapStyle(
        actual: HeatmapCellStyle?,
        expectedBackground: Color,
        message: String = ""
    ) {
        assertNotNull("$message: style attendu non-null", actual)
        assertEquals(
            "$message: background attendu $expectedBackground",
            expectedBackground,
            actual!!.background
        )
        // Cohérence contentColor / luminance — la règle est appliquée
        // via [contrastingContentColor] au moment de la construction.
        // Seuil 0.179 = seuil WCAG-optimal (cf. doc de contrastingContentColor).
        val expectedContent = if (expectedBackground.luminance() > 0.179f) Color.Black else Color.White
        assertEquals(
            "$message: contentColor doit contraster avec le fond",
            expectedContent,
            actual.contentColor
        )
    }

    // ─── contrastingContentColor ──────────────────────────────────────────

    @Test
    fun `contrastingContentColor returns black on light background`() {
        // Blanc pur → luminance 1.0 → noir attendu (contraste max).
        assertEquals(Color.Black, contrastingContentColor(Color.White))
        // Fond très clair (par exemple jaune clair) → noir aussi.
        assertEquals(Color.Black, contrastingContentColor(Color(0xFFFFF59D)))
    }

    @Test
    fun `contrastingContentColor returns white on dark background`() {
        // Noir pur → luminance 0.0 → blanc attendu.
        assertEquals(Color.White, contrastingContentColor(Color.Black))
        // Bleu foncé (le "very heavy rain" de la palette) → blanc.
        assertEquals(Color.White, contrastingContentColor(Color(0xFF0D47A1)))
    }

    @Test
    fun `contrastingContentColor is deterministic for a given color`() {
        // Sanity : appeler la fonction deux fois avec la même couleur donne
        // le même résultat. Protection contre l'utilisation accidentelle
        // d'une source d'aléa (Random) dans une future refonte.
        val bg = Color(0xFFE53935)
        assertEquals(contrastingContentColor(bg), contrastingContentColor(bg))
    }

    // ─── hourlyTemperatureHeatmap : bornes des paliers ────────────────────

    @Test
    fun `temperature - freezing bin covers strictly below 0`() {
        // < 0° → bleu foncé. -0.1° suffit à basculer dans le palier freezing.
        assertHeatmapStyle(
            hourlyTemperatureHeatmap(-0.1),
            Color(0xFF1E88E5),
            "temp -0.1°"
        )
        assertHeatmapStyle(
            hourlyTemperatureHeatmap(-15.0),
            Color(0xFF1E88E5),
            "temp -15° (canicule froide)"
        )
    }

    @Test
    fun `temperature - exactly 0 falls in cold bin not freezing`() {
        // Le comparateur est `>= 0` pour cold, donc 0.0 exact = cold (bleu clair).
        // Régression classique : si on écrivait `> 0` par erreur, 0.0 tomberait
        // à tort dans freezing.
        assertHeatmapStyle(
            hourlyTemperatureHeatmap(0.0),
            Color(0xFF4FC3F7),
            "temp 0.0° pile"
        )
    }

    @Test
    fun `temperature - cold bin covers 0 to 5 inclusive`() {
        // La borne haute utilise `> 5` pour temperate, donc 5.0 EXACT reste cold.
        // C'est une convention arbitraire : on aurait pu mettre le seuil à 5
        // dans temperate. On fige la convention actuelle pour éviter les
        // régressions inaperçues.
        assertHeatmapStyle(
            hourlyTemperatureHeatmap(2.5),
            Color(0xFF4FC3F7),
            "temp 2.5° (milieu de la fourchette cold)"
        )
        assertHeatmapStyle(
            hourlyTemperatureHeatmap(5.0),
            Color(0xFF4FC3F7),
            "temp 5.0° pile — reste dans cold"
        )
    }

    @Test
    fun `temperature - temperate bin covers strictly above 5 up to 20`() {
        // 5.01° bascule dans temperate. 20° pile est déjà dans warm (>= 20).
        assertHeatmapStyle(
            hourlyTemperatureHeatmap(5.01),
            Color(0xFFDCEDC8),
            "temp 5.01° — tempéré"
        )
        assertHeatmapStyle(
            hourlyTemperatureHeatmap(12.0),
            Color(0xFFDCEDC8),
            "temp 12° — milieu tempéré"
        )
        // 19.99° reste encore tempéré (< 20).
        assertHeatmapStyle(
            hourlyTemperatureHeatmap(19.99),
            Color(0xFFDCEDC8),
            "temp 19.99° — tempéré haut"
        )
    }

    @Test
    fun `temperature - warm bin at exactly 20 and up to 30 exclusive`() {
        // 20° pile est le premier degré du palier warm.
        assertHeatmapStyle(
            hourlyTemperatureHeatmap(20.0),
            Color(0xFFFF7043),
            "temp 20° pile — passe en warm"
        )
        assertHeatmapStyle(
            hourlyTemperatureHeatmap(29.99),
            Color(0xFFFF7043),
            "temp 29.99° — reste warm"
        )
    }

    @Test
    fun `temperature - hot bin at exactly 30 and above`() {
        assertHeatmapStyle(
            hourlyTemperatureHeatmap(30.0),
            Color(0xFFE53935),
            "temp 30° pile — passe en hot"
        )
        assertHeatmapStyle(
            hourlyTemperatureHeatmap(45.0),
            Color(0xFFE53935),
            "temp 45° — canicule extrême"
        )
    }

    @Test
    fun `temperature is NEVER null - every value maps to a color`() {
        // Contrat spécifique à la heatmap température : chaque cellule est
        // colorée (pas de retour null comme pour precip/wind). C'est ce qui
        // distingue la heatmap "toujours pleine" de la heatmap "signal only".
        // On teste une plage large pour se prémunir d'une régression du type
        // "on a réintroduit un bin null par mégarde".
        val values = listOf(-40.0, -0.001, 0.0, 4.9, 5.0, 5.001, 15.0, 19.99, 20.0, 30.0, 60.0)
        values.forEach { t ->
            assertNotNull(
                "Aucune température ne doit renvoyer null (ici $t°)",
                hourlyTemperatureHeatmap(t)
            )
        }
    }

    // ─── hourlyPrecipitationHeatmap : bornes des paliers ─────────────────

    @Test
    fun `precipitation - dry bin returns null strictly below 0_05 mm`() {
        // Le seuil "0.05 mm/h" représente essentiellement le zéro effectif
        // (bruit de mesure des modèles). Tout ce qui est en-dessous renvoie
        // null → pas de coloration, la cellule "se fond" dans le tableau.
        assertNull("0 mm doit être null (sec)", hourlyPrecipitationHeatmap(0.0))
        assertNull("0.049 mm doit être null (< seuil)", hourlyPrecipitationHeatmap(0.049))
        assertNull("mm négatif = null aussi (défensif)", hourlyPrecipitationHeatmap(-1.0))
    }

    @Test
    fun `precipitation - light rain from 0_05 to 0_5 exclusive`() {
        // Cas limite bas : 0.05 mm = premier palier coloré.
        assertHeatmapStyle(
            hourlyPrecipitationHeatmap(0.05),
            Color(0xFF4FC3F7),
            "precip 0.05 mm pile"
        )
        assertHeatmapStyle(
            hourlyPrecipitationHeatmap(0.3),
            Color(0xFF4FC3F7),
            "precip 0.3 mm — bruine"
        )
        assertHeatmapStyle(
            hourlyPrecipitationHeatmap(0.499),
            Color(0xFF4FC3F7),
            "precip 0.499 mm — juste sous 0.5"
        )
    }

    @Test
    fun `precipitation - moderate rain from 0_5 to 2 exclusive`() {
        assertHeatmapStyle(
            hourlyPrecipitationHeatmap(0.5),
            Color(0xFF1E88E5),
            "precip 0.5 mm — passe en modéré"
        )
        assertHeatmapStyle(
            hourlyPrecipitationHeatmap(1.9),
            Color(0xFF1E88E5),
            "precip 1.9 mm — encore modéré"
        )
    }

    @Test
    fun `precipitation - heavy rain from 2 to 5 exclusive`() {
        assertHeatmapStyle(
            hourlyPrecipitationHeatmap(2.0),
            Color(0xFF1565C0),
            "precip 2 mm pile — passe en fort"
        )
        assertHeatmapStyle(
            hourlyPrecipitationHeatmap(4.999),
            Color(0xFF1565C0),
            "precip 4.999 mm — encore fort"
        )
    }

    @Test
    fun `precipitation - very heavy rain at 5 and above`() {
        assertHeatmapStyle(
            hourlyPrecipitationHeatmap(5.0),
            Color(0xFF0D47A1),
            "precip 5 mm pile — orage"
        )
        assertHeatmapStyle(
            hourlyPrecipitationHeatmap(50.0),
            Color(0xFF0D47A1),
            "precip 50 mm/h — pluie diluvienne"
        )
    }

    // ─── hourlyWindHeatmap : bornes des paliers ──────────────────────────

    @Test
    fun `wind - calm bin returns null strictly below 20 kmh`() {
        // Même logique que pour la pluie : sous 20 km/h le vent est du bruit,
        // pas la peine de "polluer" la vue avec une couleur.
        assertNull("0 km/h = calme", hourlyWindHeatmap(0.0))
        assertNull("19.99 km/h = juste sous seuil", hourlyWindHeatmap(19.99))
    }

    @Test
    fun `wind - light breeze from 20 to 40 exclusive`() {
        assertHeatmapStyle(
            hourlyWindHeatmap(20.0),
            Color(0xFFFFB74D),
            "wind 20 km/h pile"
        )
        assertHeatmapStyle(
            hourlyWindHeatmap(39.9),
            Color(0xFFFFB74D),
            "wind 39.9 km/h"
        )
    }

    @Test
    fun `wind - moderate from 40 to 60 exclusive`() {
        assertHeatmapStyle(
            hourlyWindHeatmap(40.0),
            Color(0xFFFB8C00),
            "wind 40 km/h pile"
        )
        assertHeatmapStyle(
            hourlyWindHeatmap(59.9),
            Color(0xFFFB8C00),
            "wind 59.9 km/h"
        )
    }

    @Test
    fun `wind - strong from 60 to 80 exclusive`() {
        assertHeatmapStyle(
            hourlyWindHeatmap(60.0),
            Color(0xFFE64A19),
            "wind 60 km/h pile"
        )
        assertHeatmapStyle(
            hourlyWindHeatmap(79.9),
            Color(0xFFE64A19),
            "wind 79.9 km/h — juste sous tempête"
        )
    }

    @Test
    fun `wind - storm at 80 and above`() {
        assertHeatmapStyle(
            hourlyWindHeatmap(80.0),
            Color(0xFFC62828),
            "wind 80 km/h — tempête"
        )
        assertHeatmapStyle(
            hourlyWindHeatmap(150.0),
            Color(0xFFC62828),
            "wind 150 km/h — cyclone"
        )
    }

    // ─── Contraste : texte lisible sur chaque couleur de la palette ──────

    @Test
    fun `all palette backgrounds produce a readable text color`() {
        // Sanity : chaque couleur de la palette heatmap complète doit produire
        // un contentColor qui est soit noir soit blanc (pas de couleur cassée
        // qui aurait glissé dans la palette). Ce test blindé attrapera aussi
        // un ajout futur d'une couleur borderline qui aurait été oubliée dans
        // le calcul de contraste.
        val allValues = listOf(
            -10.0, 0.0, 3.0, 10.0, 25.0, 35.0                // temperature
        ).map { hourlyTemperatureHeatmap(it) } +
            listOf(0.1, 1.0, 3.0, 10.0).map { hourlyPrecipitationHeatmap(it) } +
            listOf(30.0, 50.0, 70.0, 100.0).map { hourlyWindHeatmap(it) }

        allValues.filterNotNull().forEach { style ->
            assertTrue(
                "contentColor doit être noir ou blanc pour ${style.background}",
                style.contentColor == Color.Black || style.contentColor == Color.White
            )
        }
    }

    @Test
    fun `dark backgrounds get white text`() {
        // Les couleurs les plus sombres de la palette (bleus foncés pluie
        // forte/orage, rouge foncé tempête vent) doivent avoir du texte blanc.
        // Note : avec le seuil WCAG 0.179, AUCUN palier de température ne
        // tombe en "sombre" — le bleu du gel (#1E88E5, L≈0.235) prend du
        // texte NOIR. C'est intentionnel : les couleurs de température ont
        // été choisies pour rester lisibles en noir, plus adapté à des
        // valeurs numériques serrées ("-3°", "12°") qu'on lit rapidement.
        // Régression : si quelqu'un swappait la règle luminance à `< 0.179`
        // par mégarde, ce test crierait.
        val darkStyles = listOf(
            hourlyPrecipitationHeatmap(3.0)!!,   // #1565C0
            hourlyPrecipitationHeatmap(10.0)!!,  // #0D47A1
            hourlyWindHeatmap(100.0)!!            // #C62828
        )
        darkStyles.forEach {
            assertEquals("fond sombre → texte blanc (${it.background})", Color.White, it.contentColor)
        }
    }

    @Test
    fun `light backgrounds get black text`() {
        // Les couleurs les plus claires : vert pâle tempéré, bleu clair
        // bruine, orange clair brise → toutes doivent avoir texte noir.
        val lightStyles = listOf(
            hourlyTemperatureHeatmap(10.0),      // #DCEDC8 très clair
            hourlyPrecipitationHeatmap(0.1)!!,   // #4FC3F7 clair
            hourlyWindHeatmap(25.0)!!             // #FFB74D clair
        )
        lightStyles.forEach {
            assertEquals("fond clair → texte noir (${it.background})", Color.Black, it.contentColor)
        }
    }

    // ─── HeatmapCellStyle : contrat de la data class ─────────────────────

    @Test
    fun `HeatmapCellStyle auto-computes contentColor when not provided`() {
        // Le default de contentColor doit invoquer contrastingContentColor
        // sur le background. C'est le vrai contrat de commodité de la classe
        // — sans ça, chaque call-site devrait faire le calcul manuellement.
        val bright = HeatmapCellStyle(background = Color.White)
        assertEquals(Color.Black, bright.contentColor)

        val dark = HeatmapCellStyle(background = Color.Black)
        assertEquals(Color.White, dark.contentColor)
    }

    @Test
    fun `HeatmapCellStyle allows explicit contentColor override`() {
        // Le contentColor par défaut peut être forcé, utile pour des cas
        // où le design demande explicitement une couleur (ex : accent rouge
        // sur fond clair pour indiquer "alerte" dans un futur feature).
        val custom = HeatmapCellStyle(
            background = Color.White,
            contentColor = Color(0xFFFF0000) // rouge forcé
        )
        assertEquals(Color(0xFFFF0000), custom.contentColor)
    }
}
