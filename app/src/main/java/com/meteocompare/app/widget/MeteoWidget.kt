package com.meteocompare.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
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

/**
 * Widget MeteoCompare — reproduit un résumé compact de la [TodaySummaryCard]
 * sur l'écran d'accueil.
 *
 * Trois tailles supportées, sélectionnées via [LocalSize.current.width] :
 *
 *   - **~2×1** (< 150dp) : icône + température actuelle + nom de ville.
 *     Mode "coup d'œil" — un pouce sait s'il fait beau et combien de degrés.
 *
 *   - **~3×1** (150-220dp) : + min/max du jour + badge de confiance.
 *     Assez pour planifier la journée sans ouvrir l'app.
 *
 *   - **~4×1** (> 220dp) : + précipitations si prévues (mm/j).
 *     Résumé complet, quasi-parité avec la TodaySummaryCard.
 *
 * L'utilisateur configure au tap-and-drop :
 *   - Ville affichée (parmi ses favoris)
 *   - Opacité du fond (0-100%) — utile pour laisser passer le wallpaper
 *
 * Tap sur le widget → ouvre MainActivity (retourne à l'écran d'accueil de l'app).
 * Pas d'intent extra pour naviguer directement vers la ville : trop de risque
 * de créer des Task stacks bizarres si l'app est déjà ouverte sur autre chose.
 * Le user tape "Paris" dans la liste après ouverture — un tap de plus, mais
 * comportement prévisible.
 */
internal class MeteoWidget : GlanceAppWidget() {

    /**
     * SizeMode.Exact : Glance appelle provideGlance à chaque changement de
     * taille (resize par l'utilisateur) et expose la taille via LocalSize.
     * On aurait pu utiliser SizeMode.Responsive avec des DpSizes prédéfinies,
     * mais Exact + un `when` sur width nous donne plus de flexibilité pour
     * ajuster les seuils sans multiplier les entrées de config.
     */
    override val sizeMode = SizeMode.Exact

    /**
     * Persistence des paramètres par-widget via DataStore Preferences. Chaque
     * widget a son propre fichier identifié par GlanceId, indépendamment des
     * autres widgets ou du DataStore de l'app.
     */
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState<Preferences>(
            context = context,
            definition = PreferencesGlanceStateDefinition,
            glanceId = id
        )
        val cityId = prefs[WidgetPreferences.CityIdKey]
        val opacityPct = (prefs[WidgetPreferences.OpacityPctKey]
            ?: WidgetPreferences.DEFAULT_OPACITY_PCT).coerceIn(0, 100)

        // Le fetch se fait AVANT provideContent — Glance appelle provideGlance
        // sur un CoroutineScope et suspend patientement, donc l'utilisateur voit
        // le widget se rafraîchir d'un coup une fois les données prêtes plutôt
        // que d'un état placeholder-puis-content clignotant.
        val data = loadWidgetData(context, cityId)

        provideContent {
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
    // Alpha du fond piloté par la config utilisateur. On applique sur la
    // primaryContainer du thème (jour/nuit) pour rester cohérent avec le style
    // de la TodaySummaryCard qui a la même base. Sur wallpaper photo, alpha
    // ~0.6 donne un effet "verre dépoli" agréable ; alpha 1.0 = opaque total,
    // alpha 0.0 = fond entièrement transparent (texte lisible seulement sur
    // wallpaper uni).
    //
    // Détection du mode nuit à COMPOSITION TIME plutôt qu'avec une factory
    // day/night ColorProvider — Glance 1.1 a bien un ColorProvider(day, night)
    // dans le package `androidx.glance.color`, mais son import entre en
    // conflit avec le ColorProvider(color) single-arg du package `androidx.glance.unit`
    // qu'on utilise partout ailleurs (badge de confiance, textes). Résoudre le
    // Color manuellement puis wrapper une seule fois évite le conflit sans
    // sacrifier le rendu day/night.
    val alpha = opacityPct / 100f
    val bg = ColorProvider(resolveContainerColor().copy(alpha = alpha))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp)
    ) {
        when {
            data.error != null -> ErrorLayout(data.error, opacityPct)
            else -> {
                // Sélection du layout selon la largeur DP effective. Les seuils
                // sont choisis pour correspondre grossièrement à 2×1 / 3×1 / 4×1
                // sur une grille de 70dp par cellule (formule système Android :
                // largeur = 70·n − 30 → 110 / 180 / 250 dp). On prend des seuils
                // légèrement plus généreux pour être robuste aux launchers qui
                // arrondissent différemment.
                val widthDp = LocalSize.current.width.value
                when {
                    widthDp < 150f -> SmallLayout(data)
                    widthDp < 220f -> MediumLayout(data)
                    else -> LargeLayout(data)
                }
            }
        }
    }
}

