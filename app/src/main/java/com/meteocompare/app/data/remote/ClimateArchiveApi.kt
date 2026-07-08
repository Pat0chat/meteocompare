package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.ArchiveResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Historical Weather API (archive).
 *
 * Endpoint : https://archive-api.open-meteo.com/v1/archive
 *
 * Limites de gratuité : 10 000 req/jour (partagées avec les autres endpoints
 * Open-Meteo). Un fetch de normales = 1 requête pour ~10 ans de données, ce
 * qui reste très en dessous du quota.
 *
 * ─── Variables demandées ─────────────────────────────────────────────────
 * On demande 4 variables daily : temp_max, temp_min, precipitation_sum,
 * wind_speed_10m_max. Les deux dernières servent à alimenter les traits
 * pointillés "normale 10 ans" sur les graphes de bande de confiance pluie
 * et vent respectivement — voir DayNormals pour la modélisation domaine.
 *
 * Note unités : on force `wind_speed_unit=kmh` pour matcher le forecast
 * (par défaut c'est km/h aussi mais l'expliciter garantit l'invariant).
 */
interface ClimateArchiveApi {
    @GET("v1/archive")
    suspend fun archive(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        /** Format yyyy-MM-dd */
        @Query("start_date") startDate: String,
        /** Format yyyy-MM-dd */
        @Query("end_date") endDate: String,
        @Query("daily") daily: String =
            "temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max",
        @Query("timezone") timezone: String = "auto",
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("precipitation_unit") precipitationUnit: String = "mm"
    ): ArchiveResponseDto
}
