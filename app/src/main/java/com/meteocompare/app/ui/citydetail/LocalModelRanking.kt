package com.meteocompare.app.ui.citydetail

import androidx.compose.runtime.Immutable
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.ModelReliability
import com.meteocompare.app.domain.model.ModelReliabilityCalculator
import com.meteocompare.app.domain.model.WeatherModel

/** Une ligne du classement local pour une variable météo. */
@Immutable
internal data class LocalModelRankingEntry(
    val rank: Int,
    val model: WeatherModel,
    val reliability: ModelReliability
)

/** Classement d'une variable, calculé uniquement avec l'historique de la ville. */
@Immutable
internal data class LocalVariableRanking(
    val variable: BiasVariable,
    val entries: List<LocalModelRankingEntry>
) {
    val winner: LocalModelRankingEntry? get() = entries.firstOrNull()
}

/** Les trois classements locaux affichés dans la fiche ville et dans la sheet. */
@Immutable
internal data class LocalModelRankings(
    val temperature: LocalVariableRanking,
    val precipitation: LocalVariableRanking,
    val wind: LocalVariableRanking
) {
    fun forVariable(variable: BiasVariable): LocalVariableRanking = when (variable) {
        BiasVariable.TEMPERATURE -> temperature
        BiasVariable.PRECIPITATION -> precipitation
        BiasVariable.WIND_SPEED -> wind
    }

    val hasAnyRanking: Boolean
        get() = temperature.entries.isNotEmpty() ||
            precipitation.entries.isNotEmpty() ||
            wind.entries.isNotEmpty()

    val firstAvailableVariable: BiasVariable
        get() = when {
            temperature.entries.isNotEmpty() -> BiasVariable.TEMPERATURE
            precipitation.entries.isNotEmpty() -> BiasVariable.PRECIPITATION
            wind.entries.isNotEmpty() -> BiasVariable.WIND_SPEED
            else -> BiasVariable.TEMPERATURE
        }
}

/**
 * Construit les classements à partir des mêmes échantillons que le biais sheet.
 * Le tri suit le contrat du tableau de fiabilité : score décroissant, puis MAE
 * croissante et enfin nom du modèle pour stabiliser les égalités.
 */
internal fun buildLocalModelRankings(state: BiasScreenState): LocalModelRankings =
    LocalModelRankings(
        temperature = buildLocalVariableRanking(
            variable = BiasVariable.TEMPERATURE,
            state = state.temperature
        ),
        precipitation = buildLocalVariableRanking(
            variable = BiasVariable.PRECIPITATION,
            state = state.precipitation
        ),
        wind = buildLocalVariableRanking(
            variable = BiasVariable.WIND_SPEED,
            state = state.wind
        )
    )

private fun buildLocalVariableRanking(
    variable: BiasVariable,
    state: VariableBiasState
): LocalVariableRanking {
    val reliabilities = state.historyByModel.mapNotNull { (model, samples) ->
        val windowDays = state.biasByModel[model]?.windowDays ?: DEFAULT_RANKING_WINDOW_DAYS
        ModelReliabilityCalculator.compute(
            variable = variable,
            samples = samples,
            windowDays = windowDays
        )?.let { model to it }
    }

    val sorted = reliabilities.sortedWith(
        compareByDescending<Pair<WeatherModel, ModelReliability>> { it.second.score }
            .thenBy { it.second.meanAbsoluteError }
            .thenBy { it.first.displayName }
    )

    return LocalVariableRanking(
        variable = variable,
        entries = sorted.mapIndexed { index, (model, reliability) ->
            LocalModelRankingEntry(
                rank = index + 1,
                model = model,
                reliability = reliability
            )
        }
    )
}

private const val DEFAULT_RANKING_WINDOW_DAYS = 30

/**
 * Variable à ouvrir depuis le bouton global du bloc fiabilité.
 *
 * Lorsque l'onglet actif possède un graphique mais pas encore assez
 * d'historique pour un classement, on redirige vers la première variable qui
 * dispose réellement d'un classement au lieu d'ouvrir une sheet vide.
 */
internal fun rankingVariableFor(
    activeVariable: BiasVariable,
    rankings: LocalModelRankings
): BiasVariable =
    if (rankings.forVariable(activeVariable).entries.isNotEmpty()) {
        activeVariable
    } else {
        rankings.firstAvailableVariable
    }
