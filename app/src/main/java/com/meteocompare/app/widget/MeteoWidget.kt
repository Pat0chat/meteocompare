package com.meteocompare.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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
import kotlin.math.roundToInt

// ─── Buckets de tailles ─────────────────────────────────────────────────────
//
// SizeMode.Responsive prend un ensemble de tailles cibles. Glance sélectionne
// la plus grande qui rentre dans les dimensions accordées par le launcher.
// LocalSize.current renvoie ensuite la taille cible sélectionnée — PAS la
// taille réelle du container — ce qui rend le branching de layout déterministe
// et invariant vis-à-vis des variations de largeur cellule d'un launcher à
// l'autre.
//
// Formule Android grid : dp = 70·n − 30 → 2×1 = 110, 3×1 = 180, 4×1 = 250,
// hauteur 1 cellule ≈ 40dp, 2 cellules ≈ 110dp.
private val SIZE_2x1 = DpSize(110.dp, 40.dp)
private val SIZE_3x1 = DpSize(180.dp, 40.dp)
private val SIZE_4x1 = DpSize(250.dp, 40.dp)
private val SIZE_4x2 = DpSize(250.dp, 110.dp)

/**
 * Widget MeteoCompare — reproduit un résumé compact de la [TodaySummaryCard]
 * sur l'écran d'accueil.
 *
 * Quatre tailles supportées, via [SizeMode.Responsive] :
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
 */
