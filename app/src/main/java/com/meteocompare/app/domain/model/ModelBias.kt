package com.meteocompare.app.domain.model

import androidx.compose.runtime.Immutable
import kotlin.math.abs

/**
 * Variable météorologique sur laquelle un biais est calculé.
 *
 * Une même paire (ville, modèle) peut avoir un biais différent par variable :
 * un modèle peut être bien calibré en température mais surestimer la pluie.
 * D'où la clé (variable) systématiquement présente dans [ModelBias].
 */
enum class BiasVariable { TEMPERATURE, PRECIPITATION, WIND_SPEED }

/**
 * Sens du biais moyen — dérivé de [ModelBias.meanBias].
 *
 * WARM  = le modèle surestime (mean > 0).
 * COLD  = le modèle sous-estime (mean < 0).
 * NEUTRAL = mean strictement à zéro (rare — sample fini, uniquement en test).
 */
enum class BiasDirection { WARM, COLD, NEUTRAL }

/**
 * Classification "practique" de la significativité, calculée par
 * [BiasSignificanceRule]. Choix pragmatique (voir doc de la règle) plutôt que
 * t-stat rigoureuse.
 *
 * NOT_SIGNIFICANT = le biais n'est pas assez important pour mériter d'être
 *   signalé à l'utilisateur (soit trop petit en absolu, soit noyé dans la
 *   variance journalière). Convention UI : *pas de chip affiché*.
 * MODERATE = biais visible, à garder en tête. Chip affiché en teinte moyenne.
 * HIGH = biais large et consistant. Chip affiché en teinte pleine.
 */
enum class BiasSignificance { NOT_SIGNIFICANT, MODERATE, HIGH }

/**
 * Biais moyen d'un modèle sur une fenêtre glissante, pour UNE variable.
 *
 * Calculé côté domaine à partir de l'historique forecast × observation (voir
 * ComputeBiasUseCase, à venir Phase 2). Pré-calculé — *jamais* recalculé au
 * moment du render UI.
 *
 * Contrat d'existence :
 *   - `sampleSize >= MIN_SAMPLES_FOR_BIAS` (voir compagnon), sinon l'objet
 *     n'est pas construit : le repo renvoie `null` et aucun chip n'apparaît.
 *   - `windowDays` par défaut à 30 (choix produit — cf. discussion : sweet
 *     spot entre stabilité statistique et fraîcheur saisonnière).
 *   - `stdDev >= 0`.
 *
 * Immutable + tous champs stables → Compose peut skip la recomposition d'un
 * chip dont le biais n'a pas changé. Important côté perf : ces objets vivent
 * dans un `StateFlow<Map<WeatherModel, ModelBias>>` et le mapping change à
 * chaque refresh — sans stabilité, tous les chips se recomposeraient à chaque
 * tick.
 *
 * @param variable la grandeur sur laquelle le biais est calculé.
 * @param meanBias moyenne signée de (forecast - observation) sur la fenêtre,
 *   dans l'unité de la variable (°C, mm/h, km/h).
 * @param stdDev écart-type des mêmes différences journalières. Sert au calcul
 *   de significativité (rapport bias/stdev — un biais consistant vs noise).
 * @param sampleSize nombre de jours de données effectivement disponibles dans
 *   la fenêtre (≤ [windowDays] car certains jours peuvent manquer).
 * @param windowDays taille de la fenêtre glissante en jours (30 par défaut).
 */
@Immutable
data class ModelBias(
    val variable: BiasVariable,
    val meanBias: Double,
    val stdDev: Double,
    val sampleSize: Int,
    val windowDays: Int = 30
) {
    init {
        require(stdDev >= 0.0) { "stdDev must be non-negative, got $stdDev" }
        require(sampleSize >= MIN_SAMPLES_FOR_BIAS) {
            "sampleSize $sampleSize below minimum $MIN_SAMPLES_FOR_BIAS — the repo " +
                "should return null instead of constructing a ModelBias"
        }
        require(windowDays > 0) { "windowDays must be positive, got $windowDays" }
    }

    /**
     * Sens du biais. Dérivé, mais mis en champ (non-computed) pour rester
     * `@Immutable` sans surprise et éviter les recalculs à chaque lecture.
     */
    val direction: BiasDirection = when {
        meanBias > 0.0 -> BiasDirection.WARM
        meanBias < 0.0 -> BiasDirection.COLD
        else           -> BiasDirection.NEUTRAL
    }

    /**
     * Classification pragmatique. Calculée à la construction — même argument
     * de stabilité que [direction].
     */
    val significance: BiasSignificance = BiasSignificanceRule.classify(
        variable = variable,
        meanBias = meanBias,
        stdDev = stdDev,
        sampleSize = sampleSize
    )

    companion object {
        /**
         * Seuil minimum de jours de données pour qu'un biais soit calculé.
         *
         * En dessous, le résultat est trop instable (une journée aberrante
         * bouge la moyenne de plusieurs °C). 14 jours = 2 semaines, compromis
         * entre "fraîcheur au démarrage de l'app" et "signal fiable".
         *
         * Convention produit : sous ce seuil, le repo renvoie `null` plutôt
         * qu'un [ModelBias] à faible confiance — l'absence de chip vaut mieux
         * qu'un chip trompeur.
         */
        const val MIN_SAMPLES_FOR_BIAS: Int = 14
    }
}

