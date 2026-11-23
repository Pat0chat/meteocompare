package com.meteocompare.app.widget

import com.meteocompare.app.core.locale.WeatherTimeFormat

import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.core.units.LocalWeatherUnits

import android.content.Context
import android.util.Log
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.applyPersistedLocale
import com.meteocompare.app.core.locale.weatherConditionLabelRes
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.util.ConditionDecision
import com.meteocompare.app.ui.components.statusLabelRes
import kotlin.math.roundToInt

// One weather widget update must not ship 44 independent bitmap-backed
// RemoteViews to the launcher. Exact composes for the dimensions actually
// supplied by the host; layout families are selected via bounded breakpoints.
// This also lets the chart use the full height instead of a shorter preset.
private data class WidgetPadding(val horizontal: Dp, val vertical: Dp)

// Padding minimal pour les widgets très compacts.
private val TinyPadding = WidgetPadding(6.dp, 4.dp)
private val SmallPadding = WidgetPadding(8.dp, 8.dp)
private val MediumPadding = WidgetPadding(14.dp, 10.dp)
private val LargePadding = WidgetPadding(18.dp, 12.dp)
private val CompactTallPadding = WidgetPadding(8.dp, 9.dp)
private val ExtraLargePadding = WidgetPadding(10.dp, 9.dp)

// Espacement entre les prévisions intégrées à un bandeau horizontal.
// Les modes à cinq échéances utilisent plutôt des colonnes et séparateurs.
private val ForecastColumnSpacing = 5.dp

/**
 * Thème résolu (dark/light) — passé via [CompositionLocal] pour éviter que
 * chaque helper couleur ne recalcule `ctx.resources.configuration.uiMode` à
 * son tour. L'ancienne version appelait `isNightMode()` dans 6+ endroits par
 * render (chaque `onContainerColor()`, `onContainerColorMuted()`,
 * `resolveOnContainerColor()` faisait un accès Configuration + bit-and) — sur
 * un rendu étendu, cela multipliait les lectures par recomposition.
 *
 * Avec ce local, on lit Configuration UNE fois au top du composable et on
 * propage la valeur booléenne — un simple int en pratique. Le gain n'est pas
 * critique (Configuration read est bon marché) mais rend le code plus propre
 * et matche la façon dont GlanceTheme fonctionne en interne.
 */
private val LocalNightMode = staticCompositionLocalOf { false }

/**
 * Widget météo dont le contenu s'adapte à l'espace attribué par le launcher.
 * Selon la place disponible, il affiche les conditions actuelles, la confiance,
 * les extrêmes du jour et éventuellement des prévisions étendues.
 * La ville, la couleur et l’opacité se règlent via la configuration.
 *
 * ─── Pattern reactive state ─────────────────────────────────────────────
 * Les prefs sont lues via [currentState] INSIDE [provideContent], pas dans
 * le corps de [provideGlance]. Raison : [provideGlance] est exécutée UNE
 * fois par session Glance, mais son composable interne se recompose à chaque
 * changement d'état. Lire les prefs dehors capture la valeur au moment de la
 * session ; lire dedans avec [currentState] rend le read réactif dans la
 * session. Le host est ensuite notifié explicitement via `update()`.
 * Ce pattern règle "widget bloqué sur Configurer une ville" — auparavant le
 * composable utilisait la valeur cityId capturée à la 1re render, jamais
 * rafraîchie même après le save de la config.
 *
 * ─── Refresh tick ────────────────────────────────────────────────────────
 * [WidgetRefreshWorker] écrit un timestamp dans la clé [WidgetPreferences.RefreshTickKey]
 * pour signaler "il faut reconstruire l'affichage". Ce tick est inclus dans les clés du
 * `LaunchedEffect` ci-dessous : chaque incrément invalide l'effet et
 * re-déclenche `loadWidgetData`. Cette fonction recalcule les échéances avec
 * l'heure courante, puis laisse le repository respecter le seuil de fraîcheur
 * réseau défini dans les préférences utilisateur.
 */
