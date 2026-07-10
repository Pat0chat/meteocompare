package com.meteocompare.app.ui.citydetail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires de la géométrie du sparkline. On teste les fonctions pures
 * ([sparklinePoints], [envelopeVertices]) qui produisent des coordonnées :
 * la construction des `Path` de compose-ui est mécanique à partir de ces
 * coordonnées, donc si les points sont corrects, le rendu l'est aussi.
 *
 * Axes de couverture :
 *   1. **Projection Y** — repère canvas y-vers-le-bas, donc valeur data
 *      minimum → y = h (bas), maximum → y = 0 (haut).
 *   2. **Répartition X** — points équirépartis de x=0 à x=w.
 *   3. **Cas dégénérés** — domain min == max (division par zéro évitée),
 *      série vide, série d'un seul point.
 *   4. **Clipping domain** — valeurs data hors [yDomainMin, yDomainMax]
 *      coerce'd dans [0, h] côté canvas.
 *   5. **Enveloppe** — obs forward + forecast reverse, taille attendue.
 */
class BiasSparklineTest {

    // ─── Projection Y ─────────────────────────────────────────────────────

    @Test
    fun `sparklinePoints projects data minimum to canvas bottom`() {
        val pts = sparklinePoints(
            values = listOf(0.0, 5.0, 10.0),
            yDomainMin = 0.0,
            yDomainMax = 10.0,
            canvasSize = Size(100f, 200f)
        )
        // yDomainMin = 0.0 → yNorm = 0 → y = h - 0*h = h = 200
        assertEquals(200f, pts[0].y, EPS)
    }

    @Test
    fun `sparklinePoints projects data maximum to canvas top`() {
        val pts = sparklinePoints(
            values = listOf(0.0, 5.0, 10.0),
            yDomainMin = 0.0,
            yDomainMax = 10.0,
            canvasSize = Size(100f, 200f)
        )
        // yDomainMax = 10.0 → yNorm = 1 → y = h - 1*h = 0
        assertEquals(0f, pts[2].y, EPS)
    }

    @Test
    fun `sparklinePoints projects data midpoint to canvas middle`() {
        val pts = sparklinePoints(
            values = listOf(0.0, 5.0, 10.0),
            yDomainMin = 0.0,
            yDomainMax = 10.0,
            canvasSize = Size(100f, 200f)
        )
        assertEquals(100f, pts[1].y, EPS)
    }

    // ─── Répartition X ────────────────────────────────────────────────────

    @Test
    fun `sparklinePoints spaces x coordinates evenly across canvas`() {
        val pts = sparklinePoints(
            values = listOf(1.0, 2.0, 3.0, 4.0, 5.0),
            yDomainMin = 0.0,
            yDomainMax = 10.0,
            canvasSize = Size(400f, 100f)
        )
        // 5 points → intervalles à 0, 100, 200, 300, 400
        assertEquals(0f,   pts[0].x, EPS)
        assertEquals(100f, pts[1].x, EPS)
        assertEquals(200f, pts[2].x, EPS)
        assertEquals(300f, pts[3].x, EPS)
        assertEquals(400f, pts[4].x, EPS)
    }

    @Test
    fun `sparklinePoints - first point is at left edge, last at right edge`() {
        val pts = sparklinePoints(
            values = List(30) { it.toDouble() },
            yDomainMin = 0.0,
            yDomainMax = 29.0,
            canvasSize = Size(560f, 100f)
        )
        assertEquals(0f, pts.first().x, EPS)
        assertEquals(560f, pts.last().x, EPS)
    }

    // ─── Cas dégénérés ────────────────────────────────────────────────────

    @Test
    fun `sparklinePoints returns empty list for empty input`() {
        val pts = sparklinePoints(
            values = emptyList(),
            yDomainMin = 0.0, yDomainMax = 10.0,
            canvasSize = Size(100f, 100f)
        )
        assertTrue(pts.isEmpty())
    }

    @Test
    fun `sparklinePoints handles single value at x zero`() {
        // Cas dégénéré : le composant ne devrait jamais appeler avec une
        // seule valeur (précondition size >= 2 dans BiasSparkline), mais la
        // fonction doit rester robuste — éviter une division par zéro sur
        // (values.size - 1).
        val pts = sparklinePoints(
            values = listOf(5.0),
            yDomainMin = 0.0, yDomainMax = 10.0,
            canvasSize = Size(100f, 100f)
        )
        assertEquals(1, pts.size)
        assertEquals(0f, pts[0].x, EPS)
        assertEquals(50f, pts[0].y, EPS) // milieu du canvas
    }

