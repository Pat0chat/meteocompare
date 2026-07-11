package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.BatchedForecastResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Client Retrofit pour Open-Meteo **Historical Forecast API** —
 * https://open-meteo.com/en/docs/historical-forecast-api
 *
 * Distinct de [OpenMeteoApi] (prévisions actuelles) et de [ClimateArchiveApi]
 * (observations réanalyse ERA5). Cet endpoint retourne **ce que chaque modèle
 * avait prévu** à une date passée, tel que publié à l'époque.
 *
 * ## Cas d'usage : backfill du suivi de biais
 *
 * Au premier lancement de l'app, `forecast_samples` est vide côté "passé". Le
 * `snapshotForecast` piggybacké sur les fetches utilisateur ne peut capturer
 * QUE des prévisions pour aujourd'hui + le futur (7 jours). Sans cet endpoint,
 * il faut 14 jours minimum pour que le premier chip apparaisse (temps que
 * l'intersection `forecast_samples ∩ observation_samples` accumule 14 jours
 * de recouvrement).
 *
 * En backfillant 30 jours de prévisions passées via cet endpoint, on démarre
 * immédiatement avec une fenêtre déjà remplie. Un seul appel HTTP par ville
 * au premier lancement, jamais rappelé après.
 *
 * ## Réponse : mêmes DTOs que [OpenMeteoApi]
 *
 * Le schéma est identique à `/v1/forecast?models=...` : variables suffixées
 * par la clé du modèle (`temperature_2m_max_gfs_seamless`, etc.). On réutilise
 * [BatchedForecastResponseDto] avec ses `daily: JsonObject?` non-typé — le
 * parsing manuel côté use case reste léger (3 variables à extraire).
 *
 * ## Latence attendue
 *
 * 2-8 secondes pour 30 jours × N modèles (mesuré : ~3s pour 7 modèles sur
 * fibre). Timeout Ok par défaut (15s) suffisant.
 */
interface HistoricalForecastApi {

    @GET("v1/forecast")
    suspend fun getHistoricalForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("models") models: String,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        // On ne demande QUE les daily nécessaires au suivi de biais — pas
        // besoin des hourly (30 jours × 24h × N modèles serait ~10× plus
        // lourd pour zéro bénéfice fonctionnel).
        @Query("daily") daily: String = DEFAULT_DAILY_VARS,
        @Query("timezone") timezone: String = "auto",
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("precipitation_unit") precipitationUnit: String = "mm"
    ): BatchedForecastResponseDto

    companion object {
        /**
         * Trois variables suivies pour le biais (aligné sur
         * [com.meteocompare.app.domain.model.BiasVariable]). `temperature_2m_min`
         * ajoutée pour rester consistent avec [ClimateArchiveApi] même si
         * inutilisée par le suivi de biais actuel — surcout négligeable.
         */
        const val DEFAULT_DAILY_VARS =
            "temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max"
    }
}
