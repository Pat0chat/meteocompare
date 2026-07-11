package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.ModelBias
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Transforme une liste de [BiasSample] en un [ModelBias] agrégé, ou null si
 * les données ne sont pas suffisantes.
 *
 * Cœur mathématique du feature "suivi de biais". PURE : aucune I/O, aucun
 * accès Room, aucune dépendance Android. Testable en JVM brut.
 *
 * ## Contrat
 *
 * Entrée : liste hétérogène de samples pour UNE grandeur donnée (variable
 * fixée par l'appel — le use case ne dispatche pas). Peut contenir des
 * doublons de date (l'algo les dédupliquera en gardant le sample le plus
 * récent ; voir méthode privée `dedupByDate`).
 *
 * Sortie :
 *   - `null` si après dédup et filtrage sur fenêtre, `sampleSize < MIN`
 *     (voir [ModelBias.MIN_SAMPLES_FOR_BIAS], 14 jours). Convention produit :
 *     pas assez de recul → pas de chip affiché.
 *   - un [ModelBias] sinon, avec `meanBias` = moyenne signée des daily biases,
 *     `stdDev` = écart-type d'échantillon (non-biaisé, ddl = n−1).
 *
 * ## Choix statistiques
 *
 * **Moyenne arithmétique** plutôt que médiane : la médiane serait plus robuste
 * aux outliers, mais un modèle qui a un ÉNORME écart un jour (raté total sur
 * une convection) *doit* pénaliser sa moyenne — c'est l'info utile pour
 * l'utilisateur. La médiane cacherait ça.
 *
 * **Écart-type d'échantillon** (division par n−1) plutôt que population
 * (division par n) : on considère les N jours d'historique comme un
 * échantillon d'une population théorique "biais de ce modèle dans cette
 * ville", pas comme la population entière. Corrige le sous-estimation
 * naturelle du stddev sur petit échantillon.
 *
 * **Pas de pondération temporelle** en Phase 2a : chaque jour compte
 * identiquement. Une pondération `weight = 1/age` (jours plus récents plus
 * lourds) est envisageable en Phase 3 si l'usage montre que les biais dérivent
 * avec la saison — pas de raison de complexifier tant qu'on n'a pas la donnée.
 *
 * ## Complexité
 *
 * O(n) sur la liste d'entrée (n ≤ 30 jours en pratique). Pas de tri, pas
 * d'allocations superflues. Suffisamment léger pour tourner à chaque emission
 * du Flow sans passer sur un dispatcher I/O.
 *
 * @param variable la grandeur du biais (température, précip, vent). Portée
 *   par le [ModelBias] retourné.
 * @param samples la liste des observations couplées prévision/réalité pour
 *   cette variable, sur les [windowDays] jours précédant [asOf] (exclus).
 * @param asOf la "date de calcul" — les samples strictement dans la fenêtre
 *   `[asOf - windowDays, asOf)` sont conservés. Par défaut = today (LocalDate.now).
 *   Passer une valeur explicite en test pour la reproductibilité.
 * @param windowDays taille de la fenêtre glissante en jours. 30 par défaut
 *   (choix produit sweet spot fraîcheur/stabilité), paramétrable pour les
 *   tests et pour un futur réglage.
 */
@Singleton
class ComputeBiasUseCase @Inject constructor() {

    operator fun invoke(
        variable: BiasVariable,
        samples: List<BiasSample>,
        asOf: LocalDate = LocalDate.now(),
        windowDays: Int = 30
    ): ModelBias? {
        require(windowDays > 0) { "windowDays must be positive, got $windowDays" }

        // 1. Filtrer sur la fenêtre glissante [asOf - windowDays, asOf).
        //    `asOf` exclu : aujourd'hui n'a pas encore d'observation validée.
        val windowStart = asOf.minusDays(windowDays.toLong())
        val inWindow = samples.filter {
            it.targetDate >= windowStart && it.targetDate < asOf
        }

        // 2. Déduplication : plusieurs samples pour la même date peuvent
        //    exister si la même journée a été prévue puis re-prévue par
        //    plusieurs runs du modèle. Convention : garder le dernier
        //    fournisseur (comportement "last write wins" — l'ordre de la liste
        //    d'entrée fait foi, à charge du repo de trier chronologiquement
        //    par issued_at DESC avant appel).
        val deduped = dedupByDate(inWindow)

        // 3. Guard sample size.
        if (deduped.size < ModelBias.MIN_SAMPLES_FOR_BIAS) return null

        // 4. Statistiques.
        val biases = deduped.map { it.dailyBias }
        val meanBias = biases.average()
        val stdDev = sampleStdDev(biases, meanBias)

        return ModelBias(
            variable = variable,
            meanBias = meanBias,
            stdDev = stdDev,
            sampleSize = deduped.size,
            windowDays = windowDays
        )
    }

    /**
     * Garde un seul sample par date — le premier rencontré dans l'ordre de la
     * liste. Le repo est responsable d'ordonner ses samples par issued_at DESC
     * avant l'appel, de sorte que "premier rencontré" = "plus récent connu".
     */
    private fun dedupByDate(samples: List<BiasSample>): List<BiasSample> {
        val seen = mutableSetOf<LocalDate>()
        return samples.filter { seen.add(it.targetDate) }
    }

    /**
     * Écart-type d'échantillon (division par n−1). Correction de Bessel — sur
     * un petit échantillon, l'estimateur naïf sous-estime la vraie variance
     * de la population. Retourne 0 si n ≤ 1 (edge case défensif, en pratique
     * bloqué par le guard `size < MIN_SAMPLES_FOR_BIAS`).
     */
    private fun sampleStdDev(values: List<Double>, mean: Double): Double {
        if (values.size <= 1) return 0.0
        val sumSq = values.sumOf { v -> (v - mean) * (v - mean) }
        return sqrt(sumSq / (values.size - 1))
    }
}
