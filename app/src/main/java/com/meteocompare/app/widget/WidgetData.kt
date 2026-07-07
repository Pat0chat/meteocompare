package com.meteocompare.app.widget

import android.content.Context
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

/**
 * Snapshot des données affichées par le widget, pré-calculé côté suspending
 * pour que la composition Glance reste rapide (pas de fetch dans le composable).
 *
 * Toutes les valeurs numériques sont nullables : un modèle peut manquer une
 * variable, un cache antérieur à une feature peut ne pas contenir un champ,
 * et le widget doit toujours afficher quelque chose de raisonnable. Le layout
 * décide localement de masquer les zones sans donnée plutôt que d'afficher
 * "N/A" ou "—" qui bruiterait l'affichage.
 */
internal data class WidgetData(
    val cityName: String?,
    val currentTemp: Double?,
    val currentCondition: WeatherCondition?,
    val tempMax: Double?,
    val tempMin: Double?,
    val confidencePct: Int?,
    val precipMm: Double?,
    /**
     * Confiance (%) sur la prévision de précipitations quand les modèles
     * s'accordent sur "il pleut". Null si NoRain (pas de pluie prévue, pas
     * de badge à montrer) ou Divided (trop de désaccord — le badge de
     * confiance globale suffit à signaler l'incertitude, ne pas ajouter
     * du bruit).
     */
    val precipConfidencePct: Int?,
    /**
     * Couverture nuageuse "maintenant" (0-100), agrégée entre modèles.
     * Utilisée par le layout 4×1 quand la condition est cloudy/overcast.
     * Null si aucun modèle ne fournit cloud_cover à l'instant courant
     * (cache pré-feature) — le layout omet alors le badge.
     */
    val currentCloudCover: Int?,
    /**
     * Vitesse du vent "maintenant" en km/h, agrégée entre modèles.
     * Affichée par les layouts 4×1 et 4×2 dans la ligne extras. Toujours
     * exposée quand disponible, sans seuil — un jour de vent nul est aussi
     * un signal utile que 60 km/h. Null si aucun modèle ne fournit la
     * variable à l'instant courant (rare : tous les modèles Open-Meteo
     * l'exposent, mais robuste au cas d'un cache pré-feature).
     */
    val currentWindSpeedKmh: Double?,
    /**
     * Prévision étendue affichée par le layout 4×2. Contient jusqu'à 4
     * items (heures ou jours selon la config utilisateur). Vide si le mode
     * 4×2 n'est pas utilisé, si aucun modèle ne fournit assez de données,
     * ou si le mode utilisateur est un mode CONFIDENCE_* (auquel cas c'est
     * [confidenceStrip] qui est alimenté).
     */
    val forecasts: List<WidgetForecastItem>,
    /**
     * Snapshot compact de la bande de confiance pour affichage sur widget 4×2
     * en mode CONFIDENCE_*. Contient une série de segments colorés selon la
     * confiance à chaque instant, un mean/min/max pour l'affichage textuel,
     * et le % courant. Null si le mode utilisateur n'est pas un mode confidence
     * ou si aucun modèle ne fournit la variable demandée.
     */
    val confidenceStrip: WidgetConfidenceStrip? = null,
    val error: WidgetError?
) {
    companion object {
        /**
         * Constructeur d'états sans données (loading, erreur, non configuré) —
         * seuls [cityName] et [error] varient, tout le reste est null.
         * Évite la répétition de 8 champs `null` dans chaque cas d'erreur.
         */
        fun empty(cityName: String? = null, error: WidgetError): WidgetData = WidgetData(
            cityName = cityName,
            currentTemp = null,
            currentCondition = null,
            tempMax = null,
            tempMin = null,
            confidencePct = null,
            precipMm = null,
            precipConfidencePct = null,
            currentCloudCover = null,
            currentWindSpeedKmh = null,
            forecasts = emptyList(),
            confidenceStrip = null,
            error = error
        )

        /** Placeholder "widget pas encore configuré". */
        val NotConfigured = empty(error = WidgetError.NotConfigured)

        /** Placeholder "chargement en cours" — pas encore de données mais on est configuré. */
        val Loading = empty(error = WidgetError.Loading)
    }
}

/**
 * Item du strip de prévision étendue affiché en 4×2.
 *
 * `label` : représentation du "quand" — heure ("14h") ou jour ("Lun") selon
 * le mode utilisateur. Formaté côté producteur pour que la couche layout
 * n'ait qu'à afficher.
 *
 * `condition`/`temp` : nullables — un modèle peut fournir la température
 * mais pas le weather_code (AROME HD notamment). L'UI dégrade en cascade :
 * icône si dispo, sinon rien.
 */
