package com.meteocompare.app.domain.model

/** Granularité persistante de la comparaison détaillée d'une ville. */
enum class CityDetailViewMode {
    HOURLY,
    DAILY;

    companion object {
        val DEFAULT: CityDetailViewMode = DAILY

        fun fromString(value: String?): CityDetailViewMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/**
 * Famille de données affichée dans la comparaison détaillée.
 * Une seule famille est rendue à la fois afin de garder la fiche courte.
 */
enum class CityDetailContentTab {
    CONDITIONS,
    TEMPERATURE,
    PRECIPITATION,
    WIND;

    companion object {
        val DEFAULT: CityDetailContentTab = CONDITIONS

        fun fromString(value: String?): CityDetailContentTab =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
