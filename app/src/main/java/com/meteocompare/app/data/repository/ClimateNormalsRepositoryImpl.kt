package com.meteocompare.app.data.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.apiCall
import com.meteocompare.app.data.local.ClimateNormalDao
import com.meteocompare.app.data.local.ClimateNormalEntity
import com.meteocompare.app.data.remote.ClimateArchiveApi
import com.meteocompare.app.data.remote.dto.ArchiveResponseDto
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.DayNormals
import com.meteocompare.app.domain.repository.ClimateNormalsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stratégie :
 *
 *   1. Cache local Room. Si fresh (< 180 jours), retourne directement.
 *   2. Sinon, fetch 10 années d'archives Open-Meteo (~3650 lignes daily).
 *   3. Agrégation locale : pour chaque (month, day), moyenne sur les années
 *      où la donnée existe.
 *   4. Persiste dans Room et retourne.
 *
 * Pourquoi 10 ans et pas 30 ?
 *   - 30 ans = 30 années de history à requêter. Open-Meteo répond en ~100ms
 *     pour les ranges multi-décennaux, mais ça gonfle le payload réseau et
 *     consomme du quota gratuit (10 000 req/jour partagés).
 *   - 10 ans donne une signal statistiquement solide (variance par jour
 *     d'environ ±2°C) sans payer le coût des 20 années supplémentaires.
 *   - Avec le changement climatique, les normales 30 ans sont aussi de plus
 *     en plus déphasées par rapport au climat actuel — 10 ans "glissants"
 *     représentent mieux la réalité que les utilisateurs perçoivent.
 *
 * À documenter dans l'UI : la valeur affichée est "Référence climatique
 * 10 ans" — terme moins ambigu que "normales" (qui réfèrent strictement
 * aux périodes 30 ans OMM).
 */
@Singleton
class ClimateNormalsRepositoryImpl @Inject constructor(
    private val api: ClimateArchiveApi,
    private val dao: ClimateNormalDao,
    @IoDispatcher private val io: CoroutineDispatcher
) : ClimateNormalsRepository {

    companion object {
        private const val YEARS_OF_HISTORY = 10
        private const val CACHE_FRESHNESS_MS = 180L * 24L * 60L * 60L * 1000L // 180 jours

        /**
         * Agrégation : pour chaque (month, day) rencontré dans la série, moyenne
         * arithmétique des max et des min sur toutes les années où la donnée
         * existe. Les NULL sont ignorés (les jours sans observation ne pénalisent
         * pas la moyenne).
         *
         * Exposée en `internal` dans le companion object pour testabilité —
         * c'est de la logique pure sans dépendances I/O, donc isolable.
         */
        internal fun aggregate(response: ArchiveResponseDto): List<DayNormals> {
            data class Acc(var sumMax: Double = 0.0, var sumMin: Double = 0.0, var n: Int = 0)
            val byMonthDay = HashMap<Int, Acc>()

            val n = response.daily.time.size
            for (i in 0 until n) {
                val tempMax = response.daily.tempMax.getOrNull(i)
                val tempMin = response.daily.tempMin.getOrNull(i)
                if (tempMax == null || tempMin == null) continue
                val date = LocalDate.parse(response.daily.time[i])
                val key = DayNormals.key(date.monthValue, date.dayOfMonth)
                val acc = byMonthDay.getOrPut(key) { Acc() }
                acc.sumMax += tempMax
                acc.sumMin += tempMin
                acc.n += 1
            }

            return byMonthDay.entries
                .map { (key, acc) ->
                    DayNormals(
                        month = key / 100,
                        day = key % 100,
                        tempMaxNormal = acc.sumMax / acc.n,
                        tempMinNormal = acc.sumMin / acc.n
                    )
                }
                .sortedWith(compareBy({ it.month }, { it.day }))
        }
    }

    override suspend fun getNormalsForCity(city: City): ApiResult<List<DayNormals>> =
        withContext(io) {
            // 1. Vérifie le cache
            val cached = dao.getForCity(city.id)
            if (cached.isNotEmpty()) {
                val oldest = dao.getOldestComputedAt(city.id) ?: 0L
                val isFresh = (System.currentTimeMillis() - oldest) < CACHE_FRESHNESS_MS
                if (isFresh) {
                    return@withContext ApiResult.Success(cached.map { it.toDomain() })
                }
            }

            // 2. Cache absent ou stale → fetch + agrégation
            val today = LocalDate.now()
            // On exclut l'année en cours (incomplète) en partant de l'année
            // précédente, ce qui donne YEARS_OF_HISTORY années PLEINES.
            val endDate = today.withDayOfYear(1).minusDays(1) // 31 déc N-1
            val startDate = endDate.minusYears(YEARS_OF_HISTORY.toLong() - 1)
                .withDayOfYear(1)                              // 1 jan N-10

            val result = apiCall {
                api.archive(
                    latitude = city.latitude,
                    longitude = city.longitude,
                    startDate = startDate.toString(),
                    endDate = endDate.toString()
                )
            }

            when (result) {
                is ApiResult.Error -> {
                    // Pas de réseau ET pas de cache → on remonte l'erreur.
                    // Si on a un cache stale on retourne quand même (mieux
                    // que rien — les normales bougent lentement).
                    if (cached.isNotEmpty()) {
                        ApiResult.Success(cached.map { it.toDomain() })
                    } else {
                        result
                    }
                }
                is ApiResult.Success -> {
                    val normals = aggregate(result.data)
                    val now = System.currentTimeMillis()
                    val entities = normals.map { it.toEntity(city.id, now) }
                    dao.replaceForCity(city.id, entities)
                    ApiResult.Success(normals)
                }
            }
        }

    private fun ClimateNormalEntity.toDomain() = DayNormals(
        month = month, day = day,
        tempMaxNormal = tempMaxNormal,
        tempMinNormal = tempMinNormal
    )

    private fun DayNormals.toEntity(cityId: String, now: Long) = ClimateNormalEntity(
        cityId = cityId,
        month = month, day = day,
        tempMaxNormal = tempMaxNormal,
        tempMinNormal = tempMinNormal,
        computedAt = now
    )
}
