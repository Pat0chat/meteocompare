package com.meteocompare.app.widget

/**
 * Glance/RemoteViews limite chaque Row à dix enfants directs.
 * Une échéance complète (séparateur inclus) est encapsulée dans un Row enfant :
 * le Row parent contient exactement N enfants, au maximum cinq.
 */
internal const val MAX_GLANCE_FORECAST_TIMELINE_ITEMS = 5
internal fun forecastTimelineDirectChildren(itemCount: Int): Int =
    itemCount.coerceIn(0, MAX_GLANCE_FORECAST_TIMELINE_ITEMS)

/** Profils visuels des cartes 5 heures / 5 jours selon leur hauteur utile. */
internal enum class ForecastCardHeightProfile {
    DENSE,
    COMPACT,
    COMFORTABLE,
    EXPANDED
}

/** Ordre des quatre métriques : commun aux cinq heures et aux cinq jours. */
internal enum class WidgetForecastMetric { CLOUD, RAIN, SUNSHINE, CONVERGENCE }

internal fun forecastVisibleMetrics(
    cloud: Int?, rain: Int?, sunshineHours: Double?, confidence: Int?
): List<WidgetForecastMetric> = buildList {
    if (cloud != null) add(WidgetForecastMetric.CLOUD)
    if (rain != null) add(WidgetForecastMetric.RAIN)
    if (sunshineHours?.isFinite() == true && sunshineHours in 0.0..24.0) add(WidgetForecastMetric.SUNSHINE)
    if (confidence != null) add(WidgetForecastMetric.CONVERGENCE)
}

/**
 * Échelle réelle des quatre indicateurs de prévision. Les icônes sont des
 * glyphes, mais leur taille reste indépendante de la valeur chiffrée.
 * Le budget tient compte du libellé, de l'icône météo, de la température
 * et des paddings de la carte avant de répartir le reste sur les métriques.
 */
internal data class ForecastMetricLayout(
    val visible: List<WidgetForecastMetric>,
    val textSp: Float,
    val iconSp: Float,
    val columns: Int = 1
)

private fun forecastCardFixedContentHeightDp(profile: ForecastCardHeightProfile): Float = when (profile) {
    ForecastCardHeightProfile.DENSE -> 36f
    ForecastCardHeightProfile.COMPACT -> 50f
    ForecastCardHeightProfile.COMFORTABLE -> 64f
    ForecastCardHeightProfile.EXPANDED -> 76f
}

internal fun forecastMetricLayout(
    availableCardHeightDp: Float,
    profile: ForecastCardHeightProfile,
    metrics: List<WidgetForecastMetric>,
    textScale: Float = 1f
): ForecastMetricLayout {
    // Réserve prudente : en-tête, grand pictogramme météo et température.
    val safeScale = textScale.coerceIn(0.85f, 1.4f)
    val fixedContentDp = forecastCardFixedContentHeightDp(profile) * safeScale
    val availableForMetrics = (availableCardHeightDp - fixedContentDp).coerceAtLeast(0f)
    val rowCapacity = (availableForMetrics / (7f * safeScale)).toInt().coerceIn(0, 4)
    // Lorsque tout ne rentre pas, éviter les lignes tronquées. La pluie garde
    // une priorité élevée, sans changer l'ordre canonique des lignes retenues.
    val priority = listOf(
        WidgetForecastMetric.RAIN,
        WidgetForecastMetric.CLOUD,
        WidgetForecastMetric.CONVERGENCE,
        WidgetForecastMetric.SUNSHINE
    )
    val selected = if (metrics.size <= rowCapacity) metrics else {
        val kept = priority.filter { it in metrics }.take(rowCapacity).toSet()
        metrics.filter { it in kept }
    }
    val maximumSp = if (availableCardHeightDp >= 165f &&
        profile == ForecastCardHeightProfile.EXPANDED) 11.5f else 10f
    val valueSp = if (selected.isEmpty()) 5.5f else
        (availableForMetrics / selected.size / 1.28f).coerceIn(5.5f, maximumSp)
    return ForecastMetricLayout(
        visible = selected,
        textSp = valueSp,
        iconSp = (valueSp + 1.1f).coerceAtMost(maximumSp + 1.1f)
    )
}

/**
 * Forecast cards in 4x2 and 5x2 grids frequently have only ~65-80dp of
 * vertical room. The old four-line budget dropped SUNSHINE or CONVERGENCE,
 * even when both values were provided. Pair the compact secondary values in
 * two short, balanced rows *only if the real per-card width accommodates it*.
 *
 * This width-aware adapter deliberately leaves [forecastMetricLayout] intact
 * for all existing single-column and regression-corpus contracts.
 */
