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
 * Chaque styler ayant maintenant 10 paliers, on utilise une approche
 * data-driven : une liste de cas [BinCase] par styler, itérée dans un test
 * unique — le message d'erreur JUnit identifie le cas fautif. Cela reste
 * lisible sans introduire de dépendance à un runner paramétré.
 *
 * Trois axes de couverture :
 *   1. **Frontières de seuil** — pour chaque palier, on teste la borne
 *      *inférieure exacte* et une valeur *strictement au-dessus de la borne
 *      supérieure du palier précédent* (idem "off-by-one" resistant).
 *   2. **Contraste texte/fond** — chaque style a un [HeatmapCellStyle.contentColor]
 *      lisible (noir sur fond clair, blanc sur fond sombre) ;
 *      règle `luminance > 0.179 → noir sinon blanc` (WCAG-optimal).
 *   3. **Comportement "neutre" (null)** — précip/vent renvoient `null` sous
 *      un seuil (sec, calme) ; la température ne renvoie JAMAIS null (heatmap
 *      "toujours pleine").
 *
 * Tests JVM purs — [androidx.compose.ui.graphics.Color] est un value class
 * calculable hors contexte Android.
 */
class HourlyHeatmapTest {

    /**
     * Cas d'un bin de heatmap : la valeur d'entrée [input] doit tomber dans
     * le bin [expectedColor], avec un [label] descriptif pour les messages
     * d'assertion en cas d'échec.
     */
    private data class BinCase(
        val input: Double,
        val expectedColor: Color,
        val label: String
    )

    // ─── Palette de référence ─────────────────────────────────────────────
    // Dupliquée ici volontairement : le rôle du test est de VERROUILLER les
    // couleurs. Si un dev change la palette dans HourlyHeatmap.kt sans venir
    // ici, on veut que le test échoue et force la question "est-ce voulu ?".

    private companion object {
        // Temp bins (10) — bornes ascendantes
        val TEMP_POLAR      = Color(0xFF0D47A1)  // <-10°
        val TEMP_VERY_COLD  = Color(0xFF1565C0)  // -10 ..< -5
        val TEMP_COLD       = Color(0xFF1E88E5)  // -5 ..< 0
        val TEMP_CHILL      = Color(0xFF4FC3F7)  // 0 ..< 5
        val TEMP_COOL       = Color(0xFFB3E5FC)  // 5 ..< 10
        val TEMP_MILD       = Color(0xFFDCEDC8)  // 10 ..< 15
        val TEMP_TEMPERATE  = Color(0xFFFFF59D)  // 15 ..< 20
        val TEMP_WARM       = Color(0xFFFFB74D)  // 20 ..< 25
        val TEMP_VERY_WARM  = Color(0xFFFF7043)  // 25 ..< 30
        val TEMP_HEATWAVE   = Color(0xFFC62828)  // ≥ 30

        // Precip bins (10 colored + null under 0.05)
        val P1 = Color(0xFFE3F2FD); val P2 = Color(0xFFBBDEFB)
        val P3 = Color(0xFF90CAF9); val P4 = Color(0xFF64B5F6)
        val P5 = Color(0xFF42A5F5); val P6 = Color(0xFF2196F3)
        val P7 = Color(0xFF1E88E5); val P8 = Color(0xFF1976D2)
        val P9 = Color(0xFF1565C0); val P10 = Color(0xFF0D47A1)

        // Wind bins (10 colored + null under 20)
        val W1 = Color(0xFFFFF9C4); val W2 = Color(0xFFFFF176)
        val W3 = Color(0xFFFFEB3B); val W4 = Color(0xFFFFCA28)
        val W5 = Color(0xFFFFB74D); val W6 = Color(0xFFFF9800)
        val W7 = Color(0xFFFB8C00); val W8 = Color(0xFFF57C00)
        val W9 = Color(0xFFE64A19); val W10 = Color(0xFFC62828)
    }

    // ─── Helpers de vérification ──────────────────────────────────────────