internal class MeteoWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override val stateDefinition = PreferencesGlanceStateDefinition

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable
    ) {
        // Log the original exception: the default "Can't show content" view
        // hides the cause, making launcher-specific regressions impossible to
        // diagnose from a screenshot alone.
        Log.e("MeteoCompareWidget", "Weather Glance failed: id=$appWidgetId", throwable)
        super.onCompositionError(context, glanceId, appWidgetId, throwable)
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Enrobe l'app context avec la locale persistée AVANT de le fournir
        // aux composables. Deux étages de fix :
        //
        //   1. `loadWidgetData` reçoit ce context enrobé → les strings
        //      résolues à load time (metricLabel, day labels, "Auj.") sont
        //      dans la bonne langue.
        //   2. Le `LocalContext.current` de tous les composables descendants
        //      renvoie ce même context enrobé → les strings résolues à render
        //      time (ex. messages ErrorLayout via LocalContext.current.getString)
        //      sont aussi dans la bonne langue.
        //
        // Sans ce override, les widgets étaient TOUJOURS en langue système,
        // ignorant le réglage app — bug reporté sur les grands widgets avec
        // "Vent/Pluie" affichés même quand l'app est en anglais.
        val appCtx = applyPersistedLocale(context.applicationContext)
        provideContent {
            // Lecture des prefs Glance. Le worker met à jour le tick puis appelle
            // explicitement MeteoWidget.update() pour notifier le launcher.
            val prefs = currentState<Preferences>()
            val cityId = prefs[WidgetPreferences.CityIdKey]
            val opacityPct = (prefs[WidgetPreferences.OpacityPctKey]
                ?: WidgetPreferences.DEFAULT_OPACITY_PCT).coerceIn(0, 100)
            val showCurrentConditions = prefs[WidgetPreferences.ShowCurrentConditionsKey]
                ?: WidgetPreferences.DEFAULT_SHOW_CURRENT_CONDITIONS
            val forecastMode = prefs[WidgetPreferences.ForecastModeKey]
                ?.let { runCatching { ForecastMode.valueOf(it).normalized() }.getOrNull() }
                ?: WidgetPreferences.DEFAULT_FORECAST_MODE
            // Refresh tick écrit par le WidgetRefreshWorker. Utilisé comme clé
            // du LaunchedEffect pour re-déclencher loadWidgetData quand le
            // worker signale un besoin de refresh.
            val refreshTick = prefs[WidgetPreferences.RefreshTickKey] ?: 0L

            // Couleurs custom (nullables). null = comportement historique
            // (couleurs Material selon thème système). Non-null = override.
            // La logique de résolution (contraste auto pour le texte quand
            // absent mais fond custom présent) vit dans WidgetContent, pas
            // ici — provideGlance se contente de propager la valeur brute.
            val customBgArgb = prefs[WidgetPreferences.BackgroundColorKey]
            val customTextArgb = prefs[WidgetPreferences.TextColorKey]
            val cornerStyle = WidgetCornerStyle.fromStored(prefs[WidgetPreferences.CornerStyleKey])
            val customCornerRadiusDp = (prefs[WidgetPreferences.CornerRadiusDpKey]
                ?: WidgetPreferences.DEFAULT_CORNER_RADIUS_DP).coerceIn(0, WidgetPreferences.MAX_CORNER_RADIUS_DP)
            val contentSpacing = WidgetContentSpacing.fromStored(prefs[WidgetPreferences.ContentSpacingKey])
            val textSizePct = (prefs[WidgetPreferences.TextSizePctKey]
                ?: WidgetPreferences.DEFAULT_TEXT_SIZE_PCT)
            val autoTextSize = prefs[WidgetPreferences.AutoTextSizeKey]
                ?: WidgetPreferences.DEFAULT_AUTO_TEXT_SIZE
            // Chargement des données asynchrone. `remember` persiste la
            // dernière donnée bonne à travers les recompositions ; LaunchedEffect
            // re-fetch quand cityId/forecastMode/refreshTick change.
            // L'état initial est Loading (pas NotConfigured) pour éviter le
            // flash "Configurer" quand on recompose une ville déjà configurée.
            // A city/mode change must never display the previous city's
            // forecast while the new request is loading. Keep the last good
            // snapshot only for refresh ticks of the same city and mode.
            var data by remember(cityId, forecastMode) {
                mutableStateOf<WidgetData>(
                    if (cityId == null) WidgetData.NotConfigured else WidgetData.Loading
                )
            }
            LaunchedEffect(cityId, forecastMode, refreshTick) {
                data = sharedWidgetDataLoad.getOrLoad(
                    WidgetDataRequest(cityId, forecastMode, false, refreshTick,
                        appCtx.resources.configuration.locales.toLanguageTags())
                ) {
                    loadWidgetData(appCtx, cityId, forecastMode)
                }
            }

            // Résolution UNE fois du mode nuit et propagation via CompositionLocal.
            // Voir docblock de LocalNightMode pour le pourquoi.
            val night = appCtx.resources.configuration
                .let { (it.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES }
            // Override LocalContext avec l'app context localisé — voir le
            // docblock du override ci-dessus pour l'étage #2 du fix.
            CompositionLocalProvider(
                LocalContext provides appCtx,
                LocalNightMode provides night,
                LocalWeatherUnits provides WeatherUnits(data.unitSystem, data.customUnits),
                LocalWidgetTextScale provides widgetTextScale(
                    LocalSize.current.width.value, LocalSize.current.height.value,
                    autoTextSize, textSizePct
                )
            ) {
                GlanceTheme {
                    WidgetContent(
                        data = data,
                        isCurrentLocation = shouldShowWidgetLocationAction(cityId),
                        opacityPct = opacityPct,
                        customBgArgb = customBgArgb,
                        customTextArgb = customTextArgb,
                        cornerStyle = cornerStyle,
                        customCornerRadiusDp = customCornerRadiusDp,
                        contentSpacing = contentSpacing,
                        showCurrentConditions = showCurrentConditions
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Rendering
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun WidgetContent(
    data: WidgetData,
    isCurrentLocation: Boolean,
    opacityPct: Int,
    customBgArgb: Int?,
    customTextArgb: Int?,
    cornerStyle: WidgetCornerStyle,
    customCornerRadiusDp: Int,
    contentSpacing: WidgetContentSpacing,
    showCurrentConditions: Boolean
) {
    // Material 3 / Material Expressive : le mode Auto s'appuie directement
    // sur les rôles de couleur Glance. Sur Android 12+, ils suivent la palette
    // dynamique du fond d'écran ; sur les versions plus anciennes Glance
    // fournit la palette Material de repli. Les couleurs personnalisées restent
    // prioritaires et conservent le contrôle d'opacité historique.
    val context = LocalContext.current
    val night = LocalNightMode.current
    val themeColors = GlanceTheme.colors
    val useCustomPalette = customBgArgb != null || customTextArgb != null

    val dynamicContainer = themeColors.widgetBackground.getColor(context)
    val dynamicForeground = themeColors.onSurface.getColor(context)
    val dynamicMuted = themeColors.onSurfaceVariant.getColor(context)
    val dynamicSurface = themeColors.surfaceVariant.getColor(context)
    val dynamicRaised = themeColors.secondaryContainer.getColor(context)

    val baseContainerColor = remember(dynamicContainer, customBgArgb) {
        customBgArgb?.let { Color(it) } ?: dynamicContainer
    }
    val baseOnContainerColor = remember(dynamicForeground, customTextArgb, customBgArgb) {
        when {
            customTextArgb != null -> Color(customTextArgb)
            customBgArgb != null -> {
                if (Color(customBgArgb).luminance() > 0.5f) Color.Black else Color.White
            }
            else -> dynamicForeground
        }
    }
    val mutedColor = remember(dynamicMuted, baseOnContainerColor, useCustomPalette) {
        if (useCustomPalette) baseOnContainerColor.copy(alpha = 0.68f) else dynamicMuted
    }
    val softSurfaceColor = remember(dynamicSurface, baseOnContainerColor, night, useCustomPalette) {
        if (useCustomPalette) {
            baseOnContainerColor.copy(alpha = if (night) 0.10f else 0.075f)
        } else {
            // Une surface tonale suffisamment distincte pour structurer les
            // données sans recréer l'effet « carte dans la carte » daté.
            dynamicSurface.copy(alpha = if (night) 0.72f else 0.82f)
        }
    }
    val raisedSurfaceColor = remember(dynamicRaised, baseOnContainerColor, night, useCustomPalette) {
        if (useCustomPalette) {
            baseOnContainerColor.copy(alpha = if (night) 0.17f else 0.12f)
        } else {
            dynamicRaised.copy(alpha = if (night) 0.78f else 0.86f)
        }
    }

    val onContainer = remember(baseOnContainerColor) { ColorProvider(baseOnContainerColor) }
    val onContainerMuted = remember(mutedColor) { ColorProvider(mutedColor) }
    val softSurface = remember(softSurfaceColor) { ColorProvider(softSurfaceColor) }
    val raisedSurface = remember(raisedSurfaceColor) { ColorProvider(raisedSurfaceColor) }
    val container = remember(baseContainerColor, opacityPct) {
        ColorProvider(baseContainerColor.copy(alpha = opacityPct / 100f))
    }

    val size = LocalSize.current
    // La surface colorée occupe tout l'espace réellement alloué par le launcher.
    val widthDp = size.width.value
    val heightDp = size.height.value
    val launcherLayoutKind = classifyWidgetLayout(widthDp, heightDp)
    val padding = when (launcherLayoutKind) {
        WidgetLayoutKind.TINY -> WidgetPadding(
            horizontal = TinyPadding.horizontal,
            vertical = singleRowContainerVerticalPaddingDp(heightDp, launcherLayoutKind).dp
        )
        WidgetLayoutKind.COMPACT_TALL -> WidgetPadding(
            horizontal = CompactTallPadding.horizontal,
            vertical = forecastContainerVerticalPaddingDp(heightDp).dp
        )
        WidgetLayoutKind.EXTRA_LARGE -> WidgetPadding(
            horizontal = ExtraLargePadding.horizontal,
            vertical = forecastContainerVerticalPaddingDp(heightDp).dp
        )
        WidgetLayoutKind.MEDIUM -> WidgetPadding(
            horizontal = MediumPadding.horizontal,
            vertical = singleRowContainerVerticalPaddingDp(heightDp, launcherLayoutKind).dp
        )
        WidgetLayoutKind.LARGE,
        WidgetLayoutKind.WIDE -> WidgetPadding(
            horizontal = LargePadding.horizontal,
            vertical = singleRowContainerVerticalPaddingDp(heightDp, launcherLayoutKind).dp
        )
        WidgetLayoutKind.SMALL -> WidgetPadding(
            horizontal = SmallPadding.horizontal,
            vertical = singleRowContainerVerticalPaddingDp(heightDp, launcherLayoutKind).dp
        )
    }

    val (requestedHorizontal, requestedVertical) = widgetContentPadding(
        padding.horizontal, padding.vertical, contentSpacing
    )
    val (rootHorizontalInsetDp, rootVerticalInsetDp) = widgetShapeSafeContentPaddingDp(
        cornerStyle, widthDp, heightDp, requestedHorizontal.value, requestedVertical.value
    )

    val layoutKind = classifyWidgetLayoutForShape(
        widthDp, heightDp, cornerStyle, rootHorizontalInsetDp, rootVerticalInsetDp
    )
    val displayCurrentConditions = shouldDisplayCurrentConditions(layoutKind, showCurrentConditions)

    WidgetSurface(
        background = container,
        cornerStyle = cornerStyle,
        customCornerRadiusDp = customCornerRadiusDp,
        contentSpacing = contentSpacing,
        horizontalPadding = padding.horizontal,
        verticalPadding = padding.vertical
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                when {
                    data.error != null -> ErrorLayout(
                        error = data.error,
                        onContainer = onContainer,
                        onContainerMuted = onContainerMuted,
                        softSurface = softSurface
                    )
                    else -> when (layoutKind) {
                        WidgetLayoutKind.TINY -> TinyLayout(data, onContainer)
                        WidgetLayoutKind.EXTRA_LARGE -> ExtraLargeLayout(
                            data = data,
                            onContainer = onContainer,
                            onContainerMuted = onContainerMuted,
                            softSurface = softSurface,
                            raisedSurface = raisedSurface,
                            onContainerArgb = baseOnContainerColor.toArgb(),
                            rootHorizontalInsetDp = rootHorizontalInsetDp,
                            rootVerticalInsetDp = rootVerticalInsetDp,
                            showFiveItems = extendedForecastItemCount(
                                widthDp - 2f * rootHorizontalInsetDp
                            ) == 5,
                            showExtras = widthDp - 2f * rootHorizontalInsetDp >= MEDIUM_MAX_WIDTH_DP,
                            showCurrentConditions = displayCurrentConditions,
                            showLocationRefresh = isCurrentLocation
                        )
                        WidgetLayoutKind.COMPACT_TALL -> CompactTallLayout(
                            data = data,
                            onContainer = onContainer,
                            onContainerMuted = onContainerMuted,
                            softSurface = softSurface,
                            raisedSurface = raisedSurface,
                            onContainerArgb = baseOnContainerColor.toArgb(),
                            rootHorizontalInsetDp = rootHorizontalInsetDp,
                            rootVerticalInsetDp = rootVerticalInsetDp,
                            showCurrentConditions = displayCurrentConditions,
                            showLocationRefresh = isCurrentLocation
                        )
                        WidgetLayoutKind.WIDE -> LargeLayout(
                            data = data,
                            onContainer = onContainer,
                            onContainerMuted = onContainerMuted,
                            inlineForecastItems = inlineForecastItemCount(widthDp - 2f * rootHorizontalInsetDp),
                            rootVerticalInsetDp = rootVerticalInsetDp,
                            showLocationRefresh = isCurrentLocation
                        )
                        WidgetLayoutKind.LARGE -> LargeLayout(
                            data = data,
                            onContainer = onContainer,
                            onContainerMuted = onContainerMuted,
                            inlineForecastItems = 0,
                            rootVerticalInsetDp = rootVerticalInsetDp,
                            showLocationRefresh = isCurrentLocation
                        )
                        WidgetLayoutKind.MEDIUM -> MediumLayout(data, onContainer, onContainerMuted)
                        WidgetLayoutKind.SMALL -> if (widthDp < 105f && heightDp >= 105f) {
                            NarrowTallLayout(data, onContainer, onContainerMuted,
                                showCurrentConditions = showCurrentConditions)
                        } else {
                            SmallLayout(
                                data, onContainer,
                                allowCity = widthDp - 2f * rootHorizontalInsetDp >= 142f
                            )
                        }
                    }
                }
            }

            // Le crédit Open-Meteo reste consultable dans Paramètres > À propos.
            // Seule l'action « Ma position » requiert encore un pied de widget.
            if (isCurrentLocation) {
                WidgetCurrentLocationAction(
                    onContainerMuted,
                    compact = layoutKind == WidgetLayoutKind.TINY || layoutKind == WidgetLayoutKind.SMALL
                )
            }
        }
    }
}

/**
 * Layout 1×1 — icône | Column(temp, confidence%).
 *
 * ─── Contraintes de rendu ────────────────────────────────────────────────
 * Sur 40-90dp par côté, TOUT compte : chaque sp de font, chaque dp de
 * spacing. Design contraint à trois informations :
 *   - Icône météo (le "signal" primaire — "il fait quoi dehors ?")
 *   - Température (le "chiffre" — "combien ?")
 *   - Confiance % (le "signal éditorial" — c'est ce qui distingue notre
 *     widget d'un widget météo générique)
 *
 * La ville est OMISE : sur 1×1 il n'y a physiquement pas de place, et
 * l'utilisateur sait quelle ville il a choisie (le widget est le sien).
 * Le min/max, la couverture nuageuse, les prévisions étendues sont aussi
 * omis — les rendus compacts les couvrent déjà.
 *
 * ─── Choix de disposition ────────────────────────────────────────────────
 * Column verticale plutôt que Row horizontale : les cellules 1×1 sont
 * typiquement plus HAUTES que larges (les grilles Android modernes ont des
 * cellules ~70dp × 100dp). Empiler icône, temp, confidence en 3 lignes
 * remplit mieux la surface qu'une ligne horizontale qui écraserait les
 * éléments.
 *
 * Les tailles sont adaptatives : sous 70dp de haut (ou 60dp de large),
 * le pictogramme descend à 22dp et la typographie se resserre pour éviter
 * tout débordement sur les grilles de launchers les plus compactes.
 */
@Composable
private fun TinyLayout(data: WidgetData, onContainer: ColorProvider, units: WeatherUnits = LocalWeatherUnits.current) {
    val size = LocalSize.current
    val micro = size.height.value < 52f || size.width.value < 52f
    val dense = size.height.value < 72f || size.width.value < 64f
    val glyphSize = when {
        micro -> 15
        dense -> 20
        else -> 30
    }
    val tempSize = when {
        micro -> 9.sp
        dense -> 12.sp
        else -> 16.sp
    }

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WeatherGlyph(data.currentCondition, isNight = data.currentIsNight == true, sizeDp = glyphSize, decision = data.currentConditionDecision)
        Text(
            text = formatTemp(data.currentTemp, units = units),
            style = TextStyle(
                color = onContainer,
                fontSize = (tempSize).widgetScaled(),
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        if (!micro) {
            data.confidencePct?.let {
                Text(
                    text = "$it%",
                    style = TextStyle(
                        color = confidenceTextColor(it),
                        fontSize = (if (dense) 7.sp else 9.sp).widgetScaled(),
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/** A single launcher column can also be four cells tall. A horizontal 2x1
 * summary in this shape created a large blank region and clipped the city.
 * Stack the current weather with up to two upcoming forecasts. This is a
 * deliberately narrow text-only layout: it never attempts a five-column row.
 */
@Composable
private fun NarrowTallLayout(
    data: WidgetData,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    showCurrentConditions: Boolean,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val heightDp = LocalSize.current.height.value
    // Small one-row widgets always fall back to now; tall narrow widgets
    // can honour the user's "hide current conditions" preference.
    val showNow = showCurrentConditions || data.forecasts.isEmpty()
    val slots = when {
        heightDp >= 230f -> if (showNow) 2 else 3
        heightDp >= 140f -> if (showNow) 1 else 2
        else -> if (showNow) 0 else 1
    }
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showNow) {
            WeatherGlyph(
                data.currentCondition,
                isNight = data.currentIsNight == true,
                sizeDp = 23,
                decision = data.currentConditionDecision
            )
            Text(
                text = formatTemp(data.currentTemp, units = units),
                style = TextStyle(color = onContainer, fontSize = (16.sp).widgetScaled(),
                    fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            data.confidencePct?.let {
                Text(text = "$it%", style = TextStyle(color = confidenceTextColor(it),
                    fontSize = (9.sp).widgetScaled()), maxLines = 1)
            }
        }
        val items = data.forecasts.take(slots)
        if (items.isNotEmpty()) {
            Spacer(GlanceModifier.height(4.dp))
            items.forEach { item ->
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = item.label,
                    style = TextStyle(color = onContainerMuted, fontSize = (9.sp).widgetScaled()),
                    maxLines = 1
                )
                WeatherGlyph(item.condition, isNight = item.isNight == true, sizeDp = 19,
                    decision = item.conditionDecision)
                Text(
                    text = formatTemp(item.temp, units = units),
                    style = TextStyle(color = onContainer, fontSize = (12.sp).widgetScaled(),
                        fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                item.forecastConfidencePct?.let { score ->
                    Text(text = "${score.coerceIn(0, 100)}%",
                        style = TextStyle(color = confidenceTextColor(score),
                            fontSize = (8.sp).widgetScaled()), maxLines = 1)
                }
            }
        }
    }
}

/**
 * Rendu compact horizontal — icône | colonne(température, confiance).
 *
 * La CONFIANCE reste affichée sous la température. La ville est conservée sur
 * les cellules normales, puis masquée en priorité lorsque le launcher fournit
 * une hauteur très basse : température, condition et confiance gardent ainsi
 * la priorité sans rognage vertical.
 *
 * Le pourcentage est teinté vert/orange/rouge selon le niveau (helper
 * [confidenceTextColor]) pour être lisible d'un coup d'œil, sans avoir à
 * décoder le nombre.
 */
@Composable
private fun SmallLayout(
    data: WidgetData,
    onContainer: ColorProvider,
    allowCity: Boolean = true,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val size = LocalSize.current
    val profile = singleRowWidgetHeightProfile(size.height.value)
    val showCity = allowCity && shouldShowCityInSmallWidget(size.width.value, size.height.value)
    val glyphSize = when (profile) {
        SingleRowWidgetHeightProfile.VERY_DENSE -> 26
        SingleRowWidgetHeightProfile.DENSE -> 30
        SingleRowWidgetHeightProfile.REGULAR -> 34
    }
    val citySize = when (profile) {
        SingleRowWidgetHeightProfile.VERY_DENSE -> 10.sp
        SingleRowWidgetHeightProfile.DENSE -> 11.sp
        SingleRowWidgetHeightProfile.REGULAR -> 13.sp
    }
    val tempSize = when (profile) {
        SingleRowWidgetHeightProfile.VERY_DENSE -> 17.sp
        SingleRowWidgetHeightProfile.DENSE -> 19.sp
        SingleRowWidgetHeightProfile.REGULAR -> 20.sp
    }
    val confidenceSize = when (profile) {
        SingleRowWidgetHeightProfile.VERY_DENSE -> 8.sp
        SingleRowWidgetHeightProfile.DENSE -> 9.sp
        SingleRowWidgetHeightProfile.REGULAR -> 11.sp
    }

    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WeatherGlyph(data.currentCondition, isNight = data.currentIsNight == true, sizeDp = glyphSize, decision = data.currentConditionDecision)
        Spacer(GlanceModifier.width(if (profile == SingleRowWidgetHeightProfile.VERY_DENSE) 4.dp else 6.dp))
        Column {
            if (showCity) {
                data.cityName?.let {
                    Text(
                        text = it,
                        style = TextStyle(
                            color = onContainer,
                            fontSize = (citySize).widgetScaled(),
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
                Spacer(GlanceModifier.height(1.dp))
            }
            Text(
                text = formatTemp(data.currentTemp, units = units),
                style = TextStyle(
                    color = onContainer,
                    fontSize = (tempSize).widgetScaled(),
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            data.confidencePct?.let {
                Text(
                    text = "$it%",
                    style = TextStyle(
                        color = confidenceTextColor(it),
                        fontSize = (confidenceSize).widgetScaled(),
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Min/max et ressenti sont UN seul Text Glance : ils ne peuvent donc pas être
 * séparés, réordonnés ni rendus à côté de la température actuelle. Un Text
 * unique évite aussi tout enfant de Row supplémentaire dans RemoteViews.
 * Quand les extrêmes manquent, le ressenti est masqué ; les widgets étroits
 * gardent uniquement les min/max pour éviter toute troncature.
 */
@Composable
internal fun WidgetMinMaxWithApparent(
    minMax: String,
    data: WidgetData,
    units: WeatherUnits,
    onContainerMuted: ColorProvider,
    minMaxSize: TextUnit,
    showApparent: Boolean
) {
    val context = LocalContext.current
    Text(
        text = minMaxAndApparentText(
            minMax = minMax,
            apparentTemp = data.apparentTemp,
            units = units,
            prefix = context.getString(R.string.apparent_temperature_widget_prefix),
            show = showApparent
        ),
        style = TextStyle(color = onContainerMuted, fontSize = (minMaxSize).widgetScaled()),
        maxLines = 1
    )
}

/** Layout 3×1 : ligne icône+temp | ville sur min/max | badge de confiance. */
@Composable
private fun MediumLayout(
    data: WidgetData,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val profile = singleRowWidgetHeightProfile(LocalSize.current.height.value)
    val glyphSize = when (profile) {
        SingleRowWidgetHeightProfile.VERY_DENSE -> 30
        SingleRowWidgetHeightProfile.DENSE -> 34
        SingleRowWidgetHeightProfile.REGULAR -> 38
    }
    val tempSize = when (profile) {
        SingleRowWidgetHeightProfile.VERY_DENSE -> 21.sp
        SingleRowWidgetHeightProfile.DENSE -> 23.sp
        SingleRowWidgetHeightProfile.REGULAR -> 26.sp
    }
    val citySize = when (profile) {
        SingleRowWidgetHeightProfile.VERY_DENSE -> 10.sp
        SingleRowWidgetHeightProfile.DENSE -> 11.sp
        SingleRowWidgetHeightProfile.REGULAR -> 13.sp
    }
    val minMaxSize = when (profile) {
        SingleRowWidgetHeightProfile.VERY_DENSE -> 9.sp
        SingleRowWidgetHeightProfile.DENSE -> 10.sp
        SingleRowWidgetHeightProfile.REGULAR -> 12.sp
    }
    val centerVerticalPadding = when (profile) {
        SingleRowWidgetHeightProfile.VERY_DENSE -> 3.dp
        SingleRowWidgetHeightProfile.DENSE -> 4.dp
        SingleRowWidgetHeightProfile.REGULAR -> 6.dp
    }

    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherGlyph(data.currentCondition, isNight = data.currentIsNight == true, sizeDp = glyphSize, decision = data.currentConditionDecision)
            Spacer(GlanceModifier.width(if (profile == SingleRowWidgetHeightProfile.VERY_DENSE) 4.dp else 6.dp))
            Text(
                text = formatTemp(data.currentTemp, units = units),
                style = TextStyle(
                    color = onContainer,
                    fontSize = (tempSize).widgetScaled(),
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.width(if (profile == SingleRowWidgetHeightProfile.REGULAR) 10.dp else 7.dp))

        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .padding(
                    horizontal = if (profile == SingleRowWidgetHeightProfile.REGULAR) 3.dp else 1.dp,
                    vertical = centerVerticalPadding
                )
        ) {
            data.cityName?.let {
                Text(
                    text = it,
                    style = TextStyle(
                        color = onContainer,
                        fontSize = (citySize).widgetScaled(),
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
            WidgetMinMaxWithApparent(
                minMax = formatMinMax(data.tempMin, data.tempMax, units = units),
                data = data,
                units = units,
                onContainerMuted = onContainerMuted,
                minMaxSize = minMaxSize,
                showApparent = LocalSize.current.width.value >= 300f &&
                    profile == SingleRowWidgetHeightProfile.REGULAR
            )
        }

        data.confidencePct?.let {
            Spacer(GlanceModifier.width(if (profile == SingleRowWidgetHeightProfile.VERY_DENSE) 5.dp else 8.dp))
            ConfidencePill(
                percent = it,
                compact = profile != SingleRowWidgetHeightProfile.REGULAR
            )
        }
    }
}

/**
 * Rendu horizontal étendu : ville, extrêmes et informations contextuelles.
 * Les prévisions inline sont ajoutées seulement si la largeur le permet ;
 * la même composition sert ainsi aux différentes dimensions du launcher.
 * @param inlineForecastItems Nombre de prévisions affichées à droite.
 */
@Composable
private fun LargeLayout(
    data: WidgetData,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    inlineForecastItems: Int = 0,
    rootVerticalInsetDp: Float,
    showLocationRefresh: Boolean,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val size = LocalSize.current
    val profile = singleRowWidgetHeightProfile(size.height.value)
    val glyphSize = when (profile) {
        SingleRowWidgetHeightProfile.VERY_DENSE -> 32
        SingleRowWidgetHeightProfile.DENSE -> 37
        SingleRowWidgetHeightProfile.REGULAR -> 42
    }
    val currentTempSize = when (profile) {
        SingleRowWidgetHeightProfile.VERY_DENSE -> 21.sp
        SingleRowWidgetHeightProfile.DENSE -> 24.sp
        SingleRowWidgetHeightProfile.REGULAR -> 26.sp
    }
    val citySize = when (profile) {
        SingleRowWidgetHeightProfile.VERY_DENSE -> 11.sp
        SingleRowWidgetHeightProfile.DENSE -> 13.sp
        SingleRowWidgetHeightProfile.REGULAR -> 16.sp
    }
    val minMaxSize = when (profile) {
        SingleRowWidgetHeightProfile.VERY_DENSE -> 9.sp
        SingleRowWidgetHeightProfile.DENSE -> 11.sp
        SingleRowWidgetHeightProfile.REGULAR -> 14.sp
    }
    val extrasSize = when (profile) {
        SingleRowWidgetHeightProfile.VERY_DENSE -> 8.sp
        SingleRowWidgetHeightProfile.DENSE -> 9.sp
        SingleRowWidgetHeightProfile.REGULAR -> 11.sp
    }
    val showExtras = profile != SingleRowWidgetHeightProfile.VERY_DENSE

    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherGlyph(data.currentCondition, isNight = data.currentIsNight == true, sizeDp = glyphSize, decision = data.currentConditionDecision)
            Spacer(GlanceModifier.width(if (profile == SingleRowWidgetHeightProfile.VERY_DENSE) 4.dp else 6.dp))
            Text(
                text = formatTemp(data.currentTemp, units = units),
                style = TextStyle(color = onContainer, fontSize = (currentTempSize).widgetScaled(), fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.width(if (profile == SingleRowWidgetHeightProfile.REGULAR) 12.dp else 8.dp))

        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .padding(
                    horizontal = if (profile == SingleRowWidgetHeightProfile.REGULAR) 3.dp else 1.dp,
                    vertical = when (profile) {
                        SingleRowWidgetHeightProfile.VERY_DENSE -> 2.dp
                        SingleRowWidgetHeightProfile.DENSE -> 3.dp
                        SingleRowWidgetHeightProfile.REGULAR -> 4.dp
                    }
                )
        ) {
            data.cityName?.let {
                Text(
                    text = it,
                    style = TextStyle(
                        color = onContainer,
                        fontSize = (citySize).widgetScaled(),
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
            WidgetMinMaxWithApparent(
                minMax = formatMinMax(data.tempMin, data.tempMax, units = units),
                data = data,
                units = units,
                onContainerMuted = onContainerMuted,
                minMaxSize = minMaxSize,
                showApparent = size.width.value >= 350f && inlineForecastItems == 0
            )
            val extras = buildExtrasLine(data, units = units)
            if (showExtras && extras.isNotEmpty()) {
                Spacer(GlanceModifier.height(1.dp))
                Text(
                    text = extras,
                    style = TextStyle(color = onContainerMuted, fontSize = (extrasSize).widgetScaled()),
                    maxLines = 1
                )
            }
        }

        if (inlineForecastItems > 0 && data.forecasts.isNotEmpty()) {
            val leadingSpacing =
                if (profile == SingleRowWidgetHeightProfile.VERY_DENSE) 5.dp else 8.dp
            val itemSpacing =
                if (profile == SingleRowWidgetHeightProfile.REGULAR) ForecastColumnSpacing else 5.dp
            Spacer(GlanceModifier.width(leadingSpacing))
            Row(verticalAlignment = Alignment.CenterVertically) {
                data.forecasts.take(inlineForecastItems).forEachIndexed { index, item ->
                    if (index > 0) Spacer(GlanceModifier.width(itemSpacing))
                    ForecastTimelineItem(
                        item = item,
                        onContainer = onContainer,
                        onContainerMuted = onContainerMuted,
                        mode = data.forecastMode,
                        compact = true,
                        heightProfile = forecastCardHeightProfile(
                            (size.height.value - 2f * rootVerticalInsetDp -
                                locationRefreshFooterHeightDp(showLocationRefresh)).coerceAtLeast(1f)
                        ),
                        availableCardHeightDp = (size.height.value - 2f * rootVerticalInsetDp -
                            locationRefreshFooterHeightDp(showLocationRefresh)).coerceAtLeast(1f),
                        availableCardWidthDp = inlineForecastCardWidthDp(
                            size.width.value, inlineForecastItems
                        ),
                        emphasized = index == 0
                    )
                }
            }
        }

        data.confidencePct?.let {
            Spacer(GlanceModifier.width(if (profile == SingleRowWidgetHeightProfile.VERY_DENSE) 5.dp else 8.dp))
            ConfidencePill(
                percent = it,
                compact = profile != SingleRowWidgetHeightProfile.REGULAR
            )
        }
    }
}

/**
 * Layout dédié aux formats hauts mais étroits, principalement 2×2.
 *
 * Sans ce bucket, un 2×2 tombait sur [SmallLayout] car sa largeur restait
 * sous le seuil du layout 4×2. Le résultat occupait seulement le centre de
 * la tuile et laissait une grande zone vide. Cette variante utilise la
 * hauteur disponible tout en limitant le bas du widget à deux ou trois
 * valeurs lisibles selon le mode configuré.
 */
@Composable
private fun CompactTallLayout(
    data: WidgetData,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    softSurface: ColorProvider,
    raisedSurface: ColorProvider,
    onContainerArgb: Int,
    rootHorizontalInsetDp: Float,
    rootVerticalInsetDp: Float,
    showCurrentConditions: Boolean,
    showLocationRefresh: Boolean,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val size = LocalSize.current
    val narrow = size.width.value < 150f
    val headerLayout = compactTallHeaderLayout(
        widgetHeightDp = size.height.value,
        rootVerticalInsetDp = rootVerticalInsetDp,
        narrow = narrow,
        showCurrentConditions = showCurrentConditions,
        footerHeightDp = locationRefreshFooterHeightDp(showLocationRefresh)
    )
    val smallHeader = headerLayout.compact
    val headerBudgetDp = headerLayout.headerBudgetDp
    val sectionGapDp = headerLayout.sectionGapDp
    val showPanelHeader = headerLayout.showPanelHeader
    val forecastCardHeightDp = forecastBottomStripAvailableHeightDp(
        widgetHeightDp = size.height.value,
        headerHeightDp = headerBudgetDp + forecastContentHeaderHeightDp(
            hasConfidencePanel = data.confidenceStrips.isNotEmpty(),
            showHeader = showPanelHeader,
            compact = true
        ),
        sectionGapDp = sectionGapDp,
        rootVerticalPaddingDp = rootVerticalInsetDp,
        footerHeightDp = locationRefreshFooterHeightDp(showLocationRefresh)
    )
    val forecastCardProfile = forecastCardHeightProfile(forecastCardHeightDp)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (showCurrentConditions) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WeatherGlyph(data.currentCondition, isNight = data.currentIsNight == true, decision = data.currentConditionDecision,
                    sizeDp = if (smallHeader) 23 else if (narrow) 30 else 36)
                Spacer(GlanceModifier.width(if (smallHeader) 3.dp else if (narrow) 5.dp else 8.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    if (smallHeader) {
                        // Keep the city on the temperature line instead of
                        // dropping it when a narrow 2-row preset is selected.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatTemp(data.currentTemp, units = units),
                                style = TextStyle(
                                    color = onContainer,
                                    fontSize = (18.sp).widgetScaled(),
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            data.cityName?.let {
                                Spacer(GlanceModifier.width(4.dp))
                                Text(
                                    text = it,
                                    style = TextStyle(color = onContainer, fontSize = (9.sp).widgetScaled()),
                                    maxLines = 1
                                )
                            }
                        }
                    } else {
                        data.cityName?.let {
                            Text(
                                text = it,
                                style = TextStyle(
                                    color = onContainer,
                                    fontSize = (if (narrow) 10.sp else 12.sp).widgetScaled(),
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }
                        Text(
                            text = formatTemp(data.currentTemp, units = units),
                            style = TextStyle(
                                color = onContainer,
                                fontSize = (if (narrow) 21.sp else 24.sp).widgetScaled(),
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                    val minMax = formatMinMax(data.tempMin, data.tempMax, units = units)
                    if (minMax.isNotEmpty()) {
                        WidgetMinMaxWithApparent(
                            minMax = minMax,
                            data = data,
                            units = units,
                            onContainerMuted = onContainerMuted,
                            minMaxSize = if (smallHeader) 8.sp else if (narrow) 9.sp else 10.sp,
                            showApparent = !smallHeader && size.width.value >= 175f
                        )
                    }
                    data.confidencePct?.let {
                        Text(
                            text = "$it%",
                            style = TextStyle(
                                color = confidenceTextColor(it),
                                fontSize = (if (smallHeader || narrow) 8.sp else 9.sp).widgetScaled(),
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(sectionGapDp.dp))
        }

        when {
            data.confidenceStrips.isNotEmpty() -> CompactConfidenceSummary(
                strips = data.confidenceStrips,
                onContainer = onContainer,
                onContainerMuted = onContainerMuted,
                softSurface = softSurface,
                raisedSurface = raisedSurface,
                showHeader = showPanelHeader
            )

            data.next12hTemps.isNotEmpty() -> TwelveHourForecastStrip(
                data = data,
                compact = true,
                textColorArgb = onContainerArgb,
                rootHorizontalInsetDp = rootHorizontalInsetDp,
                rootVerticalInsetDp = rootVerticalInsetDp,
                headerHeightBudgetDp = headerBudgetDp,
                sectionGapDp = sectionGapDp,
                showLocationRefresh = showLocationRefresh,
                modifier = GlanceModifier.defaultWeight()
            )

            data.forecasts.isNotEmpty() -> ForecastTimelinePanel(
                items = data.forecasts,
                mode = data.forecastMode,
                itemCount = 2,
                onContainer = onContainer,
                onContainerMuted = onContainerMuted,
                softSurface = softSurface,
                compact = true,
                veryCompact = narrow,
                heightProfile = forecastCardProfile,
                availableCardHeightDp = forecastCardHeightDp,
                availablePanelWidthDp = size.width.value - 2f * rootHorizontalInsetDp,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )

            else -> {
                val extras = buildExtrasLine(data, units = units)
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .background(softSurface)
                        .cornerRadius(14.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (showCurrentConditions) {
                            extras.ifEmpty { formatMinMax(data.tempMin, data.tempMax, units = units) }
                        } else "…",
                        style = TextStyle(
                            color = onContainerMuted,
                            fontSize = (10.sp).widgetScaled(),
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.CompactConfidenceSummary(
    strips: List<WidgetConfidenceStrip>,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    softSurface: ColorProvider,
    raisedSurface: ColorProvider,
    showHeader: Boolean
) {
    ModernConfidencePanel(
        strips = strips,
        onContainer = onContainer,
        onContainerMuted = onContainerMuted,
        softSurface = softSurface,
        raisedSurface = raisedSurface,
        compact = true,
        bucketCount = 3,
        showHeader = showHeader,
        modifier = GlanceModifier.fillMaxWidth().defaultWeight()
    )
}

/**
 * Rendu étendu avec prévisions horaires ou journalières en bas.
 * Le nombre de cartes, les extras du bandeau et la densité s'adaptent à la
 * largeur et à la hauteur exactes. Quand il manque des prévisions, `take(n)`
 * affiche celles disponibles sans laisser de cases vides artificielles.
 * @param showFiveItems Autorise une cinquième carte si la largeur suffit.
 * @param showExtras Affiche les métriques annexes quand le bandeau n'est pas étroit.
 */
@Composable
private fun ExtraLargeLayout(
    data: WidgetData,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    softSurface: ColorProvider,
    raisedSurface: ColorProvider,
    // ARGB brut de la couleur de texte "onContainer", résolu au niveau widget
    // root en tenant compte du night mode + des overrides utilisateur.
    onContainerArgb: Int,
    rootHorizontalInsetDp: Float,
    rootVerticalInsetDp: Float,
    showFiveItems: Boolean = false,
    showExtras: Boolean = true,
    showCurrentConditions: Boolean,
    showLocationRefresh: Boolean,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val size = LocalSize.current
    val widthDp = size.width.value
    val heightDp = size.height.value
    val itemCount = if (showFiveItems) 5 else 4
    // Les transitions utilisent la hauteur du contenu, sans le raccourci
    // « Ma position » éventuellement placé au pied du widget.
    val effectiveHeight = forecastEffectiveHeightForTransitions(
        (heightDp - locationRefreshFooterHeightDp(showLocationRefresh)).coerceAtLeast(0f),
        rootVerticalInsetDp,
        forecastContainerVerticalPaddingDp(heightDp)
    )
    val smallHeader = compactForecastHeader(effectiveHeight)
    val sizeProfile = twoRowWidgetSizeProfile(widthDp, effectiveHeight)
    val veryCompact = sizeProfile == TwoRowWidgetSizeProfile.VERY_DENSE
    val compactHeight = sizeProfile != TwoRowWidgetSizeProfile.REGULAR
    val actualShowExtras = showExtras && !veryCompact && !smallHeader
    val extrasLine = if (showCurrentConditions) buildExtrasLine(data, units = units) else ""
    val showContextLine = shouldShowForecastContextLine(
        widthDp = widthDp,
        heightDp = effectiveHeight,
        showCurrentConditions = showCurrentConditions,
        hasExtras = extrasLine.isNotEmpty(),
        extrasAlreadyInline = actualShowExtras
    )
    val showPanelHeader = shouldShowForecastPanelHeader(effectiveHeight, sizeProfile)
    val sectionGapDp = if (!showCurrentConditions) 0f else when {
        smallHeader -> 2f
        veryCompact -> 5f
        compactHeight -> 7f
        else -> 10f
    }
    val headerHeightBudgetDp = when {
        !showCurrentConditions -> 0f
        smallHeader -> COMPACT_FORECAST_HEADER_HEIGHT_DP
        else -> miniForecastHeaderHeightBudgetDp(compactHeight, actualShowExtras) +
            if (showContextLine) FORECAST_CONTEXT_LINE_HEIGHT_DP else 0f
    }
    val forecastCardHeightDp = forecastBottomStripAvailableHeightDp(
        widgetHeightDp = heightDp,
        headerHeightDp = headerHeightBudgetDp + forecastContentHeaderHeightDp(
            hasConfidencePanel = data.confidenceStrips.isNotEmpty(),
            showHeader = showPanelHeader,
            compact = compactHeight
        ),
        sectionGapDp = sectionGapDp,
        rootVerticalPaddingDp = rootVerticalInsetDp,
        footerHeightDp = locationRefreshFooterHeightDp(showLocationRefresh)
    )
    val forecastCardProfile = forecastCardHeightProfile(forecastCardHeightDp)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (showCurrentConditions) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (smallHeader) {
                    WeatherGlyph(
                        data.currentCondition,
                        isNight = data.currentIsNight == true,
                        sizeDp = 24,
                        decision = data.currentConditionDecision
                    )
                    Spacer(GlanceModifier.width(5.dp))
                    Text(
                        text = formatTemp(data.currentTemp, units = units),
                        style = TextStyle(
                            color = onContainer,
                            fontSize = (19.sp).widgetScaled(),
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(GlanceModifier.width(7.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        data.cityName?.let { city ->
                            Text(
                                text = city,
                                style = TextStyle(
                                    color = onContainer,
                                    fontSize = (11.sp).widgetScaled(),
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        }
                        val minMax = formatMinMax(data.tempMin, data.tempMax, units = units)
                        if (minMax.isNotEmpty()) {
                            WidgetMinMaxWithApparent(
                                minMax = minMax,
                                data = data,
                                units = units,
                                onContainerMuted = onContainerMuted,
                                minMaxSize = 9.sp,
                                showApparent = false
                            )
                        }
                    }
                    data.confidencePct?.let {
                        Spacer(GlanceModifier.width(4.dp))
                        ConfidencePill(percent = it, compact = true)
                    }
                } else {
                    WeatherGlyph(
                        data.currentCondition,
                        decision = data.currentConditionDecision,
                        isNight = data.currentIsNight == true,
                        sizeDp = when {
                            veryCompact -> 32
                            compactHeight -> 38
                            else -> 44
                        }
                    )
                    Spacer(GlanceModifier.width(if (veryCompact) 5.dp else 8.dp))
                    Text(
                        text = formatTemp(data.currentTemp, units = units),
                        style = TextStyle(
                            color = onContainer,
                            fontSize = (when {
                                veryCompact -> 22.sp
                                compactHeight -> 25.sp
                                else -> 28.sp
                            }).widgetScaled(),
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(GlanceModifier.width(if (veryCompact) 7.dp else 12.dp))

                    Column(modifier = GlanceModifier.defaultWeight()) {
                        data.cityName?.let {
                            Text(
                                text = it,
                                style = TextStyle(
                                    color = onContainer,
                                    fontSize = (when {
                                        veryCompact -> 11.sp
                                        compactHeight -> 14.sp
                                        else -> 16.sp
                                    }).widgetScaled(),
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        }
                        WidgetMinMaxWithApparent(
                            minMax = formatMinMax(data.tempMin, data.tempMax, units = units),
                            data = data,
                            units = units,
                            onContainerMuted = onContainerMuted,
                            minMaxSize = when {
                                veryCompact -> 9.sp
                                compactHeight -> 12.sp
                                else -> 14.sp
                            },
                            showApparent = !veryCompact && widthDp >= 320f
                        )
                        if (actualShowExtras && extrasLine.isNotEmpty()) {
                            Spacer(GlanceModifier.height(1.dp))
                            Text(
                                text = extrasLine,
                                style = TextStyle(
                                    color = onContainerMuted,
                                    fontSize = (if (compactHeight) 9.sp else 11.sp).widgetScaled()
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    data.confidencePct?.let {
                        Spacer(GlanceModifier.width(if (veryCompact) 5.dp else 8.dp))
                        ConfidencePill(percent = it, compact = compactHeight)
                    }
                }
            }
            if (showContextLine) {
                // A second, full-width line avoids truncating the 305dp
                // header's wind and precipitation behind the confidence pill.
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = extrasLine,
                    style = TextStyle(color = onContainerMuted, fontSize = (10.sp).widgetScaled()),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.height(sectionGapDp.dp))
        }

        val strips = data.confidenceStrips
        val hasMiniForecast = data.next12hTemps.isNotEmpty()
        when {
            strips.isNotEmpty() -> CombinedConfidenceBands(
                strips = strips,
                onContainer = onContainer,
                onContainerMuted = onContainerMuted,
                softSurface = softSurface,
                raisedSurface = raisedSurface,
                compact = compactHeight,
                bucketCount = itemCount,
                showHeader = showPanelHeader
            )
            hasMiniForecast -> TwelveHourForecastStrip(
                data = data,
                compact = compactHeight,
                textColorArgb = onContainerArgb,
                rootHorizontalInsetDp = rootHorizontalInsetDp,
                rootVerticalInsetDp = rootVerticalInsetDp,
                headerHeightBudgetDp = headerHeightBudgetDp,
                sectionGapDp = sectionGapDp,
                showLocationRefresh = showLocationRefresh,
                modifier = GlanceModifier.defaultWeight()
            )
            data.forecasts.isEmpty() -> Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "…",
                    style = TextStyle(color = onContainerMuted, fontSize = (12.sp).widgetScaled())
                )
            }
            else -> ForecastTimelinePanel(
                items = data.forecasts,
                mode = data.forecastMode,
                itemCount = itemCount,
                onContainer = onContainer,
                onContainerMuted = onContainerMuted,
                softSurface = softSurface,
                compact = compactHeight,
                veryCompact = veryCompact,
                heightProfile = forecastCardProfile,
                availableCardHeightDp = forecastCardHeightDp,
                availablePanelWidthDp = size.width.value - 2f * rootHorizontalInsetDp,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
        }
    }
}

/**
 * Mini-prévision 12 h, activée si la hauteur du widget le permet.
 *
 * Le bitmap répartit les douze heures sur deux lignes de six cellules. Chaque
 * cellule porte son heure, sa température, la condition météo de consensus et
 * le risque de pluie. Le rendu utilise ainsi la hauteur des formats ×2 au lieu de laisser
 * une bande étroite centrée dans un grand espace vide.
 */
@Composable
private fun TwelveHourForecastStrip(
    data: WidgetData,
    compact: Boolean,
    textColorArgb: Int,
    rootHorizontalInsetDp: Float,
    rootVerticalInsetDp: Float,
    headerHeightBudgetDp: Float,
    sectionGapDp: Float,
    showLocationRefresh: Boolean,
    modifier: GlanceModifier = GlanceModifier,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val usableWidthDp = (size.width.value - 2f * rootHorizontalInsetDp).coerceAtLeast(1f)
    val profile = miniForecastProfileForWidth(usableWidthDp)
    // Only a small bitmap inset: the host widget is now the single surface.
    val chartHorizontalPaddingDp = when (profile) {
        MiniForecastSizeProfile.COMPACT_2X2 -> 2f
        MiniForecastSizeProfile.MEDIUM_3X2 -> 3f
        MiniForecastSizeProfile.EXPANDED_4X2 -> 4f
    }
    val availableWidthDp = (usableWidthDp - chartHorizontalPaddingDp * 2f)
        .coerceAtLeast(1f)
    // La hauteur du bitmap suit la hauteur réellement allouée par le launcher.
    // Retirer l'en-tête/paddings avant de dimensionner le graphique évite
    // aussi une grande zone vide à l'intérieur du widget.
    val chartHeightDp = miniForecastChartHeightDp(
        widgetHeightDp = size.height.value,
        headerHeightDp = headerHeightBudgetDp,
        sectionGapDp = sectionGapDp,
        profile = profile,
        rootVerticalPaddingDp = rootVerticalInsetDp,
        footerHeightDp = locationRefreshFooterHeightDp(showLocationRefresh)
    )
    // Exact generates only the sizes reported by the host (normally
    // portrait and landscape); cap each chart rather than dividing its
    // resolution across 44 unrelated responsive layouts.
    val renderDensity = widgetResponsiveForecastBitmapDensity(
        availableWidthDp,
        chartHeightDp.toFloat(),
        density,
        maxPixels = widgetResponsiveForecastVariantPixelBudget()
    )
    val widthPx = (availableWidthDp * renderDensity).toInt().coerceAtLeast(1)
    val heightPx = (chartHeightDp * renderDensity).toInt().coerceAtLeast(1)
    val precipColorArgb = 0xFF1976D2.toInt()
    val bitmapTextScale = LocalWidgetTextScale.current

    val displayLocale = context.resources.configuration.locales[0]
    val formatter = remember(displayLocale, data.clockFormatPreference) {
        WeatherTimeFormat.hour(displayLocale, data.clockFormatPreference)
    }
    val timelineLabels = data.hourlyStartTime?.let { start ->
        List(12) { offset -> start.plusHours(offset.toLong()).format(formatter) }
    } ?: List(12) { offset -> "+${offset}h" }

    // Readable fallback instead of rendering an illegible / one-pixel bitmap
    // when a launcher offers very little width or the header consumes the height.
    val presentation = widgetTwelveHourPresentation(availableWidthDp, chartHeightDp.toFloat())
    if (presentation != WidgetTwelveHourPresentation.CHART) {
        val indices = when (presentation) {
            WidgetTwelveHourPresentation.NARROW_SUMMARY -> listOf(0)
            WidgetTwelveHourPresentation.COMPACT_SUMMARY -> listOf(0, 6)
            WidgetTwelveHourPresentation.CHART -> emptyList()
        }
        Row(
            modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            indices.forEach { index ->
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = timelineLabels.getOrNull(index) ?: "+${index}h",
                        style = TextStyle(color = ColorProvider(Color(textColorArgb)),
                            fontSize = (9.sp).widgetScaled()),
                        maxLines = 1
                    )
                    Text(
                        text = data.next12hTemps.getOrNull(index)
                            ?.takeIf { it.isFinite() }?.let { units.temp(it) } ?: "—",
                        style = TextStyle(color = ColorProvider(Color(textColorArgb)),
                            fontWeight = FontWeight.Bold,
                            fontSize = (13.sp).widgetScaled()),
                        maxLines = 1
                    )
                    data.next12hPrecipProb.getOrNull(index)?.let { rain ->
                        Text(
                            text = "${rain.coerceIn(0, 100)}%",
                            style = TextStyle(color = ColorProvider(Color(textColorArgb)),
                                fontSize = (9.sp).widgetScaled()),
                            maxLines = 1
                        )
                    }
                }
            }
        }
        return
    }

    val bitmap = remember(
        units,
        displayLocale,
        data.forecastMode,
        data.next12hTemps,
        data.next12hPrecipProb,
        data.next12hPrecipMm,
        data.next12hConditions,
        data.next12hConditionDiagnostics,
        data.next12hIsNight,
        timelineLabels,
        widthPx,
        heightPx,
        textColorArgb,
        profile,
        bitmapTextScale
    ) {
        if (data.forecastMode?.isModernHeatmapChartForecast() == true) {
            WidgetHeatmapTrendForecastRenderer.render(
                widthPx = widthPx,
                heightPx = heightPx,
                temps = data.next12hTemps,
                precipProbabilities = data.next12hPrecipProb,
                precipAmountsMm = data.next12hPrecipMm,
                conditions = data.next12hConditions,
                conditionDiagnostics = data.next12hConditionDiagnostics,
                nightFlags = data.next12hIsNight,
                precipColorArgb = precipColorArgb,
                textColorArgb = textColorArgb,
                timelineLabels = timelineLabels,
                profile = profile,
                units = units,
                textScale = bitmapTextScale
            )
        } else if (data.forecastMode?.isHeatmapChartForecast() == true) {
            WidgetHeatmapForecastRenderer.render(
                widthPx = widthPx,
                heightPx = heightPx,
                temps = data.next12hTemps,
                precipProbabilities = data.next12hPrecipProb,
                precipAmountsMm = data.next12hPrecipMm,
                conditions = data.next12hConditions,
                conditionDiagnostics = data.next12hConditionDiagnostics,
                nightFlags = data.next12hIsNight,
                precipColorArgb = precipColorArgb,
                textColorArgb = textColorArgb,
                timelineLabels = timelineLabels,
                profile = profile,
                units = units,
                textScale = bitmapTextScale
            )
        } else {
            WidgetMiniForecastRenderer.render(
                widthPx = widthPx,
                heightPx = heightPx,
                temps = data.next12hTemps,
                precipProbabilities = data.next12hPrecipProb,
                precipAmountsMm = data.next12hPrecipMm,
                conditions = data.next12hConditions,
                conditionDiagnostics = data.next12hConditionDiagnostics,
                nightFlags = data.next12hIsNight,
                precipColorArgb = precipColorArgb,
                textColorArgb = textColorArgb,
                timelineLabels = timelineLabels,
                profile = profile,
                units = units,
                textScale = bitmapTextScale
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = chartHorizontalPaddingDp.dp,
                vertical = miniForecastContainerVerticalPaddingDp(profile).dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxWidth().height(chartHeightDp.dp)
        )
    }
}

/**
 * Panneau moderne commun aux vues « prochaines heures » et « prochains jours ».
 * Les libellés des heures ou des jours suffisent à situer les prévisions : aucun
 * titre additionnel ne doit consommer leur hauteur. Les colonnes utilisent un
 * accent thermique ponctuel et des métriques secondaires légères.
 */
@Composable
private fun ForecastTimelinePanel(
    items: List<WidgetForecastItem>,
    mode: ForecastMode?,
    itemCount: Int,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    softSurface: ColorProvider,
    compact: Boolean,
    veryCompact: Boolean,
    heightProfile: ForecastCardHeightProfile,
    availableCardHeightDp: Float,
    availablePanelWidthDp: Float,
    modifier: GlanceModifier = GlanceModifier
) {
    val normalizedMode = mode?.normalized()
    val visibleItems = items.take(itemCount.coerceIn(0, MAX_GLANCE_FORECAST_TIMELINE_ITEMS))
    check(forecastTimelineDirectChildren(visibleItems.size) <= 10)
    // The forecasts identify their horizon through the day/hour labels directly.
    // No second title row: reclaim its height for the weather and metric glyphs.
    val estimatedCardWidthDp = (availablePanelWidthDp.coerceAtLeast(0f) /
        visibleItems.size.coerceAtLeast(1)).coerceAtLeast(1f)

    Column(modifier = modifier) {
        // On Nova, a weighted RemoteViews row may keep its intrinsic (tiny)
        // height despite the much taller widget host. The measured, safety-
        // adjusted card budget gives the row a predictable real height.
        Row(modifier = GlanceModifier.fillMaxWidth().height(availableCardHeightDp.dp)) {
            visibleItems.forEachIndexed { index, item ->
                // Exactly one direct parent-Row child per forecast. This is robust
                // against RemoteViews' ten-child limit even if the panel grows.
                Row(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                    if (index > 0) {
                        Box(
                            modifier = GlanceModifier.width(if (veryCompact) 3.dp else 5.dp).fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            // Keep separators shorter than the data columns: on
                            // Nova/One UI a full-height line reads as a giant
                            // blank cell and makes the forecast appear smaller.
                            Box(
                                modifier = GlanceModifier.width(1.dp)
                                    .height((availableCardHeightDp * 0.62f).coerceIn(14f, 64f).dp)
                                    .background(softSurface)
                            ) {}
                        }
                    }
                    ForecastTimelineItem(
                        item = item,
                        onContainer = onContainer,
                        onContainerMuted = onContainerMuted,
                        mode = normalizedMode,
                        compact = compact,
                        heightProfile = heightProfile,
                        availableCardHeightDp = availableCardHeightDp,
                        availableCardWidthDp = estimatedCardWidthDp,
                        emphasized = index == 0,
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                    )
                }
            }
        }
    }
}

/**
 * La confiance est désormais organisée par JOUR plutôt que par métrique.
 * Chaque colonne devient une petite carte autonome : jour, valeurs T°/pluie,
 * pourcentages colorés et couverture des modèles. Cette lecture verticale est
 * plus proche d'une heatmap et permet de comparer les jours en un regard.
 */
@Composable
private fun ColumnScope.CombinedConfidenceBands(
    strips: List<WidgetConfidenceStrip>,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    softSurface: ColorProvider,
    raisedSurface: ColorProvider,
    compact: Boolean,
    bucketCount: Int,
    showHeader: Boolean
) {
    ModernConfidencePanel(
        strips = strips,
        onContainer = onContainer,
        onContainerMuted = onContainerMuted,
        softSurface = softSurface,
        raisedSurface = raisedSurface,
        compact = compact,
        bucketCount = bucketCount,
        showHeader = showHeader,
        modifier = GlanceModifier.fillMaxWidth().defaultWeight()
    )
}

@Composable
private fun ModernConfidencePanel(
    strips: List<WidgetConfidenceStrip>,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    softSurface: ColorProvider,
    raisedSurface: ColorProvider,
    compact: Boolean,
    bucketCount: Int,
    showHeader: Boolean,
    modifier: GlanceModifier = GlanceModifier
) {
    val ctx = LocalContext.current
    val visibleStrips = strips.take(2)
    val safeBucketCount = bucketCount.coerceIn(1, 5)
    val days = visibleStrips.firstOrNull()?.buckets?.take(safeBucketCount).orEmpty()

    Column(
        modifier = modifier
            .background(softSurface)
            .cornerRadius(if (compact) 17.dp else 20.dp)
            .padding(
                horizontal = if (compact) 5.dp else 7.dp,
                vertical = if (compact) 5.dp else 7.dp
            )
    ) {
        if (showHeader) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ctx.getString(R.string.widget_forecast_confidence),
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = onContainer,
                        fontSize = (if (compact) 9.sp else 10.sp).widgetScaled(),
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = ctx.getString(R.string.widget_forecast_confidence_hint),
                    style = TextStyle(
                        color = onContainerMuted,
                        fontSize = (if (compact) 6.sp else 7.sp).widgetScaled(),
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.height(if (compact) 3.dp else 5.dp))
        }

        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            days.forEachIndexed { index, day ->
                // A whole day occupies ONE direct Row child; spacing lives inside it.
                Row(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                    if (index > 0) Spacer(GlanceModifier.width(if (compact) 3.dp else 5.dp))
                    ConfidenceDayCard(
                        dayIndex = index,
                        day = day,
                        strips = visibleStrips,
                        onContainer = onContainer,
                        onContainerMuted = onContainerMuted,
                        raisedSurface = raisedSurface,
                        compact = compact,
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfidenceDayCard(
    dayIndex: Int,
    day: StripBucket,
    strips: List<WidgetConfidenceStrip>,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    raisedSurface: ColorProvider,
    compact: Boolean,
    modifier: GlanceModifier = GlanceModifier
) {
    val night = LocalNightMode.current
    val dayModifier = if (dayIndex == 0) {
        modifier.background(raisedSurface)
    } else {
        modifier
    }
    Column(
        modifier = dayModifier
            .cornerRadius(if (compact) 13.dp else 16.dp)
            .padding(
                horizontal = if (compact) 3.dp else 5.dp,
                vertical = if (compact) 3.dp else 5.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = day.label,
            style = TextStyle(
                color = onContainer,
                fontSize = (if (compact) 7.sp else 9.sp).widgetScaled(),
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Text(
            text = "${day.modelCount}/${day.totalModelCount}",
            style = TextStyle(
                color = onContainerMuted,
                fontSize = (if (compact) 6.sp else 7.sp).widgetScaled()
            ),
            maxLines = 1
        )

        strips.forEach { strip ->
            val bucket = strip.buckets.getOrNull(dayIndex) ?: return@forEach
            Spacer(GlanceModifier.height(if (compact) 2.dp else 4.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strip.metricLabel,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = onContainerMuted,
                        fontSize = (if (compact) 6.sp else 7.sp).widgetScaled(),
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Text(
                    text = bucket.value,
                    style = TextStyle(
                        color = onContainer,
                        fontSize = (if (compact) 7.sp else 9.sp).widgetScaled(),
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.height(1.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(if (compact) 4.dp else 6.dp)
                        .background(ColorProvider(confidenceColor(bucket.percent, night)))
                        .cornerRadius(4.dp)
                ) {}
                Spacer(GlanceModifier.width(2.dp))
                Text(
                    text = "${bucket.percent}%",
                    style = TextStyle(
                        color = confidenceTextColor(bucket.percent),
                        fontSize = (if (compact) 6.sp else 7.sp).widgetScaled(),
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/** État "widget pas configuré" ou "erreur" ou "chargement". */
@Composable
private fun ErrorLayout(
    error: WidgetError,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    softSurface: ColorProvider
) {
    val ctx = LocalContext.current
    val size = LocalSize.current
    val widthDp = size.width.value
    val heightDp = size.height.value
    val message = when (error) {
        WidgetError.NotConfigured -> ctx.getString(R.string.widget_error_not_configured)
        WidgetError.Loading -> ctx.getString(R.string.widget_error_loading)
        WidgetError.CityNoLongerInFavorites -> ctx.getString(R.string.widget_error_city_gone)
        WidgetError.Fetch -> ctx.getString(R.string.widget_error_fetch)
    }
    val symbol = when (error) {
        WidgetError.NotConfigured -> "+"
        WidgetError.Loading -> "↻"
        WidgetError.CityNoLongerInFavorites -> "⌂"
        WidgetError.Fetch -> "!"
    }
    val ultraTiny = widthDp < 68f || heightDp < 58f
    val tiny = widthDp < TINY_MAX_WIDTH_DP && heightDp < TINY_MAX_HEIGHT_DP
    val shortRow = !tiny && heightDp < 78f

    when {
        ultraTiny -> Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                style = TextStyle(
                    color = onContainer,
                    fontSize = (18.sp).widgetScaled(),
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }

        shortRow -> Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = GlanceModifier
                    .background(softSurface)
                    .cornerRadius(10.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = symbol,
                    style = TextStyle(
                        color = onContainer,
                        fontSize = (15.sp).widgetScaled(),
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = message,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = onContainerMuted,
                    fontSize = (10.sp).widgetScaled(),
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 2
            )
        }

        else -> Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = GlanceModifier
                    .background(softSurface)
                    .cornerRadius(if (tiny) 11.dp else 14.dp)
                    .padding(
                        horizontal = if (tiny) 8.dp else 11.dp,
                        vertical = if (tiny) 5.dp else 7.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = symbol,
                    style = TextStyle(
                        color = onContainer,
                        fontSize = (if (tiny) 15.sp else 18.sp).widgetScaled(),
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.height(if (tiny) 3.dp else 6.dp))
            Text(
                text = message,
                style = TextStyle(
                    color = onContainerMuted,
                    fontSize = (if (tiny) 8.sp else 12.sp).widgetScaled()
                ),
                maxLines = if (tiny) 2 else 3
            )
        }
    }
}

// ─── Sous-blocs réutilisables ─────────────────────────────────────────────

@Composable
private fun WeatherGlyph(
    condition: WeatherCondition?,
    sizeDp: Int,
    isNight: Boolean = false,
    decision: ConditionDecision? = null
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val renderDensity = density.coerceAtMost(2f)
    val bitmap = remember(condition, isNight, sizeDp, renderDensity, decision) {
        WidgetWeatherIconRenderer.render(
            condition = condition,
            isNight = isNight,
            sizePx = (sizeDp * renderDensity).toInt().coerceAtLeast(1),
            decision = decision
        )
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = context.getString(weatherConditionLabelRes(condition)) +
            (decision?.status?.let { " · " + context.getString(statusLabelRes(it)) } ?: ""),
        modifier = GlanceModifier.width(sizeDp.dp).height(sizeDp.dp)
    )
}

@Composable
private fun ForecastTimelineItem(
    item: WidgetForecastItem,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    mode: ForecastMode?,
    compact: Boolean,
    heightProfile: ForecastCardHeightProfile? = null,
    availableCardHeightDp: Float,
    availableCardWidthDp: Float,
    emphasized: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val ctx = LocalContext.current
    val night = LocalNightMode.current
    val requestedProfile = heightProfile ?: if (compact) {
        ForecastCardHeightProfile.DENSE
    } else {
        ForecastCardHeightProfile.COMPACT
    }
    val availableMetrics = forecastVisibleMetrics(
        item.cloudCoverPct,
        item.precipProbabilityPct,
        item.estimatedSunHours,
        item.forecastConfidencePct
    )
    val profile = forecastFittingCardProfile(
        availableCardHeightDp, requestedProfile, availableMetrics.size,
        LocalWidgetTextScale.current
    )
    val horizontalPadding = when (profile) {
        ForecastCardHeightProfile.DENSE -> 4.dp
        ForecastCardHeightProfile.COMPACT -> 5.dp
        ForecastCardHeightProfile.COMFORTABLE -> 6.dp
        ForecastCardHeightProfile.EXPANDED -> 7.dp
    }
    val verticalPadding = when (profile) {
        ForecastCardHeightProfile.DENSE -> 2.dp
        ForecastCardHeightProfile.COMPACT -> 4.dp
        ForecastCardHeightProfile.COMFORTABLE -> 5.dp
        ForecastCardHeightProfile.EXPANDED -> 7.dp
    }
    val labelSize = when (profile) {
        ForecastCardHeightProfile.DENSE -> 7.sp
        ForecastCardHeightProfile.COMPACT -> 8.sp
        ForecastCardHeightProfile.COMFORTABLE -> 9.sp
        ForecastCardHeightProfile.EXPANDED -> 10.sp
    }
    val glyphSize = forecastTimelineGlyphSizeDp(
        profile = profile,
        availableCardWidthDp = availableCardWidthDp,
        availableCardHeightDp = availableCardHeightDp,
        isDaily = mode?.normalized() == ForecastMode.DAILY,
        emphasized = emphasized
    )
    val temperatureSize = when (profile) {
        ForecastCardHeightProfile.DENSE -> if (emphasized) 11.sp else 10.sp
        ForecastCardHeightProfile.COMPACT -> if (emphasized) 15.sp else 14.sp
        ForecastCardHeightProfile.COMFORTABLE -> if (emphasized) 17.sp else 16.sp
        ForecastCardHeightProfile.EXPANDED -> if (emphasized) 19.sp else 18.sp
    }
    val accentColor = remember(item.temp, night) {
        ColorProvider(forecastTemperatureAccent(item.temp, night))
    }
    val precipForeground = remember(night) {
        ColorProvider(if (night) Color(0xFF90CAF9) else Color(0xFF1565C0))
    }

    Column(
        modifier = modifier
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = GlanceModifier
                    .width(if (profile == ForecastCardHeightProfile.DENSE) 3.dp else 4.dp)
                    .height(if (profile == ForecastCardHeightProfile.DENSE) 3.dp else 4.dp)
                    .background(accentColor)
                    .cornerRadius(4.dp)
            ) {}
            Spacer(GlanceModifier.width(3.dp))
            Text(
                text = item.label,
                style = TextStyle(
                    color = if (emphasized) onContainer else onContainerMuted,
                    fontSize = (labelSize).widgetScaled(),
                    fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.height(if (profile == ForecastCardHeightProfile.DENSE) 2.dp else 5.dp))
        WeatherGlyph(condition = item.condition, sizeDp = glyphSize, isNight = item.isNight == true, decision = item.conditionDecision)
        Text(
            text = formatTemp(item.temp, units = units),
            style = TextStyle(
                color = onContainer,
                fontSize = (temperatureSize).widgetScaled(),
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )

        Spacer(GlanceModifier.height(if (profile == ForecastCardHeightProfile.DENSE) 2.dp else 5.dp))
        // Les tailles du symbole et de la valeur suivent le budget réel de
        // chaque colonne, et pas seulement un breakpoint global de widget.
        val metricLayout = forecastAdaptiveMetricLayout(
            availableCardHeightDp = availableCardHeightDp,
            availableCardWidthDp = availableCardWidthDp,
            profile = profile,
            metrics = availableMetrics,
            textScale = LocalWidgetTextScale.current
        )
        metricLayout.visible.chunked(metricLayout.columns).forEach { metricRow ->
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                metricRow.forEach { metric ->
            val icon: String
            val value: String
            val color: ColorProvider
            when (metric) {
                WidgetForecastMetric.CLOUD -> {
                    icon = "☁"
                    value = "${item.cloudCoverPct?.coerceIn(0, 100)}%"
                    color = onContainerMuted
                }
                WidgetForecastMetric.RAIN -> {
                    icon = "☂"
                    value = "${item.precipProbabilityPct?.coerceIn(0, 100)}%"
                    color = precipForeground
                }
                WidgetForecastMetric.SUNSHINE -> {
                    icon = "☀"
                    val minutes = (requireNotNull(item.estimatedSunHours) * 60.0)
                        .roundToInt().coerceAtLeast(0)
                    value = if (minutes < 60) {
                        ctx.getString(R.string.widget_sun_minutes_short, minutes)
                    } else {
                        val displayHours = String.format(
                            ctx.resources.configuration.locales[0], "%.1f", minutes / 60.0
                        )
                        ctx.getString(R.string.widget_sun_hours_short, displayHours)
                    }
                    color = onContainerMuted
                }
                WidgetForecastMetric.CONVERGENCE -> {
                    val pct = requireNotNull(item.forecastConfidencePct).coerceIn(0, 100)
                    // Conserve le symbole localisé « C » / « K » de la traduction.
                    val label = ctx.getString(R.string.widget_forecast_convergence_compact, pct)
                    icon = label.substringBefore(' ')
                    value = label.substringAfter(' ', label)
                    color = confidenceTextColor(pct)
                }
            }
            Row(
                modifier = if (metricLayout.columns == 2) GlanceModifier.defaultWeight()
                    else GlanceModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = icon,
                    style = TextStyle(color = color, fontSize = (metricLayout.iconSp.sp).widgetScaled()),
                    maxLines = 1
                )
                Spacer(GlanceModifier.width(1.dp))
                Text(
                    text = value,
                    style = TextStyle(
                        color = color,
                        fontSize = (metricLayout.textSp.sp).widgetScaled(),
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
                }
            }
        }
    }
}

/** Teinte thermique adoucie utilisée comme accent des cartes 5 h / 5 j. */
internal fun forecastTemperatureAccent(temp: Double?, night: Boolean): Color = when {
    temp == null -> if (night) Color(0xFF90A4AE) else Color(0xFF607D8B)
    temp < 5.0 -> if (night) Color(0xFF64B5F6) else Color(0xFF1976D2)
    temp < 14.0 -> if (night) Color(0xFF80CBC4) else Color(0xFF00897B)
    temp < 22.0 -> if (night) Color(0xFFA5D6A7) else Color(0xFF43A047)
    temp < 29.0 -> if (night) Color(0xFFFFCC80) else Color(0xFFFB8C00)
    else -> if (night) Color(0xFFEF9A9A) else Color(0xFFE53935)
}

@Composable
private fun ConfidencePill(percent: Int, compact: Boolean = false) {
    val night = LocalNightMode.current
    val color = confidenceColor(percent, night)
    val bg = remember(percent, night) { ColorProvider(color.copy(alpha = if (night) 0.16f else 0.12f)) }
    val fg = remember(percent, night) { ColorProvider(color) }
    Row(
        modifier = GlanceModifier
            .background(bg)
            .cornerRadius(if (compact) 14.dp else 18.dp)
            .padding(
                horizontal = if (compact) 6.dp else 9.dp,
                vertical = if (compact) 3.dp else 5.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .width(if (compact) 5.dp else 6.dp)
                .height(if (compact) 5.dp else 6.dp)
                .background(fg)
                .cornerRadius(6.dp)
        ) {}
        Spacer(GlanceModifier.width(if (compact) 4.dp else 6.dp))
        Text(
            text = "$percent%",
            style = TextStyle(
                color = fg,
                fontSize = (if (compact) 9.sp else 11.sp).widgetScaled(),
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

// ─── Couleurs sémantiques ───────────────────────────────────────────────

/**
 * Couleur de confiance selon le %. Les variantes claires utilisent des tons
 * 700, tandis que le mode sombre passe sur des tons 300 plus lumineux pour
 * préserver le contraste sur les surfaces dynamiques du launcher.
 */
private fun confidenceColor(percent: Int, night: Boolean = false): Color = when {
    percent >= 80 -> if (night) Color(0xFF81C784) else Color(0xFF388E3C)
    percent >= 50 -> if (night) Color(0xFFFFB74D) else Color(0xFFF57C00)
    else -> if (night) Color(0xFFEF9A9A) else Color(0xFFC62828)
}

@Composable
private fun confidenceTextColor(percent: Int): ColorProvider =
    // Mémoïsation identique à ConfidencePill — le percent change rarement.
    LocalNightMode.current.let { night ->
        remember(percent, night) { ColorProvider(confidenceColor(percent, night)) }
    }
