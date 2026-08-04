package com.meteocompare.app.domain.usecase

import com.meteocompare.app.core.util.localDateIn
import com.meteocompare.app.domain.model.BiasSample
import java.time.Instant

/**
 * Sélectionne un échantillon comparable J+1 par date cible.
 *
 * Seules les prévisions enregistrées la veille civile de [targetDate], dans
 * le fuseau de la ville, sont éligibles. Lorsqu'une ville a été rafraîchie
 * plusieurs fois cette veille, la dernière prévision est conservée. Cette
 * règle évite de mélanger dans un même score des horizons très différents et
 * exclut aussi les anciens backfills historiques appris après coup.
 */
fun selectPreviousDaySamples(
    samples: List<BiasSample>,
    timezone: String?
): List<BiasSample> = samples
    .asSequence()
    .filter { sample ->
        sample.issuedAt
            ?.localDateIn(timezone)
            ?.equals(sample.targetDate.minusDays(1)) == true
    }
    .groupBy(BiasSample::targetDate)
    .values
    .mapNotNull { sameDate -> sameDate.maxByOrNull { it.issuedAt ?: Instant.MIN } }
    .sortedBy(BiasSample::targetDate)
