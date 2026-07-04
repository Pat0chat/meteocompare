package com.meteocompare.app.widget

import android.content.Context
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.WeatherCondition
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
     * Prévision étendue affichée par le layout 4×2. Contient jusqu'à 4
     * items (heures ou jours selon la config utilisateur). Vide si le mode
     * 4×2 n'est pas utilisé ou si aucun modèle ne fournit assez de données.
     */
    val forecasts: List<WidgetForecastItem>,
    val error: WidgetError?
) {
    companion object {
        /** Placeholder "widget pas encore configuré". */
        val NotConfigured = WidgetData(
            cityName = null,
            currentTemp = null,
            currentCondition = null,
            tempMax = null,
            tempMin = null,
            confidencePct = null,
            precipMm = null,
            precipConfidencePct = null,
            currentCloudCover = null,
            forecasts = emptyList(),
            error = WidgetError.NotConfigured
        )

        /** Placeholder "chargement en cours" — pas encore de données mais on est configuré. */
        val Loading = WidgetData(
            cityName = null,
            currentTemp = null,
            currentCondition = null,
            tempMax = null,
            tempMin = null,
            confidencePct = null,
            precipMm = null,
            precipConfidencePct = null,
            currentCloudCover = null,
            forecasts = emptyList(),
            error = WidgetError.Loading
        )
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
 *   2. Fetch le forecast via la stream repository. `first()` : on prend la
 *      PREMIÈRE émission — cache si dispo, sinon réseau. Pas d'attente de la
 *      réémission "fresh" — le widget préfère montrer du contenu vite quitte
 *      à être un peu périmé, plutôt que rester blanc en attendant le réseau.
 *   3. Calcule les agrégats via ConfidenceCalculator (mêmes helpers que l'app).
 *
 * On garde le fetch au niveau ForecastRepository plutôt que d'implémenter un
 * cache-only bespoke pour le widget : le comportement "cache-first, réseau au
 * second plan" est déjà celui du repository, aucun besoin de dupliquer.
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
        ?: return WidgetData(
            cityName = null,
            currentTemp = null, currentCondition = null,
            tempMax = null, tempMin = null,
            confidencePct = null, precipMm = null,
            precipConfidencePct = null, currentCloudCover = null,
            forecasts = emptyList(),
            error = WidgetError.CityNoLongerInFavorites
        )

    val result = entry.forecastRepository().getCityForecastStream(city).firstOrNull()

    return when (result) {
        is ApiResult.Success -> {
            val forecast = result.data
            val calc = entry.confidenceCalculator()
            val today = forecast.seriesByModel.values
                .firstOrNull()?.daily?.dates?.firstOrNull()
            val dayConf = today?.let { calc.dayConfidence(forecast, it) }
            val rainConfidence = dayConf?.precipitation as?
                com.meteocompare.app.domain.model.PrecipitationConfidence.Rain
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
                forecasts = buildForecasts(forecast, forecastMode, city.timezone),
                error = null
            )
        }
        is ApiResult.Error -> WidgetData(
            cityName = city.name,
            currentTemp = null, currentCondition = null,
            tempMax = null, tempMin = null,
            confidencePct = null, precipMm = null,
            precipConfidencePct = null, currentCloudCover = null,
            forecasts = emptyList(),
            error = WidgetError.Fetch(result.message)
        )
        null -> WidgetData(
            cityName = city.name,
            currentTemp = null, currentCondition = null,
            tempMax = null, tempMin = null,
            confidencePct = null, precipMm = null,
            precipConfidencePct = null, currentCloudCover = null,
            forecasts = emptyList(),
            error = WidgetError.Fetch("no data")
        )
    }
}

/**
 * Construit la liste des 4 items de prévision étendue pour le layout 4×2.
 *
 * On utilise **le modèle de plus haute résolution** disponible dans le forecast
 * — pas la moyenne pondérée. Justification : sur un widget compact, montrer
 * les valeurs d'UN modèle est plus lisible que d'agréger 12 modèles en un
 * seul chiffre (perte d'info sans marqueur de confiance à cette granularité).
 * Le badge de confiance globale en haut du widget suffit à signaler
 * l'incertitude. AROME HD est privilégié en France, ICON-D2 en Europe centrale,
 * etc.
 *
 * Fallback : si aucun modèle n'expose weather_code (typique AROME HD), l'icône
 * sera null dans l'item et le layout affichera juste la température.
 */
private fun buildForecasts(
    forecast: com.meteocompare.app.domain.model.CityForecast,
    mode: ForecastMode,
    timezone: String?
): List<WidgetForecastItem> {
    val bestSeries = forecast.seriesByModel.entries
        .minByOrNull { it.key.resolutionKm }?.value
        ?: return emptyList()

    val zone = runCatching { java.time.ZoneId.of(timezone ?: "UTC") }
        .getOrDefault(java.time.ZoneId.of("UTC"))

    return when (mode) {
        ForecastMode.HOURLY -> buildHourlyForecasts(bestSeries.hourly, zone)
        ForecastMode.DAILY -> buildDailyForecasts(bestSeries.daily)
    }
}

private fun buildHourlyForecasts(
    hourly: com.meteocompare.app.domain.model.HourlyForecast,
    zone: java.time.ZoneId
): List<WidgetForecastItem> {
    if (hourly.timestamps.isEmpty()) return emptyList()
    // Prochaine heure = première timestamp >= maintenant. Si aucune (série
    // ne couvre que le passé — cas dégénéré), on prend juste le début.
    val now = java.time.Instant.now()
    val startIdx = hourly.timestamps.indexOfFirst { it >= now }
        .takeIf { it >= 0 } ?: 0
    val formatter = java.time.format.DateTimeFormatter.ofPattern("H'h'", java.util.Locale.getDefault())
    return (startIdx until minOf(startIdx + 4, hourly.timestamps.size)).map { i ->
        val ts = hourly.timestamps[i]
        val label = ts.atZone(zone).format(formatter)
        val condition = hourly.weatherCode.getOrNull(i)
            ?.let { com.meteocompare.app.domain.model.WeatherCondition.fromWmoCode(it) }
        val temp = hourly.temperature2m.getOrNull(i)
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
            .replace(".", "") // "lun." → "Lun" — plus propre côté widget
        val condition = daily.weatherCode.getOrNull(i)
            ?.let { com.meteocompare.app.domain.model.WeatherCondition.fromWmoCode(it) }
        // Température MAX plutôt que courante — sur un widget prévision, l'user
        // veut le "à quoi ressemblera la journée" pas "il fait combien à minuit".
        val temp = daily.tempMax.getOrNull(i)
        WidgetForecastItem(label = label, condition = condition, temp = temp)
    }
}
