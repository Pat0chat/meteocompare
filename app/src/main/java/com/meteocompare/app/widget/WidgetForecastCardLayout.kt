package com.meteocompare.app.widget

/** Profils visuels des cartes 5 heures / 5 jours selon la hauteur du widget. */
internal enum class ForecastCardHeightProfile {
    DENSE,
    COMPACT,
    COMFORTABLE,
    EXPANDED
}

/**
 * Classe la hauteur exacte exposée par Glance.
 *
 * Les seuils couvrent les différences importantes entre launchers : un widget
 * ×2 peut mesurer à peine 130 dp sur une grille compacte ou dépasser 230 dp
 * après redimensionnement manuel.
 */
internal fun forecastCardHeightProfile(heightDp: Float): ForecastCardHeightProfile = when {
    heightDp < 145f -> ForecastCardHeightProfile.DENSE
    heightDp < 175f -> ForecastCardHeightProfile.COMPACT
    heightDp < 215f -> ForecastCardHeightProfile.COMFORTABLE
    else -> ForecastCardHeightProfile.EXPANDED
}