    @Test
    fun `sparklinePoints centers all points on middle when domain is degenerate`() {
        // yDomainMin == yDomainMax : le range est nul, on ne peut pas
        // projeter. Fallback : tous les points sont sur la ligne du milieu,
        // ce qui produit une ligne plate (lisible) plutôt qu'un crash.
        val pts = sparklinePoints(
            values = listOf(5.0, 5.0, 5.0),
            yDomainMin = 5.0, yDomainMax = 5.0,
            canvasSize = Size(100f, 100f)
        )
        pts.forEach { assertEquals(50f, it.y, EPS) }
    }

    @Test
    fun `sparklinePoints coerces out-of-domain values into canvas bounds`() {
        // Valeurs hors [yDomainMin, yDomainMax] : au lieu de dépasser le
        // canvas (ce qui masquerait des points), on écrête aux bornes.
        // Choix pragmatique — on préfère "voir la ligne collée au bord"
        // que "la ligne disparaît en dehors".
        val pts = sparklinePoints(
            values = listOf(-100.0, 0.0, 100.0),
            yDomainMin = 0.0, yDomainMax = 10.0,
            canvasSize = Size(100f, 100f)
        )
        assertEquals(100f, pts[0].y, EPS) // écrêté à yDomainMin → bas du canvas
        assertEquals(100f, pts[1].y, EPS) // yDomainMin exact → bas
        assertEquals(0f,   pts[2].y, EPS) // écrêté à yDomainMax → haut
    }

    // ─── Enveloppe ────────────────────────────────────────────────────────

    @Test
    fun `envelopeVertices returns observation forward then forecast reversed`() {
        val obs = listOf(Offset(0f, 0f), Offset(10f, 5f), Offset(20f, 10f))
        val fcst = listOf(Offset(0f, 2f), Offset(10f, 7f), Offset(20f, 12f))

        val env = envelopeVertices(obs, fcst)

        assertEquals(6, env.size)
        // 3 premiers = obs dans l'ordre
        assertEquals(obs[0], env[0])
        assertEquals(obs[1], env[1])
        assertEquals(obs[2], env[2])
        // 3 suivants = fcst à l'envers
        assertEquals(fcst[2], env[3])
        assertEquals(fcst[1], env[4])
        assertEquals(fcst[0], env[5])
    }

    @Test
    fun `envelopeVertices returns empty when either series is empty`() {
        val nonEmpty = listOf(Offset(0f, 0f), Offset(10f, 5f))
        assertTrue(envelopeVertices(emptyList(), nonEmpty).isEmpty())
        assertTrue(envelopeVertices(nonEmpty, emptyList()).isEmpty())
        assertTrue(envelopeVertices(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `envelopeVertices forms closed polygon around bias envelope`() {
        // Cas réaliste : forecast systématiquement au-dessus de l'observation
        // (biais chaud). L'enveloppe doit encercler la zone entre les deux.
        // On teste qu'aller de la première obs à la dernière obs, puis
        // remonter le forecast à l'envers, ramène bien au voisinage du point
        // de départ (l'obs et le forecast partagent le même x=0 au début et
        // même x=100 à la fin, mais des y différents).
        val obs = listOf(Offset(0f, 100f), Offset(50f, 90f), Offset(100f, 100f))
        val fcst = listOf(Offset(0f, 80f), Offset(50f, 70f), Offset(100f, 80f))

        val env = envelopeVertices(obs, fcst)

        // Premier vertex = début obs (bas gauche)
        assertEquals(Offset(0f, 100f), env.first())
        // Dernier vertex = début forecast (haut gauche) — la fermeture
        // implicite ramenera au premier via `close()` dans le path.
        assertEquals(Offset(0f, 80f), env.last())
    }

    // ─── ENVELOPE_ALPHA — verrou de rendu final ──────────────────────────

    @Test
    fun `envelope alpha constant matches designed opacity`() {
        // La valeur 0.28 est calibrée à la main sur le mockup HTML pour
        // rester discrète en dark mode ET visible en light mode.
        // Si un futur refactor déplace cette constante ailleurs, ce test
        // sonne l'alerte.
        assertEquals(0.28f, ENVELOPE_ALPHA, EPS)
    }

    companion object {
        private const val EPS = 0.001f
    }
}
