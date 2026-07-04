package com.meteocompare.app.widget

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Clés de persistence des paramètres par-widget.
 *
 * Le stockage utilise le [androidx.glance.state.PreferencesGlanceStateDefinition]
 * fourni par Glance : chaque instance de widget a son propre fichier DataStore,
 * identifié par le GlanceId. On peut donc avoir plusieurs widgets sur l'écran
 * d'accueil, chacun pointant sur une ville différente et avec sa propre
 * opacité.
 *
 * On garde les clés STRING pour cityId (id de ville venant du domaine, string)
 * et INT pour l'opacité (0-100, entier — évite les problèmes de precision Float
 * dans DataStore et matche le pattern "slider entier" côté UI).
 */
internal object WidgetPreferences {
    /** Id de la ville favorite affichée par ce widget. Null = pas configuré. */
    val CityIdKey = stringPreferencesKey("widget_city_id")

    /** Opacité du fond en pourcentage entier, 0..100. 80 par défaut. */
    val OpacityPctKey = intPreferencesKey("widget_opacity_pct")

    /**
     * Défaut = 80% : assez opaque pour rester lisible sur n'importe quel fond
     * d'écran (photo lumineuse ou sombre), mais laisse voir le wallpaper au
     * travers pour rappeler que c'est un widget. 100% (opaque) écrase tout,
     * 0% (transparent) rend le texte illisible sur un wallpaper contrasté.
     */
    const val DEFAULT_OPACITY_PCT = 80
}
