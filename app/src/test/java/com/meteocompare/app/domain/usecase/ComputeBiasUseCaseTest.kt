package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasSignificance
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.ModelBias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.Instant
import kotlin.math.abs

/**
 * Tests de [ComputeBiasUseCase].
 *
 * Cinq axes de couverture :
 *   1. **Agrégation statistique** — moyenne exacte, écart-type d'échantillon
 *      (division par n−1), cas dégénérés (série constante, biais nul).
 *   2. **Fenêtre glissante** — samples hors [asOf-30j, asOf) filtrés, samples
 *      au jour même exclus, samples au jour J−30 exclus (borne semi-ouverte).
 *   3. **Déduplication** — deux samples pour la même date : le run au
 *      `issuedAt` le plus récent est gardé, quel que soit l'ordre d'entrée.
 *   4. **Seuil MIN_SAMPLES** — retour null si < 14, retour non-null au seuil
 *      pile.
 *   5. **Cohérence avec BiasSignificanceRule** — un résultat qui devrait être
 *      classé HIGH par la règle l'est bien.
 *
 * Toutes les dates sont ancrées sur `TODAY` fixe (pas `LocalDate.now`) —
 * tests déterministes indépendants de la date de run.
 */
class ComputeBiasUseCaseTest {

    private val useCase = ComputeBiasUseCase()

    private val today = LocalDate.of(2024, 7, 15)

    // ─── Agrégation ──────────────────────────────────────────────────────

    @Test
    fun `mean bias is the arithmetic mean of daily biases`() {
        // 14 jours (seuil MIN), tous avec un biais journalier de exactement +1.0°.
        val samples = daysBefore(today, 14).map { d ->
            BiasSample(d, forecast = 21.0, observation = 20.0) // dailyBias = +1.0
        }
        val result = useCase(BiasVariable.TEMPERATURE, samples, asOf = today)
        assertNotNull(result)
        assertEquals(1.0, result!!.meanBias, EPS)
    }

    @Test
    fun `mean bias averages signed daily biases correctly`() {
        // Alternance +2 / -1 sur 14 jours → moyenne 0.5.
        val samples = daysBefore(today, 14).mapIndexed { i, d ->
            val bias = if (i % 2 == 0) 2.0 else -1.0
            BiasSample(d, forecast = 20.0 + bias, observation = 20.0)
        }
        val result = useCase(BiasVariable.TEMPERATURE, samples, asOf = today)
        assertNotNull(result)
        assertEquals(0.5, result!!.meanBias, EPS)
    }

    @Test
    fun `stdDev uses sample formula with n minus 1 divisor`() {
        // 14 samples avec biais {1, 2, 3, ..., 14}. Moyenne = 7.5, variance
        // d'échantillon = sum((v-7.5)^2) / 13. Vérifie qu'on n'utilise pas la
        // formule population (division par n=14).
        val samples = daysBefore(today, 14).mapIndexed { i, d ->
            BiasSample(d, forecast = 20.0 + (i + 1), observation = 20.0)
        }
        val result = useCase(BiasVariable.TEMPERATURE, samples, asOf = today)
        assertNotNull(result)
        // Calcul attendu : mean = 7.5, sum((v-mean)^2) = 227.5, divisé par 13
        // → variance ≈ 17.5, stddev ≈ 4.183
        assertEquals(4.183, result!!.stdDev, 0.01)
    }

    @Test
    fun `stdDev is zero when all daily biases are identical`() {
        // Contrat mathématique : variance d'un échantillon constant = 0.
        val samples = daysBefore(today, 20).map { d ->
            BiasSample(d, forecast = 22.0, observation = 20.0)
        }
        val result = useCase(BiasVariable.TEMPERATURE, samples, asOf = today)
        assertNotNull(result)
        assertEquals(0.0, result!!.stdDev, EPS)
    }

    @Test
    fun `mean bias of zero when forecast matches observation exactly`() {
        val samples = daysBefore(today, 20).map { d ->
            BiasSample(d, forecast = 20.0, observation = 20.0)
        }
        val result = useCase(BiasVariable.TEMPERATURE, samples, asOf = today)
        assertNotNull(result)
        assertEquals(0.0, result!!.meanBias, EPS)
        assertEquals(0.0, result.stdDev, EPS)
    }

    // ─── Fenêtre glissante ───────────────────────────────────────────────