internal data class WidgetForecastItem(
    val label: String,
    val condition: WeatherCondition?,
    val temp: Double?
)

/**
 * Snapshot compact d'une bande de confiance pour rendu widget.
 *
 * Le widget 4×2 ne peut pas rendre un vrai Canvas (Glance ne supporte pas
 * `androidx.compose.foundation.Canvas` — seulement des primitives Row/Column/
 * Box). On dégrade donc la bande en une série de "cellules colorées" alignées
 * horizontalement, façon heatmap : chaque cellule = un pas de temps, teintée
 * selon le niveau de confiance à cet instant.
 *
 * `metricLabel` : "T°", "mm", ou "km/h" — libellé court affiché à côté du
 * strip pour rappeler ce qu'on regarde (sinon le user zappe et ne sait plus
 * quelle métrique il a choisie).
 *
 * `nowValue` : valeur "maintenant" de la métrique (température actuelle,
 * précipitation actuelle, vent actuel). Affichée en gros à gauche du strip
 * pour donner un ancrage numérique — sans ça la bande visuelle seule serait
 * trop abstraite pour un coup d'œil.
 *
 * `currentPct` : % de confiance à l'instant courant. Affiché à côté de
 * `nowValue` avec la couleur du niveau (vert/orange/rouge).
 *
 * `bucketPercents` : liste des confidences par cellule (typiquement 24 pas
 * pour couvrir 7 jours à ~7h/pas). L'ordre est chronologique, gauche→droite.
 */
internal data class WidgetConfidenceStrip(
    val metricLabel: String,
    val nowValue: String?,
    val currentPct: Int?,
    val bucketPercents: List<Int>
)

/**
 * États d'erreur affichables. On distingue explicitement chaque cas pour
 * pouvoir choisir un message et un CTA différents dans le layout — un widget
 * "pas configuré" ouvre la config activity au tap, un widget "ville supprimée"
 * ouvre l'app pour re-choisir, un widget "réseau" reste discret.
 */
internal sealed class WidgetError {
    /** Aucune ville sélectionnée dans les prefs — user vient d'ajouter le widget. */
    data object NotConfigured : WidgetError()
    /** Chargement en cours — état transient entre le change de config et l'arrivée des données. */
    data object Loading : WidgetError()
    /** La ville configurée n'est plus dans les favoris (user l'a supprimée). */
    data object CityNoLongerInFavorites : WidgetError()
    /** Fetch réseau échoué ET pas de cache disponible pour cette ville. */
    data class Fetch(val message: String) : WidgetError()
}

/**
 * Résout les données du widget pour un [cityId] donné (ou l'état "pas configuré"
 * si null). Suspending — appelé depuis provideGlance() qui est un CoroutineScope.
 *
 * Étapes :
 *   1. Cherche la ville dans les favoris. Absente → CityNoLongerInFavorites.
 *   2. Lit l'intervalle de rafraîchissement utilisateur et fetch le forecast
 *      via la stream repository avec `maxCacheAgeMs` = cet intervalle.
 *   3. `first()` : on prend la PREMIÈRE émission — cache si assez récent
 *      (aucune requête réseau), sinon cache immédiat suivi de fetch, mais
 *      on ne l'attend pas — on affiche déjà quelque chose.
 *   4. Calcule les agrégats via ConfidenceCalculator (mêmes helpers que l'app).
 *
 * ─── Économie batterie/data via maxCacheAgeMs ────────────────────────────
 * L'ancien code fetchait TOUJOURS le réseau, même si le cache était vieux
 * de quelques secondes. Sur un widget rafraîchi toutes les 15 min par
 * WorkManager, cela signifiait 5 requêtes réseau × 4 modèles × 4 fois par
 * heure = 80 requêtes/heure, dont la grande majorité renvoient les mêmes
 * données que le cache. Le passage `maxCacheAgeMs = interval` élimine ce
 * gaspillage : si le cache est plus jeune que l'intervalle utilisateur, on
 * réutilise juste le cache sans requête réseau.
 *
 * Pour MANUAL (interval = ZERO), on considère le cache comme toujours frais
 * — le widget ne fetch plus jamais automatiquement, seul un pull-to-refresh
 * dans l'app rafraîchira les données.
 */