    /**
     * Vérifie qu'un style a le fond attendu ET une couleur de contenu
     * cohérente avec [contrastingContentColor]. Teste les deux ensemble
     * parce qu'une régression sur l'une passerait souvent inaperçue si on
     * ne testait que l'autre.
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
        assertEquals(Color.Black, contrastingContentColor(Color.White))
        assertEquals(Color.Black, contrastingContentColor(Color(0xFFFFF59D)))
    }

    @Test
    fun `contrastingContentColor returns white on dark background`() {
        assertEquals(Color.White, contrastingContentColor(Color.Black))
        assertEquals(Color.White, contrastingContentColor(Color(0xFF0D47A1)))
    }

    @Test
    fun `contrastingContentColor is deterministic for a given color`() {
        // Sanity : protéger contre l'introduction accidentelle d'aléa (Random)
        // dans une future refonte.
        val bg = Color(0xFFE53935)
        assertEquals(contrastingContentColor(bg), contrastingContentColor(bg))
    }

    // ─── Température : 10 bins, JAMAIS null ──────────────────────────────

    @Test
    fun `temperature - each bin returns its expected color for representative values`() {
        // Deux valeurs par bin : la borne inférieure exacte, et une valeur
        // milieu-de-bin. La borne supérieure est couverte par la borne
        // inférieure du bin suivant.
        val cases = listOf(
            // <-10° : polaire
            BinCase(-40.0, TEMP_POLAR,     "polaire -40°"),
            BinCase(-10.1, TEMP_POLAR,     "polaire -10.1° (juste sous -10)"),
            // -10 ..< -5
            BinCase(-10.0, TEMP_VERY_COLD, "très froid -10° pile"),
            BinCase(-7.5,  TEMP_VERY_COLD, "très froid milieu -7.5°"),
            // -5 ..< 0
            BinCase(-5.0,  TEMP_COLD,      "froid -5° pile"),
            BinCase(-0.1,  TEMP_COLD,      "froid -0.1° (juste sous 0)"),
            // 0 ..< 5
            BinCase(0.0,   TEMP_CHILL,     "frais 0° pile"),
            BinCase(2.5,   TEMP_CHILL,     "frais milieu 2.5°"),
            // 5 ..< 10
            BinCase(5.0,   TEMP_COOL,      "frais léger 5° pile"),
            BinCase(9.99,  TEMP_COOL,      "frais léger 9.99°"),
            // 10 ..< 15
            BinCase(10.0,  TEMP_MILD,      "doux 10° pile"),
            BinCase(12.5,  TEMP_MILD,      "doux milieu 12.5°"),
            // 15 ..< 20
            BinCase(15.0,  TEMP_TEMPERATE, "tempéré 15° pile"),
            BinCase(19.9,  TEMP_TEMPERATE, "tempéré 19.9°"),
            // 20 ..< 25
            BinCase(20.0,  TEMP_WARM,      "chaud 20° pile"),
            BinCase(22.5,  TEMP_WARM,      "chaud milieu 22.5°"),
            // 25 ..< 30
            BinCase(25.0,  TEMP_VERY_WARM, "très chaud 25° pile"),
            BinCase(29.99, TEMP_VERY_WARM, "très chaud 29.99°"),
            // ≥ 30
            BinCase(30.0,  TEMP_HEATWAVE,  "canicule 30° pile"),
            BinCase(45.0,  TEMP_HEATWAVE,  "canicule extrême 45°")
        )
        cases.forEach { case ->
            assertHeatmapStyle(
                hourlyTemperatureHeatmap(case.input),
                case.expectedColor,
                "temp ${case.label}"
            )
        }
    }

    @Test
    fun `temperature is NEVER null - every value maps to a color`() {
        // Contrat spécifique à la heatmap température : chaque cellule est
        // colorée (pas de retour null comme pour precip/wind).
        val values = listOf(
            -50.0, -10.0001, -10.0, -9.9, -5.0, -0.001, 0.0, 4.999, 5.0,
            10.0, 14.999, 15.0, 19.999, 20.0, 25.0, 29.999, 30.0, 60.0
        )
        values.forEach { t ->
            assertNotNull(
                "Aucune température ne doit renvoyer null (ici $t°)",
                hourlyTemperatureHeatmap(t)
            )
        }
    }

    // ─── Précipitations : 10 bins colorés + null pour sec ─────────────────

    @Test
    fun `precipitation - dry bin returns null strictly below 0_05 mm`() {
        // Le seuil 0.05 mm/h ≈ zéro effectif (bruit de mesure des modèles).
        assertNull("0 mm doit être null (sec)", hourlyPrecipitationHeatmap(0.0))
        assertNull("0.049 mm doit être null (< seuil)", hourlyPrecipitationHeatmap(0.049))
        assertNull("mm négatif = null aussi (défensif)", hourlyPrecipitationHeatmap(-1.0))
    }

    @Test
    fun `precipitation - each bin returns its expected color for representative values`() {
        val cases = listOf(
            // 0.05 ..< 0.1 : bruine invisible
            BinCase(0.05,  P1,  "0.05 mm pile"),
            BinCase(0.099, P1,  "0.099 mm"),
            // 0.1 ..< 0.2
            BinCase(0.1,   P2,  "0.1 mm pile"),
            BinCase(0.15,  P2,  "0.15 mm milieu"),
            // 0.2 ..< 0.5
            BinCase(0.2,   P3,  "0.2 mm pile"),
            BinCase(0.499, P3,  "0.499 mm"),
            // 0.5 ..< 1
            BinCase(0.5,   P4,  "0.5 mm pile"),
            BinCase(0.75,  P4,  "0.75 mm"),
            // 1 ..< 2
            BinCase(1.0,   P5,  "1 mm pile"),
            BinCase(1.999, P5,  "1.999 mm"),
            // 2 ..< 3
            BinCase(2.0,   P6,  "2 mm pile"),
            BinCase(2.5,   P6,  "2.5 mm milieu"),
            // 3 ..< 5
            BinCase(3.0,   P7,  "3 mm pile"),
            BinCase(4.99,  P7,  "4.99 mm"),
            // 5 ..< 7
            BinCase(5.0,   P8,  "5 mm pile"),
            BinCase(6.0,   P8,  "6 mm"),
            // 7 ..< 10
            BinCase(7.0,   P9,  "7 mm pile"),
            BinCase(9.99,  P9,  "9.99 mm"),
            // ≥ 10
            BinCase(10.0,  P10, "10 mm pile — déluge"),
            BinCase(50.0,  P10, "50 mm/h — pluie diluvienne")
        )
        cases.forEach { case ->
            assertHeatmapStyle(
                hourlyPrecipitationHeatmap(case.input),
                case.expectedColor,
                "precip ${case.label}"
            )
        }
    }

    // ─── Vent : 10 bins colorés + null pour calme ─────────────────────────

    @Test
    fun `wind - calm bin returns null strictly below 20 kmh`() {
        assertNull("0 km/h = calme", hourlyWindHeatmap(0.0))
        assertNull("19.99 km/h = juste sous seuil", hourlyWindHeatmap(19.99))
    }

    @Test
    fun `wind - each bin returns its expected color for representative values`() {
        val cases = listOf(
            // 20 ..< 30 : B3 brise légère
            BinCase(20.0,  W1,  "20 km/h pile"),
            BinCase(29.0,  W1,  "29 km/h"),
            // 30 ..< 40 : B4
            BinCase(30.0,  W2,  "30 km/h pile"),
            BinCase(35.0,  W2,  "35 km/h milieu"),
            // 40 ..< 50 : B5
            BinCase(40.0,  W3,  "40 km/h pile"),
            BinCase(49.99, W3,  "49.99 km/h"),
            // 50 ..< 60 : B6
            BinCase(50.0,  W4,  "50 km/h pile"),
            BinCase(55.0,  W4,  "55 km/h"),
            // 60 ..< 70 : B7
            BinCase(60.0,  W5,  "60 km/h pile"),
            BinCase(69.9,  W5,  "69.9 km/h"),
            // 70 ..< 80 : B8
            BinCase(70.0,  W6,  "70 km/h pile"),
            BinCase(75.0,  W6,  "75 km/h"),
            // 80 ..< 90 : B9
            BinCase(80.0,  W7,  "80 km/h pile"),
            BinCase(89.99, W7,  "89.99 km/h"),
            // 90 ..< 100 : B10
            BinCase(90.0,  W8,  "90 km/h pile"),
            BinCase(95.0,  W8,  "95 km/h"),
            // 100 ..< 120 : B11
            BinCase(100.0, W9,  "100 km/h pile"),
            BinCase(119.9, W9,  "119.9 km/h"),
            // ≥ 120 : B12 ouragan
            BinCase(120.0, W10, "120 km/h pile — ouragan"),
            BinCase(200.0, W10, "200 km/h — cyclone")
        )
        cases.forEach { case ->
            assertHeatmapStyle(
                hourlyWindHeatmap(case.input),
                case.expectedColor,
                "wind ${case.label}"
            )
        }
    }

    // ─── Contraste : texte lisible sur chaque couleur de la palette ──────

    @Test
    fun `all palette backgrounds produce a readable text color`() {
        // Sanity blindé : chaque couleur produit soit noir soit blanc, jamais
        // une couleur cassée qui aurait glissé dans la palette. Attrape aussi
        // un ajout futur d'une couleur borderline oubliée dans le calcul.
        val allValues =
            listOf(-15.0, -7.5, -2.5, 2.5, 7.5, 12.5, 17.5, 22.5, 27.5, 35.0)
                .map { hourlyTemperatureHeatmap(it) } +
                    listOf(0.06, 0.15, 0.3, 0.7, 1.5, 2.5, 4.0, 6.0, 8.5, 15.0)
                        .map { hourlyPrecipitationHeatmap(it) } +
                    listOf(25.0, 35.0, 45.0, 55.0, 65.0, 75.0, 85.0, 95.0, 110.0, 150.0)
                        .map { hourlyWindHeatmap(it) }

        allValues.filterNotNull().forEach { style ->
            assertTrue(
                "contentColor doit être noir ou blanc pour ${style.background}",
                style.contentColor == Color.Black || style.contentColor == Color.White
            )
        }
    }

    @Test
    fun `dark backgrounds get white text`() {
        // Les couleurs les plus sombres de chaque palette doivent avoir du
        // texte blanc. Avec le seuil WCAG 0.179, ce sont uniquement :
        //  - temp : polaire #0D47A1 et très froid #1565C0
        //  - precip : bins ≥ 5 mm/h (#1976D2 et plus foncé)
        //  - wind : ouragan #C62828
        val darkStyles = listOf(
            hourlyTemperatureHeatmap(-20.0),      // TEMP_POLAR
            hourlyTemperatureHeatmap(-8.0),       // TEMP_VERY_COLD
            hourlyPrecipitationHeatmap(6.0)!!,    // P8
            hourlyPrecipitationHeatmap(15.0)!!,   // P10
            hourlyWindHeatmap(150.0)!!             // W10
        )
        darkStyles.forEach {
            assertEquals("fond sombre → texte blanc (${it.background})", Color.White, it.contentColor)
        }
    }

    @Test
    fun `light backgrounds get black text`() {
        // Représentants des couleurs claires de chaque palette.
        val lightStyles = listOf(
            hourlyTemperatureHeatmap(12.0),      // TEMP_MILD (vert pâle)
            hourlyTemperatureHeatmap(17.0),      // TEMP_TEMPERATE (jaune pâle)
            hourlyPrecipitationHeatmap(0.06)!!,  // P1 (bleu très clair)
            hourlyPrecipitationHeatmap(0.15)!!,  // P2
            hourlyWindHeatmap(25.0)!!,            // W1 (jaune très clair)
            hourlyWindHeatmap(65.0)!!             // W5 (orange clair)
        )
        lightStyles.forEach {
            assertEquals("fond clair → texte noir (${it.background})", Color.Black, it.contentColor)
        }
    }

    // ─── HeatmapCellStyle : contrat de la data class ─────────────────────

    @Test
    fun `HeatmapCellStyle auto-computes contentColor when not provided`() {
        val bright = HeatmapCellStyle(background = Color.White)
        assertEquals(Color.Black, bright.contentColor)

        val dark = HeatmapCellStyle(background = Color.Black)
        assertEquals(Color.White, dark.contentColor)
    }

    @Test
    fun `HeatmapCellStyle allows explicit contentColor override`() {
        val custom = HeatmapCellStyle(
            background = Color.White,
            contentColor = Color(0xFFFF0000) // rouge forcé
        )
        assertEquals(Color(0xFFFF0000), custom.contentColor)
    }

    // ─── Invariants inter-paliers ────────────────────────────────────────

    @Test
    fun `temperature - luminance decreases monotonically outward from the mild zone`() {
        // Contrat perceptif : la couleur des paliers extrêmes doit être plus
        // "saturée/sombre" que celle des paliers moyens, pour que les extrêmes
        // ressortent visuellement. On teste que :
        //   L(polaire) < L(cold) < L(chill)   (côté froid)
        //   L(heatwave) < L(very_warm) < L(warm)  (côté chaud)
        val polaire  = hourlyTemperatureHeatmap(-20.0).background.luminance()
        val cold     = hourlyTemperatureHeatmap(-2.0).background.luminance()
        val chill    = hourlyTemperatureHeatmap(2.0).background.luminance()
        val warm     = hourlyTemperatureHeatmap(22.0).background.luminance()
        val veryWarm = hourlyTemperatureHeatmap(27.0).background.luminance()
        val heatwave = hourlyTemperatureHeatmap(35.0).background.luminance()

        assertTrue("polaire ($polaire) doit être + sombre que froid ($cold)", polaire < cold)
        assertTrue("froid ($cold) doit être + sombre que frais ($chill)", cold < chill)
        assertTrue("canicule ($heatwave) doit être + sombre que très chaud ($veryWarm)", heatwave < veryWarm)
        assertTrue("très chaud ($veryWarm) doit être + sombre que chaud ($warm)", veryWarm < warm)
    }

    @Test
    fun `precipitation - luminance decreases monotonically as intensity increases`() {
        // Contrat perceptif : plus il pleut, plus la cellule est sombre.
        // On échantillonne un point par bin et on vérifie la décroissance.
        val samples = listOf(0.07, 0.15, 0.3, 0.7, 1.5, 2.5, 4.0, 6.0, 8.5, 15.0)
        val luminances = samples.map { hourlyPrecipitationHeatmap(it)!!.background.luminance() }
        for (i in 1 until luminances.size) {
            assertTrue(
                "luminance doit décroître entre ${samples[i-1]} et ${samples[i]} mm/h " +
                        "(vu ${luminances[i-1]} → ${luminances[i]})",
                luminances[i] < luminances[i - 1]
            )
        }
    }

    @Test
    fun `wind - luminance decreases monotonically as speed increases`() {
        // Idem pour le vent : plus il souffle, plus la cellule est sombre.
        val samples = listOf(25.0, 35.0, 45.0, 55.0, 65.0, 75.0, 85.0, 95.0, 110.0, 150.0)
        val luminances = samples.map { hourlyWindHeatmap(it)!!.background.luminance() }
        for (i in 1 until luminances.size) {
            assertTrue(
                "luminance doit décroître entre ${samples[i-1]} et ${samples[i]} km/h " +
                        "(vu ${luminances[i-1]} → ${luminances[i]})",
                luminances[i] < luminances[i - 1]
            )
        }
    }
}