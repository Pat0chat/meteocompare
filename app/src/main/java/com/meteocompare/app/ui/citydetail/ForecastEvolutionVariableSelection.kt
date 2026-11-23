package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.ForecastEvolutionVariable

/**
 * La variable préférée reste indépendante du jour affiché. Si elle est absente
 * pour ce jour, afficher une variable disponible sans modifier la préférence.
 */
internal fun displayedEvolutionVariable(
    preferredName: String,
    available: List<ForecastEvolutionVariable>
): ForecastEvolutionVariable? =
    available.firstOrNull { it.name == preferredName } ?: available.firstOrNull()