internal suspend fun loadWidgetData(
    context: Context,
    cityId: String?,
    forecastMode: ForecastMode
): WidgetData {
    if (cityId == null) return WidgetData.NotConfigured

    val entry = EntryPointAccessors.fromApplication(
        context.applicationContext,
        WidgetEntryPoint::class.java
    )

    val favorites = entry.cityRepository().observeFavorites().first()
    val city = favorites.firstOrNull { it.id == cityId }
        ?: return WidgetData.empty(error = WidgetError.CityNoLongerInFavorites)

    // Lecture de l'intervalle utilisateur pour respecter le seuil de fraîcheur
    // cache. Un widget rafraîchi par WorkManager toutes les 15 min mais avec
    // un intervalle utilisateur de 1h ne fera de fetch réseau QU'une fois par
    // heure (les 3 autres runs se contentent du cache).
    //
    // MANUAL : on utilise Long.MAX_VALUE comme seuil → tout cache est considéré
    // "frais", aucun fetch réseau ne se déclenche automatiquement. Le user
    // doit ouvrir l'app et pull-to-refresh pour rafraîchir manuellement.
    val interval = entry.userPreferencesRepository().observeRefreshInterval().first()
    val maxCacheAgeMs = if (interval == RefreshInterval.MANUAL) Long.MAX_VALUE
        else interval.millis

    val result = entry.forecastRepository()
        .getCityForecastStream(city, maxCacheAgeMs = maxCacheAgeMs)
        .firstOrNull()

    return when (result) {
        is ApiResult.Success -> {
            val forecast = result.data
            val calc = entry.confidenceCalculator()
            val today = forecast.seriesByModel.values
                .firstOrNull()?.daily?.dates?.firstOrNull()
            val dayConf = today?.let { calc.dayConfidence(forecast, it) }
            val rainConfidence = dayConf?.precipitation as?
                com.meteocompare.app.domain.model.PrecipitationConfidence.Rain

            // Selon le mode utilisateur, on alimente soit la ligne de prévisions
            // 4 items (HOURLY/DAILY), soit la mini bande de confiance
            // (CONFIDENCE_*). Les deux ne sont jamais alimentés en même temps
            // — c'est une exclusivité contrôlée par le mode config utilisateur.
            val forecasts = if (forecastMode.isConfidenceBand()) emptyList()
                else buildForecasts(forecast, forecastMode, city.timezone)
            val confidenceStrip = if (forecastMode.isConfidenceBand())
                buildConfidenceStrip(forecast, forecastMode, calc)
                else null

            WidgetData(
                cityName = city.name,
                currentTemp = calc.currentTemperature(forecast),
                currentCondition = calc.currentWeatherCondition(forecast),
                tempMax = dayConf?.tempMax?.meanValue,
                tempMin = dayConf?.tempMin?.meanValue,
                confidencePct = dayConf?.overallPercent,
                precipMm = rainConfidence?.meanMm,
                precipConfidencePct = rainConfidence?.percent,
                currentCloudCover = calc.currentCloudCover(forecast),
                currentWindSpeedKmh = calc.currentWindSpeed(forecast),
                forecasts = forecasts,
                confidenceStrip = confidenceStrip,
                error = null
            )
        }
        is ApiResult.Error -> WidgetData.empty(
            cityName = city.name,
            error = WidgetError.Fetch(result.message)
        )
        null -> WidgetData.empty(
            cityName = city.name,
            error = WidgetError.Fetch("no data")
        )
    }
}

/**
 * Construit la liste des 4 items de prévision étendue pour le layout 4×2.
 *
 * ─── Choix du modèle "meilleur" ────────────────────────────────────────
 * On veut le modèle le plus fin (résolution basse en km) qui ait :
 *   1. Assez d'horizon pour couvrir 4 items (4h en HOURLY, 4j en DAILY)
 *   2. Des `weather_code` non-vides pour afficher des icônes
 *
 * L'ancien code prenait le plus haut résolution sans filtre — AROME HD (1.5km)
 * gagnait toujours. Or AROME HD n'expose PAS weather_code (voir Note dans le
 * README) → aucune icône dans le widget 4×2. Idem AROME HD ne couvre que ~2j,
 * ce qui laissait J+2 et J+3 vides en mode DAILY.
 *
 * Fallback en 3 niveaux :
 *   a) Modèle fin AVEC weather_code et horizon suffisant (idéal : ICON-D2 en
 *      Europe centrale, ARPEGE Europe partout, ECMWF/GFS en global)
 *   b) Modèle avec horizon suffisant mais SANS weather_code — on utilisera
 *      l'inférence précipitation-based (voir WeatherCondition.inferFromPrecipAndTemp)
 *   c) N'importe quel modèle disponible, dernier recours pour ne pas retourner
 *      une liste vide (préférable à afficher rien)
 */
