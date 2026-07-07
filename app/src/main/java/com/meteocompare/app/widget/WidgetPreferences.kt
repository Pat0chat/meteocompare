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
     * Mode d'affichage des prévisions dans le layout 4×2 (colonne du bas).
     * Persisté comme String (nom de l'enum) pour rester lisible en cas de
     * migration ; int aurait été plus compact mais introduit un couplage
     * fragile "1=HOURLY 2=DAILY" facile à casser en renommant.
     */
    val ForecastModeKey = stringPreferencesKey("widget_forecast_mode")

    /**
     * Défaut = 80% : assez opaque pour rester lisible sur n'importe quel fond
     * d'écran (photo lumineuse ou sombre), mais laisse voir le wallpaper au
     * travers pour rappeler que c'est un widget. 100% (opaque) écrase tout,
     * 0% (transparent) rend le texte illisible sur un wallpaper contrasté.
     */
    const val DEFAULT_OPACITY_PCT = 80

    /**
     * Défaut = HOURLY : sur un widget de bureau consulté en cours de journée,
     * "les 4 prochaines heures" est le signal le plus actionnable (dois-je
     * prendre un parapluie en sortant ?). La vue journalière reste accessible
     * via le paramètre.
     */
    val DEFAULT_FORECAST_MODE = ForecastMode.HOURLY
}

/**
 * Deux modes d'affichage de la prévision étendue (4×2 uniquement).
 *
 * Sealed via enum plutôt que sealed class : pas de données associées, juste
 * un discriminant simple. La persistance stocke `name` (String), la lecture
 * fait `runCatching { valueOf(...) }` pour tolérer une clé inconnue en cache
 * (retombe sur le défaut sans crash).
 */
internal enum class ForecastMode {
    /** 4 prochaines heures — libellés type "14h", "15h"… */
    HOURLY,
    /** 4 prochains jours — libellés type "Lun", "Mar"… */
    DAILY
}
