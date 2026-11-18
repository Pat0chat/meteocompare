package com.meteocompare.app.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Archive METAR mondiale de l'Iowa Environmental Mesonet (Iowa State
 * University), gratuite et sans clé. Seul l'identifiant OACI de la station
 * est envoyé, jamais la position de la ville.
 *
 * Réponse CSV `station,valid,tmpc` en UTC ; `report_type=3` ne garde que les
 * relevés de routine, comme le banc d'essai (`scripts/backtest/sources.py`).
 */
interface MetarArchiveApi {
    @GET("cgi-bin/request/asos.py")
    suspend fun hourly(
        @Query("station") station: String,
        @Query("year1") startYear: Int,
        @Query("month1") startMonth: Int,
        @Query("day1") startDay: Int,
        /** Borne de fin exclue par IEM. */
        @Query("year2") endYear: Int,
        @Query("month2") endMonth: Int,
        @Query("day2") endDay: Int,
        @Query("data") data: String = "tmpc",
        @Query("tz") timezone: String = "Etc/UTC",
        @Query("format") format: String = "onlycomma",
        @Query("latlon") latLon: String = "no",
        @Query("missing") missing: String = "M",
        @Query("trace") trace: String = "T",
        @Query("report_type") reportType: Int = 3
    ): ResponseBody
}
