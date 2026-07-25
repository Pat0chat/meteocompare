package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.HourlyConfidenceBand

/**
 * Retourne uniquement les métriques pour lesquelles la fiche peut présenter
 * au moins un signal réel : une bande de confiance exploitable ou un classement
 * local calculé. Cette logique reste hors de Compose pour être testable sans UI.
 */
internal fun availableReliabilityMetrics(
    rankings: LocalModelRankings,
    tempBands: List<HourlyConfidenceBand>,
    precipBands: List<HourlyConfidenceBand>,
    windBands: List<HourlyConfidenceBand>
): List<ConfidenceMetric> = buildList {
    if (tempBands.size >= 2 || rankings.temperature.entries.isNotEmpty()) {
        add(ConfidenceMetric.TEMPERATURE)
    }
    if (precipBands.size >= 2 || rankings.precipitation.entries.isNotEmpty()) {
        add(ConfidenceMetric.PRECIPITATION)
    }
    if (windBands.size >= 2 || rankings.wind.entries.isNotEmpty()) {
        add(ConfidenceMetric.WIND)
    }
}
