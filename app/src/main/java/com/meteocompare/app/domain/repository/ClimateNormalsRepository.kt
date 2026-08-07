package com.meteocompare.app.domain.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.DayNormals

/**
 * Source des repères météorologiques calendaires pour une ville.
 *
 * Le nom historique `ClimateNormalsRepository` est conservé pour éviter un
 * renommage transversal sans valeur fonctionnelle, mais les valeurs produites
 * ne sont pas des « normales climatiques » WMO sur 30 ans : le repository
 * agrège 10 années complètes de réanalyse ERA5, jour du calendrier par jour.
 *
 * Contrat :
 *   - Renvoie les [DayNormals] disponibles par couple mois/jour (jusqu'à 366).
 *   - Cache long terme (180 jours dans l'implémentation actuelle).
 *   - Premier calcul : un fetch ERA5 d'environ 10 ans puis agrégation locale.
 *   - Réponses suivantes : cache tant qu'il reste frais, ou fallback cache hors ligne.
 */
interface ClimateNormalsRepository {
    suspend fun getNormalsForCity(city: City): ApiResult<List<DayNormals>>
}
