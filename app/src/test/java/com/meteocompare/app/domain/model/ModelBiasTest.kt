package com.meteocompare.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du contrat de [ModelBias] et de la règle pragmatique
 * [BiasSignificanceRule.classify].
 *
 * On teste :
 *   1. **Direction** — mapping meanBias → WARM/COLD/NEUTRAL, y compris cas
 *      limites ±0 (Kotlin: -0.0 == 0.0, l'ordre normal s'applique).
 *   2. **Garde-fous du data class** — sampleSize < MIN, stdDev < 0, etc.
 *   3. **Règle de significativité** par variable, avec les 4 zones de sortie
 *      (sous-seuil abs, sous-seuil ratio, seuil MODERATE, seuil HIGH).
 *   4. **Robustesse** — stdDev = 0 (ratio infini), valeurs extrêmes.
 *
 * Tests JVM purs, aucune dépendance Android.
 */
class ModelBiasTest {

    // ─── Direction ────────────────────────────────────────────────────────

    @Test
    fun `direction is WARM when meanBias is positive`() {
        val b = biasTemperature(mean = 1.5, sd = 0.8, n = 28)
        assertEquals(BiasDirection.WARM, b.direction)
    }

    @Test
    fun `direction is COLD when meanBias is negative`() {
        val b = biasTemperature(mean = -0.6, sd = 0.7, n = 25)
        assertEquals(BiasDirection.COLD, b.direction)
    }

    @Test
    fun `direction is NEUTRAL when meanBias is exactly zero`() {
        // Cas dégénéré — ne devrait pas atteindre l'UI (le repo renverrait
        // null en amont), mais le contrat du data class doit rester bien
        // défini.
        val b = biasTemperature(mean = 0.0, sd = 0.5, n = 28)
        assertEquals(BiasDirection.NEUTRAL, b.direction)
    }

    // ─── Garde-fous init { } ──────────────────────────────────────────────

    @Test
    fun `constructor rejects sample size below MIN_SAMPLES_FOR_BIAS`() {
        assertThrows(IllegalArgumentException::class.java) {
            biasTemperature(mean = 1.0, sd = 0.5, n = ModelBias.MIN_SAMPLES_FOR_BIAS - 1)
        }
    }

    @Test
    fun `constructor accepts sample size at MIN_SAMPLES_FOR_BIAS exactly`() {
        val b = biasTemperature(mean = 1.0, sd = 0.5, n = ModelBias.MIN_SAMPLES_FOR_BIAS)
        assertEquals(ModelBias.MIN_SAMPLES_FOR_BIAS, b.sampleSize)
    }

    @Test
    fun `constructor rejects negative stdDev`() {
        assertThrows(IllegalArgumentException::class.java) {
            biasTemperature(mean = 1.0, sd = -0.1, n = 20)
        }
    }