internal fun forecastAdaptiveMetricLayout(
    availableCardHeightDp: Float,
    availableCardWidthDp: Float,
    profile: ForecastCardHeightProfile,
    metrics: List<WidgetForecastMetric>,
    textScale: Float = 1f
): ForecastMetricLayout {
    val base = forecastMetricLayout(availableCardHeightDp, profile, metrics, textScale)
    if (base.visible.size == metrics.size) return base
    val scale = textScale.coerceIn(0.85f, 1.4f)
    // Account for card padding (4-7dp per side) and glyph+value lengths.
    val canPair = metrics.size >= 3 &&
        availableCardWidthDp >= 70f * scale &&
        availableCardHeightDp >= (36f + 18f) * scale
    if (!canPair) {
        // In one-column cards the last space is better spent on the two
        // high-value signals than on a redundant cloud-cover percentage.
        if (base.visible.size < metrics.size && base.visible.size >= 2) {
            val priority = listOf(WidgetForecastMetric.RAIN,
                WidgetForecastMetric.CONVERGENCE, WidgetForecastMetric.SUNSHINE,
                WidgetForecastMetric.CLOUD)
            val kept = priority.filter { it in metrics }.take(base.visible.size).toSet()
            return base.copy(visible = metrics.filter { it in kept })
        }
        return base
    }
    // Two pairs need less vertical space than four individual lines. Scale
    // with Android's real text percentage; do not squeeze 140%-size text into
    // columns where it cannot fit.
    val rows = (metrics.size + 1) / 2
    val spaceNeeded = (36f + rows * 9f) * scale
    if (availableCardHeightDp < spaceNeeded) return base
    return base.copy(
        visible = metrics,
        columns = 2,
        textSp = minOf(base.textSp, 8f),
        iconSp = minOf(base.iconSp, 8.7f)
    )
}

/**
 * Ne pas changer pour un profil plus haut si ses grandes icônes laisseraient
 * moins de place aux indicateurs que dans le profil précédent. Le resize
 * Nova ne doit pas faire DISPARAÎTRE une ligne lorsque le widget grandit.
 */
internal fun forecastFittingCardProfile(
    heightDp: Float,
    requested: ForecastCardHeightProfile,
    metricCount: Int,
    textScale: Float = 1f
): ForecastCardHeightProfile {
    val candidates = ForecastCardHeightProfile.entries.take(requested.ordinal + 1).asReversed()
    val safeScale = textScale.coerceIn(0.85f, 1.4f)
    val requiredRows = metricCount.coerceIn(0, 4) * 7f * safeScale
    return candidates.firstOrNull { candidate ->
        heightDp >= forecastCardFixedContentHeightDp(candidate) * safeScale + requiredRows
    } ?: ForecastCardHeightProfile.DENSE
}

/**
 * Les marges supplémentaires ne doivent pas activer une variante « grande »
 * qui n'a physiquement plus de place pour son en-tête. Les marges normales
 * conservent les seuils historiques, afin de ne pas dégrader les widgets
 * installés ; la marge grande peut retarder une transition.
 */
internal fun forecastEffectiveHeightForTransitions(
    widgetHeightDp: Float,
    appliedVerticalInsetDp: Float,
    normalVerticalInsetDp: Float
): Float = (widgetHeightDp - 2f * (appliedVerticalInsetDp - normalVerticalInsetDp)
    .coerceAtLeast(0f)).coerceAtLeast(0f)

/**
 * Nova and other launchers expose different cell widths. The inline cards
 * share a row with the city, current temperature and convergence badge; don't
 * assume that the whole widget width belongs to the forecasts.
 */
internal fun inlineForecastCardWidthDp(widgetWidthDp: Float, itemCount: Int): Float =
    ((widgetWidthDp - 280f).coerceAtLeast(48f) / itemCount.coerceAtLeast(1))
        .coerceIn(42f, 82f)

/**
 * Weather glyph for an actual forecast slot. Large Nova widgets previously
 * kept 32dp icons no matter how tall their forecast cells were. Grow only
 * when BOTH height and column width permit it, rather than scaling the whole
 * RemoteViews hierarchy (which can clip or blur on third-party launchers).
 */
