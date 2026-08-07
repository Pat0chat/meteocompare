package com.meteocompare.app.widget

import android.content.Context
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
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.applyPersistedLocale
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
import com.meteocompare.app.MainActivity
import com.meteocompare.app.domain.model.WeatherCondition

// ─── Sélection de layout ────────────────────────────────────────────────────
//
// SizeMode.Exact expose la taille RÉELLE du container via LocalSize (par
// opposition à Responsive qui expose une taille "bucket" pré-configurée). On
// prend Exact pour éviter le piège : sur un launcher à cellules de 90dp, un
// widget physique 2×1 fait 180dp de large — pile la valeur d'un bucket
// "3×1 = 180dp" qui aurait été choisi par Responsive et déclenché MediumLayout
// (badge à droite) au lieu de SmallLayout (badge sous la temp). Exact permet
// des seuils ajustés à la réalité des grilles Android (74-130dp par cellule
// selon launcher).
//
// Seuils choisis pour couvrir la variance cross-launcher :
//   - Tiny : width < 105dp AND height < 105dp. Cible le 1×1 physique. Le
//     ET sur les deux dimensions évite qu'un widget 2×1 très étroit (édge case
//     launchers custom) ne soit classé Tiny. En pratique 1×1 = ~40-90dp
//     dans chaque dim selon le launcher.
//   - Small : 105dp ≤ width < 210dp. Couvre 2×1 physique jusqu'à ~103dp/cellule
//     (Samsung).
//   - Medium : 210 ≤ width < 320dp. Couvre 3×1 physique typique.
//   - Large : width ≥ 320dp AND width < 380dp. Couvre 4×1 physique.
//   - WideRow (5×1) : width ≥ 380dp AND height < EXTRA_LARGE_MIN_HEIGHT_DP.
//     Couvre 5×1. Utilise LargeLayout avec 1-2 items de prévision inline pour
//     tirer parti de la largeur supplémentaire.
//   - ExtraLarge (4×2) : width ≥ 220dp AND height ≥ 130dp AND width < 380dp.
//     La double condition width+height évite de mal classer un widget 1-cellule
//     sur launcher à cellules hautes.
//   - ExtraLargeWide (5×2) : width ≥ 380dp AND height ≥ 130dp. Même layout
//     qu'ExtraLarge mais avec 5 items de prévision au lieu de 4.
/**
 * Padding intérieur par taille de widget.
 *
 * ─── Pourquoi un padding différent selon la taille ? ─────────────────────
 * L'ancien code utilisait un padding uniforme de 10.dp pour toutes les
 * tailles. Sur 2×1, c'est OK — l'espace est déjà minuscule, tout serré
 * est acceptable. Mais sur 3×1, 4×1 et 4×2 :
 *
 *   - Le contenu touche presque les bords → aspect "collé" peu premium.
 *   - Sur les launchers qui appliquent un liseré léger autour du widget
 *     (One UI, MIUI), le texte semble sortir du cadre.
 *   - Le confidence pill à droite en 3×1/4×1 se retrouve pratiquement
 *     contre le bord droit, sans respiration visuelle.
 *
 * Le padding horizontal reste progressif selon la largeur. Le padding vertical
 * est désormais adaptatif : il conserve les valeurs confortables ci-dessous
 * sur une cellule haute, mais descend jusqu'à 4 dp sur les launchers dont une
 * rangée ne laisse que 55–70 dp au widget.
 *   - Small (2×1)      : 8 dp horizontal, 4–8 dp vertical.
 *   - Medium (3×1)     : 14 dp horizontal, 4–10 dp vertical.
 *   - Large/Wide       : 18 dp horizontal, 4–12 dp vertical.
 *   - ExtraLarge (×2)  : 16 dp horizontal, 7–13 dp vertical.
 *
 * Les valeurs restent SYMÉTRIQUES gauche/droite pour que le contenu reste
 * centré au regard, et légèrement plus resserrées verticalement que
 * horizontalement pour tirer parti de la forme 3-4:1 des layouts.
 */
private data class WidgetPadding(val horizontal: Dp, val vertical: Dp)

// Tiny (1×1) : padding minimum pour préserver le maximum de contenu utile.
// Sur 40-90dp par côté, 8dp de padding "mange" déjà 20-40% de la surface,
// mais moins et le contenu touche les bords.
private val TinyPadding = WidgetPadding(6.dp, 4.dp)
private val SmallPadding = WidgetPadding(8.dp, 8.dp)
private val MediumPadding = WidgetPadding(14.dp, 10.dp)
private val LargePadding = WidgetPadding(18.dp, 12.dp)
private val CompactTallPadding = WidgetPadding(12.dp, 12.dp)
private val ExtraLargePadding = WidgetPadding(16.dp, 12.dp)

// Espace explicite entre deux cartes de prévision. Un vrai Spacer est plus
// fiable qu'un padding porté par un enfant pondéré : selon le launcher et la
// traduction RemoteViews de Glance, ce padding pouvait être absorbé dans la
// largeur de la surface et les fonds arrondis semblaient encore se toucher.
private val ForecastCardSpacing = 8.dp

/**
 * Thème résolu (dark/light) — passé via [CompositionLocal] pour éviter que
 * chaque helper couleur ne recalcule `ctx.resources.configuration.uiMode` à
 * son tour. L'ancienne version appelait `isNightMode()` dans 6+ endroits par
 * render (chaque `onContainerColor()`, `onContainerColorMuted()`,
 * `resolveOnContainerColor()` faisait un accès Configuration + bit-and) — sur
 * un widget avec strip 4×2 c'est ~30 lookups Configuration par recomposition.
 *
 * Avec ce local, on lit Configuration UNE fois au top du composable et on
 * propage la valeur booléenne — un simple int en pratique. Le gain n'est pas
 * critique (Configuration read est bon marché) mais rend le code plus propre
 * et matche la façon dont GlanceTheme fonctionne en interne.
 */
