package com.meteocompare.app.data.repository

import android.content.Context
import com.meteocompare.app.R
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.core.network.apiCall
import com.meteocompare.app.data.local.ClimateNormalDao
import com.meteocompare.app.data.local.ClimateNormalEntity
import com.meteocompare.app.data.remote.ClimateArchiveApi
import com.meteocompare.app.data.remote.dto.ArchiveResponseDto
import com.meteocompare.app.di.DefaultDispatcher
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.DayNormals
import com.meteocompare.app.domain.repository.ClimateNormalsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stratégie identique à la version antérieure :
 *
 *   1. Cache local Room. Si fresh (< 180 jours), retourne directement.
 *   2. Sinon, fetch 10 années d'archives Open-Meteo (~3650 lignes daily).
 *   3. Agrégation locale : pour chaque (month, day), moyenne sur les années
 *      où la donnée existe.
 *   4. Persiste dans Room et retourne.
 *
 * Nouveau (v3) : on agrège aussi la précipitation et le vent max moyen. Ces
 * séries sont OPTIONNELLES — un cache d'archive Open-Meteo qui n'a pas ces
 * variables (ou une future coupure d'API sur ces champs) laisse le champ
 * `precipMeanNormal`/`windMeanNormal` à null plutôt que de crash.
 *
 * Le nombre de requêtes réseau ne change pas : on ajoute juste 2 variables à
 * la query `daily=` existante, tout est retourné en un seul appel HTTP.
 */
@Singleton
class ClimateNormalsRepositoryImpl @Inject constructor(
    private val api: ClimateArchiveApi,
    private val dao: ClimateNormalDao,
    private val networkMonitor: NetworkMonitor,
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    @param:DefaultDispatcher private val computation: CoroutineDispatcher = Dispatchers.Default
) : ClimateNormalsRepository {

    companion object {
        private const val YEARS_OF_HISTORY = 10
        private const val CACHE_FRESHNESS_MS = 180L * 24L * 60L * 60L * 1000L // 180 jours

        /**
         * Agrégation : pour chaque (month, day) rencontré dans la série, moyenne
         * arithmétique des variables sur toutes les années où la donnée existe.
         * Les NULL sont ignorés champ par champ — un jour où seule la pluie
         * est manquante contribue quand même aux moyennes température/vent.
         *
         * Retour : les champs `precipMeanNormal` et `windMeanNormal` sont null
         * quand aucune donnée valide n'existe (ex. série sans la variable, cas
         * de coupure d'API), sinon la moyenne.
         */
        internal fun aggregate(response: ArchiveResponseDto): List<DayNormals> {
            data class Acc(
                var sumMax: Double = 0.0, var sumMin: Double = 0.0, var nTemp: Int = 0,
                var sumPrecip: Double = 0.0, var nPrecip: Int = 0,
                var sumWind: Double = 0.0, var nWind: Int = 0
            )
            val byMonthDay = HashMap<Int, Acc>()

            val n = response.daily.time.size
            for (i in 0 until n) {
                // Une ligne distante malformée ne doit pas faire perdre les
                // milliers d'autres jours valides du lot.
                val date = runCatching { LocalDate.parse(response.daily.time[i]) }
                    .getOrNull() ?: continue
                val key = DayNormals.key(date.monthValue, date.dayOfMonth)
                val acc = byMonthDay.getOrPut(key) { Acc() }

                // Température : max et min forment une paire. Une année sans
                // cette paire ne contribue pas à la normale thermique, mais ne
                // doit pas empêcher pluie/vent de contribuer indépendamment.
                val tempMax = response.daily.tempMax.getOrNull(i)
                val tempMin = response.daily.tempMin.getOrNull(i)
                if (tempMax != null && tempMin != null) {
                    acc.sumMax += tempMax
                    acc.sumMin += tempMin
                    acc.nTemp += 1
                }

                response.daily.precipSum?.getOrNull(i)?.let {
                    acc.sumPrecip += it
                    acc.nPrecip += 1
                }
                response.daily.windSpeedMax?.getOrNull(i)?.let {
                    acc.sumWind += it
                    acc.nWind += 1
                }
            }

            return byMonthDay.entries
                // DayNormals exige une base thermique exploitable. Les autres
                // variables restent toutefois agrégées avec toutes leurs
                // années valides, même quand la température manque ponctuellement.
                .filter { (_, acc) -> acc.nTemp > 0 }
                .map { (key, acc) ->
                    DayNormals(
                        month = key / 100,
                        day = key % 100,
                        tempMaxNormal = acc.sumMax / acc.nTemp,
                        tempMinNormal = acc.sumMin / acc.nTemp,
                        precipMeanNormal = if (acc.nPrecip > 0) acc.sumPrecip / acc.nPrecip else null,
                        windMeanNormal = if (acc.nWind > 0) acc.sumWind / acc.nWind else null
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
            if (!networkMonitor.isOnline()) {
                return@withContext if (cached.isNotEmpty()) {
                    ApiResult.Success(cached.map { it.toDomain() })
                } else {
                    ApiResult.Error(
                        IOException("No network"),
                        context.getString(R.string.error_no_network)
                    )
                }
            }

            val today = LocalDate.now()
            val endDate = today.withDayOfYear(1).minusDays(1) // 31 déc N-1
            val startDate = endDate.minusYears(YEARS_OF_HISTORY.toLong() - 1)
                .withDayOfYear(1)                              // 1 jan N-10

            val result = apiCall(context) {
                api.archive(
                    latitude = city.latitude,
                    longitude = city.longitude,
                    startDate = startDate.toString(),
                    endDate = endDate.toString()
                )
            }

            when (result) {
                is ApiResult.Error -> {
                    if (cached.isNotEmpty()) {
                        ApiResult.Success(cached.map { it.toDomain() })
                    } else {
                        result
                    }
                }
                is ApiResult.Success -> {
                    val normals = withContext(computation) { aggregate(result.data) }
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
        tempMinNormal = tempMinNormal,
        precipMeanNormal = precipMeanNormal,
        windMeanNormal = windMeanNormal
    )

    private fun DayNormals.toEntity(cityId: String, now: Long) = ClimateNormalEntity(
        cityId = cityId,
        month = month, day = day,
        tempMaxNormal = tempMaxNormal,
        tempMinNormal = tempMinNormal,
        precipMeanNormal = precipMeanNormal,
        windMeanNormal = windMeanNormal,
        computedAt = now
    )
}
