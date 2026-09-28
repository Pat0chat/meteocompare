package com.meteocompare.app.ui.citydetail

import androidx.compose.runtime.Immutable
import com.meteocompare.app.domain.model.ModelBias
import com.meteocompare.app.domain.model.WeatherModel

/**
 * Avancement de la collecte d'historique J+1 pour UNE variable, résumé pour
 * le bandeau d'information des prévisions détaillées.
 *
 * Un modèle est :
 *   - **prêt** quand son biais est calculable (≥ [ModelBias.MIN_SAMPLES_FOR_BIAS]
 *     jours comparés) ;
 *   - **en collecte** quand il a déjà quelques jours comparés ;
 *   - **sans historique** sinon (aucun jour encore, ou source sans archive
 *     Previous Runs). Ce dernier cas ne maintient pas le bandeau à lui seul :
 *     un modèle qui ne progresse pas n'a pas de promesse à tenir.
 *
 * @property bestSampleCount jours comparés du modèle en collecte le plus
 *   avancé, borné à `MIN_SAMPLES_FOR_BIAS - 1` pour ne jamais afficher « 14/14 »
 *   sur un modèle encore incomplet (doublons de dates dédupliqués ensuite).
 */
@Immutable
internal data class BiasHistoryProgress(
    val readyModels: Int,
    val collectingModels: Int,
    val totalModels: Int,
    val bestSampleCount: Int
) {
    /** Aucun modèle prêt : le bandeau explique la collecte. */
    val isPreparing: Boolean get() = readyModels == 0

    /**
     * Le bandeau reste utile tant qu'aucun modèle n'est prêt, ou tant qu'une
     * partie des modèles progresse encore. Il disparaît quand la collecte est
     * terminée pour tous les modèles qui peuvent l'être.
     */
    val shouldShowBanner: Boolean get() = totalModels > 0 && (isPreparing || collectingModels > 0)
}

/** Résume l'avancement de [models] pour cette variable. */
internal fun VariableBiasState.historyProgress(models: Collection<WeatherModel>): BiasHistoryProgress {
    var ready = 0
    var collecting = 0
    var best = 0
    models.forEach { model ->
        if (biasByModel[model] != null) {
            ready++
        } else {
            val samples = historyByModel[model]?.size ?: 0
            if (samples > 0) {
                collecting++
                best = maxOf(best, samples)
            }
        }
    }
    return BiasHistoryProgress(
        readyModels = ready,
        collectingModels = collecting,
        totalModels = models.size,
        bestSampleCount = best.coerceAtMost(ModelBias.MIN_SAMPLES_FOR_BIAS - 1)
    )
}
