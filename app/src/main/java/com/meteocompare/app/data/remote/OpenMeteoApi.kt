package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.BatchedForecastResponseDto
import com.meteocompare.app.data.remote.dto.ForecastResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Client Retrofit pour Open-Meteo — https://open-meteo.com/en/docs
 *
 * ─── Deux endpoints, deux DTOs ────────────────────────────────────────────
 *
 *   [getForecast]        : single-modèle, réponse à variables non-suffixées.
 *                          Conservé pour rétro-compat et tests unitaires
 *                          (setup de fake API plus simple). Non utilisé
 *                          en runtime par le repository → cf. plus bas.
 *
 *   [getForecastBatched] : multi-modèles en une seule requête HTTP. Variables
 *                          suffixées par la clé du modèle (`temperature_2m_gfs_seamless`),
 *                          décomposées côté domaine par [BatchedForecastSplitter].
 *                          C'est le code path DE PRODUCTION depuis l'introduction
 *                          du batching.
 *
 * ─── Pourquoi batcher ? ─────────────────────────────────────────────────
 * L'app compare N modèles météo (N = 7 par défaut, jusqu'à 17 configurables).
 * Sans batching, un refresh = N requêtes HTTPS parallèles :
 *
 *   - N handshakes TLS (~50-200 ms chacun)
 *   - N wakeups radio cellulaire → coût batterie non négligeable sur 3G/4G
 *   - N points de panne indépendants (retry par modèle) ET N timeouts
 *     séquentiels si le serveur est lent
 *
 * Avec batching, un refresh = 1 requête HTTPS :
 *
 *   - 1 seul handshake TLS
 *   - 1 seul wakeup radio (idéal pour la batterie)
 *   - Réponse "tout ou rien" : plus simple, cohérent, et Open-Meteo est
 *     conçu pour ce mode (variables suffixées natives)
 *
 * Contrepartie : perte de granularité de retry (on ne peut plus réessayer
 * juste GFS si GFS a échoué). Acceptable car Open-Meteo répond 200 + null
 * pour un modèle indisponible plutôt qu'une erreur HTTP → [BatchedForecastSplitter]
 * filtre ces "modèles vides" et laisse le reste passer.
 */
interface OpenMeteoApi {

    /**
     * Prévisions single-modèle avec variables NON suffixées.
     *
     * Utilisé pour tests unitaires ou expérimentations manuelles. Le path
     * de production passe par [getForecastBatched] même quand un seul
     * modèle est demandé (le splitter gère le cas single-model via son
     * fallback non-suffixé — cohérent, un seul path à maintenir).
     */
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("models") models: String,
        // weather_code (WMO 4677) ajouté pour permettre l'affichage iconique
        // du temps (soleil, nuages, pluie, etc.) en plus des chiffres.
        // Disponible sur tous les modèles utilisés par l'app (AROME, ARPEGE,
        // ICON, GFS, ECMWF) — donc pas de risque d'erreur 400 sélective.
        @Query("hourly") hourly: String = DEFAULT_HOURLY_VARS,
        @Query("daily") daily: String = DEFAULT_DAILY_VARS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("precipitation_unit") precipitationUnit: String = "mm"
    ): ForecastResponseDto

    /**
     * Prévisions multi-modèles en une seule requête HTTP. La réponse porte
     * les variables sous forme SUFFIXÉE par la clé du modèle, décomposée
     * ensuite par [BatchedForecastSplitter] en un [ForecastResponseDto] par
     * modèle demandé.
     *
     * @param models Liste des clés de modèles Open-Meteo séparées par des
     *   virgules. Exemple : `"arome_france_hd,arpege_europe,gfs_seamless"`.
     *   Construite depuis `WeatherModel.entries.joinToString(",") { it.apiKey }`.
     *
     * @param forecastDays Horizon commun à tous les modèles. Il faut passer
     *   `models.maxOf { it.maxForecastDays }` (borné par le forecastDays voulu
     *   côté UI) sinon les modèles à long horizon (GFS 16j, ECMWF 10j) sont
     *   tronqués. Les modèles à horizon plus court (AROME HD 2j) retournent
     *   simplement null au-delà, ce que le mapper gère déjà proprement.
     */
    @GET("v1/forecast")
    suspend fun getForecastBatched(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("models") models: String,
        @Query("hourly") hourly: String = DEFAULT_HOURLY_VARS,
        @Query("daily") daily: String = DEFAULT_DAILY_VARS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("precipitation_unit") precipitationUnit: String = "mm"
    ): BatchedForecastResponseDto

    companion object {
        /**
         * Liste des variables horaires demandées — centralisée pour rester
         * synchronisée entre single-modèle et batched. Toute variable ajoutée
         * ici doit AUSSI être ajoutée aux `HourlyVar` de [BatchedForecastSplitter]
         * sinon elle sera visible dans la réponse mais silencieusement ignorée
         * côté domaine (surcoût réseau sans bénéfice).
         */
        const val DEFAULT_HOURLY_VARS =
            "temperature_2m,precipitation,precipitation_probability,cloud_cover," +
                "wind_speed_10m,wind_direction_10m,weather_code"

        const val DEFAULT_DAILY_VARS =
            "temperature_2m_max,temperature_2m_min,precipitation_sum," +
                "precipitation_probability_max,wind_speed_10m_max," +
                "wind_direction_10m_dominant,weather_code"
    }
}