internal fun forecastTimelineGlyphSizeDp(
    profile: ForecastCardHeightProfile,
    availableCardWidthDp: Float,
    availableCardHeightDp: Float,
    isDaily: Boolean = false,
    emphasized: Boolean = false
): Int {
    val base = when (profile) {
        ForecastCardHeightProfile.DENSE -> 16
        ForecastCardHeightProfile.COMPACT -> 22
        ForecastCardHeightProfile.COMFORTABLE -> 28
        ForecastCardHeightProfile.EXPANDED -> 32
    }
    val heightBonus = when (profile) {
        ForecastCardHeightProfile.DENSE -> 0
        ForecastCardHeightProfile.COMPACT -> ((availableCardHeightDp - 72f) / 14f).toInt().coerceIn(0, 2)
        ForecastCardHeightProfile.COMFORTABLE -> ((availableCardHeightDp - 102f) / 9f).toInt().coerceIn(0, 4)
        ForecastCardHeightProfile.EXPANDED -> ((availableCardHeightDp - 142f) / 6f).toInt().coerceIn(0, 12)
    }
    val dailyBonus = if (isDaily && profile != ForecastCardHeightProfile.DENSE) 2 else 0
    val emphasisBonus = if (emphasized && profile != ForecastCardHeightProfile.DENSE) 1 else 0
    val widthLimit = (availableCardWidthDp.coerceAtLeast(0f) * 0.55f).toInt().coerceAtLeast(16)
    return (base + heightBonus + dailyBonus + emphasisBonus).coerceAtMost(minOf(widthLimit, 46))
}

/** Un petit widget peut rester sur deux niveaux si le bandeau supérieur est simplifié. */
internal fun compactForecastHeader(widgetHeightDp: Float): Boolean = widgetHeightDp < 152f

/**
 * Keep a minimal two-line current-weather header even for the 120dp fallback.
 * One line for city and another for min/max plus a compact confidence pill
 * replaces the old icon + temperature only layout.
 */
internal const val COMPACT_FORECAST_HEADER_HEIGHT_DP = 36f
internal const val COMPACT_TALL_FORECAST_HEADER_HEIGHT_DP = 40f

/**
 * A wide-ish widget should not lose wind/rain/cloud just because its responsive
 * width preset is 305dp rather than the former 380dp. Put that context on a
 * separate full-width line only when the selected height can afford it.
 */
internal fun shouldShowForecastContextLine(
    widthDp: Float,
    heightDp: Float,
    showCurrentConditions: Boolean,
    hasExtras: Boolean,
    extrasAlreadyInline: Boolean
): Boolean = showCurrentConditions && hasExtras && !extrasAlreadyInline &&
    widthDp >= 285f && heightDp >= 190f

internal const val FORECAST_CONTEXT_LINE_HEIGHT_DP = 16f



/** 36 dp de contenu fixe + quatre lignes de 7 dp, au minimum. */
internal const val MIN_FOUR_METRIC_CARD_HEIGHT_DP = 64f

internal data class CompactTallHeaderLayout(
    val compact: Boolean,
    val showPanelHeader: Boolean,
    val headerBudgetDp: Float,
    val sectionGapDp: Float
)

/**
 * Transition des widgets 2×2 : n'agrandir le bandeau actuel ou l'en-tête
 * de convergence que si les quatre métriques restent lisibles en bas.
 * Sans ce garde-fou, la largeur Nova suffisait mais deux transitions à
 * 152/165 dp RETIRAIENT une ligne alors que la taille totale augmentait.
 */
internal fun compactTallHeaderLayout(
    widgetHeightDp: Float,
    rootVerticalInsetDp: Float,
    narrow: Boolean,
    showCurrentConditions: Boolean,
    footerHeightDp: Float = 0f
): CompactTallHeaderLayout {
    val effective = forecastEffectiveHeightForTransitions(
        (widgetHeightDp - footerHeightDp).coerceAtLeast(0f),
        rootVerticalInsetDp,
        forecastContainerVerticalPaddingDp(widgetHeightDp)
    )
    val fullHeaderBudget = if (showCurrentConditions) compactTallHeaderHeightBudgetDp(narrow) else 0f
    val fullSectionGap = if (showCurrentConditions) 6f else 0f
    val fullHeight = forecastBottomStripAvailableHeightDp(
        widgetHeightDp, fullHeaderBudget, fullSectionGap, rootVerticalInsetDp, footerHeightDp
    )
    val compact = showCurrentConditions && (
        compactForecastHeader(effective) || fullHeight < MIN_FOUR_METRIC_CARD_HEIGHT_DP
    )
    val headerBudget = if (!showCurrentConditions) 0f else if (compact) {
        COMPACT_TALL_FORECAST_HEADER_HEIGHT_DP
    } else fullHeaderBudget
    val gap = if (!showCurrentConditions) 0f else if (compact) 2f else fullSectionGap
    val availableWithPanel = forecastBottomStripAvailableHeightDp(
        widgetHeightDp,
        headerBudget + forecastPanelHeaderHeightDp(showHeader = true, compact = true),
        gap,
        rootVerticalInsetDp,
        footerHeightDp
    )
    return CompactTallHeaderLayout(
        compact = compact,
        showPanelHeader = !narrow && effective >= 165f &&
            availableWithPanel >= MIN_FOUR_METRIC_CARD_HEIGHT_DP,
        headerBudgetDp = headerBudget,
        sectionGapDp = gap
    )
}