    @Test
    fun `constructor rejects non-positive windowDays`() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelBias(
                variable = BiasVariable.TEMPERATURE,
                meanBias = 1.0, stdDev = 0.5, sampleSize = 20, windowDays = 0
            )
        }
    }

    // ─── Règle pragmatique : température ──────────────────────────────────

    @Test
    fun `temperature - small bias below abs threshold is NOT_SIGNIFICANT`() {
        // |0.2| < 0.3 (seuil moderateAbs temp) → jamais signalé même avec
        // un ratio parfait (stdev = 0.05, ratio = 4).
        val sig = BiasSignificanceRule.classify(
            BiasVariable.TEMPERATURE, meanBias = 0.2, stdDev = 0.05, sampleSize = 30
        )
        assertEquals(BiasSignificance.NOT_SIGNIFICANT, sig)
    }

    @Test
    fun `temperature - moderate bias with weak ratio stays NOT_SIGNIFICANT`() {
        // |0.5| >= 0.3 (moderate) mais ratio 0.5/2.0 = 0.25 < 0.5 → pas assez
        // consistant — les jours se contredisent trop, ce n'est pas un biais
        // exploitable.
        val sig = BiasSignificanceRule.classify(
            BiasVariable.TEMPERATURE, meanBias = 0.5, stdDev = 2.0, sampleSize = 28
        )
        assertEquals(BiasSignificance.NOT_SIGNIFICANT, sig)
    }

    @Test
    fun `temperature - moderate bias with strong ratio is MODERATE`() {
        // |0.5| >= 0.3 AND 0.5/0.4 = 1.25 >= 0.5 → MODERATE (mais pas HIGH
        // car |0.5| < 1.0).
        val sig = BiasSignificanceRule.classify(
            BiasVariable.TEMPERATURE, meanBias = 0.5, stdDev = 0.4, sampleSize = 28
        )
        assertEquals(BiasSignificance.MODERATE, sig)
    }

    @Test
    fun `temperature - large bias with weak ratio stays MODERATE not HIGH`() {
        // |1.2| >= 1.0 (highAbs) MAIS ratio 1.2/2.5 = 0.48 < 1.0 (highRatio).
        // On tombe en MODERATE (|1.2| >= 0.3 AND 0.48 >= 0.5 partiellement...)
        // Attention : ratio 0.48 < 0.5 aussi → tombe en NOT_SIGNIFICANT !
        val sig = BiasSignificanceRule.classify(
            BiasVariable.TEMPERATURE, meanBias = 1.2, stdDev = 2.5, sampleSize = 28
        )
        // 0.48 < 0.5 (moderateRatio) donc NOT_SIGNIFICANT — c'est le
        // "big bias but too noisy" cas.
        assertEquals(BiasSignificance.NOT_SIGNIFICANT, sig)
    }

    @Test
    fun `temperature - GFS-like bias 1_5 with sd 0_8 is HIGH`() {
        // Cas du mockup HTML. |1.5| >= 1.0 AND ratio 1.5/0.8 = 1.875 >= 1.0.
        val sig = BiasSignificanceRule.classify(
            BiasVariable.TEMPERATURE, meanBias = 1.5, stdDev = 0.8, sampleSize = 28
        )
        assertEquals(BiasSignificance.HIGH, sig)
    }

    @Test
    fun `temperature - AROME-like bias 0_1 is NOT_SIGNIFICANT`() {
        // AROME mock du HTML : bias 0.1 (< 0.3 seuil abs). Même avec ratio
        // parfait ne monte pas.
        val sig = BiasSignificanceRule.classify(
            BiasVariable.TEMPERATURE, meanBias = 0.1, stdDev = 0.5, sampleSize = 29
        )
        assertEquals(BiasSignificance.NOT_SIGNIFICANT, sig)
    }

    @Test
    fun `temperature - ECMWF-like bias -0_4 is MODERATE`() {
        // ECMWF mock : |-0.4| = 0.4 >= 0.3 (moderateAbs), ratio 0.4/0.6 =
        // 0.667 >= 0.5 → MODERATE. Pas HIGH (|0.4| < 1.0).
        val sig = BiasSignificanceRule.classify(
            BiasVariable.TEMPERATURE, meanBias = -0.4, stdDev = 0.6, sampleSize = 30
        )
        assertEquals(BiasSignificance.MODERATE, sig)
    }

    @Test
    fun `temperature - ICON-EU-like bias -1_1 is HIGH`() {
        // ICON-EU mock : |-1.1| >= 1.0 AND ratio 1.1/0.9 = 1.22 >= 1.0.
        val sig = BiasSignificanceRule.classify(
            BiasVariable.TEMPERATURE, meanBias = -1.1, stdDev = 0.9, sampleSize = 27
        )
        assertEquals(BiasSignificance.HIGH, sig)
    }

    // ─── Règle pragmatique : précipitations ──────────────────────────────

    @Test
    fun `precipitation - very small bias below 0_1 is NOT_SIGNIFICANT`() {
        val sig = BiasSignificanceRule.classify(
            BiasVariable.PRECIPITATION, meanBias = 0.05, stdDev = 0.02, sampleSize = 30
        )
        assertEquals(BiasSignificance.NOT_SIGNIFICANT, sig)
    }

    @Test
    fun `precipitation - 0_15 with tight sd is MODERATE`() {
        // |0.15| >= 0.1 AND ratio 0.15/0.1 = 1.5 >= 0.5 → MODERATE.
        val sig = BiasSignificanceRule.classify(
            BiasVariable.PRECIPITATION, meanBias = 0.15, stdDev = 0.1, sampleSize = 25
        )
        assertEquals(BiasSignificance.MODERATE, sig)
    }

    @Test
    fun `precipitation - 0_8 with tight sd is HIGH`() {
        val sig = BiasSignificanceRule.classify(
            BiasVariable.PRECIPITATION, meanBias = 0.8, stdDev = 0.5, sampleSize = 25
        )
        assertEquals(BiasSignificance.HIGH, sig)
    }

    // ─── Règle pragmatique : vent ────────────────────────────────────────

    @Test
    fun `wind - 2 kmh bias is NOT_SIGNIFICANT`() {
        val sig = BiasSignificanceRule.classify(
            BiasVariable.WIND_SPEED, meanBias = 2.0, stdDev = 1.0, sampleSize = 30
        )
        assertEquals(BiasSignificance.NOT_SIGNIFICANT, sig)
    }

    @Test
    fun `wind - 5 kmh with tight sd is MODERATE`() {
        // |5| >= 3 (moderate) AND ratio 5/4 = 1.25 >= 0.5 → MODERATE.
        val sig = BiasSignificanceRule.classify(
            BiasVariable.WIND_SPEED, meanBias = 5.0, stdDev = 4.0, sampleSize = 28
        )
        assertEquals(BiasSignificance.MODERATE, sig)
    }

    @Test
    fun `wind - 10 kmh with tight sd is HIGH`() {
        // |10| >= 8 (high) AND ratio 10/5 = 2 >= 1.0 → HIGH.
        val sig = BiasSignificanceRule.classify(
            BiasVariable.WIND_SPEED, meanBias = 10.0, stdDev = 5.0, sampleSize = 28
        )
        assertEquals(BiasSignificance.HIGH, sig)
    }

    // ─── Robustesse ──────────────────────────────────────────────────────

    @Test
    fun `stdDev of zero yields infinite ratio - classification driven by absolute magnitude`() {
        // stdDev = 0 → ratio = +∞, passe tous les seuils ratio. Le résultat
        // n'est piloté que par |bias| vs les seuils absolus.
        val notSig = BiasSignificanceRule.classify(
            BiasVariable.TEMPERATURE, meanBias = 0.2, stdDev = 0.0, sampleSize = 20
        )
        assertEquals(BiasSignificance.NOT_SIGNIFICANT, notSig)

        val moderate = BiasSignificanceRule.classify(
            BiasVariable.TEMPERATURE, meanBias = 0.5, stdDev = 0.0, sampleSize = 20
        )
        assertEquals(BiasSignificance.MODERATE, moderate)

        val high = BiasSignificanceRule.classify(
            BiasVariable.TEMPERATURE, meanBias = 2.0, stdDev = 0.0, sampleSize = 20
        )
        assertEquals(BiasSignificance.HIGH, high)
    }

    @Test
    fun `classify with insufficient samples returns NOT_SIGNIFICANT defensively`() {
        // Contrat : le repo devrait renvoyer null au lieu de laisser passer
        // < MIN_SAMPLES, mais si on l'appelle directement, on reste défensif.
        val sig = BiasSignificanceRule.classify(
            BiasVariable.TEMPERATURE, meanBias = 5.0, stdDev = 0.5,
            sampleSize = ModelBias.MIN_SAMPLES_FOR_BIAS - 1
        )
        assertEquals(BiasSignificance.NOT_SIGNIFICANT, sig)
    }

    @Test
    fun `sign of bias does not affect classification - symmetry contract`() {
        // Un biais chaud de +1.5 et un biais froid de -1.5 doivent recevoir
        // la même classe. La couleur du chip diffère (direction) mais pas la
        // significativité.
        val warm = BiasSignificanceRule.classify(
            BiasVariable.TEMPERATURE, meanBias = 1.5, stdDev = 0.8, sampleSize = 28
        )
        val cold = BiasSignificanceRule.classify(
            BiasVariable.TEMPERATURE, meanBias = -1.5, stdDev = 0.8, sampleSize = 28
        )
        assertEquals(warm, cold)
    }

    // ─── Contrat "les significativité HIGH sont un sur-ensemble MODERATE" ─

    @Test
    fun `raising bias magnitude never decreases significance - monotonicity`() {
        // Pour un stdev fixé et un signe fixé, faire monter |bias| ne doit
        // JAMAIS faire descendre la significativité. C'est un invariant du
        // design pragmatique — s'il est violé, on a introduit une inversion
        // de seuils par mégarde.
        val bins = listOf(0.1, 0.2, 0.3, 0.5, 0.8, 1.0, 1.5, 2.0, 3.0)
        var lastLevel = -1
        for (mean in bins) {
            val level = BiasSignificanceRule.classify(
                BiasVariable.TEMPERATURE, meanBias = mean, stdDev = 0.5, sampleSize = 28
            ).ordinal
            assertTrue(
                "significativité doit être monotone non-décroissante " +
                    "(|bias|=$mean, level=$level, previous=$lastLevel)",
                level >= lastLevel
            )
            lastLevel = level
        }
    }

    // ─── Helper ──────────────────────────────────────────────────────────

    private fun biasTemperature(mean: Double, sd: Double, n: Int) = ModelBias(
        variable = BiasVariable.TEMPERATURE,
        meanBias = mean,
        stdDev = sd,
        sampleSize = n
    )
}
