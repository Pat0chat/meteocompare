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
            error = WidgetError.NotConfigured
        )
    }
}

/**
 * États d'erreur affichables. On distingue explicitement chaque cas pour
 * pouvoir choisir un message et un CTA différents dans le layout — un widget
 * "pas configuré" ouvre la config activity au tap, un widget "ville supprimée"
 * ouvre l'app pour re-choisir, un widget "réseau" reste discret.
 */
internal sealed class WidgetError {
    /** Aucune ville sélectionnée dans les prefs — user vient d'ajouter le widget. */
    data object NotConfigured : WidgetError()
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
internal suspend fun loadWidgetData(context: Context, cityId: String?): WidgetData {
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
            // PrecipitationConfidence est sealed — on ne récupère un montant
            // que sur la variante Rain (modèles d'accord "il pleut", moyenne
            // en mm). NoRain → pas de nombre à montrer (pas de pluie prévue),
            // Divided → trop de désaccord pour un chiffre unique — le badge
            // de confiance basse le signale déjà.
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
                error = null
            )
        }
        is ApiResult.Error -> WidgetData(
            cityName = city.name,
            currentTemp = null, currentCondition = null,
            tempMax = null, tempMin = null,
            confidencePct = null, precipMm = null,
            precipConfidencePct = null, currentCloudCover = null,
            error = WidgetError.Fetch(result.message)
        )
        null -> WidgetData(
            cityName = city.name,
            currentTemp = null, currentCondition = null,
            tempMax = null, tempMin = null,
            confidencePct = null, precipMm = null,
            precipConfidencePct = null, currentCloudCover = null,
            error = WidgetError.Fetch("no data")
        )
    }
}
