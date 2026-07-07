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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
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
//   - Small : width < 210dp. Couvre 2×1 physique jusqu'à ~103dp/cellule (Samsung).
//   - Medium : 210 ≤ width < 320dp. Couvre 3×1 physique typique.
//   - Large : width ≥ 320dp. Couvre 4×1 physique.
//   - ExtraLarge : width ≥ 220dp AND height ≥ 130dp. La double condition évite
//     de mal classer un widget 1-cellule sur un launcher à cellules hautes
//     (Pixel avec ~100-130dp de haut) — un vrai 4×2 fait au moins 145dp de
//     haut sur tous les launchers testés.
private const val SMALL_MAX_WIDTH_DP = 210
private const val MEDIUM_MAX_WIDTH_DP = 320
private const val EXTRA_LARGE_MIN_HEIGHT_DP = 130
private const val EXTRA_LARGE_MIN_WIDTH_DP = 220

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

private val SmallPadding = WidgetPadding(8.dp, 8.dp)
private val MediumPadding = WidgetPadding(14.dp, 10.dp)
private val LargePadding = WidgetPadding(18.dp, 12.dp)
private val ExtraLargePadding = WidgetPadding(20.dp, 20.dp)

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
        val appCtx = context.applicationContext
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
            val night = LocalContext.current.resources.configuration
                .let { (it.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES }
            CompositionLocalProvider(LocalNightMode provides night) {
                GlanceTheme {
                    WidgetContent(data = data, opacityPct = opacityPct)
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Rendering
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun WidgetContent(data: WidgetData, opacityPct: Int) {
    // Mémoïsation des couleurs sur la clé "nightMode" : recompute UNIQUEMENT
    // si le mode dark/light du système change. En pratique ces couleurs ne
    // changent jamais pendant la durée d'affichage d'un widget — la mémoïsation
    // évite quand même 5+ nouvelles allocations Color par recomposition
    // (chaque `.copy(alpha = ...)` crée un nouvel objet immuable).
    val night = LocalNightMode.current
    val onContainer = remember(night) {
        ColorProvider(if (night) onPrimaryContainerDark else onPrimaryContainerLight)
    }
    val onContainerMuted = remember(night) {
        ColorProvider(
            (if (night) onPrimaryContainerDark else onPrimaryContainerLight)
                .copy(alpha = 0.7f)
        )
    }
    val container = remember(night, opacityPct) {
        val base = if (night) primaryContainerDark else primaryContainerLight
        ColorProvider(base.copy(alpha = opacityPct / 100f))
    }

    // Padding calculé selon la taille du widget — voir docblock WidgetPadding
    // pour la motivation des valeurs par taille.
    val size = LocalSize.current
    val widthDp = size.width.value
    val heightDp = size.height.value
    val padding = when {
        heightDp >= EXTRA_LARGE_MIN_HEIGHT_DP &&
            widthDp >= EXTRA_LARGE_MIN_WIDTH_DP -> ExtraLargePadding
        widthDp >= MEDIUM_MAX_WIDTH_DP -> LargePadding
        widthDp >= SMALL_MAX_WIDTH_DP -> MediumPadding
        else -> SmallPadding
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(container)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = padding.horizontal, vertical = padding.vertical)
    ) {
        when {
            data.error != null -> ErrorLayout(data.error, onContainerMuted)
            else -> {
                // Sélection du layout via la taille exacte du container. Voir
                // les constantes en tête de fichier pour les seuils et leurs
                // motivations. La condition ExtraLarge combine width ET height
                // pour distinguer un vrai 4×2 d'un 3×1 sur launcher à cellules
                // hautes (le width seul suffit à choisir Small/Medium/Large).
                when {
                    heightDp >= EXTRA_LARGE_MIN_HEIGHT_DP &&
                        widthDp >= EXTRA_LARGE_MIN_WIDTH_DP ->
                        ExtraLargeLayout(data, onContainer, onContainerMuted)
                    widthDp >= MEDIUM_MAX_WIDTH_DP ->
                        LargeLayout(data, onContainer, onContainerMuted)
                    widthDp >= SMALL_MAX_WIDTH_DP ->
                        MediumLayout(data, onContainer, onContainerMuted)
                    else -> SmallLayout(data, onContainer)
                }
            }
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
        WeatherGlyph(data.currentCondition, sizeSp = 24, onContainer)
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
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainer,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
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
    onContainerMuted: ColorProvider
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherGlyph(data.currentCondition, sizeSp = 28, onContainer)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainer,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        Spacer(GlanceModifier.width(10.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
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
            ConfidencePill(percent = it)
        }
    }
}

/**
 * Layout 4×1 : version enrichie avec 3 lignes centrales — ville, min/max,
 * ligne d'extras contextuels (cloud cover, pluie avec confiance).
 */
@Composable
private fun LargeLayout(
    data: WidgetData,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherGlyph(data.currentCondition, sizeSp = 30, onContainer)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainer,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        Spacer(GlanceModifier.width(12.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
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
                    style = TextStyle(color = onContainerMuted, fontSize = 14.sp)
                )
            }
        }

        data.confidencePct?.let {
            ConfidencePill(percent = it)
        }
    }
}

/**
 * Layout 4×2 : top strip identique au 4×1 + bas strip avec 4 items de prévision
 * étendue (heures ou jours selon la config utilisateur).
 *
 * Tailles délibérément plus grandes que 4×1 pour REMPLIR l'espace vertical
 * doublé — sans ça le widget paraît vide, avec beaucoup de "coussin blanc"
 * en haut et en bas de chaque bloc. Un icône 32sp et une temp 28sp
 * consomment le top strip visuellement ; le bottom strip a icônes 26sp et
 * temp 15sp pour occuper les 4 colonnes.
 */
@Composable
private fun ExtraLargeLayout(
    data: WidgetData,
    onContainer: ColorProvider,
    onContainerMuted: ColorProvider
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        // ─── Top strip (comme 4×1 mais TAILLES BUMPÉES pour remplir la hauteur)
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherGlyph(data.currentCondition, sizeSp = 32, onContainer)
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainer,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(GlanceModifier.width(12.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
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
                        style = TextStyle(color = onContainerMuted, fontSize = 14.sp)
                    )
                }
            }

            data.confidencePct?.let {
                ConfidencePill(percent = it)
            }
        }

        Spacer(GlanceModifier.height(18.dp))

        // ─── Bottom strip : selon le mode utilisateur ────────────────────
        // Deux rendus mutuellement exclusifs pilotés par la présence de
        // data.confidenceStrip vs data.forecasts (voir loadWidgetData qui
        // n'alimente qu'un seul des deux selon le mode config utilisateur).
        val strip = data.confidenceStrip
        if (strip != null) {
            ConfidenceBandStrip(
                strip = strip,
                onContainer = onContainer,
                onContainerMuted = onContainerMuted
            )
        } else if (data.forecasts.isEmpty()) {
            Text(
                text = "…",
                style = TextStyle(color = onContainerMuted, fontSize = 12.sp)
            )
        } else {
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                data.forecasts.take(4).forEach { item ->
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.label,
                            style = TextStyle(color = onContainerMuted, fontSize = 14.sp)
                        )
                        WeatherGlyph(item.condition, sizeSp = 26, onContainer)
                        Text(
                            text = formatTemp(item.temp),
                            style = TextStyle(color = onContainer, fontSize = 15.sp)
                        )
                    }
                }
            }
        }
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
    onContainerMuted: ColorProvider
) {
    Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
        // Ligne 1 : "T° 22° · 87%" (couleur du % teintée par le niveau)
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strip.metricLabel,
                style = TextStyle(
                    color = onContainerMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            strip.nowValue?.let {
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = it,
                    style = TextStyle(
                        color = onContainer,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            strip.currentPct?.let {
                Text(
                    text = "$it%",
                    style = TextStyle(
                        color = confidenceTextColor(it),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // Ligne 2 : heatmap 24 cellules. Glance ne supporte pas Arrangement.spacedBy
        // sur Row, on met un tout petit padding sur chaque Box pour la séparation.
        // Chaque cellule prend defaultWeight() → largeur ~ egaleway.
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            strip.bucketPercents.forEach { pct ->
                val color = confidenceColor(pct)
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

        // Ligne 3 : ancres temporelles ("Auj." → "J+7 · 18°").
        //
        // Sans cette ligne, la heatmap au-dessus est illisible : couleurs sans
        // échelle temporelle ni valeur de fin d'horizon. Avec, l'utilisateur
        // capte d'un coup d'œil "aujourd'hui T° 22° / dans 7j 18°" avec le
        // dégradé qui montre l'évolution de la confiance entre les deux.
        //
        // Cas dégénéré : strip couvrant moins de 24h (spanDays == 0). Alors
        // startLabel == endLabel et afficher "Auj. → Auj. · 22°" est laid et
        // redondant avec la ligne 1. On skip la ligne du bas dans ce cas —
        // la strip reste utile (dégradé de couleur sur les prochaines heures)
        // mais sans ancres temporelles fantômes.
        if (strip.startLabel != strip.endLabel) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strip.startLabel,
                    style = TextStyle(
                        color = onContainerMuted,
                        fontSize = 11.sp
                    )
                )
                Spacer(GlanceModifier.defaultWeight())
                // À droite : "J+7 · 18°" — le libellé temporel PLUS la valeur
                // projetée. La juxtaposition raconte l'histoire "dans 7 jours il
                // fera 18°" là où juste "18°" seul serait ambigü (il fait ça où ?
                // quand ?).
                Text(
                    text = if (strip.endValue != null) "${strip.endLabel} · ${strip.endValue}"
                        else strip.endLabel,
                    style = TextStyle(
                        color = onContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

/** État "widget pas configuré" ou "erreur" ou "chargement". */
@Composable
private fun ErrorLayout(error: WidgetError, onContainerMuted: ColorProvider) {
    val message = when (error) {
        WidgetError.NotConfigured -> "Configurer\nla ville"
        WidgetError.Loading -> "Chargement…"
        WidgetError.CityNoLongerInFavorites -> "Ville\nsupprimée"
        is WidgetError.Fetch -> "Pas de\ndonnées"
    }
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
    sizeSp: Int,
    onContainer: ColorProvider
) {
    val glyph = when (condition) {
        WeatherCondition.CLEAR, WeatherCondition.MAINLY_CLEAR -> "☀"
        WeatherCondition.PARTLY_CLOUDY -> "⛅"
        WeatherCondition.OVERCAST -> "☁"
        WeatherCondition.FOG -> "🌫"
        WeatherCondition.DRIZZLE, WeatherCondition.RAIN_SHOWERS -> "🌦"
        WeatherCondition.RAIN -> "🌧"
        WeatherCondition.FREEZING_RAIN -> "🌨"
        WeatherCondition.SNOW, WeatherCondition.SNOW_SHOWERS -> "❄"
        WeatherCondition.THUNDERSTORM -> "⛈"
        WeatherCondition.UNKNOWN, null -> "—"
    }
    Text(
        text = glyph,
        style = TextStyle(color = onContainer, fontSize = sizeSp.sp)
    )
}

@Composable
private fun ConfidencePill(percent: Int) {
    val color = confidenceColor(percent)
    // Mémoïsation des ColorProviders : sans ça, `.copy(alpha = 0.18f)` alloue
    // un nouvel objet Color à chaque recomposition — pour un widget avec strip
    // 4×2, ça peut être plusieurs allocations par render. Le remember(percent)
    // n'invalide que si le % change, ce qui n'arrive qu'au fetch de données.
    val bg = remember(percent) { ColorProvider(color.copy(alpha = 0.18f)) }
    val fg = remember(percent) { ColorProvider(color) }
    Box(
        modifier = GlanceModifier
            .background(bg)
            .cornerRadius(8.dp)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$percent%",
            style = TextStyle(
                color = fg,
                fontSize = 12.sp,
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
 * Couleur du texte de confiance selon le %. Alignées sur ConfidenceColors de
 * l'app (vert/orange/rouge M3-ish), hardcodées ici parce que Glance n'a pas
 * accès à MaterialTheme. Un même chiffre garde ainsi une teinte identique
 * dans le widget et dans l'app.
 */
private fun confidenceColor(percent: Int): Color = when {
    percent >= 80 -> Color(0xFF388E3C)  // green 700
    percent >= 50 -> Color(0xFFF57C00)  // orange 700
    else -> Color(0xFFC62828)           // red 700
}

@Composable
private fun confidenceTextColor(percent: Int): ColorProvider =
    // Mémoïsation identique à ConfidencePill — le percent change rarement.
    remember(percent) { ColorProvider(confidenceColor(percent)) }