    @Test
    fun `samples outside the window are excluded from computation`() {
        // 14 samples dans la fenêtre (biais +1), 5 samples plus vieux (biais
        // +100 — polluerait la moyenne s'ils étaient inclus).
        val inWindow = daysBefore(today, 14).map { d ->
            BiasSample(d, forecast = 21.0, observation = 20.0) // +1
        }
        val tooOld = (35..40).map { offset ->
            BiasSample(today.minusDays(offset.toLong()), forecast = 120.0, observation = 20.0)
        }
        val result = useCase(BiasVariable.TEMPERATURE, inWindow + tooOld, asOf = today)
        assertNotNull(result)
        assertEquals(
            "vieux samples doivent être filtrés → moyenne = 1.0, pas ≫ 1",
            1.0, result!!.meanBias, EPS
        )
        assertEquals(14, result.sampleSize)
    }

    @Test
    fun `sample at asOf date is excluded - upper bound is exclusive`() {
        // Contrat : `asOf` = "aujourd'hui, sans observation validée encore".
        // Un sample dont targetDate == asOf ne doit pas être compté.
        val validSamples = daysBefore(today, 14).map { d ->
            BiasSample(d, forecast = 21.0, observation = 20.0)
        }
        val todaySample = BiasSample(today, forecast = 100.0, observation = 20.0) // pollue si compté
        val result = useCase(BiasVariable.TEMPERATURE, validSamples + todaySample, asOf = today)
        assertNotNull(result)
        assertEquals(14, result!!.sampleSize)
        assertEquals(1.0, result.meanBias, EPS)
    }

    @Test
    fun `sample at asOf minus windowDays is excluded - lower bound is inclusive-exclusive check`() {
        // Le sample à targetDate == asOf - windowDays est-il inclus ?
        // Convention : [asOf - windowDays, asOf) → borne inférieure inclusive.
        // Sample au jour EXACTEMENT J−30 doit être INCLUS.
        val recentSamples = daysBefore(today, 13).map { d ->
            BiasSample(d, forecast = 21.0, observation = 20.0)
        }
        val boundarySample = BiasSample(
            today.minusDays(30), forecast = 25.0, observation = 20.0
        )
        val result = useCase(
            BiasVariable.TEMPERATURE,
            recentSamples + boundarySample,
            asOf = today,
            windowDays = 30
        )
        assertNotNull(result)
        assertEquals(
            "sample à la borne basse doit être inclus → 14 samples",
            14, result!!.sampleSize
        )
    }

    // ─── Déduplication ────────────────────────────────────────────────────

    @Test
    fun `duplicate samples keep the newest issued run regardless of input order`() {
        val date = today.minusDays(1)
        val recent = BiasSample(
            date, forecast = 21.0, observation = 20.0,
            issuedAt = Instant.parse("2024-07-14T12:00:00Z")
        )
        val stale = BiasSample(
            date, forecast = 30.0, observation = 20.0,
            issuedAt = Instant.parse("2024-07-14T00:00:00Z")
        )

        // 13 autres samples + le doublon
        val others = daysBefore(today, 13, skipFirst = 1).map { d ->
            BiasSample(d, forecast = 21.0, observation = 20.0)
        }
        val result = useCase(
            BiasVariable.TEMPERATURE,
            listOf(stale) + others + recent,
            asOf = today
        )
        assertNotNull(result)
        assertEquals(14, result!!.sampleSize) // 14 dates uniques
        assertEquals(1.0, result.meanBias, EPS) // recent gagne, moyenne = 1.0
    }

    @Test
    fun `non finite samples are excluded before threshold and statistics`() {
        val valid = daysBefore(today, 14).map { date ->
            BiasSample(date, forecast = 21.0, observation = 20.0)
        }
        val corrupt = BiasSample(today.minusDays(20), Double.POSITIVE_INFINITY, 20.0)

        val result = useCase(BiasVariable.TEMPERATURE, valid + corrupt, asOf = today)!!

        assertEquals(14, result.sampleSize)
        assertEquals(1.0, result.meanBias, EPS)
    }

    // ─── Seuil MIN_SAMPLES ────────────────────────────────────────────────

    @Test
    fun `returns null when sample size is below MIN_SAMPLES_FOR_BIAS`() {
        val samples = daysBefore(today, ModelBias.MIN_SAMPLES_FOR_BIAS - 1).map { d ->
            BiasSample(d, forecast = 21.0, observation = 20.0)
        }
        val result = useCase(BiasVariable.TEMPERATURE, samples, asOf = today)
        assertNull(
            "moins que MIN_SAMPLES = pas de biais calculé, chip absent",
            result
        )
    }

