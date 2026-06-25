package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.GeocodingResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingApi {

    /**
     * Recherche de villes par nom.
     *
     * Doc : https://open-meteo.com/en/docs/geocoding-api
     *
     * @param name Nom partiel ou complet. Minimum 2 caractères.
     * @param count Nombre de résultats (1-100).
     * @param language Langue des noms (fr, en, de, etc.).
     */
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "fr",
        @Query("format") format: String = "json"
    ): GeocodingResponseDto
}