private val LocalNightMode = staticCompositionLocalOf { false }

/**
 * Widget MeteoCompare — reproduit un résumé compact de la [TodaySummaryCard]
 * sur l'écran d'accueil.
 *
 * Neuf points d'entrée (1×1 à 5×2) sont supportés via [SizeMode.Exact], puis
 * reclassés d'après la taille réellement fournie par le launcher :
 *
 *   - **1×1** : icône, température et confiance en pile compacte.
 *   - **2×1** : icône + température actuelle | ville + confiance dessous.
 *     Mode "coup d'œil" — un pouce sait s'il fait beau et si la prévision est
 *     fiable.
 *
 *   - **3×1** : + min/max du jour + badge de confiance à droite.
 *
 *   - **4×1** : + couverture nuageuse ou pluie avec confiance associée.
 *     Résumé complet, quasi-parité avec la TodaySummaryCard.
 *
 *   - **4×2** : ajoute au 4×1 un strip de 5 prévisions étendues (5 prochaines
 *     heures OU 5 prochains jours selon le paramètre utilisateur).
 *
 * L'utilisateur configure : ville affichée, opacité du fond (0-100%), mode
 * de prévision étendue (Hourly/Daily) — tout accessible via l'activity de
 * config au drop du widget ou via "Reconfigurer" (long-press, Android 12+).
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
 * pour signaler "il faut re-fetch". Ce tick est inclus dans les clés du
 * `LaunchedEffect` ci-dessous : chaque incrément invalide l'effet et
 * re-déclenche `loadWidgetData`, qui à son tour respecte le seuil de
 * fraîcheur cache défini dans les préférences utilisateur.
 */
