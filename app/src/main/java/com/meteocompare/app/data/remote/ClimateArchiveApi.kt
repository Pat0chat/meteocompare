package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.ArchiveResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Historical Weather API (archive).
 *
 * Deux consommateurs partagent cet endpoint mais PAS le même contrat métier :
 * - les repères climatiques demandent uniquement Tmax/Tmin sur ERA5 ;
 * - le suivi de biais demande Tmax + cumul pluie + vent max sur la source
 *   historique choisie par Open-Meteo.
 *
 * Le paramètre [daily] est donc volontairement explicite à chaque appel. Cela
 * empêche qu'une évolution des repères climatiques retire silencieusement une
 * variable dont le pipeline de biais a besoin (ou inversement).
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
        @Query("daily") daily: String,
        @Query("timezone") timezone: String = "auto",
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("precipitation_unit") precipitationUnit: String = "mm",
        /** Source de réanalyse explicite si le caller a besoin d'une série homogène. */
        @Query("models") models: String? = null
    ): ArchiveResponseDto

    companion object {
        /** Repères calendaires : mêmes unités/semantiques que Tmin/Tmax daily du forecast. */
        const val NORMALS_DAILY_VARS = "temperature_2m_max,temperature_2m_min"

        /** Référence du biais : grandeurs quotidiennes comparées aux forecasts J+1. */
        const val BIAS_DAILY_VARS =
            "temperature_2m_max,precipitation_sum,wind_speed_10m_max"
    }
}