private fun buildForecasts(
    forecast: com.meteocompare.app.domain.model.CityForecast,
    mode: ForecastMode,
    timezone: String?
): List<WidgetForecastItem> {
    val zone = runCatching { java.time.ZoneId.of(timezone ?: "UTC") }
        .getOrDefault(java.time.ZoneId.of("UTC"))
    val now = java.time.Instant.now()

    // Combien d'items chaque modèle candidate peut-il fournir depuis "maintenant" ?
    fun horizonSize(series: com.meteocompare.app.domain.model.ForecastSeries): Int =
        when (mode) {
            ForecastMode.HOURLY -> {
                val startIdx = series.hourly.timestamps.indexOfFirst { it >= now }
                if (startIdx < 0) 0
                else series.hourly.timestamps.size - startIdx
            }
            ForecastMode.DAILY -> series.daily.dates.size
            // Ne devrait jamais arriver : buildForecasts n'est appelée QUE quand
            // !forecastMode.isConfidenceBand() (voir loadWidgetData). On retourne
            // 0 par safety pour rester exhaustif sans crasher.
            ForecastMode.CONFIDENCE_TEMPERATURE,
            ForecastMode.CONFIDENCE_PRECIPITATION,
            ForecastMode.CONFIDENCE_WIND -> 0
        }

    fun hasWeatherCodes(series: com.meteocompare.app.domain.model.ForecastSeries): Boolean =
        when (mode) {
            ForecastMode.HOURLY -> series.hourly.weatherCode.isNotEmpty()
            ForecastMode.DAILY -> series.daily.weatherCode.isNotEmpty()
            ForecastMode.CONFIDENCE_TEMPERATURE,
            ForecastMode.CONFIDENCE_PRECIPITATION,
            ForecastMode.CONFIDENCE_WIND -> false
        }

    // Priorité (a) : couverture 4 items + weather_code présent
    val ideal = forecast.seriesByModel.entries
        .filter { horizonSize(it.value) >= 4 && hasWeatherCodes(it.value) }
        .minByOrNull { it.key.resolutionKm }?.value
    // Priorité (b) : couverture suffisante, weather_code peut manquer (inférence)
    val fallback = forecast.seriesByModel.entries
        .filter { horizonSize(it.value) >= 4 }
        .minByOrNull { it.key.resolutionKm }?.value
    // Priorité (c) : n'importe quel modèle avec au moins 1 item — dernière chance
    val lastResort = forecast.seriesByModel.entries
        .minByOrNull { it.key.resolutionKm }?.value

    val bestSeries = ideal ?: fallback ?: lastResort ?: return emptyList()

    return when (mode) {
        ForecastMode.HOURLY -> buildHourlyForecasts(bestSeries.hourly, zone)
        ForecastMode.DAILY -> buildDailyForecasts(bestSeries.daily)
        ForecastMode.CONFIDENCE_TEMPERATURE,
        ForecastMode.CONFIDENCE_PRECIPITATION,
        ForecastMode.CONFIDENCE_WIND -> emptyList()
    }
}

private fun buildHourlyForecasts(
    hourly: com.meteocompare.app.domain.model.HourlyForecast,
    zone: java.time.ZoneId
): List<WidgetForecastItem> {
    if (hourly.timestamps.isEmpty()) return emptyList()
    val now = java.time.Instant.now()
    val startIdx = hourly.timestamps.indexOfFirst { it >= now }
        .takeIf { it >= 0 } ?: 0
    val formatter = java.time.format.DateTimeFormatter.ofPattern("H'h'", java.util.Locale.getDefault())
    return (startIdx until minOf(startIdx + 4, hourly.timestamps.size)).map { i ->
        val ts = hourly.timestamps[i]
        val label = ts.atZone(zone).format(formatter)
        // Priorité au weather_code natif. Si absent (AROME HD notamment),
        // fallback sur inférence précipitation-based : même règle que dans
        // ConfidenceCalculator.dailyConditionsByModel. Sur AROME HD sans pluie
        // ni gel, inferFromPrecipAndTemp renverra null et l'UI affichera "—"
        // (pas d'icône) — accepté comme limitation d'AROME HD sans cloud_cover.
        val code = hourly.weatherCode.getOrNull(i)
        val precip = hourly.precipitation.getOrNull(i)
        val temp = hourly.temperature2m.getOrNull(i)
        val condition = com.meteocompare.app.domain.model.WeatherCondition.fromWmoCode(code)
            ?: com.meteocompare.app.domain.model.WeatherCondition.inferFromPrecipAndTemp(
                precipMm = precip,
                tempMinC = temp
            )
        WidgetForecastItem(label = label, condition = condition, temp = temp)
    }
}