internal class MeteoWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override val stateDefinition = PreferencesGlanceStateDefinition

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
        // ignorant le réglage app — bug reporté sur les widgets 4×2 avec
        // "Vent/Pluie" affichés même quand l'app est en anglais.
        val appCtx = applyPersistedLocale(context.applicationContext)
        provideContent {
            // Lecture des prefs Glance. Le worker met à jour le tick puis appelle
            // explicitement MeteoWidget.update() pour notifier le launcher.
            val prefs = currentState<Preferences>()
            val cityId = prefs[WidgetPreferences.CityIdKey]
            val opacityPct = (prefs[WidgetPreferences.OpacityPctKey]
                ?: WidgetPreferences.DEFAULT_OPACITY_PCT).coerceIn(0, 100)
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

            // Chargement des données asynchrone. `remember` persiste la
            // dernière donnée bonne à travers les recompositions ; LaunchedEffect
            // re-fetch quand cityId/forecastMode/refreshTick change.
            // L'état initial est Loading (pas NotConfigured) pour éviter le
            // flash "Configurer" quand on recompose une ville déjà configurée.
            var data by remember {
                mutableStateOf<WidgetData>(
                    if (cityId == null) WidgetData.NotConfigured else WidgetData.Loading
                )
            }
            LaunchedEffect(cityId, forecastMode, refreshTick) {
                data = loadWidgetData(appCtx, cityId, forecastMode)
            }

            // Résolution UNE fois du mode nuit et propagation via CompositionLocal.
            // Voir docblock de LocalNightMode pour le pourquoi.
            val night = appCtx.resources.configuration
                .let { (it.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES }
            // Override LocalContext avec l'app context localisé — voir le
            // docblock du override ci-dessus pour l'étage #2 du fix.
            CompositionLocalProvider(
                LocalContext provides appCtx,
                LocalNightMode provides night
            ) {
                GlanceTheme {
                    WidgetContent(
                        data = data,
                        opacityPct = opacityPct,
                        customBgArgb = customBgArgb,
                        customTextArgb = customTextArgb
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
    opacityPct: Int,
    customBgArgb: Int?,
    customTextArgb: Int?
) {
    // ─── Résolution des couleurs ────────────────────────────────────────
    // Trois cas :
    //   1. Aucun custom → couleurs Material historiques (primaryContainer,
    //      onPrimaryContainer) selon dark/light mode. Défaut.
    //   2. Bg custom SEUL → utiliser le bg choisi, calculer une couleur texte
    //      contrastée automatiquement (blanc/noir selon luminance du bg).
    //   3. Text custom aussi → utiliser les deux tels quels.
    //
    // La couleur "muted" (utilisée pour cityName, min/max, extras dans les
    // gros layouts) est dérivée du texte principal via .copy(alpha = 0.7f)
    // — même contraste relatif quel que soit le choix de l'utilisateur.
    //
    // Mémoïsation : les 3 ColorProviders sont recréés uniquement quand une
    // des dépendances change (nightMode, opacityPct, ou choix custom). En
    // pratique, dans la durée d'affichage d'un widget, ces valeurs bougent
    // rarement — la mémoïsation évite des allocations inutiles à chaque
    // recomposition.
    val night = LocalNightMode.current

    val baseContainerColor = remember(night, customBgArgb) {
        customBgArgb?.let { Color(it) }
            ?: if (night) primaryContainerDark else primaryContainerLight
    }
    val baseOnContainerColor = remember(night, customTextArgb, customBgArgb) {
        when {
            // Priorité 1 : texte choisi par l'utilisateur.
            customTextArgb != null -> Color(customTextArgb)
            // Priorité 2 : fond choisi mais pas de texte → auto-contrast.
            // Calcul via luminance perceptuelle Compose (WCAG-ish). Sur un
            // fond bleu foncé #1976D2 (luminance ~0.2), on prend blanc ;
            // sur un fond blanc/gris clair (luminance > 0.5), on prend noir.
            customBgArgb != null -> {
                if (Color(customBgArgb).luminance() > 0.5f) Color.Black else Color.White
            }
            // Priorité 3 : rien de custom → défaut Material.
            else -> if (night) onPrimaryContainerDark else onPrimaryContainerLight
        }
    }

    val onContainer = remember(baseOnContainerColor) {
        ColorProvider(baseOnContainerColor)
    }
    val onContainerMuted = remember(baseOnContainerColor) {
        ColorProvider(baseOnContainerColor.copy(alpha = 0.7f))
    }
    val softSurface = remember(baseOnContainerColor, night) {
        ColorProvider(baseOnContainerColor.copy(alpha = if (night) 0.12f else 0.08f))
    }
    val raisedSurface = remember(baseOnContainerColor, night) {
        ColorProvider(baseOnContainerColor.copy(alpha = if (night) 0.20f else 0.13f))
    }
    val container = remember(baseContainerColor, opacityPct) {
        ColorProvider(baseContainerColor.copy(alpha = opacityPct / 100f))
    }

    // Padding calculé selon la taille du widget — voir docblock WidgetPadding
    // pour la motivation des valeurs par taille.
    val size = LocalSize.current
    val widthDp = size.width.value
    val heightDp = size.height.value
    val layoutKind = classifyWidgetLayout(widthDp, heightDp)
    val padding = when (layoutKind) {
        WidgetLayoutKind.TINY -> WidgetPadding(
            horizontal = TinyPadding.horizontal,
            vertical = singleRowContainerVerticalPaddingDp(heightDp, layoutKind).dp
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
            vertical = singleRowContainerVerticalPaddingDp(heightDp, layoutKind).dp
        )
        WidgetLayoutKind.LARGE,
        WidgetLayoutKind.WIDE -> WidgetPadding(
            horizontal = LargePadding.horizontal,
            vertical = singleRowContainerVerticalPaddingDp(heightDp, layoutKind).dp
        )
        WidgetLayoutKind.SMALL -> WidgetPadding(
            horizontal = SmallPadding.horizontal,
            vertical = singleRowContainerVerticalPaddingDp(heightDp, layoutKind).dp
        )
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(container)
            .cornerRadius(
                if (layoutKind == WidgetLayoutKind.TINY) {
                    18.dp
                } else {
                    22.dp
                }
            )
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = padding.horizontal, vertical = padding.vertical)
    ) {
        when {
            data.error != null -> ErrorLayout(
                error = data.error,
                onContainer = onContainer,
                onContainerMuted = onContainerMuted,
                softSurface = softSurface
            )
            else -> {
                // Sélection du layout via la taille exacte du container. Voir
                // les constantes en tête de fichier pour les seuils et leurs
                // motivations.
                //
                // Important : le check TinyLayout est FIRST — le cas 1×1 fait
                // à la fois width < 105 et height < 105, ce qui aurait matché
                // "else → Small" sinon (SmallLayout ne gère pas les hauteurs
                // < 40dp gracieusement).
                //
                // Le check "wide" (5×) vient APRÈS le check ExtraLarge pour
                // que le 5×2 aille en ExtraLargeLayout avec 5 items (via son
                // paramètre showFiveItems calculé depuis widthDp), pas en
                // LargeLayout single-row. La différence est portée par le
                // heightDp du bucket ExtraLarge.
                when (layoutKind) {
                    WidgetLayoutKind.TINY ->
                        TinyLayout(data, onContainer)
                    WidgetLayoutKind.EXTRA_LARGE ->
                        ExtraLargeLayout(
                            data = data,
                            onContainer = onContainer,
                            onContainerMuted = onContainerMuted,
                            softSurface = softSurface,
                            raisedSurface = raisedSurface,
                            onContainerArgb = baseOnContainerColor.toArgb(),
                            showFiveItems = extendedForecastItemCount(widthDp) == 5,
                            showExtras = widthDp >= MEDIUM_MAX_WIDTH_DP
                        )
                    WidgetLayoutKind.COMPACT_TALL ->
                        CompactTallLayout(
                            data = data,
                            onContainer = onContainer,
                            onContainerMuted = onContainerMuted,
                            softSurface = softSurface,
                            raisedSurface = raisedSurface,
                            onContainerArgb = baseOnContainerColor.toArgb()
                        )
                    WidgetLayoutKind.WIDE ->
                        LargeLayout(
                            data = data,
                            onContainer = onContainer,
                            onContainerMuted = onContainerMuted,
                            softSurface = softSurface,
                            raisedSurface = raisedSurface,
                            inlineForecastItems = inlineForecastItemCount(widthDp)
                        )
                    WidgetLayoutKind.LARGE ->
                        LargeLayout(
                            data = data,
                            onContainer = onContainer,
                            onContainerMuted = onContainerMuted,
                            softSurface = softSurface,
                            raisedSurface = raisedSurface,
                            inlineForecastItems = 0
                        )
                    WidgetLayoutKind.MEDIUM ->
                        MediumLayout(data, onContainer, onContainerMuted, softSurface)
                    WidgetLayoutKind.SMALL -> SmallLayout(data, onContainer)
                }
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
 * omis — le layout 2×1+ les couvre déjà.
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
private fun TinyLayout(data: WidgetData, onContainer: ColorProvider) {
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
        WeatherGlyph(data.currentCondition, sizeDp = glyphSize)
        Text(
            text = formatTemp(data.currentTemp),
            style = TextStyle(
                color = onContainer,
                fontSize = tempSize,
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
                        fontSize = if (dense) 7.sp else 9.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Layout 2×1 — icône | Column(temp, confidence%).
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
private fun SmallLayout(data: WidgetData, onContainer: ColorProvider) {
    val size = LocalSize.current
    val profile = singleRowWidgetHeightProfile(size.height.value)
    val showCity = shouldShowCityInSmallWidget(size.width.value, size.height.value)
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
        WeatherGlyph(data.currentCondition, sizeDp = glyphSize)
        Spacer(GlanceModifier.width(if (profile == SingleRowWidgetHeightProfile.VERY_DENSE) 4.dp else 6.dp))
        Column {
            if (showCity) {
                data.cityName?.let {
                    Text(
                        text = it,
                        style = TextStyle(
                            color = onContainer,
                            fontSize = citySize,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
                Spacer(GlanceModifier.height(1.dp))
            }
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainer,
                    fontSize = tempSize,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            data.confidencePct?.let {
                Text(
                    text = "$it%",
                    style = TextStyle(
                        color = confidenceTextColor(it),
                        fontSize = confidenceSize,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/** Layout 3×1 : ligne icône+temp | ville sur min/max | badge de confiance. */
@Composable
private fun MediumLayout(
    data: WidgetData,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    softSurface: ColorProvider
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
            WeatherGlyph(data.currentCondition, sizeDp = glyphSize)
            Spacer(GlanceModifier.width(if (profile == SingleRowWidgetHeightProfile.VERY_DENSE) 4.dp else 6.dp))
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainer,
                    fontSize = tempSize,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.width(if (profile == SingleRowWidgetHeightProfile.REGULAR) 10.dp else 7.dp))

        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .background(softSurface)
                .cornerRadius(if (profile == SingleRowWidgetHeightProfile.VERY_DENSE) 9.dp else 12.dp)
                .padding(
                    horizontal = if (profile == SingleRowWidgetHeightProfile.REGULAR) 10.dp else 7.dp,
                    vertical = centerVerticalPadding
                )
        ) {
            data.cityName?.let {
                Text(
                    text = it,
                    style = TextStyle(
                        color = onContainer,
                        fontSize = citySize,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
            Text(
                text = formatMinMax(data.tempMin, data.tempMax),
                style = TextStyle(color = onContainerMuted, fontSize = minMaxSize),
                maxLines = 1
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
 * Layout 4×1 / 5×1 : version enrichie avec 3 lignes centrales — ville,
 * min/max, ligne d'extras contextuels (cloud cover, pluie avec confiance).
 *
 * @param inlineForecastItems Nombre d'items de prévision "inline" à afficher
 *   à droite du bloc principal, AVANT le badge de confiance. Utilisé pour
 *   remplir l'espace supplémentaire en 5×1 sans passer à un vrai layout
 *   2 rangées. 0 = comportement 4×1 historique, 2 = variante 5×1.
 *
 * ─── Pourquoi paramétrer plutôt qu'un layout séparé Wide5x1Layout ? ─────
 * Le composable est presque entièrement identique — seule la Row de droite
 * change (badge vs badge+2 items). Dédupliquer avec un flag évite ~80
 * lignes de copie et garantit que les tweaks futurs (padding, tailles de
 * font, couleurs) s'appliquent aux DEUX variantes automatiquement. Le
 * risque de "flag hell" est faible ici : un seul param, sémantique claire.
 */
@Composable
private fun LargeLayout(
    data: WidgetData,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    softSurface: ColorProvider,
    raisedSurface: ColorProvider,
    inlineForecastItems: Int = 0
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
            WeatherGlyph(data.currentCondition, sizeDp = glyphSize)
            Spacer(GlanceModifier.width(if (profile == SingleRowWidgetHeightProfile.VERY_DENSE) 4.dp else 6.dp))
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainer,
                    fontSize = currentTempSize,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.width(if (profile == SingleRowWidgetHeightProfile.REGULAR) 12.dp else 8.dp))

        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .background(softSurface)
                .cornerRadius(if (profile == SingleRowWidgetHeightProfile.VERY_DENSE) 9.dp else 12.dp)
                .padding(
                    horizontal = if (profile == SingleRowWidgetHeightProfile.REGULAR) 10.dp else 7.dp,
                    vertical = when (profile) {
                        SingleRowWidgetHeightProfile.VERY_DENSE -> 3.dp
                        SingleRowWidgetHeightProfile.DENSE -> 4.dp
                        SingleRowWidgetHeightProfile.REGULAR -> 6.dp
                    }
                )
        ) {
            data.cityName?.let {
                Text(
                    text = it,
                    style = TextStyle(
                        color = onContainer,
                        fontSize = citySize,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
            Text(
                text = formatMinMax(data.tempMin, data.tempMax),
                style = TextStyle(color = onContainerMuted, fontSize = minMaxSize),
                maxLines = 1
            )
            val extras = buildExtrasLine(data)
            if (showExtras && extras.isNotEmpty()) {
                Spacer(GlanceModifier.height(1.dp))
                Text(
                    text = extras,
                    style = TextStyle(color = onContainerMuted, fontSize = extrasSize),
                    maxLines = 1
                )
            }
        }

        if (inlineForecastItems > 0 && data.forecasts.isNotEmpty()) {
            Spacer(GlanceModifier.width(if (profile == SingleRowWidgetHeightProfile.VERY_DENSE) 5.dp else 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                data.forecasts.take(inlineForecastItems).forEachIndexed { index, item ->
                    if (index > 0) Spacer(GlanceModifier.width(if (profile == SingleRowWidgetHeightProfile.REGULAR) ForecastCardSpacing else 5.dp))
                    ForecastItemCard(
                        item = item,
                        onContainer = onContainer,
                        onContainerMuted = onContainerMuted,
                        softSurface = softSurface,
                        raisedSurface = raisedSurface,
                        mode = data.forecastMode,
                        compact = true,
                        heightProfile = ForecastCardHeightProfile.DENSE,
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
    onContainerArgb: Int
) {
    val size = LocalSize.current
    val narrow = size.width.value < 150f
    val headerBudgetDp = compactTallHeaderHeightBudgetDp(narrow)
    val sectionGapDp = 6f
    val showPanelHeader = !narrow && size.height.value >= 165f
    val forecastCardProfile = forecastBottomCardHeightProfile(
        widgetHeightDp = size.height.value,
        headerHeightDp = headerBudgetDp + forecastPanelHeaderHeightDp(
            showHeader = showPanelHeader,
            compact = true
        ),
        sectionGapDp = sectionGapDp
    )

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherGlyph(data.currentCondition, sizeDp = if (narrow) 30 else 36)
            Spacer(GlanceModifier.width(if (narrow) 5.dp else 8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                data.cityName?.let {
                    Text(
                        text = it,
                        style = TextStyle(
                            color = onContainer,
                            fontSize = if (narrow) 10.sp else 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
                Text(
                    text = formatTemp(data.currentTemp),
                    style = TextStyle(
                        color = onContainer,
                        fontSize = if (narrow) 21.sp else 24.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                val minMax = formatMinMax(data.tempMin, data.tempMax)
                if (minMax.isNotEmpty()) {
                    Text(
                        text = minMax,
                        style = TextStyle(
                            color = onContainerMuted,
                            fontSize = if (narrow) 9.sp else 10.sp
                        ),
                        maxLines = 1
                    )
                }
                data.confidencePct?.let {
                    Text(
                        text = "$it%",
                        style = TextStyle(
                            color = confidenceTextColor(it),
                            fontSize = if (narrow) 8.sp else 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(GlanceModifier.height(sectionGapDp.dp))

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
                softSurface = softSurface,
                compact = true,
                textColorArgb = onContainerArgb,
                outerHorizontalPadding = CompactTallPadding.horizontal,
                headerHeightBudgetDp = headerBudgetDp,
                modifier = GlanceModifier.defaultWeight()
            )

            data.forecasts.isNotEmpty() -> ForecastCardsPanel(
                items = data.forecasts,
                mode = data.forecastMode,
                itemCount = 2,
                onContainer = onContainer,
                onContainerMuted = onContainerMuted,
                softSurface = softSurface,
                raisedSurface = raisedSurface,
                compact = true,
                veryCompact = narrow,
                heightProfile = forecastCardProfile,
                showHeader = showPanelHeader,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )

            else -> {
                val extras = buildExtrasLine(data)
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
                        text = extras.ifEmpty { formatMinMax(data.tempMin, data.tempMax) },
                        style = TextStyle(
                            color = onContainerMuted,
                            fontSize = 10.sp,
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
 * Layout 4×2 / 5×2 : top strip identique au 4×1 + bas strip avec 4 ou 5
 * items de prévision étendue (heures ou jours selon la config utilisateur).
 *
 * @param showFiveItems `true` pour les variantes 4×2 et 5×2 — affiche 5 items dans
 *   le bas strip (le 3×2 reste limité à 4). Utilise l'espace supplémentaire en largeur
 *   sans surcharger le rendu.
 * @param showExtras masque la ligne vent/humidité sur le format 3×2, où elle
 *   surcharge le bandeau supérieur. Elle reste affichée à partir du 4×2.
 *
 * Les tailles sont adaptées à la hauteur ET à la largeur exactes : un profil
 * très compact protège les 3×2 et les widgets sous 150 dp, un profil compact
 * couvre les cellules sous 185 dp, puis le rendu confortable prend le relais.
 *
 * Note : quand `showFiveItems=true` et que `data.forecasts` contient moins
 * de 5 items (edge case si le fetch est parti sur un horizon plus court),
 * `.take(n)` renvoie ce qui est disponible sans crash — les weight-column
 * s'adaptent en s'élargissant proportionnellement.
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
    showFiveItems: Boolean = false,
    showExtras: Boolean = true
) {
    val size = LocalSize.current
    val widthDp = size.width.value
    val heightDp = size.height.value
    val itemCount = if (showFiveItems) 5 else 4
    val sizeProfile = twoRowWidgetSizeProfile(widthDp, heightDp)
    val veryCompact = sizeProfile == TwoRowWidgetSizeProfile.VERY_DENSE
    val compactHeight = sizeProfile != TwoRowWidgetSizeProfile.REGULAR
    val actualShowExtras = showExtras && !veryCompact
    val showPanelHeader = shouldShowForecastPanelHeader(heightDp, sizeProfile)
    val sectionGapDp = when {
        veryCompact -> 5f
        compactHeight -> 7f
        else -> 10f
    }
    val headerHeightBudgetDp = miniForecastHeaderHeightBudgetDp(
        compact = compactHeight,
        showExtras = actualShowExtras
    )
    val forecastCardProfile = forecastBottomCardHeightProfile(
        widgetHeightDp = heightDp,
        headerHeightDp = headerHeightBudgetDp + forecastPanelHeaderHeightDp(
            showHeader = showPanelHeader,
            compact = compactHeight
        ),
        sectionGapDp = sectionGapDp
    )

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherGlyph(
                data.currentCondition,
                sizeDp = when {
                    veryCompact -> 32
                    compactHeight -> 38
                    else -> 44
                }
            )
            Spacer(GlanceModifier.width(if (veryCompact) 5.dp else 8.dp))
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainer,
                    fontSize = when {
                        veryCompact -> 22.sp
                        compactHeight -> 25.sp
                        else -> 28.sp
                    },
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
                            fontSize = when {
                                veryCompact -> 11.sp
                                compactHeight -> 14.sp
                                else -> 16.sp
                            },
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
                Text(
                    text = formatMinMax(data.tempMin, data.tempMax),
                    style = TextStyle(
                        color = onContainerMuted,
                        fontSize = when {
                            veryCompact -> 9.sp
                            compactHeight -> 12.sp
                            else -> 14.sp
                        }
                    ),
                    maxLines = 1
                )
                if (actualShowExtras) {
                    val extras = buildExtrasLine(data)
                    if (extras.isNotEmpty()) {
                        Spacer(GlanceModifier.height(1.dp))
                        Text(
                            text = extras,
                            style = TextStyle(
                                color = onContainerMuted,
                                fontSize = if (compactHeight) 9.sp else 11.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            data.confidencePct?.let {
                Spacer(GlanceModifier.width(if (veryCompact) 5.dp else 8.dp))
                ConfidencePill(percent = it, compact = compactHeight)
            }
        }

        Spacer(GlanceModifier.height(sectionGapDp.dp))

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
                softSurface = softSurface,
                compact = compactHeight,
                textColorArgb = onContainerArgb,
                headerHeightBudgetDp = headerHeightBudgetDp,
                modifier = GlanceModifier.defaultWeight()
            )
            data.forecasts.isEmpty() -> Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "…",
                    style = TextStyle(color = onContainerMuted, fontSize = 12.sp)
                )
            }
            else -> ForecastCardsPanel(
                items = data.forecasts,
                mode = data.forecastMode,
                itemCount = itemCount,
                onContainer = onContainer,
                onContainerMuted = onContainerMuted,
                softSurface = softSurface,
                raisedSurface = raisedSurface,
                compact = compactHeight,
                veryCompact = veryCompact,
                heightProfile = forecastCardProfile,
                showHeader = showPanelHeader,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
        }
    }
}

/**
 * Mini-prévision 12 h pour les widgets hauts 2×2 à 5×2.
 *
 * Le bitmap répartit les douze heures sur deux lignes de six cellules. Chaque
 * cellule porte son heure, sa température, la condition météo de consensus et
 * le risque de pluie. Le rendu utilise ainsi la hauteur des formats ×2 au lieu de laisser
 * une bande étroite centrée dans un grand espace vide.
 */
@Composable
private fun TwelveHourForecastStrip(
    data: WidgetData,
    softSurface: ColorProvider,
    compact: Boolean,
    textColorArgb: Int,
    outerHorizontalPadding: Dp = ExtraLargePadding.horizontal,
    headerHeightBudgetDp: Float,
    modifier: GlanceModifier = GlanceModifier
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val renderDensity = density.coerceAtMost(2f)
    val profile = miniForecastProfileForWidth(size.width.value)
    val chartHorizontalPaddingDp = when (profile) {
        MiniForecastSizeProfile.COMPACT_2X2 -> 4f
        MiniForecastSizeProfile.MEDIUM_3X2 -> 6f
        MiniForecastSizeProfile.EXPANDED_4X2 -> 8f
    }
    val availableWidthDp = (
        size.width.value -
            outerHorizontalPadding.value * 2f -
            chartHorizontalPaddingDp * 2f
        ).coerceAtLeast(80f)
    val widthPx = (availableWidthDp * renderDensity).toInt().coerceAtLeast(1)
    // La hauteur du bitmap suit désormais la hauteur exacte du widget. Le
    // calcul retire le bandeau courant et les paddings, puis laisse les deux
    // lignes de heatmap grandir jusqu'à une borne adaptée à la largeur.
    val chartHeightDp = miniForecastChartHeightDp(
        widgetHeightDp = size.height.value,
        headerHeightDp = headerHeightBudgetDp,
        sectionGapDp = if (compact) 6f else 10f,
        profile = profile
    )
    val heightPx = (chartHeightDp * renderDensity).toInt().coerceAtLeast(1)
    val precipColorArgb = 0xFF1976D2.toInt()

    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val formatter = remember(is24) {
        java.time.format.DateTimeFormatter.ofPattern(
            if (is24) "H'h'" else "h a",
            java.util.Locale.getDefault()
        )
    }
    val timelineLabels = data.hourlyStartTime?.let { start ->
        List(12) { offset -> start.plusHours(offset.toLong()).format(formatter) }
    } ?: List(12) { offset -> "+${offset}h" }

    val bitmap = remember(
        data.forecastMode,
        data.next12hTemps,
        data.next12hPrecipProb,
        data.next12hPrecipMm,
        data.next12hConditions,
        timelineLabels,
        widthPx,
        heightPx,
        textColorArgb,
        profile
    ) {
        if (data.forecastMode?.isModernHeatmapChartForecast() == true) {
            WidgetHeatmapTrendForecastRenderer.render(
                widthPx = widthPx,
                heightPx = heightPx,
                temps = data.next12hTemps,
                precipProbabilities = data.next12hPrecipProb,
                precipAmountsMm = data.next12hPrecipMm,
                conditions = data.next12hConditions,
                precipColorArgb = precipColorArgb,
                textColorArgb = textColorArgb,
                timelineLabels = timelineLabels,
                profile = profile
            )
        } else if (data.forecastMode?.isHeatmapChartForecast() == true) {
            WidgetHeatmapForecastRenderer.render(
                widthPx = widthPx,
                heightPx = heightPx,
                temps = data.next12hTemps,
                precipProbabilities = data.next12hPrecipProb,
                precipAmountsMm = data.next12hPrecipMm,
                conditions = data.next12hConditions,
                precipColorArgb = precipColorArgb,
                textColorArgb = textColorArgb,
                timelineLabels = timelineLabels,
                profile = profile
            )
        } else {
            WidgetMiniForecastRenderer.render(
                widthPx = widthPx,
                heightPx = heightPx,
                temps = data.next12hTemps,
                precipProbabilities = data.next12hPrecipProb,
                precipAmountsMm = data.next12hPrecipMm,
                conditions = data.next12hConditions,
                precipColorArgb = precipColorArgb,
                textColorArgb = textColorArgb,
                timelineLabels = timelineLabels,
                profile = profile
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(softSurface)
            .cornerRadius(14.dp)
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
 * L'en-tête donne immédiatement le contexte, tandis que les cartes utilisent
 * une bande thermique, une hiérarchie typographique plus nette et des micro-
 * badges pour les nuages et la pluie.
 */
@Composable
private fun ForecastCardsPanel(
    items: List<WidgetForecastItem>,
    mode: ForecastMode?,
    itemCount: Int,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    softSurface: ColorProvider,
    raisedSurface: ColorProvider,
    compact: Boolean,
    veryCompact: Boolean,
    heightProfile: ForecastCardHeightProfile,
    showHeader: Boolean,
    modifier: GlanceModifier = GlanceModifier
) {
    val ctx = LocalContext.current
    val normalizedMode = mode?.normalized()
    val title = when (normalizedMode) {
        ForecastMode.HOURLY -> ctx.getString(R.string.widget_forecast_hours)
        ForecastMode.DAILY -> ctx.getString(R.string.widget_forecast_days)
        else -> ctx.getString(R.string.widget_forecast_generic)
    }
    val visibleItems = items.take(itemCount)

    Column(modifier = modifier) {
        if (showHeader) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = onContainer,
                        fontSize = if (compact) 9.sp else 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Box(
                    modifier = GlanceModifier
                        .background(raisedSurface)
                        .cornerRadius(8.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = visibleItems.size.toString(),
                        style = TextStyle(
                            color = onContainerMuted,
                            fontSize = if (compact) 7.sp else 8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }
            Spacer(GlanceModifier.height(if (compact) 3.dp else 5.dp))
        }

        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            visibleItems.forEachIndexed { index, item ->
                if (index > 0) {
                    Spacer(
                        GlanceModifier.width(
                            when {
                                veryCompact -> 4.dp
                                compact -> 6.dp
                                else -> ForecastCardSpacing
                            }
                        )
                    )
                }
                ForecastItemCard(
                    item = item,
                    onContainer = onContainer,
                    onContainerMuted = onContainerMuted,
                    softSurface = softSurface,
                    raisedSurface = raisedSurface,
                    mode = normalizedMode,
                    compact = compact,
                    heightProfile = heightProfile,
                    emphasized = index == 0,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                )
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
            .cornerRadius(16.dp)
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
                        fontSize = if (compact) 9.sp else 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = ctx.getString(R.string.widget_forecast_confidence_hint),
                    style = TextStyle(
                        color = onContainerMuted,
                        fontSize = if (compact) 6.sp else 7.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.height(if (compact) 3.dp else 5.dp))
        }

        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            days.forEachIndexed { index, day ->
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
    Column(
        modifier = modifier
            .cornerRadius(if (compact) 11.dp else 14.dp)
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
                fontSize = if (compact) 7.sp else 9.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Text(
            text = "${day.modelCount}/${day.totalModelCount}",
            style = TextStyle(
                color = onContainerMuted,
                fontSize = if (compact) 6.sp else 7.sp
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
                        fontSize = if (compact) 6.sp else 7.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Text(
                    text = bucket.value,
                    style = TextStyle(
                        color = onContainer,
                        fontSize = if (compact) 7.sp else 9.sp,
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
                        fontSize = if (compact) 6.sp else 7.sp,
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
        is WidgetError.Fetch -> ctx.getString(R.string.widget_error_fetch)
    }
    val symbol = when (error) {
        WidgetError.NotConfigured -> "+"
        WidgetError.Loading -> "↻"
        WidgetError.CityNoLongerInFavorites -> "⌂"
        is WidgetError.Fetch -> "!"
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
                    fontSize = 18.sp,
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
                        fontSize = 15.sp,
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
                    fontSize = 10.sp,
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
                        fontSize = if (tiny) 15.sp else 18.sp,
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
                    fontSize = if (tiny) 8.sp else 12.sp
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
    sizeDp: Int
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val renderDensity = density.coerceAtMost(2f)
    val bitmap = remember(condition, sizeDp, renderDensity) {
        WidgetWeatherIconRenderer.render(
            condition = condition,
            sizePx = (sizeDp * renderDensity).toInt().coerceAtLeast(1)
        )
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = context.getString(weatherDescriptionRes(condition)),
        modifier = GlanceModifier.width(sizeDp.dp).height(sizeDp.dp)
    )
}

private fun weatherDescriptionRes(condition: WeatherCondition?): Int = when (condition) {
    WeatherCondition.CLEAR -> R.string.weather_clear
    WeatherCondition.MAINLY_CLEAR -> R.string.weather_mainly_clear
    WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
    WeatherCondition.OVERCAST -> R.string.weather_overcast
    WeatherCondition.FOG -> R.string.weather_fog
    WeatherCondition.DRIZZLE -> R.string.weather_drizzle
    WeatherCondition.RAIN -> R.string.weather_rain
    WeatherCondition.FREEZING_RAIN -> R.string.weather_freezing_rain
    WeatherCondition.SNOW -> R.string.weather_snow
    WeatherCondition.RAIN_SHOWERS -> R.string.weather_rain_showers
    WeatherCondition.SNOW_SHOWERS -> R.string.weather_snow_showers
    WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
    WeatherCondition.UNKNOWN,
    null -> R.string.weather_unknown
}

@Composable
private fun ForecastItemCard(
    item: WidgetForecastItem,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    softSurface: ColorProvider,
    raisedSurface: ColorProvider,
    mode: ForecastMode?,
    compact: Boolean,
    heightProfile: ForecastCardHeightProfile? = null,
    emphasized: Boolean = false,
    modifier: GlanceModifier = GlanceModifier
) {
    val ctx = LocalContext.current
    val night = LocalNightMode.current
    val profile = heightProfile ?: if (compact) {
        ForecastCardHeightProfile.DENSE
    } else {
        ForecastCardHeightProfile.COMPACT
    }
    val horizontalPadding = when (profile) {
        ForecastCardHeightProfile.DENSE -> 3.dp
        ForecastCardHeightProfile.COMPACT -> 4.dp
        ForecastCardHeightProfile.COMFORTABLE -> 5.dp
        ForecastCardHeightProfile.EXPANDED -> 6.dp
    }
    val verticalPadding = when (profile) {
        ForecastCardHeightProfile.DENSE -> 2.dp
        ForecastCardHeightProfile.COMPACT -> 3.dp
        ForecastCardHeightProfile.COMFORTABLE -> 5.dp
        ForecastCardHeightProfile.EXPANDED -> 6.dp
    }
    val labelSize = when (profile) {
        ForecastCardHeightProfile.DENSE -> 7.sp
        ForecastCardHeightProfile.COMPACT -> 8.sp
        ForecastCardHeightProfile.COMFORTABLE -> 9.sp
        ForecastCardHeightProfile.EXPANDED -> 10.sp
    }
    val baseGlyphSize = when (profile) {
        ForecastCardHeightProfile.DENSE -> 18
        ForecastCardHeightProfile.COMPACT -> 22
        ForecastCardHeightProfile.COMFORTABLE -> 27
        ForecastCardHeightProfile.EXPANDED -> 31
    }
    val dailyGlyphBoost = if (mode?.normalized() == ForecastMode.DAILY &&
        profile != ForecastCardHeightProfile.DENSE
    ) 2 else 0
    val glyphSize = baseGlyphSize + dailyGlyphBoost +
        if (emphasized && profile != ForecastCardHeightProfile.DENSE) 1 else 0
    val temperatureSize = when (profile) {
        ForecastCardHeightProfile.DENSE -> if (emphasized) 11.sp else 10.sp
        ForecastCardHeightProfile.COMPACT -> if (emphasized) 14.sp else 13.sp
        ForecastCardHeightProfile.COMFORTABLE -> if (emphasized) 16.sp else 15.sp
        ForecastCardHeightProfile.EXPANDED -> if (emphasized) 18.sp else 17.sp
    }
    val detailSize = when (profile) {
        ForecastCardHeightProfile.DENSE -> 6.sp
        ForecastCardHeightProfile.COMPACT -> 7.sp
        ForecastCardHeightProfile.COMFORTABLE -> 7.sp
        ForecastCardHeightProfile.EXPANDED -> 8.sp
    }
    val cardBackground = if (emphasized) raisedSurface else softSurface
    val accentColor = remember(item.temp, night) {
        ColorProvider(forecastTemperatureAccent(item.temp, night))
    }
    val precipForeground = remember(night) {
        ColorProvider(if (night) Color(0xFF90CAF9) else Color(0xFF1565C0))
    }
    val precipSurface = remember(night) {
        ColorProvider(
            (if (night) Color(0xFF90CAF9) else Color(0xFF1565C0)).copy(alpha = 0.18f)
        )
    }

    Column(
        modifier = modifier
            .background(cardBackground)
            .cornerRadius(
                when {
                    emphasized && profile != ForecastCardHeightProfile.DENSE -> 15.dp
                    profile == ForecastCardHeightProfile.DENSE -> 10.dp
                    else -> 13.dp
                }
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.label,
            style = TextStyle(
                color = if (emphasized) onContainer else onContainerMuted,
                fontSize = labelSize,
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium
            ),
            maxLines = 1
        )
        WeatherGlyph(condition = item.condition, sizeDp = glyphSize)
        Text(
            text = formatTemp(item.temp),
            style = TextStyle(
                color = onContainer,
                fontSize = temperatureSize,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )

        val hasCloud = item.cloudCoverPct != null
        val hasRain = item.precipProbabilityPct != null
        val showForecastConfidence = hasRain &&
                item.forecastConfidencePct != null &&
                shouldShowForecastCardConfidence(profile)
        if (hasCloud || hasRain) {
            Spacer(GlanceModifier.height(if (profile == ForecastCardHeightProfile.DENSE) 1.dp else 2.dp))
            Column (
                horizontalAlignment = Alignment.CenterHorizontally
            ){
                item.cloudCoverPct?.let { cloud ->
                    ForecastMetricChip(
                        text = "☁ ${cloud.coerceIn(0, 100)}%",
                        foreground = onContainerMuted,
                        background = raisedSurface,
                        fontSize = detailSize,
                        modifier = GlanceModifier.fillMaxWidth()
                    )
                }
                if (hasCloud && hasRain) Spacer(GlanceModifier.height(4.dp))
                item.precipProbabilityPct?.let { rain ->
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ForecastMetricChip(
                            text = "☂ ${rain.coerceIn(0, 100)}%",
                            foreground = precipForeground,
                            background = precipSurface,
                            fontSize = detailSize,
                            modifier = GlanceModifier.fillMaxWidth()
                        )
                        if (showForecastConfidence) {
                            val confidence = item.forecastConfidencePct
                            Spacer(GlanceModifier.height(1.dp))
                            Text(
                                text = ctx.getString(
                                    R.string.widget_forecast_confidence_short,
                                    confidence.coerceIn(0, 100)
                                ),
                                style = TextStyle(
                                    color = confidenceTextColor(confidence),
                                    fontSize = detailSize,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastMetricChip(
    text: String,
    foreground: ColorProvider,
    background: ColorProvider,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: GlanceModifier = GlanceModifier
) {
    Box(
        modifier = modifier
            .background(background)
            .cornerRadius(6.dp)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = foreground,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
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
    val bg = remember(percent, night) { ColorProvider(color.copy(alpha = 0.18f)) }
    val fg = remember(percent, night) { ColorProvider(color) }
    Row(
        modifier = GlanceModifier
            .background(bg)
            .cornerRadius(if (compact) 10.dp else 12.dp)
            .padding(
                horizontal = if (compact) 5.dp else 7.dp,
                vertical = if (compact) 3.dp else 5.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .width(if (compact) 3.dp else 4.dp)
                .height(if (compact) 11.dp else 14.dp)
                .background(fg)
                .cornerRadius(3.dp)
        ) {}
        Spacer(GlanceModifier.width(if (compact) 4.dp else 5.dp))
        Text(
            text = "$percent%",
            style = TextStyle(
                color = fg,
                fontSize = if (compact) 9.sp else 11.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

// ─── Couleurs ───────────────────────────────────────────────────────────

private val primaryContainerLight = Color(0xFFDBE2FF)
private val primaryContainerDark = Color(0xFF283960)
private val onPrimaryContainerLight = Color(0xFF001A41)
private val onPrimaryContainerDark = Color(0xFFDBE2FF)

/**
 * Couleur de confiance selon le %. Les variantes claires utilisent des tons
 * 700, tandis que le mode sombre passe sur des tons 300 plus lumineux pour
 * préserver le contraste sur le container bleu nuit.
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