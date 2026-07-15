package com.meteocompare.app.widget

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.meteocompare.app.R

/**
 * Clés de persistence des paramètres par-widget.
 *
 * ─── Où sont stockées ces prefs ? ────────────────────────────────────────
 * Glance stocke ces prefs dans un DataStore Preferences séparé pour chaque
 * widgetId, via [PreferencesGlanceStateDefinition]. Deux conséquences :
 *   - Chaque widget posé sur l'écran d'accueil a sa propre config indépendante
 *     (ville, couleurs, opacité). Deux widgets peuvent afficher deux villes
 *     différentes avec deux thèmes de couleur différents.
 *   - Ces prefs sont différentes des UserPreferences (Settings de l'app).
 *     Le modèle utilisateur, l'intervalle de rafraîchissement, la langue,
 *     etc. vivent dans UserPreferencesRepository — les prefs ci-dessous ne
 *     concernent que le RENDU de ce widget spécifique.
 */
internal object WidgetPreferences {
    /** Id de la ville favorite affichée par ce widget. Null = pas configuré. */
    val CityIdKey = stringPreferencesKey("widget_city_id")

    /** Opacité du fond en pourcentage entier, 0..100. 80 par défaut. */
    val OpacityPctKey = intPreferencesKey("widget_opacity_pct")

    /**
     * Mode d'affichage de la ligne du bas dans le layout 4×2. Persisté comme
     * String (nom de l'enum) pour rester lisible en cas de migration ; int
     * aurait été plus compact mais introduit un couplage fragile.
     */
    val ForecastModeKey = stringPreferencesKey("widget_forecast_mode")

    /**
     * Refresh tick — timestamp (ms epoch) de la dernière demande de refresh
     * automatique par [WidgetRefreshWorker]. Utilisé comme clé du
     * `LaunchedEffect` dans le widget pour déclencher le re-fetch.
     */
    val RefreshTickKey = longPreferencesKey("widget_refresh_tick")

    /**
     * Couleur de fond custom, encodée en ARGB Int (compatible
     * `android.graphics.Color`).
     *
     * ─── Sémantique de "absent" ─────────────────────────────────────────
     * Si cette clé N'EST PAS présente dans les prefs, le widget retombe sur
     * les couleurs Material (primaryContainer light/dark selon le thème
     * système). C'est le comportement historique et le défaut.
     *
     * Si la clé EST présente, sa valeur override la couleur Material —
     * l'opacité ([OpacityPctKey]) reste appliquée par-dessus.
     *
     * Pourquoi un sentinel-null plutôt qu'un booléen `useCustomColors` +
     * une valeur systématiquement présente ? Deux raisons :
     *   1. Moins de clés = moins d'états incohérents possibles (impossible
     *      d'avoir "useCustom=true mais color=null").
     *   2. Une migration future peut retirer les clés pour revenir au défaut
     *      sans avoir à gérer un état "custom activé mais valeur invalide".
     */
    val BackgroundColorKey = intPreferencesKey("widget_bg_color_argb")

    /**
     * Couleur du texte custom, encodée en ARGB Int.
     *
     * Sémantique identique à [BackgroundColorKey] : absent = comportement
     * historique (onPrimaryContainer selon thème), présent = override.
     *
     * Note : à défaut de couleur texte custom mais AVEC une couleur bg
     * custom, on calcule automatiquement une couleur texte contrastée
     * (blanc ou noir selon la luminance du fond). Voir la logique de
     * résolution dans [MeteoWidget.WidgetContent].
     */
    val TextColorKey = intPreferencesKey("widget_text_color_argb")

    const val DEFAULT_OPACITY_PCT = 80

    /**
     * Défaut = HOURLY : les prochaines heures sont le signal le plus
     * actionnable pour un widget consulté en cours de journée. Les modes
     * confidence restent opt-in — plus abstraits, ils demandent une lecture
     * intentionnelle plutôt qu'un coup d'œil rapide.
     */
    val DEFAULT_FORECAST_MODE = ForecastMode.HOURLY
}