private fun buildDailyForecasts(
    daily: com.meteocompare.app.domain.model.DailyForecast
): List<WidgetForecastItem> {
    if (daily.dates.isEmpty()) return emptyList()
    val locale = java.util.Locale.getDefault()
    return daily.dates.take(4).mapIndexed { i, date ->
        val label = date.dayOfWeek
            .getDisplayName(java.time.format.TextStyle.SHORT, locale)
            .replaceFirstChar { it.uppercase() }
            .replace(".", "")
        val code = daily.weatherCode.getOrNull(i)
        val precip = daily.precipitationSum.getOrNull(i)
        val tempMin = daily.tempMin.getOrNull(i)
        val temp = daily.tempMax.getOrNull(i)
        val condition = com.meteocompare.app.domain.model.WeatherCondition.fromWmoCode(code)
            ?: com.meteocompare.app.domain.model.WeatherCondition.inferFromPrecipAndTemp(
                precipMm = precip,
                tempMinC = tempMin
            )
        WidgetForecastItem(label = label, condition = condition, temp = temp)
    }
}

/**
 * Construit le snapshot de bande de confiance pour affichage widget.
 *
 * Réutilise directement les mêmes méthodes que l'écran détail
 * ([ConfidenceCalculator.hourlyTemperatureConfidence] etc.) — cohérence
 * garantie entre app et widget, une seule source de vérité pour la logique
 * d'agrégation.
 *
 * Downsampling : la bande fait typiquement 168 pas horaires (7 jours). Pour
 * un widget qui fait ~350 dp de large avec des cellules 12 dp, on ne peut
 * afficher que 20-25 cellules max sans qu'elles deviennent illisibles. On
 * échantillonne donc à 24 buckets équirépartis — un pas ≈ 7h, granularité
 * suffisante pour voir "ça se gâte cet après-midi" ou "ça s'améliore samedi".
 * Cohérent avec le strip du chart grand format qui utilise la même
 * granularité (voir ConfidenceTimeline dans HourlyConfidenceChart.kt).
 *
 * Format de `nowValue` : température en °, précip en mm, vent en km/h — même
 * unité que l'app pour continuité perceptuelle.
 */
private fun buildConfidenceStrip(
    forecast: CityForecast,
    mode: ForecastMode,
    calc: ConfidenceCalculator
): WidgetConfidenceStrip? {
    val bands = when (mode) {
        ForecastMode.CONFIDENCE_TEMPERATURE -> calc.hourlyTemperatureConfidence(forecast)
        ForecastMode.CONFIDENCE_PRECIPITATION -> calc.hourlyPrecipitationConfidence(forecast)
        ForecastMode.CONFIDENCE_WIND -> calc.hourlyWindConfidence(forecast)
        else -> return null // sécurité — signature contrôlée par isConfidenceBand()
    }
    if (bands.size < 2) return null

    // Downsample à 24 buckets uniformes. Si la série fait déjà ≤ 24 points
    // (série tronquée par un cache pauvre), on la garde telle quelle.
    val bucketPercents = if (bands.size <= 24) bands.map { it.percent }
    else {
        val step = bands.size / 24
        bands.filterIndexed { idx, _ -> idx % step == 0 }
            .take(24)
            .map { it.percent }
    }

    // Valeur "maintenant" — reprend l'agrégat pondéré de l'app (cohérent avec
    // la TodaySummaryCard). Pour le vent et la pluie, on utilise les getters
    // dédiés du calculator qui traitent proprement les cas où seul un
    // sous-ensemble de modèles a la variable.
    val nowValue = when (mode) {
        ForecastMode.CONFIDENCE_TEMPERATURE -> {
            calc.currentTemperature(forecast)?.let { formatTemp(it) }
        }
        ForecastMode.CONFIDENCE_PRECIPITATION -> {
            // Précipitation "maintenant" — pas de getter dédié, on prend la
            // valeur mean de la première bande (== instant courant).
            bands.first().meanValue.let {
                if (it < 0.1) "0 mm" else "%.1f mm".format(it)
            }
        }
        ForecastMode.CONFIDENCE_WIND -> {
            calc.currentWindSpeed(forecast)?.let { "${it.toInt()} km/h" }
        }
        else -> null
    }

    val metricLabel = when (mode) {
        ForecastMode.CONFIDENCE_TEMPERATURE -> "T°"
        ForecastMode.CONFIDENCE_PRECIPITATION -> "Pluie"
        ForecastMode.CONFIDENCE_WIND -> "Vent"
        else -> ""
    }

    return WidgetConfidenceStrip(
        metricLabel = metricLabel,
        nowValue = nowValue,
        currentPct = bands.first().percent,
        bucketPercents = bucketPercents
    )
}
