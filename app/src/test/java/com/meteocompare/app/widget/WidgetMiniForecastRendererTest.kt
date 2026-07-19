package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM des parties pures de [WidgetMiniForecastRenderer].
 *
 * ─── Périmètre ────────────────────────────────────────────────────────────
 * On ne teste PAS `render()` complet — ça nécessiterait Robolectric pour
 * simuler Bitmap/Canvas/Paint. On teste les fonctions utilitaires pures qui
 * concentrent 90% de la logique métier :
 *   - [temperatureHeatmapArgb] : mapping temp → couleur ARGB
 *   - Le pipeline complet est validé manuellement via inspection visuelle
 *     sur émulateur.
 *
 * ─── Pourquoi c'est suffisant ─────────────────────────────────────────────
 * Le rendu Bitmap est mécanique (drawRoundRect + drawLine + drawText) — les seuls bugs qui
 * peuvent apparaître sont dans les CALCULS (couleurs, offsets, tailles). Ces
 * calculs sont pures functions ou boucles triviales, tous testables ici.
 * Les appels Canvas ne peuvent pas mal se passer une fois
 * `rect` et `paint.color` calculés correctement.
 */
class WidgetMiniForecastRendererTest {

    // ─── temperatureHeatmapArgb ──────────────────────────────────────────

    @Test
    fun `temp glaciale renvoie bleu profond`() {
        val color = WidgetMiniForecastRenderer.temperatureHeatmapArgb(-15.0)
        // Bleu profond = 0xFF1976D2 (le premier stop, clampé)
        assertEquals(0xFF1976D2.toInt(), color)
    }

    @Test
    fun `temp caniculaire renvoie rouge sombre`() {
        val color = WidgetMiniForecastRenderer.temperatureHeatmapArgb(45.0)
        // Rouge sombre = 0xFFB71C1C (dernier stop, clampé)
        assertEquals(0xFFB71C1C.toInt(), color)
    }

    @Test
    fun `stops exacts renvoient les couleurs exactes`() {
        // À la valeur exacte d'un stop, l'interpolation retourne cette couleur.
        assertEquals(0xFF1976D2.toInt(),
            WidgetMiniForecastRenderer.temperatureHeatmapArgb(-10.0))
        assertEquals(0xFF4FC3F7.toInt(),
            WidgetMiniForecastRenderer.temperatureHeatmapArgb(5.0))
        assertEquals(0xFF81C784.toInt(),
            WidgetMiniForecastRenderer.temperatureHeatmapArgb(15.0))
        assertEquals(0xFFFFB74D.toInt(),
            WidgetMiniForecastRenderer.temperatureHeatmapArgb(22.0))
        assertEquals(0xFFEF5350.toInt(),
            WidgetMiniForecastRenderer.temperatureHeatmapArgb(30.0))
        assertEquals(0xFFB71C1C.toInt(),
            WidgetMiniForecastRenderer.temperatureHeatmapArgb(40.0))
    }

    @Test
    fun `interpolation monotone entre stops`() {
        // Les couleurs doivent VARIER quand la temp varie — pas de plateau
        // caché dû à un bug d'interpolation.
        val temps = listOf(-5.0, 0.0, 10.0, 18.0, 25.0, 35.0)
        val colors = temps.map { WidgetMiniForecastRenderer.temperatureHeatmapArgb(it) }
        // Chaque couleur consécutive doit différer de la précédente
        colors.zipWithNext().forEachIndexed { i, (a, b) ->
            assertNotEquals(
                "colors[$i] == colors[$i+1] : interpolation cassée entre ${temps[i]}°C et ${temps[i+1]}°C",
                a, b
            )
        }
    }

    @Test
    fun `alpha toujours 255 opaque quel que soit le temp`() {
        // Les couleurs des stops sont toutes opaques (préfixe 0xFF). L'interpolation
        // ne doit pas introduire de translucidité — le dot pluie et les bars doivent
        // être franchement lisibles sur fond de widget.
        val temps = listOf(-20.0, -10.0, 0.0, 10.0, 20.0, 30.0, 40.0, 50.0)
        temps.forEach { temp ->
            val color = WidgetMiniForecastRenderer.temperatureHeatmapArgb(temp)
            val alpha = (color shr 24) and 0xFF
            assertEquals("Temp $temp°C devrait donner alpha 255, obtenu $alpha", 255, alpha)
        }
    }