internal class MeteoWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(SIZE_2x1, SIZE_3x1, SIZE_4x1, SIZE_4x2)
    )

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

            // Chargement des données asynchrone. `remember` persiste la
            // dernière donnée bonne à travers les recompositions ; LaunchedEffect
            // re-fetch quand cityId/forecastMode change (nouveau save de config).
            // L'état initial est Loading (pas NotConfigured) pour éviter le
            // flash "Configurer" quand on recompose une ville déjà configurée.
            var data by remember {
                mutableStateOf<WidgetData>(
                    if (cityId == null) WidgetData.NotConfigured else WidgetData.Loading
                )
            }
            LaunchedEffect(cityId, forecastMode) {
                data = loadWidgetData(appCtx, cityId, forecastMode)
            }

            GlanceTheme {
                WidgetContent(data = data, opacityPct = opacityPct)
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Rendering
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun WidgetContent(data: WidgetData, opacityPct: Int) {
    val alpha = opacityPct / 100f
    val bg = ColorProvider(resolveContainerColor().copy(alpha = alpha))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(10.dp)
    ) {
        when {
            data.error != null -> ErrorLayout(data.error)
            else -> {
                // Sélection du layout via la taille cible du bucket Responsive
                // (pas la taille réelle du container). Comparer par height
                // d'abord permet de distinguer 4×2 vs 4×1 même si la largeur
                // est identique.
                val size = LocalSize.current
                when {
                    size.height >= 100.dp -> ExtraLargeLayout(data)
                    size.width >= 250.dp -> LargeLayout(data)
                    size.width >= 180.dp -> MediumLayout(data)
                    else -> SmallLayout(data)
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
private fun SmallLayout(data: WidgetData) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WeatherGlyph(data.currentCondition, sizeSp = 22)
        Spacer(GlanceModifier.width(6.dp))
        Column {
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainerColor(),
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
private fun MediumLayout(data: WidgetData) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherGlyph(data.currentCondition, sizeSp = 28)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainerColor(),
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
                        color = onContainerColor(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            Text(
                text = formatMinMax(data.tempMin, data.tempMax),
                style = TextStyle(color = onContainerColorMuted(), fontSize = 12.sp)
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
private fun LargeLayout(data: WidgetData) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherGlyph(data.currentCondition, sizeSp = 30)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainerColor(),
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
                        color = onContainerColor(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            Text(
                text = formatMinMax(data.tempMin, data.tempMax),
                style = TextStyle(color = onContainerColorMuted(), fontSize = 11.sp)
            )
            val extras = buildExtrasLine(data)
            if (extras.isNotEmpty()) {
                Text(
                    text = extras,
                    style = TextStyle(color = onContainerColorMuted(), fontSize = 11.sp)
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
 * Le top strip garde les mêmes tailles que le 4×1 pour cohérence visuelle
 * entre les deux tailles voisines — l'utilisateur qui resize doit reconnaître
 * "c'est le même widget en plus grand".
 *
 * Le bas strip est un Row de 4 Column équi-larges (defaultWeight), chacune
 * avec label + icône + température. Compact — 40dp de haut environ. Textes
 * en 10-12sp pour tenir sans crowd.
 */
@Composable
private fun ExtraLargeLayout(data: WidgetData) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        // ─── Top strip (comme 4×1 mais tailles légèrement réduites) ────
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherGlyph(data.currentCondition, sizeSp = 26)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainerColor(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(GlanceModifier.width(10.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
                data.cityName?.let {
                    Text(
                        text = it,
                        style = TextStyle(
                            color = onContainerColor(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                Text(
                    text = formatMinMax(data.tempMin, data.tempMax),
                    style = TextStyle(color = onContainerColorMuted(), fontSize = 10.sp)
                )
            }

            data.confidencePct?.let {
                ConfidencePill(percent = it)
            }
        }

        Spacer(GlanceModifier.height(6.dp))

        // ─── Bottom strip : 4 items de prévision étendue ──────────────
        // Si aucune prévision (cache vide), on affiche le message discret ci-dessous
        // — plutôt qu'un strip blanc qui laisserait penser à un bug de rendu.
        if (data.forecasts.isEmpty()) {
            Text(
                text = "…",
                style = TextStyle(color = onContainerColorMuted(), fontSize = 11.sp)
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
                            style = TextStyle(color = onContainerColorMuted(), fontSize = 10.sp)
                        )
                        WeatherGlyph(item.condition, sizeSp = 18)
                        Text(
                            text = formatTemp(item.temp),
                            style = TextStyle(color = onContainerColor(), fontSize = 13.sp)
                        )
                    }
                }
            }
        }
    }
}

/** État "widget pas configuré" ou "erreur" ou "chargement". */
@Composable
private fun ErrorLayout(error: WidgetError) {
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
                color = onContainerColorMuted(),
                fontSize = 12.sp
            )
        )
    }
}

// ─── Sous-blocs réutilisables ─────────────────────────────────────────────

@Composable
private fun WeatherGlyph(condition: WeatherCondition?, sizeSp: Int) {
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
        style = TextStyle(color = onContainerColor(), fontSize = sizeSp.sp)
    )
}

@Composable
private fun ConfidencePill(percent: Int) {
    val color = confidenceColor(percent)
    Box(
        modifier = GlanceModifier
            .background(ColorProvider(color.copy(alpha = 0.18f)))
            .cornerRadius(8.dp)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$percent%",
            style = TextStyle(
                color = ColorProvider(color),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

private fun buildExtrasLine(data: WidgetData): String = buildString {
    val cond = data.currentCondition
    val showCloud = data.currentCloudCover != null &&
        (cond == WeatherCondition.PARTLY_CLOUDY || cond == WeatherCondition.OVERCAST)
    if (showCloud) {
        append("☁ ${data.currentCloudCover}%")
    }
    if (data.precipMm != null) {
        if (isNotEmpty()) append(" · ")
        append("🌧 %.1f mm".format(data.precipMm))
        data.precipConfidencePct?.let { append(" ($it%)") }
    }
}

// ─── Formatage ──────────────────────────────────────────────────────────

private fun formatTemp(value: Double?): String =
    if (value == null) "—" else "${value.roundToInt()}°"

private fun formatMinMax(min: Double?, max: Double?): String = when {
    min != null && max != null -> "${min.roundToInt()}° / ${max.roundToInt()}°"
    max != null -> "max ${max.roundToInt()}°"
    min != null -> "min ${min.roundToInt()}°"
    else -> ""
}

// ─── Couleurs ───────────────────────────────────────────────────────────

private val primaryContainerLight = Color(0xFFDBE2FF)
private val primaryContainerDark = Color(0xFF283960)
private val onPrimaryContainerLight = Color(0xFF001A41)
private val onPrimaryContainerDark = Color(0xFFDBE2FF)

@Composable
private fun isNightMode(): Boolean {
    val ctx = LocalContext.current
    val uiMode = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return uiMode == Configuration.UI_MODE_NIGHT_YES
}

@Composable
private fun resolveContainerColor(): Color =
    if (isNightMode()) primaryContainerDark else primaryContainerLight

@Composable
private fun resolveOnContainerColor(): Color =
    if (isNightMode()) onPrimaryContainerDark else onPrimaryContainerLight

@Composable
private fun onContainerColor(): ColorProvider =
    ColorProvider(resolveOnContainerColor())

@Composable
private fun onContainerColorMuted(): ColorProvider =
    ColorProvider(resolveOnContainerColor().copy(alpha = 0.7f))

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
    ColorProvider(confidenceColor(percent))