/**
 * Contenu de la ligne du bas du widget 4×2.
 *
 *   - [HOURLY] : jusqu'à 5 prévisions horaires (labels "14h", "15h", …).
 *     Signal le plus actionnable à courte échéance.
 *   - [DAILY] : jusqu'à 5 prévisions journalières ("Lun", "Mar", …).
 *   - [CONFIDENCE_ALL] : trois bandes synchronisées sur cinq jours pour la
 *     température, la pluie et le vent.
 *   - Les trois valeurs `CONFIDENCE_*` historiques sont uniquement conservées
 *     pour migrer les widgets existants vers [CONFIDENCE_ALL].
 *
 * Sealed via enum plutôt que sealed class : pas de données associées, juste
 * un discriminant simple. La persistance stocke `name` (String), la lecture
 * fait `runCatching { valueOf(...) }` pour tolérer une clé inconnue en cache
 * (retombe sur le défaut sans crash).
 */
internal enum class ForecastMode {
    HOURLY,
    DAILY,
    /**
     * Vue synthétique qui superpose les trois indicateurs de confiance :
     * température, précipitations et vent. C'est l'unique choix exposé dans
     * la configuration du widget.
     */
    CONFIDENCE_ALL,
    /** Anciennes valeurs conservées uniquement pour relire les widgets existants. */
    CONFIDENCE_TEMPERATURE,
    CONFIDENCE_PRECIPITATION,
    CONFIDENCE_WIND,
    /**
     * Mini prévision 12h : barres de température (heatmap) + dots pluie +
     * ancres horaires. Rendu via Bitmap ([WidgetMiniForecastRenderer]) +
     * Row Glance de 3 Text pour les heures. Same look que le composable de
     * la home. Uniquement pertinent pour les widgets 2-row (3×2, 4×2, 5×2).
     */
    MINI_FORECAST_12H
}

/**
 * Helper : cette mode affiche-t-elle une bande de confiance (vs une prévision
 * discrète 5 items) ? Sert dans le widget pour choisir le layout du bas.
 */
internal fun ForecastMode.isConfidenceBand(): Boolean = when (this) {
    ForecastMode.CONFIDENCE_ALL,
    ForecastMode.CONFIDENCE_TEMPERATURE,
    ForecastMode.CONFIDENCE_PRECIPITATION,
    ForecastMode.CONFIDENCE_WIND -> true
    ForecastMode.HOURLY, ForecastMode.DAILY, ForecastMode.MINI_FORECAST_12H -> false
}

/**
 * Migre à la volée les trois anciens choix séparés vers la vue combinée.
 * Les noms historiques restent dans l'enum pour que `valueOf` puisse relire
 * les préférences de widgets déjà installés sans les casser.
 */
internal fun ForecastMode.normalized(): ForecastMode = when (this) {
    ForecastMode.CONFIDENCE_TEMPERATURE,
    ForecastMode.CONFIDENCE_PRECIPITATION,
    ForecastMode.CONFIDENCE_WIND -> ForecastMode.CONFIDENCE_ALL
    else -> this
}

/**
 * Helper : cette mode nécessite-t-elle un rendu Bitmap custom (mini forecast) ?
 * Introduit avec MINI_FORECAST_12H — pour distinguer du confidence strip qui
 * lui est fait avec des Box Glance colorées.
 */
internal fun ForecastMode.isMiniForecast(): Boolean =
    this == ForecastMode.MINI_FORECAST_12H

// ═══════════════════════════════════════════════════════════════════════════
//  Palette de couleurs proposée dans la config activity
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Une couleur préréglée présentée dans la palette de la config activity.
 *
 * @property argb valeur ARGB Int à persister dans les prefs. `null` = option
 *   "Auto" (le widget retombe sur ses couleurs Material historiques).
 * @property labelRes clé strings.xml pour l'accessibilité (contentDescription
 *   et TalkBack). Pas affichée visuellement — le chip lui-même est un carré
 *   coloré, l'label serait redondant à l'œil et occuperait de l'espace.
 *
 * Le champ argb est nullable spécifiquement pour représenter "Auto" en
 * première position de la palette sans avoir besoin d'un discriminant
 * séparé. Le code de rendu du chip fait un `if (color.argb == null)` pour
 * dessiner l'état "Auto" (motif damier ou icône) au lieu d'un swatch plein.
 */
internal data class WidgetColorOption(
    val argb: Int?,
    val labelRes: Int
)

