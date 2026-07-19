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

/** Padding vertical partagé par les layouts hauts et leurs contenus adaptatifs. */
internal fun forecastContainerVerticalPaddingDp(heightDp: Float): Float =
    when (forecastCardHeightProfile(heightDp)) {
        ForecastCardHeightProfile.DENSE -> 7f
        ForecastCardHeightProfile.COMPACT -> 9f
        ForecastCardHeightProfile.COMFORTABLE -> 11f
        ForecastCardHeightProfile.EXPANDED -> 13f
    }

/**
 * Respiration verticale autour du bitmap de la mini-prévision.
 *
 * Le profil 4×2 est volontairement un peu plus généreux : sur certains
 * launchers, l'arrondi en pixels de RemoteViews rognait visuellement la
 * première et la dernière ligne quand le bitmap remplissait exactement le
 * poids disponible.
 */
internal fun miniForecastContainerVerticalPaddingDp(
    profile: MiniForecastSizeProfile
): Float = when (profile) {
    MiniForecastSizeProfile.COMPACT_2X2 -> 3f
    MiniForecastSizeProfile.MEDIUM_3X2 -> 4f
    MiniForecastSizeProfile.EXPANDED_4X2 -> 5f
}

/**
 * Hauteur réaliste du bandeau météo placé au-dessus de la heatmap.
 *
 * À partir du 4×2, la ligne vent/humidité est présente : la pile de trois
 * textes devient alors plus haute que l'icône. L'ancien budget de 38/44 dp
 * sous-estimait cette hauteur et reportait le dépassement sur la heatmap.
 */
internal fun miniForecastHeaderHeightBudgetDp(
    compact: Boolean,
    showExtras: Boolean
): Float = when {
    showExtras && compact -> 46f
    showExtras -> 54f
    compact -> 38f
    else -> 44f
}