/** Layout 2×1 : icône + temp + ville en 2 lignes compactes. */
@Composable
private fun SmallLayout(data: WidgetData) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherGlyph(data.currentCondition, sizeSp = 22)
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainerColor(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        data.cityName?.let {
            Text(
                text = it,
                style = TextStyle(color = onContainerColorMuted(), fontSize = 11.sp)
            )
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
        // ─── Bloc gauche : icône + big temp ───────────────────────
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

        // ─── Bloc milieu : ville + min/max ─────────────────────────
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

        // ─── Bloc droit : badge de confiance ─────────────────────
        data.confidencePct?.let {
            ConfidencePill(percent = it)
        }
    }
}

/** Layout 4×1 : identique au medium + précipitations. */
@Composable
private fun LargeLayout(data: WidgetData) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherGlyph(data.currentCondition, sizeSp = 32)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = formatTemp(data.currentTemp),
                style = TextStyle(
                    color = onContainerColor(),
                    fontSize = 28.sp,
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            Text(
                text = formatMinMax(data.tempMin, data.tempMax),
                style = TextStyle(color = onContainerColorMuted(), fontSize = 12.sp)
            )
            data.precipMm?.let {
                Text(
                    text = "☔ %.1f mm".format(it),
                    style = TextStyle(color = onContainerColorMuted(), fontSize = 11.sp)
                )
            }
        }

        data.confidencePct?.let {
            ConfidencePill(percent = it)
        }
    }
}

/** État "widget pas configuré" ou "erreur". */
@Composable
private fun ErrorLayout(error: WidgetError, opacityPct: Int) {
    val message = when (error) {
        WidgetError.NotConfigured -> "Configurer\nla ville"
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

/**
 * Icône météo — rendue comme glyphe Unicode dans un Text plutôt qu'une Image
 * pour rester DPI-invariant et ne pas embarquer 13 drawables.
 *
 * Trade-off : les emojis Unicode ont un rendu qui varie selon la version
 * d'Android (Noto vs system emoji font). Sur Android 12+ c'est cohérent ;
 * sur 26-30 le style peut différer. Acceptable pour un MVP — si on veut un
 * rendu strictement identique cross-version, il faudra passer par des
 * vector drawables au format Bitmap via ImageProvider.
 */
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

/**
 * Petit pill de confidence — badge coloré avec le %.
 *
 * Trois seuils sémantiques alignés sur les couleurs `confidenceColor` de l'app :
 *   - ≥ 80 : vert
 *   - ≥ 50 : orange
 *   - < 50 : rouge
 *
 * On ne va pas chercher `confidenceColor()` dans le theme app parce qu'on est
 * dans un composable Glance (pas Compose Material), donc pas d'accès direct à
 * MaterialTheme. Les couleurs sont hardcodées ici — les mêmes hexa que dans
 * `ui/theme/ConfidenceColors.kt` pour rester visuellement cohérent.
 */
@Composable
private fun ConfidencePill(percent: Int) {
    val color = when {
        percent >= 80 -> Color(0xFF388E3C)  // green 700
        percent >= 50 -> Color(0xFFF57C00)  // orange 700
        else -> Color(0xFFC62828)           // red 700
    }
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
//
// Base fixe non-liée au dynamic color système : un widget doit avoir une
// identité visuelle stable entre les thèmes utilisateur, sinon il change
// d'apparence à chaque wallpaper. On assume les valeurs de primaryContainer
// et onPrimaryContainer du thème M3 statique de l'app.

private val primaryContainerLight = Color(0xFFDBE2FF)  // bleu très clair
private val primaryContainerDark = Color(0xFF283960)   // bleu sombre profond
private val onPrimaryContainerLight = Color(0xFF001A41)
private val onPrimaryContainerDark = Color(0xFFDBE2FF)

/**
 * Détecte le mode nuit en lisant la configuration système via LocalContext.
 *
 * Alternative envisagée : `androidx.glance.color.ColorProvider(day, night)`
 * — factory day/night native à Glance qui aurait fait le boulot. Elle entre
 * malheureusement en conflit d'import avec `androidx.glance.unit.ColorProvider(color)`
 * single-arg utilisé ailleurs dans ce fichier (badge de confiance, textes),
 * et Kotlin ne peut pas résoudre deux fonctions homonymes de packages
 * différents dans le même fichier sans renommage.
 *
 * Résoudre manuellement puis wrapper une seule fois dans ColorProvider(color)
 * est plus simple et évite le rename `as` sur tous les call sites.
 */
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