/**
 * Règle pragmatique de classification de significativité.
 *
 * Choix explicite d'écarter la t-stat classique (|bias| / (stdev / √n))
 * pour deux raisons produit :
 *
 * 1. La t-stat peut classer "hautement significatif" un biais de 0,2 °C si le
 *    stdev est ridiculement bas — statistiquement vrai, pratiquement inutile.
 *    L'utilisateur n'a pas besoin d'être alerté d'un biais qu'il ne percevra
 *    même pas au thermomètre.
 * 2. À l'inverse un vrai biais de 2 °C avec un stdev bruité (3 °C) peut être
 *    classé "non significatif" par la t-stat alors que l'utilisateur va
 *    vraiment lire 2 °C de trop en moyenne.
 *
 * Règle retenue : **|bias| absolu ET |bias|/stdev en ratio**, avec des seuils
 * calibrés PAR VARIABLE (les unités et magnitudes de bruit varient — 1 °C
 * n'est pas du tout la même chose que 1 mm/h ou 1 km/h). Deux conditions
 * doivent être remplies simultanément pour monter en gamme :
 *   - le biais doit être "gros" en absolu (pertinence pratique)
 *   - le biais doit être "consistant" par rapport au bruit (pertinence
 *     statistique)
 *
 * Seuils choisis empiriquement à partir de l'ordre de grandeur des biais
 * observés sur Open-Meteo / stations Météo-France (à réviser quand on aura
 * plus de données réelles Phase 3).
 */
object BiasSignificanceRule {

    /**
     * Seuils par variable — internal pour permettre aux tests d'assertion
     * dessus sans reproduire les constantes.
     *
     * @param moderateAbs seuil |bias| pour passer NOT_SIGNIFICANT → MODERATE
     * @param highAbs seuil |bias| pour passer MODERATE → HIGH
     * @param moderateRatio seuil |bias|/stdev pour valider MODERATE
     * @param highRatio seuil |bias|/stdev pour valider HIGH
     */
    internal data class Thresholds(
        val moderateAbs: Double,
        val highAbs: Double,
        val moderateRatio: Double,
        val highRatio: Double
    )

    // Températures : ordre °C. Seuil pratique "on sent la différence à
    // 0,3°" (thermorégulation humaine), "on doit corriger mentalement à 1°".
    internal val TEMPERATURE_THRESHOLDS = Thresholds(
        moderateAbs = 0.3, highAbs = 1.0,
        moderateRatio = 0.5, highRatio = 1.0
    )

    // Précipitations : ordre mm/h. Un biais de 0,1 mm/h sur 30j = 2,4 mm
    // cumulés/jour, déjà perceptible dans les totaux. 0,5 mm/h = très gros.
    internal val PRECIPITATION_THRESHOLDS = Thresholds(
        moderateAbs = 0.1, highAbs = 0.5,
        moderateRatio = 0.5, highRatio = 1.0
    )

    // Vent : ordre km/h. Un biais de 3 km/h est le "on sent que ça pousse
    // plus/moins" ressenti. 8 km/h = ça change la nature de l'événement
    // (petite brise → vent modéré, etc.).
    internal val WIND_THRESHOLDS = Thresholds(
        moderateAbs = 3.0, highAbs = 8.0,
        moderateRatio = 0.5, highRatio = 1.0
    )

    private fun thresholdsFor(variable: BiasVariable): Thresholds = when (variable) {
        BiasVariable.TEMPERATURE   -> TEMPERATURE_THRESHOLDS
        BiasVariable.PRECIPITATION -> PRECIPITATION_THRESHOLDS
        BiasVariable.WIND_SPEED    -> WIND_THRESHOLDS
    }

    /**
     * Classifie un biais.
     *
     * Cas particulier `stdDev == 0` (dégénéré, uniquement en test) : ratio
     * traité comme `+∞` (biais infiniment consistant) — le seul frein reste
     * alors la magnitude absolue.
     */
    fun classify(
        variable: BiasVariable,
        meanBias: Double,
        stdDev: Double,
        sampleSize: Int
    ): BiasSignificance {
        // Défense : sous le seuil minimum de samples, on refuse de classer.
        // Ce chemin ne devrait pas être atteint si le repo respecte le
        // contrat (renvoie null au lieu de construire), mais on reste
        // robuste.
        if (sampleSize < ModelBias.MIN_SAMPLES_FOR_BIAS) {
            return BiasSignificance.NOT_SIGNIFICANT
        }

        val t = thresholdsFor(variable)
        val absBias = abs(meanBias)
        // ratio consistance : bias vs bruit journalier. stdDev=0 → ratio
        // "infini" (Double.POSITIVE_INFINITY passe toutes les comparaisons).
        val ratio = if (stdDev > 0.0) absBias / stdDev else Double.POSITIVE_INFINITY

        return when {
            absBias >= t.highAbs && ratio >= t.highRatio -> BiasSignificance.HIGH
            absBias >= t.moderateAbs && ratio >= t.moderateRatio -> BiasSignificance.MODERATE
            else -> BiasSignificance.NOT_SIGNIFICANT
        }
    }
}
