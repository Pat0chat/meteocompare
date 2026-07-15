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
 * Nouveau padding progressif :
 *   - Small (2×1)      : 8.dp — inchangé, l'espace est trop précieux.
 *   - Medium (3×1)     : 14.dp horizontal, 10.dp vertical — respiration à
 *                        gauche/droite pour éloigner le pill du bord.
 *   - Large (4×1)      : 16.dp horizontal, 12.dp vertical — un poil plus,
 *                        la largeur autorise le confort.
 *   - ExtraLarge (4×2) : 16.dp horizontal, 14.dp vertical — le vertical est
 *                        doublé du 4×1 pour éviter que le strip du bas colle
 *                        au bord bas quand les icônes météo sont hautes.
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
 * Quatre tailles supportées, via [SizeMode.Exact] :
 *
 *   - **2×1** : icône + température actuelle | ville + confiance dessous.
 *     Mode "coup d'œil" — un pouce sait s'il fait beau et si la prévision est
 *     fiable.
 *
 *   - **3×1** : + min/max du jour + badge de confiance à droite.
 *
 *   - **4×1** : + couverture nuageuse ou pluie avec confiance associée.
 *     Résumé complet, quasi-parité avec la TodaySummaryCard.
 *
 *   - **4×2** : ajoute au 4×1 un strip de 4 prévisions étendues (4 prochaines
 *     heures OU 4 prochains jours selon le paramètre utilisateur).
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
 * session ; lire dedans avec [currentState] rend le read réactif — un
 * `updateAppWidgetState` déclenche automatiquement la recomposition.
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
            // Lecture réactive des prefs. Chaque updateAppWidgetState() sur
            // ce widget invalide cette lecture → recomposition automatique.
            val prefs = currentState<Preferences>()
            val cityId = prefs[WidgetPreferences.CityIdKey]
            val opacityPct = (prefs[WidgetPreferences.OpacityPctKey]
                ?: WidgetPreferences.DEFAULT_OPACITY_PCT).coerceIn(0, 100)
            val forecastMode = prefs[WidgetPreferences.ForecastModeKey]
                ?.let { runCatching { ForecastMode.valueOf(it) }.getOrNull() }
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
        WidgetLayoutKind.TINY -> TinyPadding
        WidgetLayoutKind.COMPACT_TALL -> CompactTallPadding
        WidgetLayoutKind.EXTRA_LARGE -> ExtraLargePadding
        WidgetLayoutKind.MEDIUM -> MediumPadding
        WidgetLayoutKind.LARGE,
        WidgetLayoutKind.WIDE -> LargePadding
        WidgetLayoutKind.SMALL -> SmallPadding
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
                            onContainerArgb = baseOnContainerColor.toArgb(),
                            showFiveItems = widthDp >= WIDE_MIN_WIDTH_DP,
                            showExtras = widthDp >= MEDIUM_MAX_WIDTH_DP
                        )
                    WidgetLayoutKind.COMPACT_TALL ->
                        CompactTallLayout(
                            data = data,
                            onContainer = onContainer,
                            onContainerMuted = onContainerMuted,
                            softSurface = softSurface,
                            onContainerArgb = baseOnContainerColor.toArgb()
                        )
                    WidgetLayoutKind.WIDE ->
                        LargeLayout(
                            data = data,
                            onContainer = onContainer,
                            onContainerMuted = onContainerMuted,
                            softSurface = softSurface,
                            inlineForecastItems = 2
                        )
                    WidgetLayoutKind.LARGE ->
                        LargeLayout(
                            data = data,
                            onContainer = onContainer,
                            onContainerMuted = onContainerMuted,
                            softSurface = softSurface,
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
    val ultraCompact = size.height.value < 70f || size.width.value < 60f
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WeatherGlyph(data.currentCondition, sizeDp = if (ultraCompact) 22 else 30)
        Text(
            text = formatTemp(data.currentTemp),
            style = TextStyle(
                color = onContainer,
                fontSize = if (ultraCompact) 13.sp else 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
        data.confidencePct?.let {
            Text(
                text = "$it%",
                style = TextStyle(
                    color = confidenceTextColor(it),
                    fontSize = if (ultraCompact) 8.sp else 9.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

/**
 * Layout 2×1 — icône | Column(temp, confidence%).
 *
 * Nouveau design : la CONFIANCE est affichée sous la température, remplaçant
 * la ville. Sur un 2×1 la ville est identifiée par sa position sur l'écran
 * (l'utilisateur SAIT quelle ville il a choisie), tandis que la confiance
 * est le signal éditorial le plus précieux de l'app — sans elle le widget
 * ressemble à n'importe quelle app météo.
 *
 * Le pourcentage est teinté vert/orange/rouge selon le niveau (helper
 * [confidenceTextColor]) pour être lisible d'un coup d'œil, sans avoir à
 * décoder le nombre.
 */
@Composable
private fun SmallLayout(data: WidgetData, onContainer: ColorProvider) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WeatherGlyph(data.currentCondition, sizeDp = 34)
        Spacer(GlanceModifier.width(6.dp))
        Column {
            data.cityName?.let {
                Text(
                    text = it,
                    style = TextStyle(
                        color = onContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            Spacer(GlanceModifier.height(1.dp))
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainer,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            data.confidencePct?.let {
                Text(
                    text = "$it%",
                    style = TextStyle(
                        color = confidenceTextColor(it),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
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
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherGlyph(data.currentCondition, sizeDp = 38)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainer,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(GlanceModifier.width(10.dp))

        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .background(softSurface)
                .cornerRadius(12.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            data.cityName?.let {
                Text(
                    text = it,
                    style = TextStyle(
                        color = onContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            Text(
                text = formatMinMax(data.tempMin, data.tempMax),
                style = TextStyle(color = onContainerMuted, fontSize = 12.sp)
            )
        }

        data.confidencePct?.let {
            Spacer(GlanceModifier.width(8.dp))
            ConfidencePill(percent = it)
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
    inlineForecastItems: Int = 0
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherGlyph(data.currentCondition, sizeDp = 42)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainer,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(GlanceModifier.width(12.dp))

        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .background(softSurface)
                .cornerRadius(12.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            data.cityName?.let {
                Text(
                    text = it,
                    style = TextStyle(
                        color = onContainer,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            Text(
                text = formatMinMax(data.tempMin, data.tempMax),
                style = TextStyle(color = onContainerMuted, fontSize = 14.sp)
            )
            val extras = buildExtrasLine(data)
            if (extras.isNotEmpty()) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = extras,
                    style = TextStyle(color = onContainerMuted, fontSize = 11.sp)
                )
            }
        }

        // Items de prévision inline (5×1 uniquement). Utile pour tirer
        // parti de la largeur supplémentaire quand le user pose un widget
        // qui fait 5 cellules mais ne veut PAS d'un deuxième row. On
        // affiche 2 mini-items (heure/jour + icône + temp compacte)
        // séparés d'un fin espace vertical.
        //
        // Note : `data.forecasts` peut être vide si le fetch n'a pas fini
        // ou si le mode utilisateur est CONFIDENCE_* (pas de forecasts
        // discrets). Dans ces cas on skip silencieusement — pas de
        // placeholder "…" pour ne pas polluer l'aperçu.
        if (inlineForecastItems > 0 && data.forecasts.isNotEmpty()) {
            Spacer(GlanceModifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                data.forecasts.take(inlineForecastItems).forEachIndexed { index, item ->
                    if (index > 0) Spacer(GlanceModifier.width(ForecastCardSpacing))
                    ForecastItemCard(
                        item = item,
                        onContainer = onContainer,
                        onContainerMuted = onContainerMuted,
                        softSurface = softSurface,
                        compact = true
                    )
                }
            }
        }

        data.confidencePct?.let {
            Spacer(GlanceModifier.width(8.dp))
            ConfidencePill(percent = it)
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
    onContainerArgb: Int
) {
    val narrow = LocalSize.current.width.value < 150f
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherGlyph(data.currentCondition, sizeDp = if (narrow) 32 else 38)
            Spacer(GlanceModifier.width(if (narrow) 6.dp else 8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                data.cityName?.let {
                    Text(
                        text = it,
                        style = TextStyle(
                            color = onContainer,
                            fontSize = if (narrow) 11.sp else 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Text(
                    text = formatTemp(data.currentTemp),
                    style = TextStyle(
                        color = onContainer,
                        fontSize = if (narrow) 22.sp else 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                val minMax = formatMinMax(data.tempMin, data.tempMax)
                if (minMax.isNotEmpty()) {
                    Text(
                        text = minMax,
                        style = TextStyle(color = onContainerMuted, fontSize = 10.sp)
                    )
                }
                data.confidencePct?.let {
                    Text(
                        text = "● $it%",
                        style = TextStyle(
                            color = confidenceTextColor(it),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Spacer(GlanceModifier.height(6.dp))

        when {
            data.confidenceStrip != null -> CompactConfidenceSummary(
                strip = data.confidenceStrip,
                onContainer = onContainer,
                onContainerMuted = onContainerMuted,
                softSurface = softSurface
            )

            data.next12hTemps.isNotEmpty() -> MiniForecastStrip(
                data = data,
                softSurface = softSurface,
                compact = true,
                textColorArgb = onContainerArgb,
                outerHorizontalPadding = CompactTallPadding.horizontal,
                modifier = GlanceModifier.defaultWeight()
            )

            data.forecasts.isNotEmpty() -> Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            ) {
                data.forecasts.take(2).forEachIndexed { index, item ->
                    if (index > 0) Spacer(GlanceModifier.width(ForecastCardSpacing))
                    ForecastItemCard(
                        item = item,
                        onContainer = onContainer,
                        onContainerMuted = onContainerMuted,
                        softSurface = softSurface,
                        compact = true,
                        modifier = GlanceModifier.defaultWeight()
                    )
                }
            }

            else -> {
                val extras = buildExtrasLine(data)
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .background(softSurface)
                        .cornerRadius(12.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = extras.ifEmpty { formatMinMax(data.tempMin, data.tempMax) },
                        style = TextStyle(
                            color = onContainerMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.CompactConfidenceSummary(
    strip: WidgetConfidenceStrip,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    softSurface: ColorProvider
) {
    val night = LocalNightMode.current
    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
        strip.buckets.take(2).forEachIndexed { index, bucket ->
            if (index > 0) Spacer(GlanceModifier.width(ForecastCardSpacing))
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .background(softSurface)
                    .cornerRadius(10.dp)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(ColorProvider(confidenceColor(bucket.percent, night)))
                        .cornerRadius(3.dp)
                ) {}
                Spacer(GlanceModifier.height(3.dp))
                Text(
                    text = bucket.value,
                    style = TextStyle(
                        color = onContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = bucket.label,
                    style = TextStyle(color = onContainerMuted, fontSize = 9.sp)
                )
            }
        }
    }
}

/**
 * Layout 4×2 / 5×2 : top strip identique au 4×1 + bas strip avec 4 (ou 5)
 * items de prévision étendue (heures ou jours selon la config utilisateur).
 *
 * @param showFiveItems `true` pour la variante 5×2 — affiche 5 items dans
 *   le bas strip au lieu de 4. Utilise l'espace supplémentaire en largeur
 *   sans surcharger le rendu.
 * @param showExtras masque la ligne vent/humidité sur le format 3×2, où elle
 *   surcharge le bandeau supérieur. Elle reste affichée à partir du 4×2.
 *
 * Les tailles sont adaptées à la hauteur exacte : le rendu se compacte sous
 * 165dp afin de rester dans le budget des launchers aux cellules basses, tout
 * en conservant des pictogrammes et une température plus généreux au-dessus.
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
    // ARGB brut de la couleur de texte "onContainer", résolu au niveau widget
    // root en tenant compte du night mode + des overrides utilisateur (voir
    // baseOnContainerColor). Sert au rendu Bitmap de la mini forecast, où on
    // ne peut pas passer un ColorProvider (le canvas Android exige un Int).
    onContainerArgb: Int,
    showFiveItems: Boolean = false,
    showExtras: Boolean = true
) {
    val itemCount = if (showFiveItems) 5 else 4
    val compactHeight = LocalSize.current.height.value < 165f
    Column(modifier = GlanceModifier.fillMaxSize()) {
        // ─── Top strip (comme 4×1 mais TAILLES BUMPÉES pour remplir la hauteur)
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherGlyph(data.currentCondition, sizeDp = if (compactHeight) 38 else 44)
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainer,
                    fontSize = if (compactHeight) 25.sp else 28.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.width(12.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
                data.cityName?.let {
                    Text(
                        text = it,
                        style = TextStyle(
                            color = onContainer,
                            fontSize = if (compactHeight) 14.sp else 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                Text(
                    text = formatMinMax(data.tempMin, data.tempMax),
                    style = TextStyle(
                        color = onContainerMuted,
                        fontSize = if (compactHeight) 12.sp else 14.sp
                    )
                )
                if (showExtras) {
                    val extras = buildExtrasLine(data)
                    if (extras.isNotEmpty()) {
                        Spacer(GlanceModifier.height(2.dp))
                        Text(
                            text = extras,
                            style = TextStyle(
                                color = onContainerMuted,
                                fontSize = if (compactHeight) 9.sp else 11.sp
                            )
                        )
                    }
                }
            }

            data.confidencePct?.let {
                Spacer(GlanceModifier.width(8.dp))
                ConfidencePill(percent = it)
            }
        }

        Spacer(GlanceModifier.height(if (compactHeight) 6.dp else 10.dp))

        // ─── Bottom strip : selon le mode utilisateur ────────────────────
        // TROIS rendus mutuellement exclusifs pilotés par les data alimentées
        // dans loadWidgetData selon le mode config :
        //   - confidenceStrip non-null : heatmap 7 jours de confiance
        //   - next12hTemps non-vide   : mini prévision 12h centrée sur l'axe horaire
        //   - sinon                   : Row de 4-5 forecast items (HOURLY/DAILY)
        val strip = data.confidenceStrip
        val hasMiniForecast = data.next12hTemps.isNotEmpty()
        when {
            strip != null -> ConfidenceBandStrip(
                strip = strip,
                onContainer = onContainer,
                onContainerMuted = onContainerMuted,
                softSurface = softSurface,
                compact = compactHeight
            )
            hasMiniForecast -> MiniForecastStrip(
                data = data,
                softSurface = softSurface,
                compact = compactHeight,
                textColorArgb = onContainerArgb,
                // defaultWeight ici parce qu'on est dans le ColumnScope de
                // l'ExtraLargeLayout — occupe l'espace vertical restant sous le
                // top strip pour ne pas laisser un vide de 40+dp en bas de card.
                modifier = GlanceModifier.defaultWeight()
            )
            data.forecasts.isEmpty() -> Text(
                text = "…",
                style = TextStyle(color = onContainerMuted, fontSize = 12.sp)
            )
            else -> Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                data.forecasts.take(itemCount).forEachIndexed { index, item ->
                    if (index > 0) Spacer(GlanceModifier.width(ForecastCardSpacing))
                    ForecastItemCard(
                        item = item,
                        onContainer = onContainer,
                        onContainerMuted = onContainerMuted,
                        softSurface = softSurface,
                        compact = compactHeight,
                        modifier = GlanceModifier.defaultWeight()
                    )
                }
            }
        }
    }
}

/**
 * Mini-prévision 12 h pour les widgets hauts 2×2 à 5×2.
 *
 * Le bitmap place l'axe horaire au centre. La température est lue au-dessus
 * de sa heatmap et la probabilité de pluie sous sa propre heatmap. Les douze
 * colonnes restent ainsi parfaitement alignées, même quand le launcher
 * redimensionne légèrement le widget.
 */
@Composable
private fun MiniForecastStrip(
    data: WidgetData,
    softSurface: ColorProvider,
    compact: Boolean,
    textColorArgb: Int,
    outerHorizontalPadding: Dp = ExtraLargePadding.horizontal,
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
    val chartHeightDp = when (profile) {
        MiniForecastSizeProfile.COMPACT_2X2 -> 48
        MiniForecastSizeProfile.MEDIUM_3X2 -> 56
        MiniForecastSizeProfile.EXPANDED_4X2 -> if (compact) 60 else 68
    }
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
        listOf(
            start.format(formatter),
            start.plusHours(6).format(formatter),
            start.plusHours(11).format(formatter)
        )
    } ?: listOf("+0h", "+6h", "+11h")

    val bitmap = remember(
        data.next12hTemps,
        data.next12hPrecipProb,
        timelineLabels,
        widthPx,
        heightPx,
        textColorArgb,
        profile
    ) {
        WidgetMiniForecastRenderer.render(
            widthPx = widthPx,
            heightPx = heightPx,
            temps = data.next12hTemps,
            precips = data.next12hPrecipProb,
            precipColorArgb = precipColorArgb,
            textColorArgb = textColorArgb,
            timelineLabels = timelineLabels,
            profile = profile
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(softSurface)
            .cornerRadius(14.dp)
            .padding(
                horizontal = chartHorizontalPaddingDp.dp,
                vertical = when (profile) {
                    MiniForecastSizeProfile.COMPACT_2X2 -> 2.dp
                    MiniForecastSizeProfile.MEDIUM_3X2 -> 3.dp
                    MiniForecastSizeProfile.EXPANDED_4X2 -> 5.dp
                }
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
 * Rendu de la bande de confiance dans le layout 4×2.
 *
 * ─── Contraintes Glance ─────────────────────────────────────────────────
 * Glance ne supporte pas Canvas/DrawScope — impossible de dessiner un chart
 * "vrai" comme dans l'écran détail. On dégrade en Row de Box colorées : une
 * heatmap horizontale où chaque cellule = une portion de l'horizon (~7h de
 * prévision par cellule, 24 cellules pour couvrir 7 jours). C'est le même
 * pattern visuel que ConfidenceTimeline dans HourlyConfidenceChart, qui a
 * été validé comme un signal lisible d'un coup d'œil.
 *
 * ─── Composition ────────────────────────────────────────────────────────
 * 1. Ligne du haut : libellé métrique + valeur maintenant + % confiance.
 *    Donne un ancrage numérique — la bande visuelle seule serait trop abstraite.
 * 2. Ligne du bas : heatmap 24 cellules. Chaque cellule prend
 *    weight(1f) → largeur adaptée automatiquement à la largeur du widget.
 */
@Composable
private fun ColumnScope.ConfidenceBandStrip(
    strip: WidgetConfidenceStrip,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider,
    softSurface: ColorProvider,
    compact: Boolean
) {
    val night = LocalNightMode.current
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .defaultWeight()
            .background(softSurface)
            .cornerRadius(14.dp)
            .padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 4.dp else 6.dp
            )
    ) {
        // ─── Ligne 1 : "T° · 87%" — libellé métrique + confiance actuelle ─
        // Plus de valeur "maintenant" ici : elle est désormais reprise dans
        // la première colonne de la ligne 3 (bucket "Auj."). Éviter la
        // redondance libère de la place pour le "%" à droite qui a une
        // valeur informative propre (couleur teintée par le niveau).
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strip.metricLabel,
                style = TextStyle(
                    color = onContainer,
                    fontSize = if (compact) 11.sp else 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(GlanceModifier.defaultWeight())
            strip.currentPct?.let {
                Text(
                    text = "$it%",
                    style = TextStyle(
                        color = confidenceTextColor(it),
                        fontSize = if (compact) 11.sp else 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // ─── Ligne 2 : bande de confiance colorée par jour ─────────────
        // Hauteur FIXE réduite (8 dp) au lieu de defaultWeight → la strip
        // devient un liseré de couleur discret, laissant la place aux
        // valeurs numériques en dessous qui portent l'info actionnable.
        // 8 dp est un compromis : assez épais pour rester visible sur un
        // écran mobile depuis un bras tendu, assez fin pour ne pas
        // dominer les valeurs numériques.
        //
        // Chaque cellule s'aligne verticalement avec sa colonne value+label
        // en ligne 3 grâce au `defaultWeight()` partagé (même nombre de
        // colonnes → même largeur individuelle).
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(if (compact) 6.dp else 8.dp)
        ) {
            strip.buckets.forEach { bucket ->
                val color = confidenceColor(bucket.percent, night)
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .padding(horizontal = 0.5.dp)
                        .background(ColorProvider(color))
                        .cornerRadius(2.dp),
                    contentAlignment = Alignment.Center
                ) {}
            }
        }

        // ─── Ligne 3 : valeur + libellé par jour ───────────────────────
        // Une colonne par bucket, alignée verticalement avec la cellule de
        // couleur du dessus. C'est CETTE ligne qui donne du sens à la
        // bande de couleur : sans les valeurs numériques, la confiance
        // colorée n'est référencée à rien.
        //
        // Ordre des textes : valeur en gras (le chiffre est ce qu'on
        // regarde en premier), libellé jour discret dessous (contexte
        // temporel). L'inverse — libellé au-dessus — casserait la
        // lecture "quel jour donne quoi".
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp)
                .defaultWeight()
        ) {
            strip.buckets.forEach { bucket ->
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = bucket.value,
                        style = TextStyle(
                            color = onContainer,
                            fontSize = if (compact) 10.sp else 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = bucket.label,
                        style = TextStyle(
                            color = onContainerMuted,
                            fontSize = if (compact) 9.sp else 10.sp
                        )
                    )
                }
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
    // Résolution via LocalContext.current (surchargé dans provideGlance avec
    // le context localisé) plutôt qu'en dur — sinon les widgets restaient
    // affichés en français quel que soit le réglage app.
    val ctx = LocalContext.current
    val message = when (error) {
        WidgetError.NotConfigured ->
            ctx.getString(R.string.widget_error_not_configured)
        WidgetError.Loading ->
            ctx.getString(R.string.widget_error_loading)
        WidgetError.CityNoLongerInFavorites ->
            ctx.getString(R.string.widget_error_city_gone)
        is WidgetError.Fetch ->
            ctx.getString(R.string.widget_error_fetch)
    }
    val symbol = when (error) {
        WidgetError.NotConfigured -> "+"
        WidgetError.Loading -> "↻"
        WidgetError.CityNoLongerInFavorites -> "⌂"
        is WidgetError.Fetch -> "!"
    }
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = GlanceModifier
                .background(softSurface)
                .cornerRadius(14.dp)
                .padding(horizontal = 11.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                style = TextStyle(
                    color = onContainer,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = message,
            style = TextStyle(
                color = onContainerMuted,
                fontSize = 12.sp
            )
        )
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
    compact: Boolean,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(
        modifier = modifier
            .background(softSurface)
            .cornerRadius(if (compact) 10.dp else 12.dp)
            .padding(
                horizontal = if (compact) 4.dp else 6.dp,
                vertical = if (compact) 3.dp else 5.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.label,
            style = TextStyle(
                color = onContainerMuted,
                fontSize = if (compact) 9.sp else 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
        WeatherGlyph(
            condition = item.condition,
            sizeDp = if (compact) 20 else 30
        )
        Text(
            text = formatTemp(item.temp),
            style = TextStyle(
                color = onContainer,
                fontSize = if (compact) 11.sp else 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun ConfidencePill(percent: Int) {
    val night = LocalNightMode.current
    val color = confidenceColor(percent, night)
    // Mémoïsation des ColorProviders : sans ça, `.copy(alpha = 0.18f)` alloue
    // un nouvel objet Color à chaque recomposition — pour un widget avec strip
    // 4×2, ça peut être plusieurs allocations par render. Le remember(percent)
    // n'invalide que si le % change, ce qui n'arrive qu'au fetch de données.
    val bg = remember(percent, night) { ColorProvider(color.copy(alpha = 0.18f)) }
    val fg = remember(percent, night) { ColorProvider(color) }
    Box(
        modifier = GlanceModifier
            .background(bg)
            .cornerRadius(12.dp)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "● $percent%",
            style = TextStyle(
                color = fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
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