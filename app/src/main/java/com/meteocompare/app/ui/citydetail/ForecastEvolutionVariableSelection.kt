package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.ForecastEvolutionVariable

/**
 * La variable préférée reste indépendante du jour affiché. Si elle est absente
 * pour ce jour, privilégier la température (indépendamment de l'ordre des
 * entrées de la carte), puis une autre variable disponible si nécessaire.
 * Le repli n'écrase jamais la préférence de l'utilisateur.
 */
internal fun displayedEvolutionVariable(
    preferredName: String,
    available: List<ForecastEvolutionVariable>
): ForecastEvolutionVariable? =
    available.firstOrNull { it.name == preferredName }
        ?: available.firstOrNull { it == ForecastEvolutionVariable.TEMPERATURE }
        ?: available.firstOrNull()