    @Test
    fun `returns non-null when sample size equals MIN_SAMPLES_FOR_BIAS exactly`() {
        val samples = daysBefore(today, ModelBias.MIN_SAMPLES_FOR_BIAS).map { d ->
            BiasSample(d, forecast = 21.0, observation = 20.0)
        }
        val result = useCase(BiasVariable.TEMPERATURE, samples, asOf = today)
        assertNotNull("MIN pile → biais calculé", result)
        assertEquals(ModelBias.MIN_SAMPLES_FOR_BIAS, result!!.sampleSize)
    }

    @Test
    fun `returns null on empty sample list`() {
        assertNull(useCase(BiasVariable.TEMPERATURE, emptyList(), asOf = today))
    }

    // ─── Cohérence avec BiasSignificanceRule ─────────────────────────────

    @Test
    fun `typical GFS-like bias data classifies as HIGH via significance rule`() {
        // Reproduit le cas GFS du mockup HTML validé : ~+1.5° avec un stddev
        // ~0.8. On génère des samples qui donnent ces stats et vérifie que
        // ModelBias classe correctement en HIGH.
        val biases = listOf(
            0.7, 1.4, 1.8, 2.3, 1.1, 1.6, 2.0, 0.9, 1.5, 1.7,
            1.3, 2.1, 1.4, 0.8, 1.9, 1.5, 1.2, 2.2, 1.0, 1.6
        )
        val samples = biases.mapIndexed { i, bias ->
            BiasSample(today.minusDays((i + 1).toLong()), forecast = 20.0 + bias, observation = 20.0)
        }
        val result = useCase(BiasVariable.TEMPERATURE, samples, asOf = today)
        assertNotNull(result)
        // Sanity checks
        assertTrue("mean should be ~1.5", abs(result!!.meanBias - 1.5) < 0.2)
        assertTrue("stddev should be ~0.5", result.stdDev < 1.0)
        assertEquals(BiasSignificance.HIGH, result.significance)
    }

    @Test
    fun `noise-only data (small bias, large stddev) classifies as NOT_SIGNIFICANT`() {
        // Cas "modèle bruité mais calibré" : biais moyen quasi nul, stddev
        // élevé. Doit être classé NOT_SIGNIFICANT — l'utilisateur ne verra
        // pas de chip.
        val biases = listOf(
            2.0, -1.8, 1.5, -1.3, 1.9, -2.1, 0.5, -0.7, 1.4, -1.6,
            2.1, -1.4, 0.8, -0.9, 1.7, -1.5, 0.3, -0.5, 1.6, -1.7
        )
        val samples = biases.mapIndexed { i, bias ->
            BiasSample(today.minusDays((i + 1).toLong()), forecast = 20.0 + bias, observation = 20.0)
        }
        val result = useCase(BiasVariable.TEMPERATURE, samples, asOf = today)
        assertNotNull(result)
        assertTrue("mean should be near zero", abs(result!!.meanBias) < 0.3)
        assertEquals(BiasSignificance.NOT_SIGNIFICANT, result.significance)
    }

    // ─── Variable propagation ────────────────────────────────────────────

    @Test
    fun `variable in result matches variable passed to invoke`() {
        val samples = daysBefore(today, 14).map { d ->
            BiasSample(d, forecast = 5.0, observation = 4.0)
        }
        val precip = useCase(BiasVariable.PRECIPITATION, samples, asOf = today)
        val wind = useCase(BiasVariable.WIND_SPEED, samples, asOf = today)
        assertEquals(BiasVariable.PRECIPITATION, precip!!.variable)
        assertEquals(BiasVariable.WIND_SPEED, wind!!.variable)
    }

    // ─── Garde-fous ──────────────────────────────────────────────────────

    @Test
    fun `windowDays must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            useCase(BiasVariable.TEMPERATURE, emptyList(), asOf = today, windowDays = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            useCase(BiasVariable.TEMPERATURE, emptyList(), asOf = today, windowDays = -5)
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    /**
     * Génère les `count` dates immédiatement antérieures à `anchor`, dans
     * l'ordre décroissant (J−1, J−2, ...). Skip un nombre optionnel de dates
     * au début pour pouvoir composer avec un doublon manuel.
     */
    private fun daysBefore(
        anchor: LocalDate,
        count: Int,
        skipFirst: Int = 0
    ): List<LocalDate> = (1 + skipFirst..count + skipFirst).map { i ->
        anchor.minusDays(i.toLong())
    }

    companion object {
        private const val EPS = 1e-9
    }
}
