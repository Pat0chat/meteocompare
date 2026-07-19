package com.meteocompare.app.widget

/** Profils visuels des cartes 5 heures / 5 jours selon leur hauteur utile. */
internal enum class ForecastCardHeightProfile {
    DENSE,
    COMPACT,
    COMFORTABLE,
    EXPANDED
}

/**
 * Densité verticale des widgets sur une seule rangée (1×1 à 5×1).
 *
 * Les launchers ne donnent pas tous la même hauteur à une cellule. Les profils
 * évitent donc d'utiliser les tailles prévues pour ~90 dp dans un bandeau qui
 * ne fait parfois que 55–65 dp une fois les marges du host appliquées.
 */
internal enum class SingleRowWidgetHeightProfile {
    VERY_DENSE,
    DENSE,
    REGULAR
}


/** Densité des widgets sur deux rangées (3×2 à 5×2). */
internal enum class TwoRowWidgetSizeProfile {
    VERY_DENSE,
    COMPACT,
    REGULAR
}

internal fun twoRowWidgetSizeProfile(widthDp: Float, heightDp: Float): TwoRowWidgetSizeProfile = when {
    heightDp < 150f || widthDp < 260f -> TwoRowWidgetSizeProfile.VERY_DENSE
    heightDp < 185f || widthDp < MEDIUM_MAX_WIDTH_DP -> TwoRowWidgetSizeProfile.COMPACT
    else -> TwoRowWidgetSizeProfile.REGULAR
}

/** Budget du bandeau supérieur du 2×2, où quatre lignes peuvent cohabiter. */
internal fun compactTallHeaderHeightBudgetDp(narrow: Boolean): Float =
    if (narrow) 52f else 56f

/**
 * Classe la hauteur exacte exposée par Glance.
 *
 * Les seuils couvrent les différences importantes entre launchers : un widget
 * ×2 peut mesurer à peine 130 dp sur une grille compacte ou dépasser 230 dp
 * après redimensionnement manuel.
 */
internal fun forecastCardHeightProfile(heightDp: Float): ForecastCardHeightProfile = when {
    heightDp < 72f -> ForecastCardHeightProfile.DENSE
    heightDp < 102f -> ForecastCardHeightProfile.COMPACT
    heightDp < 142f -> ForecastCardHeightProfile.COMFORTABLE
    else -> ForecastCardHeightProfile.EXPANDED
}

/** Profil vertical des widgets horizontaux après mesure réelle du launcher. */
internal fun singleRowWidgetHeightProfile(heightDp: Float): SingleRowWidgetHeightProfile = when {
    heightDp < 68f -> SingleRowWidgetHeightProfile.VERY_DENSE
    heightDp < 84f -> SingleRowWidgetHeightProfile.DENSE
    else -> SingleRowWidgetHeightProfile.REGULAR
}

/**
 * Padding vertical du conteneur racine pour les widgets sur une rangée.
 *
 * Le padding historique (8–12 dp de chaque côté) pouvait consommer presque la
 * moitié d'un bandeau bas. Cette fonction réduit d'abord les marges, avant de
 * réduire les informations ou leur typographie.
 */
internal fun singleRowContainerVerticalPaddingDp(
    heightDp: Float,
    layoutKind: WidgetLayoutKind
): Float {
    val profile = singleRowWidgetHeightProfile(heightDp)
    return when (layoutKind) {
        WidgetLayoutKind.TINY -> if (profile == SingleRowWidgetHeightProfile.VERY_DENSE) 3f else 4f
        WidgetLayoutKind.SMALL -> when (profile) {
            SingleRowWidgetHeightProfile.VERY_DENSE -> 4f
            SingleRowWidgetHeightProfile.DENSE -> 6f
            SingleRowWidgetHeightProfile.REGULAR -> 8f
        }
        WidgetLayoutKind.MEDIUM -> when (profile) {
            SingleRowWidgetHeightProfile.VERY_DENSE -> 4f
            SingleRowWidgetHeightProfile.DENSE -> 7f
            SingleRowWidgetHeightProfile.REGULAR -> 10f
        }
        WidgetLayoutKind.LARGE,
        WidgetLayoutKind.WIDE -> when (profile) {
            SingleRowWidgetHeightProfile.VERY_DENSE -> 4f
            SingleRowWidgetHeightProfile.DENSE -> 8f
            SingleRowWidgetHeightProfile.REGULAR -> 12f
        }
        WidgetLayoutKind.COMPACT_TALL,
        WidgetLayoutKind.EXTRA_LARGE -> forecastContainerVerticalPaddingDp(heightDp)
    }
}

/**
 * Sur un 2×1 très bas, la ville est l'information la moins prioritaire.
 * On la conserve dès que le budget vertical permet trois lignes confortables.
 */
internal fun shouldShowCityInSmallWidget(widthDp: Float, heightDp: Float): Boolean =
    widthDp >= 142f && singleRowWidgetHeightProfile(heightDp) != SingleRowWidgetHeightProfile.VERY_DENSE

/**
 * Nombre de cartes horaires compactes dans un 5×1.
 *
 * Une largeur juste au-dessus du breakpoint 5×1 reste trop serrée pour deux
 * cartes + le résumé principal + le badge de confiance. On passe à deux cartes
 * uniquement quand le host offre une largeur réellement confortable.
 */
internal fun inlineForecastItemCount(widthDp: Float): Int = when {
    widthDp < WIDE_MIN_WIDTH_DP -> 0
    widthDp < 430f -> 1
    else -> 2
}

/** Padding vertical partagé par les layouts hauts et leurs contenus adaptatifs. */
internal fun forecastContainerVerticalPaddingDp(heightDp: Float): Float =
    when {
        heightDp < 145f -> 7f
        heightDp < 175f -> 9f
        heightDp < 215f -> 11f
        else -> 13f
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

/**
 * Hauteur réellement disponible pour une rangée de cartes dans un widget ×2.
 *
 * Contrairement à l'ancien code, le profil n'est plus choisi depuis la hauteur
 * TOTALE du widget mais depuis ce qui reste après le bandeau, l'espacement et
 * les paddings racine. Cela évite que des cartes « expanded » soient dessinées
 * dans une zone basse qui ne dispose en réalité que de 70–90 dp.
 */
internal fun forecastBottomStripAvailableHeightDp(
    widgetHeightDp: Float,
    headerHeightDp: Float,
    sectionGapDp: Float
): Float {
    val rootPadding = forecastContainerVerticalPaddingDp(widgetHeightDp) * 2f
    val hostRoundingSafety = when {
        widgetHeightDp < 150f -> 3f
        widgetHeightDp < 190f -> 4f
        else -> 5f
    }
    return (
        widgetHeightDp -
            rootPadding -
            headerHeightDp -
            sectionGapDp -
            hostRoundingSafety
        ).coerceAtLeast(1f)
}

internal fun forecastBottomCardHeightProfile(
    widgetHeightDp: Float,
    headerHeightDp: Float,
    sectionGapDp: Float
): ForecastCardHeightProfile = forecastCardHeightProfile(
    forecastBottomStripAvailableHeightDp(
        widgetHeightDp = widgetHeightDp,
        headerHeightDp = headerHeightDp,
        sectionGapDp = sectionGapDp
    )
)
