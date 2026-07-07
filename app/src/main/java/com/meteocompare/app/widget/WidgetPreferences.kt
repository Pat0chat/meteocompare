package com.meteocompare.app.widget

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Clés de persistence des paramètres par-widget.
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

    const val DEFAULT_OPACITY_PCT = 80

    /**
     * Défaut = HOURLY : "les 4 prochaines heures" est le signal le plus
     * actionnable pour un widget consulté en cours de journée. Les modes
     * confidence restent opt-in — plus abstraits, ils demandent une lecture
     * intentionnelle plutôt qu'un coup d'œil rapide.
     */
    val DEFAULT_FORECAST_MODE = ForecastMode.HOURLY
}

/**
 * Contenu de la ligne du bas du widget 4×2.
 *
 *   - [HOURLY] : 4 prévisions horaires (labels "14h", "15h", …) — c'est le
 *     comportement historique, défaut de l'app. Signal le plus actionnable
 *     à courte échéance.
 *   - [DAILY] : 4 prévisions journalières (labels "Lun", "Mar", …) — vue
 *     synthétique de la semaine à venir.
 *   - [CONFIDENCE_TEMPERATURE] : mini bande de confiance température sur
 *     l'horizon complet (7 jours), rendue comme un strip coloré selon la
 *     confiance locale. Réplique visuelle compacte du graphe grand-format
 *     de l'écran détail.
 *   - [CONFIDENCE_PRECIPITATION] : idem pour la pluie.
 *   - [CONFIDENCE_WIND] : idem pour le vent.
 *
 * Ces 3 modes confidence sont l'application au widget de la même feature qui
 * a été ajoutée à l'écran détail — un utilisateur qui trouve la bande de
 * confiance utile veut pouvoir la voir en un coup d'œil sur son écran d'accueil
 * sans ouvrir l'app.
 *
 * Sealed via enum plutôt que sealed class : pas de données associées, juste
 * un discriminant simple. La persistance stocke `name` (String), la lecture
 * fait `runCatching { valueOf(...) }` pour tolérer une clé inconnue en cache
 * (retombe sur le défaut sans crash).
 */
internal enum class ForecastMode {
    HOURLY,
    DAILY,
    CONFIDENCE_TEMPERATURE,
    CONFIDENCE_PRECIPITATION,
    CONFIDENCE_WIND
}

/**
 * Helper : cette mode affiche-t-elle une bande de confiance (vs une prévision
 * discrète 4 items) ? Sert dans le widget pour choisir le layout du bas.
 */
internal fun ForecastMode.isConfidenceBand(): Boolean = when (this) {
    ForecastMode.CONFIDENCE_TEMPERATURE,
    ForecastMode.CONFIDENCE_PRECIPITATION,
    ForecastMode.CONFIDENCE_WIND -> true
    ForecastMode.HOURLY, ForecastMode.DAILY -> false
}