/**
 * Densité verticale des widgets de faible hauteur.
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

/** Densité des widgets disposant de plus de hauteur. */
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

/** Budget du bandeau supérieur en disposition compacte verticale. */
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
 * Lorsque le widget est très bas, la ville est l'information la moins prioritaire.
 * On la conserve dès que le budget vertical permet trois lignes confortables.
 */
internal fun shouldShowCityInSmallWidget(widthDp: Float, heightDp: Float): Boolean =
    widthDp >= 142f && singleRowWidgetHeightProfile(heightDp) != SingleRowWidgetHeightProfile.VERY_DENSE

/**
 * Nombre de cartes horaires compactes dans un 5×1.
 *
 * Une largeur juste au-dessus du breakpoint 5×1 reste trop serrée pour deux
 * cartes + le résumé principal + le badge de convergence. On passe à deux cartes
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
        heightDp < 145f -> 5f
        heightDp < 175f -> 7f
        heightDp < 215f -> 9f
        else -> 10f
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
 * Budget vertical réservé uniquement à l'en-tête « convergence » des panneaux
 * de confiance. Les prévisions horaires/journalières n'ont plus de titre.
 */
internal fun forecastPanelHeaderHeightDp(showHeader: Boolean, compact: Boolean): Float = when {
    !showHeader -> 0f
    compact -> 14f
    else -> 18f
}

/**
 * The title row belongs to the *convergence* panel only. Hourly/daily
 * forecast cards have their own day/hour labels, so they never reserve
 * a generic title row (issue #39 on compact Nova launcher grids).
 */
internal fun forecastContentHeaderHeightDp(
    hasConfidencePanel: Boolean,
    showHeader: Boolean,
    compact: Boolean
): Float = forecastPanelHeaderHeightDp(showHeader && hasConfidencePanel, compact)

/**
 * L'en-tête de convergence n'est affiché que si la zone basse le permet.
 * Les cartes météo ordinaires utilisent directement la place disponible.
 */
internal fun shouldShowForecastPanelHeader(
    widgetHeightDp: Float,
    sizeProfile: TwoRowWidgetSizeProfile
): Boolean = sizeProfile != TwoRowWidgetSizeProfile.VERY_DENSE && widgetHeightDp >= 165f

/**
 * Hauteur réellement disponible pour une rangée de cartes dans un widget ×2.
 *
 * Contrairement à l'ancien code, le profil n'est plus choisi depuis la hauteur
 * TOTALE du widget mais depuis ce qui reste après le bandeau, l'espacement et
 * les paddings racine. Cela évite que des cartes « expanded » soient dessinées
 * dans une zone basse qui ne dispose en réalité que de 70–90 dp.
 */
/** Hauteur réservée à l'action de localisation, sans attribution météo. */
internal fun locationRefreshFooterHeightDp(showLocationRefresh: Boolean): Float =
    if (showLocationRefresh) 16f else 0f

internal fun forecastBottomStripAvailableHeightDp(
    widgetHeightDp: Float,
    headerHeightDp: Float,
    sectionGapDp: Float,
    rootVerticalPaddingDp: Float = forecastContainerVerticalPaddingDp(widgetHeightDp),
    footerHeightDp: Float = 0f
): Float {
    val rootPadding = rootVerticalPaddingDp * 2f
    // L'attribution est dans l'app, hors widget. Seul le raccourci de
    // rafraîchissement « Ma position » peut encore occuper le pied de widget.
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
            footerHeightDp -
            hostRoundingSafety
        ).coerceAtLeast(1f)
}
