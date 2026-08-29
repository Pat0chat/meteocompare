package com.meteocompare.app.domain.model

import java.time.Instant

/** Niveau officiel de Vigilance Météo-France. */
enum class VigilanceColor(val id: Int) {
    GREEN(1),
    YELLOW(2),
    ORANGE(3),
    RED(4);

    companion object {
        fun fromId(id: Int?): VigilanceColor? = entries.firstOrNull { it.id == id }
    }
}

/** Phénomènes normalisés du flux Vigilance Météo-France. */
enum class VigilancePhenomenon(val id: String) {
    WIND("1"),
    RAIN_FLOOD("2"),
    THUNDERSTORMS("3"),
    FLOODS("4"),
    SNOW_ICE("5"),
    HEATWAVE("6"),
    EXTREME_COLD("7"),
    AVALANCHES("8"),
    COASTAL_FLOODING("9"),
    UNKNOWN("?");

    companion object {
        fun fromId(id: String): VigilancePhenomenon = entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}

enum class VigilanceScope {
    DEPARTMENT,
    COAST,
    UNKNOWN
}

data class VigilanceInterval(
    val begin: Instant?,
    val end: Instant?,
    val color: VigilanceColor,
    val scope: VigilanceScope,
    val timingApproximate: Boolean = false
)

data class VigilancePhenomenonAlert(
    val phenomenon: VigilancePhenomenon,
    val maxColor: VigilanceColor,
    val intervals: List<VigilanceInterval>
)

data class VigilancePeriod(
    val term: String?,
    val begin: Instant?,
    val end: Instant?,
    val maxColor: VigilanceColor,
    val departmentMaxColor: VigilanceColor?,
    val coastMaxColor: VigilanceColor?,
    val phenomena: List<VigilancePhenomenonAlert>
)

data class VigilanceForecast(
    val source: String,
    val department: String,
    val includeCoast: Boolean,
    val updateTime: Instant?,
    val productDatetime: Instant?,
    val generationTimestamp: Instant?,
    val periods: List<VigilancePeriod>,
    val fetchedAt: Instant,
    val isStale: Boolean = false,
    /** Heure utilisée pour évaluer les créneaux encore actifs, distincte de la fraîcheur du cache. */
    val evaluationTime: Instant = fetchedAt
) {
    /**
     * Alertes jaune/orange/rouge encore en cours ou à venir au moment de la récupération.
     *
     * Le `maxColorId` du produit peut représenter le maximum de toute une période, y compris
     * un épisode déjà terminé. Le niveau affiché est donc recalculé depuis les intervalles non
     * expirés pour ne jamais conserver artificiellement un ancien orange/rouge.
     */
    val activeAlerts: List<VigilancePhenomenonAlert>
        get() = periods
            .flatMap(VigilancePeriod::phenomena)
            .mapNotNull { alert ->
                val futureIntervals = alert.intervals
                    .filter { interval ->
                        interval.color.id >= VigilanceColor.YELLOW.id &&
                            (interval.end == null || interval.end.isAfter(evaluationTime))
                    }
                    .sortedBy { it.begin }

                when {
                    futureIntervals.isNotEmpty() -> alert.copy(
                        maxColor = futureIntervals.maxBy { it.color.id }.color,
                        intervals = futureIntervals
                    )
                    alert.intervals.isEmpty() && alert.maxColor.id >= VigilanceColor.YELLOW.id -> alert
                    else -> null
                }
            }
            .groupBy(VigilancePhenomenonAlert::phenomenon)
            .map { (phenomenon, alerts) ->
                VigilancePhenomenonAlert(
                    phenomenon = phenomenon,
                    maxColor = alerts.maxBy { it.maxColor.id }.maxColor,
                    intervals = alerts.flatMap { it.intervals }.sortedBy { it.begin }
                )
            }
            .sortedWith(
                compareByDescending<VigilancePhenomenonAlert> { it.maxColor.id }
                    .thenBy { alert -> alert.intervals.firstOrNull()?.begin ?: Instant.MAX }
            )

    val maxAlertColor: VigilanceColor?
        get() = activeAlerts.maxByOrNull { it.maxColor.id }?.maxColor

    val coastalFloodingAlert: VigilancePhenomenonAlert?
        get() = activeAlerts.firstOrNull { alert ->
            alert.phenomenon == VigilancePhenomenon.COASTAL_FLOODING &&
                alert.intervals.any { it.scope == VigilanceScope.COAST || includeCoast }
        }
}
