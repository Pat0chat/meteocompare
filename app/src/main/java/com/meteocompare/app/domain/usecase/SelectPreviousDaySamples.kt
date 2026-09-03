package com.meteocompare.app.domain.usecase

import com.meteocompare.app.core.util.localDateIn
import com.meteocompare.app.domain.model.BiasSample
import java.time.Instant

/**
 * Sélectionne un échantillon comparable à échéance fixe par date cible.
 *
 * Seules les prévisions enregistrées [leadDay] jours civils avant [targetDate], dans
 * le fuseau de la ville, sont éligibles. Lorsqu'une ville a été rafraîchie
 * plusieurs fois cette veille, la dernière prévision est conservée. Cette
 * règle évite de mélanger dans un même score des horizons très différents et
 * exclut aussi les anciens backfills sans échéance de prévision homogène.
 */
fun selectPreviousDaySamples(
    samples: List<BiasSample>,
    timezone: String?,
    leadDay: Int = 1
): List<BiasSample> = samples
    .asSequence()
    .filter { sample ->
        sample.leadDay == leadDay && sample.issuedAt
            ?.localDateIn(timezone)
            ?.equals(sample.targetDate.minusDays(leadDay.toLong())) == true
    }
    .groupBy(BiasSample::targetDate)
    .values
    .mapNotNull { sameDate -> sameDate.maxByOrNull { it.issuedAt ?: Instant.MIN } }
    .sortedBy(BiasSample::targetDate)
