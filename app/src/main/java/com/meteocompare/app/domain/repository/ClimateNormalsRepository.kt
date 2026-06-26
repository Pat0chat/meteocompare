package com.meteocompare.app.domain.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.DayNormals

/**
 * Source des normales climatiques pour une ville.
 *
 * Contrat :
 *   - Renvoie 366 [DayNormals] (un par jour-de-l'année y compris 29 février)
 *   - Cache à long terme : les normales évoluent extrêmement lentement
 *   - Première requête réseau lourde (~30 ans × 365 jours) → background acceptable
 *   - Réponses suivantes : cache uniquement
 */
interface ClimateNormalsRepository {
    suspend fun getNormalsForCity(city: City): ApiResult<List<DayNormals>>
}