    @Test
    fun `interpolation lineaire entre deux stops donne le milieu`() {
        // À mi-chemin entre 15°C (vert 81C784) et 22°C (orange FFB74D), on doit
        // obtenir une couleur proche du milieu RGB canal par canal.
        // 15°C : R=0x81=129, G=0xC7=199, B=0x84=132
        // 22°C : R=0xFF=255, G=0xB7=183, B=0x4D=77
        // Mid (18.5°C, f=0.5) : R=192, G=191, B=104 → ~0xFFC0BF68
        val mid = WidgetMiniForecastRenderer.temperatureHeatmapArgb(18.5)
        val r = (mid shr 16) and 0xFF
        val g = (mid shr 8) and 0xFF
        val b = mid and 0xFF
        // Tolérance ±1 sur chaque canal pour absorber l'arrondi float→int
        assertTrue("R attendu ~192, obtenu $r", (r - 192).let { it in -1..1 })
        assertTrue("G attendu ~191, obtenu $g", (g - 191).let { it in -1..1 })
        assertTrue("B attendu ~104, obtenu $b", (b - 104).let { it in -1..1 })
    }


    // ─── precipitationHeatmapArgb ────────────────────────────────────────

    @Test
    fun `heatmap pluie devient plus opaque quand la probabilite augmente`() {
        val blue = 0xFF1976D2.toInt()
        val text = 0xFF001A41.toInt()
        val alphas = listOf(0, 25, 50, 75, 100).map { probability ->
            val color = WidgetMiniForecastRenderer.precipitationHeatmapArgb(
                probability = probability,
                precipColorArgb = blue,
                textColorArgb = text
            )
            (color ushr 24) and 0xFF
        }
        alphas.zipWithNext().forEach { (a, b) ->
            assertTrue("L'alpha pluie doit augmenter: $a puis $b", b > a)
        }
    }

    @Test
    fun `heatmap pluie conserve la teinte bleue`() {
        val blue = 0xFF1976D2.toInt()
        val color = WidgetMiniForecastRenderer.precipitationHeatmapArgb(
            probability = 60,
            precipColorArgb = blue,
            textColorArgb = 0xFF001A41.toInt()
        )
        assertEquals(blue and 0x00FFFFFF, color and 0x00FFFFFF)
    }

    @Test
    fun `heatmap pluie inconnue utilise une cellule neutre discrete`() {
        val text = 0xFF001A41.toInt()
        val color = WidgetMiniForecastRenderer.precipitationHeatmapArgb(
            probability = null,
            precipColorArgb = 0xFF1976D2.toInt(),
            textColorArgb = text
        )
        assertEquals(0x12, (color ushr 24) and 0xFF)
        assertEquals(text and 0x00FFFFFF, color and 0x00FFFFFF)
    }


    // ─── Hauteur des barres ─────────────────────────────────────────────

    @Test
    fun `barres temperature grandissent avec la temperature sur la fenetre`() {
        val fractions = WidgetMiniForecastRenderer.temperatureBarFractions(
            listOf(10.0, 15.0, 20.0, 25.0)
        ).filterNotNull()

        fractions.zipWithNext().forEach { (a, b) ->
            assertTrue("La barre chaude doit être plus haute: $a puis $b", b > a)
        }
    }

    @Test
    fun `barres temperature restent visibles quand les valeurs sont identiques`() {
        val fractions = WidgetMiniForecastRenderer.temperatureBarFractions(
            List(12) { 18.0 }
        ).filterNotNull()

        assertEquals(12, fractions.size)
        fractions.forEach { fraction ->
            assertTrue(fraction in 0.14f..1f)
        }
    }

    @Test
    fun `barre pluie augmente avec la quantite a probabilite egale`() {
        val light = WidgetMiniForecastRenderer.precipitationBarFraction(0.2, 70)
            ?: error("fraction manquante")
        val heavy = WidgetMiniForecastRenderer.precipitationBarFraction(3.5, 70)
            ?: error("fraction manquante")

        assertTrue(heavy > light)
    }

    @Test
    fun `barre pluie augmente avec la probabilite a quantite egale`() {
        val unlikely = WidgetMiniForecastRenderer.precipitationBarFraction(0.5, 20)
            ?: error("fraction manquante")
        val likely = WidgetMiniForecastRenderer.precipitationBarFraction(0.5, 90)
            ?: error("fraction manquante")

        assertTrue(likely > unlikely)
    }

    @Test
    fun `barre pluie est absente sans quantite ni probabilite`() {
        assertEquals(null, WidgetMiniForecastRenderer.precipitationBarFraction(null, null))
    }

