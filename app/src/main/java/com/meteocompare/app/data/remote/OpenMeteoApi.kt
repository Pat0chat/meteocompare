package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.ForecastResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {

    /**
     * Récupère les prévisions horaires + journalières pour un seul modèle.
     *
     * Pour comparer plusieurs modèles, faire N appels en parallèle avec
     * un `models` différent — cela évite de devoir parser les suffixes
     * comme `temperature_2m_icon_seamless`.
     *
     * ─── Piste d'optimisation future : batching multi-modèles ───────────
     * Open-Meteo accepte `&models=arome_france_hd,arpege_europe,icon_eu`
     * dans une seule requête. Cela permettrait de passer de N requêtes
     * HTTP à 1 seule (avec N modèles activés dans les préférences).
     *
     * Bénéfices attendus :
     *   - Économie de handshake TLS (1 vs N) → gain latence perçue
     *   - Économie batterie mobile (moins de wakeup radio)
     *   - Moins de code de retry/timeout à orchestrer côté repository
     *
     * Coûts du refactor (raison pour laquelle ce n'est pas fait ici) :
     *   - Le response DTO devient dynamique : `temperature_2m_arome_france_hd`,
     *     `temperature_2m_arpege_europe`, etc. → parser sur mesure (JsonElement
     *     tree + suffix parsing) au lieu du @Serializable auto de kotlinx.
     *   - Perte de granularité sur les erreurs : si un modèle est indisponible,
     *     Open-Meteo retourne quand même 200 avec des NaN → il faut détecter
     *     ces NaN par modèle pour reporter proprement l'échec.
     *   - Change le contrat public de [ForecastRepository] (retour actuel :
     *     N flows indépendants avec succès/échec individuels).
     *
     * À considérer si le nombre médian de modèles activés dépasse 8, ou si
     * les métriques UX remontent une latence perçue élevée sur les cellulaires
     * lents (3G/EDGE).
     *
     * Doc : https://open-meteo.com/en/docs
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
        @Query("hourly") hourly: String =
            "temperature_2m,precipitation,precipitation_probability,cloud_cover," +
                "wind_speed_10m,wind_direction_10m,weather_code",
        @Query("daily") daily: String =
            "temperature_2m_max,temperature_2m_min,precipitation_sum," +
                "precipitation_probability_max,wind_speed_10m_max," +
                "wind_direction_10m_dominant,weather_code",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("precipitation_unit") precipitationUnit: String = "mm"
    ): ForecastResponseDto
}