/**
 * Palette de fonds et de couleurs texte proposée dans la config activity.
 *
 * ─── Choix des couleurs ─────────────────────────────────────────────────
 * Priorité aux couleurs qui restent lisibles avec du texte contrasté auto
 * (voir logique de contraste dans [MeteoWidget]) sur la variété typique
 * des wallpapers Android : photos sombres, gradients clairs, unis vifs.
 *
 * On garde une palette RESTREINTE (9 fonds, 5 textes) pour éviter la
 * fatigue de décision. Un color picker HSV complet serait techniquement
 * possible mais ajouterait beaucoup d'UI et d'états sans amener de valeur
 * — 9 presets couvrent 90% des besoins raisonnables. Les 10% restants
 * (bleu très spécifique, teinte pantone d'entreprise) restent en TODO si
 * demande utilisateur.
 *
 * Note : les Ints ARGB sont exprimés avec `0xFF` en byte alpha — c'est
 * l'opacité de la couleur BASE. L'opacité utilisateur ([OpacityPctKey])
 * est appliquée PAR-DESSUS via `.copy(alpha = ...)` au moment du rendu,
 * donc mettre 0xFF ici ne fige pas le fond opaque — l'utilisateur peut
 * toujours choisir 20% dans le slider et le widget devient semi-transparent.
 */
internal object WidgetColorPalette {
    /**
     * Fonds proposés. L'ordre a un sens éditorial :
     *   1. Auto — cible principale pour l'utilisateur type "je veux que ça
     *      s'accorde avec le thème système", mise en avant en tête de liste.
     *   2. Blanc / Noir — les deux neutres extrêmes qui donnent le plus
     *      grand contraste texte, marchent sur presque tous les wallpapers.
     *   3. Couleurs vives (bleu, teal, vert, orange, rouge, violet) —
     *      ordre standard de la roue chromatique pour une navigation
     *      visuelle naturelle.
     */
    val Backgrounds: List<WidgetColorOption> = listOf(
        WidgetColorOption(argb = null, labelRes = R.string.widget_color_auto),
        WidgetColorOption(argb = 0xFFFAFAFA.toInt(), labelRes = R.string.widget_color_white),
        WidgetColorOption(argb = 0xFF212121.toInt(), labelRes = R.string.widget_color_black),
        WidgetColorOption(argb = 0xFF1976D2.toInt(), labelRes = R.string.widget_color_blue),
        WidgetColorOption(argb = 0xFF00796B.toInt(), labelRes = R.string.widget_color_teal),
        WidgetColorOption(argb = 0xFF388E3C.toInt(), labelRes = R.string.widget_color_green),
        WidgetColorOption(argb = 0xFFF57C00.toInt(), labelRes = R.string.widget_color_orange),
        WidgetColorOption(argb = 0xFFC62828.toInt(), labelRes = R.string.widget_color_red),
        WidgetColorOption(argb = 0xFF7B1FA2.toInt(), labelRes = R.string.widget_color_purple)
    )

    /**
     * Couleurs texte proposées. Palette VOLONTAIREMENT plus courte que celle
     * du fond :
     *
     *   - "Auto" fait 90% du boulot bien : blanc sur fond sombre, noir sur
     *     fond clair, calculé par luminance. La plupart des users ne
     *     changeront jamais cette option.
     *   - Les couleurs vives comme texte sont rarement souhaitables — dans
     *     un widget qui doit rester lisible, du texte rouge sur fond bleu
     *     est illisible. On offre uniquement les neutres (blanc, noir, +
     *     deux gris) pour laisser un peu de flexibilité sans encourager les
     *     combos illisibles.
     *
     * Si l'utilisateur choisit Auto pour le texte MAIS un fond custom, le
     * code de rendu utilise la luminance du fond custom pour décider
     * blanc/noir automatiquement.
     */
    val Texts: List<WidgetColorOption> = listOf(
        WidgetColorOption(argb = null, labelRes = R.string.widget_color_auto),
        WidgetColorOption(argb = 0xFFFFFFFF.toInt(), labelRes = R.string.widget_color_white),
        WidgetColorOption(argb = 0xFF000000.toInt(), labelRes = R.string.widget_color_black),
        WidgetColorOption(argb = 0xFFEEEEEE.toInt(), labelRes = R.string.widget_color_light_grey),
        WidgetColorOption(argb = 0xFF424242.toInt(), labelRes = R.string.widget_color_dark_grey)
    )
}
