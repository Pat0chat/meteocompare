package com.meteocompare.app.widget

import android.content.Context
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.applyPersistedLocale
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.util.localDateIn
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.HourlyConfidenceBand
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.util.ForecastAggregates
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
    /**
     * Quantité de pluie représentative pour aujourd'hui. En cas d'accord
     * complet, c'est la moyenne de tous les modèles pluvieux ; en cas de
     * désaccord, c'est la moyenne des seuls modèles qui annoncent une pluie
     * significative, afin de ne plus masquer complètement les millimètres.
     */
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
    /** Mode ayant produit la vue étendue. Conservé dans le snapshot pour que
     * le rendu puisse distinguer visuellement les 5 heures des 5 jours sans
     * essayer de déduire le mode depuis les libellés localisés. */
    val forecastMode: ForecastMode? = null,
    /**
     * Prévision étendue affichée par le layout 4×2. Contient jusqu'à 5
     * items (heures ou jours selon la config utilisateur). Vide si le mode
     * 4×2 n'est pas utilisé, si aucun modèle ne fournit assez de données,
     * ou si le mode utilisateur est un mode CONFIDENCE_* (auquel cas c'est
     * [confidenceStrips] qui est alimenté).
     */
    val forecasts: List<WidgetForecastItem>,
    /**
     * Les deux bandes de confiance synchronisées (température et pluie).
     * La liste est vide hors mode confiance. Une métrique peut être absente si
     * aucun modèle ne fournit la variable correspondante.
     */
    val confidenceStrips: List<WidgetConfidenceStrip> = emptyList(),
    /**
     * Températures agrégées 12h à partir de "maintenant" pour la mini prévision
     * du widget 2-row (mode [ForecastMode.MINI_FORECAST_12H]). Vide dans tous
     * les autres modes — le rendu du widget n'invoque pas le renderer bitmap.
     */
    val next12hTemps: List<Double?> = emptyList(),
    /**
     * Probabilités de précipitation (0-100) agrégées 12h, alignées sur
     * [next12hTemps]. Affichées dans chaque cellule horaire de la grille 2 × 6.
     */
    val next12hPrecipProb: List<Int?> = emptyList(),
    /**
     * Quantités de précipitation horaires en millimètres, agrégées entre
     * modèles et alignées sur [next12hTemps]. Avec [next12hPrecipProb], elles
     * pilotent l'intensité de la bande pluie du mini-forecast.
     */
    val next12hPrecipMm: List<Double?> = emptyList(),
    /**
     * Condition météo de consensus pour chaque heure, alignée sur
     * [next12hTemps]. Une valeur null signifie que les modèles ne fournissent
     * pas assez d'information pour afficher honnêtement un pictogramme.
     */
    val next12hConditions: List<WeatherCondition?> = emptyList(),
    /**
     * Moment de la première heure de [next12hTemps] dans le fuseau de la
     * ville, pour produire les 12 libellés horaires de la grille.
     * Null si la ville n'a pas de fuseau connu ou si le mode n'est pas
     * MINI_FORECAST_12H.
     */
    val hourlyStartTime: java.time.LocalDateTime? = null,
    /** Synthèse éditoriale la plus utile issue des événements multi-modèles. */
    val keyInsight: WidgetKeyInsight? = null,
    /** Instantané comparatif par variable pour les widgets à valeur ajoutée. */
    val comparisonSnapshot: WidgetComparisonSnapshot? = null,
    /** Nombre de modèles effectivement présents dans le forecast chargé. */
    val modelCount: Int = 0,
    val error: WidgetError?
) {
    companion object {
        /**
         * Constructeur d'états sans données (loading, erreur, non configuré) —
         * seuls [cityName] et [error] varient, tout le reste est null.
         * Évite la répétition des champs `null` dans chaque cas d'erreur.
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
            forecastMode = null,
            forecasts = emptyList(),
            confidenceStrips = emptyList(),
            keyInsight = null,
            comparisonSnapshot = null,
            modelCount = 0,
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
    val temp: Double?,
    /** Couverture nuageuse de l'échéance, 0-100%. */
    val cloudCoverPct: Int? = null,
    /** Probabilité de précipitation de l'échéance, 0-100%. */
    val precipProbabilityPct: Int? = null,
    /**
     * Confiance globale inter-modèles sur la prévision de l'échéance.
     *
     * En mode 5 heures, le score combine la convergence sur la température,
     * les précipitations et le vent. En mode 5 jours, il combine les scores
     * journaliers température min/max, précipitations et vent. Il ne dépend
     * donc pas de la seule pluie affichée juste au-dessus.
     *
     * Le score est calculé sur tous les modèles disponibles, puis pénalisé
     * lorsque seuls quelques modèles couvrent encore l'horizon. Null si moins
     * de deux familles de variables sont comparables : le widget ne présente
     * jamais une métrique isolée comme une confiance globale.
     */
    val forecastConfidencePct: Int? = null
)

/**
 * Snapshot compact d'une bande de confiance pour rendu widget.
 *
 * Le widget 4×2 ne peut pas rendre un vrai Canvas (Glance ne supporte pas
 * `androidx.compose.foundation.Canvas` — seulement des primitives Row/Column/
 * Box). On dégrade la bande en une série de "buckets journaliers" alignés
 * horizontalement, chaque bucket portant TROIS informations superposées :
 *
 *   1. Une cellule de couleur — le niveau de confiance à ce jour
 *   2. Une valeur numérique — la prévision agrégée pour ce jour (T°/mm/km/h)
 *   3. Un libellé — le jour de la semaine ("Auj.", "Mar", "Mer", ...)
 *
 * ─── Pourquoi des buckets JOURNALIERS et pas horaires ? ─────────────────
 * Version précédente : 24 cellules très fines couvrant les 168 h. Belle
 * densité de couleur mais aucun ancrage temporel/numérique au milieu de la
 * strip — l'utilisateur voyait un dégradé sans savoir "22° quand ?".
 *
 * Nouvelle version : jusqu’à 5 buckets, un par jour. Chaque cellule est un peu plus
 * large et surtout PORTE une valeur numérique en dessous. La confiance devient
 * lisible parce qu'on sait à quoi elle réfère.
 *
 * ─── Champs ────────────────────────────────────────────────────────────
 * `metricLabel` : "T°", "Pluie", "Vent" — libellé métrique de la strip.
 *
 * `buckets` : liste ordonnée chronologiquement des buckets de la strip.
 * Aujourd'hui en premier, puis jours suivants. Typiquement 5 éléments
 * (5 jours d'horizon), moins si la série est plus courte.
 */
internal data class WidgetConfidenceStrip(
    val metricLabel: String,
    val buckets: List<StripBucket>
)

/**
 * Un bucket journalier de la strip de confiance widget.
 *
 * @property percent Niveau de confiance prudent sur les heures futures
 *   (quartile bas, ajusté à la couverture modèles). Dicte la couleur de la
 *   cellule (vert/orange/rouge via [confidenceColor]).
 * @property value Prévision agrégée pour la journée, PRÉ-FORMATÉE prête à
 *   afficher ("22°", "0.5 mm", "18 km/h"). Format cohérent avec l'app.
 * @property label Libellé jour de la semaine court ("Auj.", "Mar", "Mer"...).
 *   Localisé côté [buildConfidenceStrip] via context.
 */
internal data class StripBucket(
    val percent: Int,
    val value: String,
    val label: String,
    /** Nombre minimal de modèles disponibles sur les heures du bucket. */
    val modelCount: Int,
    /** Nombre total de modèles activés pour cette prévision. */
    val totalModelCount: Int
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
 *   3. `lastOrNull()` : si le cache est frais, il reste l'unique émission.
 *      S'il est périmé, le repository émet d'abord le cache puis lance le
 *      réseau ; on attend la dernière émission pour réellement afficher les
 *      données fraîches. En cas d'échec réseau après un cache, le repository
 *      termine sans erreur supplémentaire et la dernière valeur reste le cache.
 *   4. Calcule les agrégats via ConfidenceCalculator (mêmes helpers que l'app).
 *
 * ─── Économie batterie/data via maxCacheAgeMs ────────────────────────────
 * Le passage `maxCacheAgeMs = interval` évite un fetch à chaque tick du
 * worker : si le cache est plus jeune que l'intervalle utilisateur, le widget
 * le réutilise sans requête réseau. Le repository conserve par ailleurs un
 * fetch batched pour tous les modèles activés.
 *
 * Pour MANUAL, tout cache existant est considéré frais. Un premier chargement
 * peut toutefois amorcer le cache s'il est vide ; après cela, seul un refresh
 * explicite dans l'app renouvelle les données réseau.
 */
internal suspend fun loadWidgetData(
    context: Context,
    cityId: String?,
    forecastMode: ForecastMode,
    includeValueSnapshot: Boolean = false
): WidgetData {
    if (cityId == null) return WidgetData.NotConfigured

    // Enrobe le context avec la locale persistée AVANT tout appel à
    // context.getString(...). Sans ça, les libellés du widget rendus par
    // Glance étaient toujours en langue système alors que l'app peut être
    // configurée sur une autre langue via Settings. Bug typique cross-process
    // Glance : le widget provider tourne avec le Context sans configuration
    // AppCompat, donc n'hérite pas de la locale du recreate() de MainActivity.
    val localizedContext = applyPersistedLocale(context)

    val entry = EntryPointAccessors.fromApplication(
        localizedContext.applicationContext,
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
    // MANUAL : Long.MAX_VALUE rend tout cache existant "frais". Un bootstrap
    // réseau reste possible si aucun cache n'existe encore, puis les mises à
    // jour suivantes sont exclusivement manuelles.
    val prefsRepo = entry.userPreferencesRepository()
    val interval = prefsRepo.observeRefreshInterval().first()
    // Aligne les modèles du widget sur ceux que l'utilisateur a choisis dans
    // Settings. Sans ce lookup, `getCityForecastStream` retombait sur le
    // défaut `WeatherModel.MVP_SELECTION`, qui pouvait différer de la
    // sélection app — résultat : deux entrées de cache disjointes (les clés
    // Room sont (cityId, modelKey), donc un modèle absent d'un côté n'est
    // pas réutilisable par l'autre), et des fetches distincts alors qu'une
    // seule réponse batched peut servir aux deux consommateurs.
    // Avec `enabledModels`, app et widget écrivent/lisent EXACTEMENT les
    // mêmes lignes de cache → le premier à démarrer sert le second.
    val enabledModels = prefsRepo.observeEnabledModels().first()
    val maxCacheAgeMs = if (interval == RefreshInterval.MANUAL) Long.MAX_VALUE
    else interval.millis

    val result = entry.forecastRepository()
        .getCityForecastStream(
            city = city,
            models = enabledModels,
            maxCacheAgeMs = maxCacheAgeMs
        )
        .awaitWidgetTerminalEmission()

    return when (result) {
        is ApiResult.Success -> withContext(Dispatchers.Default) {
            val forecast = result.data
            val calc = entry.confidenceCalculator()
            val currentInstant = java.time.Instant.now()
            val today = currentInstant.localDateIn(city.timezone)
            val dayConf = calc.dayConfidence(forecast, today)
            val precipitation = dayConf.precipitation
            val rainConfidence = precipitation as?
                com.meteocompare.app.domain.model.PrecipitationConfidence.Rain
            val precipAmountMm = when (precipitation) {
                is com.meteocompare.app.domain.model.PrecipitationConfidence.Rain ->
                    precipitation.meanMm
                is com.meteocompare.app.domain.model.PrecipitationConfidence.Divided ->
                    precipitation.rainMeanMm
                else -> null
            }

            // Selon le mode utilisateur, on alimente soit la ligne de prévisions
            // 5 items (HOURLY/DAILY), soit la mini bande de confiance
            // (CONFIDENCE_*), soit la mini prévision 12h (MINI_FORECAST_12H).
            // Les trois sont exclusifs — c'est ExtraLargeLayout qui aiguille.
            //
            // La confiance placée sous la probabilité de pluie est GLOBALE :
            // elle décrit la convergence des modèles sur la prévision complète,
            // et non la seule quantité de pluie. Les calculs restent limités au
            // mode réellement affiché pour ne pas alourdir les autres widgets.
            val totalModelCount = forecast.seriesByModel.size.coerceAtLeast(1)
            val forecastConfidence = when (forecastMode) {
                ForecastMode.HOURLY -> WidgetForecastConfidence(
                    hourlyByTimestamp = hourlyForecastConfidenceByTimestamp(
                        temperatureBands = calc.hourlyTemperatureConfidence(forecast),
                        precipitationBands = calc.hourlyPrecipitationConfidence(forecast),
                        windBands = calc.hourlyWindConfidence(forecast),
                        totalModelCount = totalModelCount
                    )
                )
                ForecastMode.DAILY -> WidgetForecastConfidence(
                    dailyByDate = dailyForecastConfidenceByDate(
                        days = calc.weeklyConfidence(forecast),
                        totalModelCount = totalModelCount
                    )
                )
                else -> WidgetForecastConfidence.Empty
            }
            val forecasts = if (forecastMode.isConfidenceBand() || forecastMode.isMiniForecast()) {
                emptyList()
            } else {
                buildForecasts(
                    forecast = forecast,
                    mode = forecastMode,
                    timezone = city.timezone,
                    now = currentInstant,
                    forecastConfidence = forecastConfidence
                )
            }
            val confidenceStrips = if (forecastMode.isConfidenceBand())
                buildAllConfidenceStrips(localizedContext, forecast, calc, currentInstant)
            else emptyList()

            // ─── Mini forecast 12h (nouveau mode) ─────────────────────────
            // Réutilise l'agrégateur de la home pour la cohérence : mêmes valeurs
            // dans le widget que dans la card home. hourlyStartTime = maintenant
            // tronqué à l'heure, dans le fuseau de la ville (les ancres
            // s'affichent en heure locale ville, pas device).
            val miniForecastNow = currentInstant
            val valueWidgetData = if (includeValueSnapshot) {
                buildWidgetValueSnapshot(
                    context = localizedContext,
                    forecast = forecast,
                    now = miniForecastNow
                )
            } else {
                WidgetValueSnapshot(keyInsight = null, comparison = null)
            }
            val miniForecast = if (forecastMode.isMiniForecast()) {
                ForecastAggregates.next12h(
                    forecast = forecast,
                    now = miniForecastNow,
                    includeConditions = true
                )
            } else {
                null
            }
            val hourlyStartTime = if (miniForecast != null) {
                val zone = runCatching {
                    java.time.ZoneId.of(city.timezone ?: "UTC")
                }.getOrDefault(java.time.ZoneId.of("UTC"))
                miniForecastNow
                    .atZone(zone)
                    .toLocalDateTime()
                    .truncatedTo(java.time.temporal.ChronoUnit.HOURS)
            } else {
                null
            }

            WidgetData(
                cityName = city.name,
                currentTemp = calc.currentTemperature(forecast, currentInstant),
                currentCondition = calc.currentWeatherCondition(forecast, currentInstant),
                tempMax = dayConf.tempMax?.meanValue,
                tempMin = dayConf.tempMin?.meanValue,
                confidencePct = dayConf.overallPercent,
                precipMm = precipAmountMm,
                precipConfidencePct = rainConfidence?.percent,
                currentCloudCover = calc.currentCloudCover(forecast, currentInstant),
                currentWindSpeedKmh = calc.currentWindSpeed(forecast, currentInstant),
                forecastMode = forecastMode.normalized(),
                forecasts = forecasts,
                confidenceStrips = confidenceStrips,
                next12hTemps = miniForecast?.temperatures.orEmpty(),
                next12hPrecipProb = miniForecast?.precipitationProbabilities.orEmpty(),
                next12hPrecipMm = miniForecast?.precipitationAmountsMm.orEmpty(),
                next12hConditions = miniForecast?.conditions.orEmpty(),
                hourlyStartTime = hourlyStartTime,
                keyInsight = valueWidgetData.keyInsight,
                comparisonSnapshot = valueWidgetData.comparison,
                modelCount = forecast.seriesByModel.size,
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
 * Attend la fin du flux repository au lieu de s'arrêter sur le cache initial.
 * Un cache périmé est émis avant le résultat réseau ; utiliser `first()` ici
 * annulerait l'amont avant même que le fetch ne démarre.
 */
internal suspend fun <T> Flow<T>.awaitWidgetTerminalEmission(): T? = lastOrNull()

/**
 * Construit la liste des 5 items de prévision étendue pour le layout 4×2.
 *
 * ─── Choix du modèle "meilleur" ────────────────────────────────────────
 * Les réponses batched utilisent un axe temporel commun. La taille de
 * `dates`/`timestamps` ne prouve donc pas qu'un modèle possède réellement des
 * valeurs sur cinq échéances : un modèle court peut avoir cinq positions dont
 * les trois dernières sont nulles.
 *
 * On construit les cinq cartes de chaque modèle et on classe les candidats
 * selon les données réellement exploitables : cinq échéances complètes,
 * présence des détails utiles, puis ordre stable des modèles activés. Ce choix
 * évite de traiter la résolution spatiale comme un score de qualité et empêche
 * de sélectionner un modèle régional dont l'horizon utile est incomplet.
 */
internal fun buildForecasts(
    forecast: com.meteocompare.app.domain.model.CityForecast,
    mode: ForecastMode,
    timezone: String?,
    now: java.time.Instant = java.time.Instant.now(),
    forecastConfidence: WidgetForecastConfidence = WidgetForecastConfidence.Empty
): List<WidgetForecastItem> {
    val zone = runCatching { java.time.ZoneId.of(timezone ?: "UTC") }
        .getOrDefault(java.time.ZoneId.of("UTC"))

    data class Candidate(
        val stableOrder: Int,
        val items: List<WidgetForecastItem>
    ) {
        val visibleItems: List<WidgetForecastItem> = items.take(5)
        val temperatureCount: Int = visibleItems.count { it.temp != null }
        val conditionCount: Int = visibleItems.count { it.condition != null }
        val detailCount: Int = visibleItems.sumOf { item ->
            (if (item.cloudCoverPct != null) 1 else 0) +
                (if (item.precipProbabilityPct != null) 1 else 0)
        }
        val complete: Boolean = visibleItems.size == 5 && temperatureCount == 5
        val fullyDetailed: Boolean = complete && detailCount == 10
    }

    /*
     * Les tableaux batched partagent le même axe temporel pour tous les modèles.
     * Un modèle court comme AROME HD reçoit donc bien 5/7 dates dans `dates`,
     * mais ses valeurs deviennent null après son horizon réel. Compter la taille
     * des listes sélectionnait à tort ce modèle fin : deux jours remplis puis
     * trois cartes vides, et parfois cinq heures vides autour d'un run incomplet.
     *
     * On construit désormais les cinq cartes réelles de chaque modèle, puis on
     * choisit d'abord un candidat COMPLET. À complétude égale, on favorise les
     * cartes qui possèdent le plus de détails utiles, puis l'ordre stable du
     * modèle. Si aucun modèle ne couvre les cinq échéances, on prend celui qui
     * fournit le plus de températures utiles, sans masquer les données disponibles.
     */
    val candidates = forecast.seriesByModel.entries.mapIndexed { stableOrder, (model, series) ->
        val items = when (mode) {
            ForecastMode.HOURLY -> buildHourlyForecasts(
                hourly = series.hourly,
                zone = zone,
                now = now,
                forecastConfidenceByTimestamp = forecastConfidence.hourlyByTimestamp
            )
            ForecastMode.DAILY -> buildDailyForecasts(
                daily = series.daily,
                hourly = series.hourly,
                zone = zone,
                forecastConfidenceByDate = forecastConfidence.dailyByDate
            )
            ForecastMode.CONFIDENCE_ALL,
            ForecastMode.CONFIDENCE_TEMPERATURE,
            ForecastMode.CONFIDENCE_PRECIPITATION,
            ForecastMode.CONFIDENCE_WIND,
            ForecastMode.MINI_FORECAST_12H -> emptyList()
        }
        Candidate(stableOrder, items)
    }.filter { it.visibleItems.isNotEmpty() }

    val best = candidates.minWithOrNull(
        compareByDescending<Candidate> { it.complete }
            .thenByDescending { it.fullyDetailed }
            .thenByDescending { it.detailCount }
            .thenByDescending { it.temperatureCount }
            .thenByDescending { it.conditionCount }
            .thenBy { it.stableOrder }
    ) ?: return emptyList()

    return best.visibleItems
}

internal fun buildHourlyForecasts(
    hourly: com.meteocompare.app.domain.model.HourlyForecast,
    zone: java.time.ZoneId,
    now: java.time.Instant = java.time.Instant.now(),
    forecastConfidenceByTimestamp: Map<java.time.Instant, Int> = emptyMap()
): List<WidgetForecastItem> {
    if (hourly.timestamps.isEmpty()) return emptyList()
    val startIdx = hourly.timestamps.indexOfFirst { it >= now }
    // Un cache dont tout l'horizon horaire est déjà passé ne doit pas afficher
    // les cinq premières heures historiques comme si elles étaient futures.
    if (startIdx < 0) return emptyList()
    val formatter = java.time.format.DateTimeFormatter.ofPattern("H'h'", java.util.Locale.getDefault())
    return (startIdx until minOf(startIdx + 5, hourly.timestamps.size)).map { i ->
        val ts = hourly.timestamps[i]
        val label = ts.atZone(zone).format(formatter)
        // Priorité au weather_code natif. Si absent (AROME HD notamment),
        // fallback sur l'inférence précipitation/température.
        val code = hourly.weatherCode.getOrNull(i)
        val precip = hourly.precipitation.getOrNull(i)
        val temp = hourly.temperature2m.getOrNull(i)
        val condition = com.meteocompare.app.domain.model.WeatherCondition.fromWmoCode(code)
            ?: com.meteocompare.app.domain.model.WeatherCondition.inferFromPrecipAndTemp(
                precipMm = precip,
                tempMinC = temp
            )
        WidgetForecastItem(
            label = label,
            condition = condition,
            temp = temp,
            cloudCoverPct = hourly.cloudCover.getOrNull(i),
            precipProbabilityPct = hourly.precipitationProbability.getOrNull(i),
            forecastConfidencePct = forecastConfidenceByTimestamp[ts]
        )
    }
}

internal fun buildDailyForecasts(
    daily: com.meteocompare.app.domain.model.DailyForecast,
    hourly: com.meteocompare.app.domain.model.HourlyForecast,
    zone: java.time.ZoneId,
    forecastConfidenceByDate: Map<java.time.LocalDate, Int> = emptyMap()
): List<WidgetForecastItem> {
    if (daily.dates.isEmpty()) return emptyList()
    val locale = java.util.Locale.getDefault()
    return daily.dates.take(5).mapIndexed { i, date ->
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
        WidgetForecastItem(
            label = label,
            condition = condition,
            temp = temp,
            cloudCoverPct = dailyCloudCoverPct(hourly, date, zone),
            precipProbabilityPct = daily.precipitationProbabilityMax.getOrNull(i),
            forecastConfidencePct = forecastConfidenceByDate[date]
        )
    }
}

/**
 * Confiance globale pré-calculée pour les cartes 5 heures / 5 jours.
 *
 * Les maps sont séparées afin de rendre impossible un mélange accidentel entre
 * timestamp UTC et date civile locale. [Empty] évite les allocations et garde
 * les appels de [buildForecasts] simples dans les tests sans confiance.
 */
internal data class WidgetForecastConfidence(
    val hourlyByTimestamp: Map<java.time.Instant, Int> = emptyMap(),
    val dailyByDate: Map<java.time.LocalDate, Int> = emptyMap()
) {
    companion object {
        val Empty = WidgetForecastConfidence()
    }
}

/**
 * Combine les confiances horaires température, précipitations et vent.
 *
 * Chaque métrique est d'abord ajustée à sa couverture réelle des modèles.
 * Une confiance globale n'est produite que si au moins deux familles de
 * variables sont disponibles au même timestamp : une seule métrique, même
 * très confiante, ne peut pas représenter honnêtement toute la prévision.
 */
internal fun hourlyForecastConfidenceByTimestamp(
    temperatureBands: List<HourlyConfidenceBand>,
    precipitationBands: List<HourlyConfidenceBand>,
    windBands: List<HourlyConfidenceBand>,
    totalModelCount: Int
): Map<java.time.Instant, Int> {
    val metrics = listOf(temperatureBands, precipitationBands, windBands)
        .map { bands -> bands.associateBy { it.timestamp } }
    val timestamps = metrics.flatMap { it.keys }.distinct().sorted()

    return timestamps.mapNotNull { timestamp ->
        val scores = metrics.mapNotNull metric@ { byTimestamp ->
            val band = byTimestamp[timestamp] ?: return@metric null
            coverageAdjustedConfidence(
                percent = band.percent,
                contributingModels = band.modelCount,
                totalModels = totalModelCount
            )
        }
        if (scores.size < 2) null
        else timestamp to scores.average().roundToInt().coerceIn(0, 100)
    }.toMap()
}

/**
 * Combine les confidences journalières déjà utilisées par City Details.
 *
 * Les composantes sont température max/min, précipitations et vent max. Comme
 * pour l'horaire, chaque score est ajusté à la couverture et au moins deux
 * composantes sont requises. Le pourcentage affiché sous la pluie décrit donc
 * bien la prévision du jour dans son ensemble.
 */
internal fun dailyForecastConfidenceByDate(
    days: List<DayConfidence>,
    totalModelCount: Int
): Map<java.time.LocalDate, Int> = days.mapNotNull { day ->
    val scores = listOfNotNull(
        day.tempMax?.let { it.percent to it.modelCount },
        day.tempMin?.let { it.percent to it.modelCount },
        day.precipitation?.let { it.percent to it.modelCount },
        day.windMax?.let { it.percent to it.modelCount }
    ).map { (percent, modelCount) ->
        coverageAdjustedConfidence(
            percent = percent,
            contributingModels = modelCount,
            totalModels = totalModelCount
        )
    }

    if (scores.size < 2) null
    else day.date to scores.average().roundToInt().coerceIn(0, 100)
}.toMap()

/** Ajustement de couverture partagé par les scores globaux horaire et quotidien. */
private fun coverageAdjustedConfidence(
    percent: Int,
    contributingModels: Int,
    totalModels: Int
): Int = conservativeConfidencePercent(
    percents = listOf(percent),
    contributingModels = contributingModels,
    totalModels = totalModels
)

/**
 * Couverture nuageuse moyenne d'une journée pour un modèle.
 *
 * On privilégie les heures 7h-19h locales, plus représentatives de ce que
 * l'utilisateur voit réellement en consultant une prévision journalière. Si
 * aucune valeur diurne n'est disponible, on retombe sur toutes les heures de
 * la journée afin de rester compatible avec les horizons partiels.
 */
internal fun dailyCloudCoverPct(
    hourly: com.meteocompare.app.domain.model.HourlyForecast,
    date: java.time.LocalDate,
    zone: java.time.ZoneId
): Int? {
    val valuesForDate = hourly.timestamps.indices.mapNotNull { index ->
        val local = hourly.timestamps[index].atZone(zone)
        if (local.toLocalDate() != date) return@mapNotNull null
        hourly.cloudCover.getOrNull(index)?.let { local.hour to it }
    }
    if (valuesForDate.isEmpty()) return null
    val daytime = valuesForDate.filter { (hour, _) -> hour in 7..19 }
    val selected = if (daytime.isNotEmpty()) daytime else valuesForDate
    return selected.map { it.second }.average().roundToInt().coerceIn(0, 100)
}

/**
 * Construit le snapshot de bande de confiance pour affichage widget.
 *
 * Réutilise directement les mêmes méthodes que l'écran détail
 * ([ConfidenceCalculator.hourlyTemperatureConfidence] etc.) — cohérence
 * garantie entre app et widget, une seule source de vérité pour la logique
 * d'agrégation.
 *
 * ─── Agrégation par jour ───────────────────────────────────────────────
 * On regroupe les bandes horaires (~168 pas) en buckets JOURNALIERS. Pour
 * chaque jour civil couvert par la série :
 *
 *   percent = moyenne des `percent` de toutes les bandes tombant dans ce jour
 *   value   = moyenne des `meanValue` de toutes les bandes tombant dans ce jour
 *
 * Cette agrégation par jour civil (dans la timezone de la ville, pas UTC)
 * garantit que "aujourd'hui" ne dépasse pas au milieu de la nuit locale — un
 * bucket ne mélange pas mardi soir avec mercredi matin.
 *
 * Format de `value` : température en ° et précipitations en mm — mêmes
 * unités que l'app pour continuité perceptuelle.
 *
 * Labels de jour :
 *   Position 0 : "Auj." (chaîne locale, cf. R.string.widget_confidence_now_short)
 *   Positions 1+ : nom court du jour de la semaine ("Mar", "Mer", ...) via
 *                  [java.time.DayOfWeek.getDisplayName] pour la locale active.
 */
private fun buildAllConfidenceStrips(
    context: Context,
    forecast: CityForecast,
    calc: ConfidenceCalculator,
    now: java.time.Instant
): List<WidgetConfidenceStrip> = listOfNotNull(
    buildConfidenceStrip(context, forecast, ForecastMode.CONFIDENCE_TEMPERATURE, calc, now),
    buildConfidenceStrip(context, forecast, ForecastMode.CONFIDENCE_PRECIPITATION, calc, now)
)

private fun buildConfidenceStrip(
    context: Context,
    forecast: CityForecast,
    mode: ForecastMode,
    calc: ConfidenceCalculator,
    now: java.time.Instant
): WidgetConfidenceStrip? {
    val bands = when (mode) {
        ForecastMode.CONFIDENCE_TEMPERATURE -> calc.hourlyTemperatureConfidence(forecast)
        ForecastMode.CONFIDENCE_PRECIPITATION -> calc.hourlyPrecipitationConfidence(forecast)
        ForecastMode.CONFIDENCE_WIND -> calc.hourlyWindConfidence(forecast)
        else -> return null // sécurité — signature contrôlée par isConfidenceBand()
    }
    if (bands.size < 2) return null

    val zone = runCatching {
        java.time.ZoneId.of(forecast.city.timezone)
    }.getOrDefault(java.time.ZoneId.systemDefault())
    val locale = context.resources.configuration.locales[0]
        ?: java.util.Locale.getDefault()

    // Le widget répond à la question "à partir de maintenant" : les heures
    // déjà passées ne doivent pas relever artificiellement (ou abaisser) la
    // confiance du jour courant.
    val futureBands = bands.filter { it.timestamp >= now }
    if (futureBands.size < 2) return null

    // Groupement par jour civil dans la timezone de la ville. LinkedHashMap
    // pour préserver l'ordre chronologique — critique pour l'affichage.
    val byDay = LinkedHashMap<java.time.LocalDate, MutableList<
            com.meteocompare.app.domain.model.HourlyConfidenceBand>>()
    for (band in futureBands) {
        val day = band.timestamp.atZone(zone).toLocalDate()
        byDay.getOrPut(day) { mutableListOf() }.add(band)
    }

    // Cinq jours correspondent au nombre de colonnes affichées sur les
    // widgets 4×2 et 5×2. Les formats plus petits en montrent un sous-ensemble.
    val today = now.atZone(zone).toLocalDate()
    val nowShortLabel = context.getString(R.string.widget_confidence_now_short)
    val totalModels = forecast.seriesByModel.size.coerceAtLeast(1)
    val buckets = byDay.entries.take(5).map { (date, dayBands) ->
        val minModelCount = dayBands.minOf { it.modelCount }
        val conservativePercent = conservativeConfidencePercent(
            percents = dayBands.map { it.percent },
            contributingModels = minModelCount,
            totalModels = totalModels
        )
        val avgValue = dayBands.sumOf { it.meanValue } / dayBands.size
        StripBucket(
            percent = conservativePercent,
            value = formatBucketValue(mode, avgValue),
            label = if (date == today) nowShortLabel
            else date.dayOfWeek
                .getDisplayName(java.time.format.TextStyle.SHORT, locale)
                .replace(".", ""),
            modelCount = minModelCount,
            totalModelCount = totalModels
        )
    }

    if (buckets.isEmpty()) return null

    val metricLabel = when (mode) {
        ForecastMode.CONFIDENCE_TEMPERATURE ->
            context.getString(R.string.widget_metric_temperature)
        ForecastMode.CONFIDENCE_PRECIPITATION ->
            context.getString(R.string.widget_metric_precipitation)
        ForecastMode.CONFIDENCE_WIND ->
            context.getString(R.string.widget_metric_wind)
        ForecastMode.HOURLY,
        ForecastMode.DAILY,
        ForecastMode.CONFIDENCE_ALL,
        ForecastMode.MINI_FORECAST_12H -> return null
    }

    return WidgetConfidenceStrip(
        metricLabel = metricLabel,
        buckets = buckets
    )
}

/**
 * Score journalier prudent pour le widget de confiance.
 *
 * 1. On retient le quartile bas des scores horaires plutôt que leur moyenne :
 *    une fenêtre très incertaine ne disparaît plus dans une journée globalement
 *    stable.
 * 2. On applique une pénalité de couverture : un accord entre 2 modèles sur 7
 *    ne peut pas être présenté avec la même force qu'un accord entre 7 modèles.
 */
internal fun conservativeConfidencePercent(
    percents: List<Int>,
    contributingModels: Int,
    totalModels: Int
): Int {
    if (percents.isEmpty()) return 0
    val sorted = percents.map { it.coerceIn(0, 100) }.sorted()
    val lowerQuartile = sorted[((sorted.size - 1) * 0.25).toInt()]
    val coverage = if (totalModels <= 0) 0.0
    else contributingModels.coerceIn(0, totalModels).toDouble() / totalModels
    val coverageFactor = 0.60 + 0.40 * coverage
    return (lowerQuartile * coverageFactor).roundToInt().coerceIn(0, 100)
}

/**
 * Format compact de la valeur agrégée d'un bucket selon la métrique.
 *
 * Extrait de [buildConfidenceStrip] pour rester lisible et pour partager
 * la même règle de formatage à travers TOUS les buckets (sinon "aujourd'hui"
 * et "demain" pourraient dériver visuellement).
 *
 * ─── Contraintes de largeur ────────────────────────────────────────────
 * Un bucket widget 4×2 fait ~40 dp de large. Le texte doit tenir en 4-5
 * caractères max :
 *   Temp  : "22°" (3 char) — OK
 *   Précip: "0.5 mm" (6 char) — tight ; on omet l'unité "mm" quand une seule
 *           métrique est affichée dans la strip et gardée en libellé du haut.
 *           Compromis : format "0.5" seul, on comprend via metricLabel.
 *   Vent  : "18" (2-3 char) — sans unité pour la même raison.
 */
private fun formatBucketValue(mode: ForecastMode, value: Double): String = when (mode) {
    ForecastMode.CONFIDENCE_TEMPERATURE -> formatTemp(value)
    ForecastMode.CONFIDENCE_PRECIPITATION -> {
        // Précipitation : arrondi à 0 pour valeurs sous 0.1 mm (trace),
        // sinon 1 décimale. L'unité "mm" est portée par metricLabel="Pluie"
        // dans la ligne du haut, pour ne pas surcharger la valeur elle-même.
        if (value < 0.1) "0" else "%.1f".format(value)
    }
    ForecastMode.CONFIDENCE_WIND -> "${value.toInt()}"
    else -> ""
}