    // ─── Contrat public de render (bornes d'input) ───────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `render leve si widthPx est zero`() {
        WidgetMiniForecastRenderer.render(
            widthPx = 0, heightPx = 48,
            temps = List(12) { 20.0 },
            precipProbabilities = List(12) { 0 },
            precipColorArgb = 0xFF2196F3.toInt(),
            textColorArgb = 0xDE000000.toInt()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `render leve si heightPx est negatif`() {
        WidgetMiniForecastRenderer.render(
            widthPx = 480, heightPx = -1,
            temps = List(12) { 20.0 },
            precipProbabilities = List(12) { 0 },
            precipColorArgb = 0xFF2196F3.toInt(),
            textColorArgb = 0xDE000000.toInt()
        )
    }
    // ─── Profils responsives ─────────────────────────────────────────────

    @Test
    fun `profil 2x2 conserve les douze valeurs grace a la grille deux lignes`() {
        assertEquals(
            MiniForecastSizeProfile.COMPACT_2X2,
            miniForecastProfileForWidth(180f)
        )
        assertEquals(
            (0..11).toList(),
            WidgetMiniForecastRenderer.visibleValueIndices(MiniForecastSizeProfile.COMPACT_2X2)
        )
    }

    @Test
    fun `profil 3x2 conserve les douze valeurs`() {
        assertEquals(
            MiniForecastSizeProfile.MEDIUM_3X2,
            miniForecastProfileForWidth(260f)
        )
        assertEquals(
            (0..11).toList(),
            WidgetMiniForecastRenderer.visibleValueIndices(MiniForecastSizeProfile.MEDIUM_3X2)
        )
    }

    @Test
    fun `profil 4x2 conserve les douze valeurs`() {
        assertEquals(
            MiniForecastSizeProfile.EXPANDED_4X2,
            miniForecastProfileForWidth(340f)
        )
        assertEquals(
            (0..11).toList(),
            WidgetMiniForecastRenderer.visibleValueIndices(MiniForecastSizeProfile.EXPANDED_4X2)
        )
    }



    @Test
    fun `hauteur mini forecast grandit avec le widget`() {
        val dense = miniForecastChartHeightDp(
            widgetHeightDp = 130f,
            headerHeightDp = 52f,
            sectionGapDp = 6f,
            profile = MiniForecastSizeProfile.COMPACT_2X2
        )
        val medium = miniForecastChartHeightDp(
            widgetHeightDp = 175f,
            headerHeightDp = 44f,
            sectionGapDp = 10f,
            profile = MiniForecastSizeProfile.MEDIUM_3X2
        )
        val tall = miniForecastChartHeightDp(
            widgetHeightDp = 260f,
            headerHeightDp = 44f,
            sectionGapDp = 10f,
            profile = MiniForecastSizeProfile.EXPANDED_4X2
        )

        assertTrue("La hauteur doit suivre le redimensionnement: $dense, $medium, $tall", dense < medium)
        assertTrue("La hauteur doit suivre le redimensionnement: $dense, $medium, $tall", medium < tall)
    }

    @Test
    fun `hauteur mini forecast reste bornee sur widget tres haut`() {
        assertEquals(
            220,
            miniForecastChartHeightDp(
                widgetHeightDp = 600f,
                headerHeightDp = 44f,
                sectionGapDp = 10f,
                profile = MiniForecastSizeProfile.EXPANDED_4X2
            )
        )
    }

    @Test
    fun `texte heatmap est sombre sur une tuile claire`() {
        val orange = WidgetMiniForecastRenderer.temperatureHeatmapArgb(22.0)
        assertEquals(0xFF17202A.toInt(), WidgetMiniForecastRenderer.heatmapContentColorArgb(orange))
    }

    @Test
    fun `texte heatmap est blanc sur une tuile sombre`() {
        val blue = WidgetMiniForecastRenderer.temperatureHeatmapArgb(-10.0)
        assertEquals(0xFFFFFFFF.toInt(), WidgetMiniForecastRenderer.heatmapContentColorArgb(blue))
    }

    @Test
    fun `heatmap pluie devient plus forte avec le cumul a probabilite egale`() {
        val blue = 0xFF1976D2.toInt()
        val text = 0xFF001A41.toInt()
        val light = WidgetMiniForecastRenderer.precipitationHeatmapArgb(
            probability = 50,
            precipColorArgb = blue,
            textColorArgb = text,
            amountMm = 0.1
        )
        val heavy = WidgetMiniForecastRenderer.precipitationHeatmapArgb(
            probability = 50,
            precipColorArgb = blue,
            textColorArgb = text,
            amountMm = 4.0
        )

        assertTrue(((heavy ushr 24) and 0xFF) > ((light ushr 24) and 0xFF))
    }
}