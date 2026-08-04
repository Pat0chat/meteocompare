package com.meteocompare.app.ui.citydetail

import androidx.compose.runtime.Immutable
import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.ModelBias
import com.meteocompare.app.domain.model.WeatherModel

/**
 * État "suivi de biais" complet pour l'écran CityDetail. Regroupe les trois
 * variables trackées (température, précipitations, vent) sous une même
 * structure — le screen ne consomme que ce champ pour tout ce qui touche aux
 * chips et à la sheet de détail.
 *
 * ## Sourcing
 *
 * Produit par [CityDetailViewModel] en combinant :
 *   - Les samples historiques Room (`BiasSampleRepository.observeSamples`)
 *   - L'algo d'agrégation ([com.meteocompare.app.domain.usecase.ComputeBiasUseCase])
 *   - Le calcul d'un domain Y commun aux modèles pour chaque variable
 *
 * ## Consommation
 *
 * Trois usages côté screen :
 *   1. **Chips** — pour chaque `(variable, model)`, lire `biasByModel[model]`.
 *      `null` = pas de chip (pas assez de données OU biais non significatif —
 *      la deuxième condition est calculée dans le chip lui-même).
 *   2. **Tableau de fiabilité** — à l'ouverture de la sheet, utiliser
 *      `historyByModel` pour calculer MAE, RMSE, jours proches, tendance,
 *      rang local et référence multi-modèles.
 *   3. **Sparkline** — lire `historyByModel[model]` pour la variable
 *      sélectionnée. Contient les samples 30j dans l'ordre chronologique.
 *   4. **Axe Y du sparkline** — lire `yDomainMin` / `yDomainMax` pour la
 *      variable sélectionnée. Bornes calculées sur l'union de tous les
 *      modèles, permettant la comparaison visuelle inter-modèles.
 *
 * ## État initial et transitions
 *
 * L'état initial est [EMPTY] — tous les maps vides, tous les domains null.
 * Le screen fonctionne parfaitement dans cet état : les chips ne s'affichent
 * simplement pas. Aucun crash, aucun placeholder. Idéal pour le cold start
 * ou les premières semaines d'utilisation où l'historique se remplit.
 */
@Immutable
data class BiasScreenState(
    val temperature: VariableBiasState,
    val precipitation: VariableBiasState,
    val wind: VariableBiasState
) {
    companion object {
        val EMPTY = BiasScreenState(
            temperature = VariableBiasState.EMPTY,
            precipitation = VariableBiasState.EMPTY,
            wind = VariableBiasState.EMPTY
        )
    }
}

/**
 * État pour UNE variable (température, précipitations, ou vent).
 *
 * @property biasByModel biais agrégé par modèle. `null` pour un modèle donné
 *   = pas assez de données pour lui (< [ModelBias.MIN_SAMPLES_FOR_BIAS] samples
 *   après dédup). Le screen filtre côté chip sur significance != NOT_SIGNIFICANT.
 * @property historyByModel série 30j de samples par modèle, chronologique,
 *   dédupliquée par date (un point Previous Runs J+1 par jour). Utilisée
 *   par le sparkline et le tableau de fiabilité dans la sheet de détail.
 * @property yDomainMin borne inférieure de l'axe Y du sparkline pour CETTE
 *   variable. Physiquement clampée à 0 pour précip et vent, avec marge
 *   symétrique pour température. `null` si aucun sample n'est disponible.
 * @property yDomainMax borne supérieure symétrique.
 */
@Immutable
data class VariableBiasState(
    val biasByModel: Map<WeatherModel, ModelBias?>,
    val historyByModel: Map<WeatherModel, List<BiasSample>>,
    val yDomainMin: Double?,
    val yDomainMax: Double?
) {
    companion object {
        val EMPTY = VariableBiasState(
            biasByModel = emptyMap(),
            historyByModel = emptyMap(),
            yDomainMin = null,
            yDomainMax = null
        )
    }
}
