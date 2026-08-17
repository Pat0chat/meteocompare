package com.meteocompare.app.data.repository

import android.content.Context
import com.meteocompare.app.R
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.core.network.apiCall
import com.meteocompare.app.core.util.localDateIn
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
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stratégie identique à la version antérieure :
 *
 *   1. Cache local Room. Si fresh (< 180 jours), retourne directement.
 *   2. Sinon, fetch 10 années de réanalyse ERA5 Open-Meteo (~3650 lignes daily).
 *   3. Agrégation locale : pour chaque (month, day), moyenne sur les années
 *      où la donnée existe.
 *   4. Persiste dans Room et retourne.
 *
 * Les repères affichés sur la bande horaire sont uniquement thermiques :
 * Tmax/Tmin journaliers ERA5. Les anciennes moyennes de cumul pluie journalier
 * et de vent max journalier ne sont plus utilisées comme overlays horaires,
 * car elles n'ont pas la même fenêtre temporelle que les séries du graphe.
 * Les colonnes Room historiques restent nullables pour compatibilité de schéma.
 */
@Singleton
class ClimateNormalsRepositoryImpl @Inject constructor(
    private val api: ClimateArchiveApi,
    private val dao: ClimateNormalDao,
    private val networkMonitor: NetworkMonitor,
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    @param:DefaultDispatcher private val computation: CoroutineDispatcher = Dispatchers.Default
) : ClimateNormalsRepository {

    companion object {
        private const val YEARS_OF_HISTORY = 10
        private const val CACHE_FRESHNESS_MS = 180L * 24L * 60L * 60L * 1000L // 180 jours
        /** Série homogène pour une référence climatique : évite le Best Match multi-datasets. */
        internal const val NORMALS_REANALYSIS_MODEL = "era5"
        /**
         * Namespace logique du cache climatique. Il donne une provenance aux
         * lignes sans modifier le schéma Room : les anciens caches « Best Match »
         * restent sous `cityId`, les nouveaux sous `era5-v1:<cityId>`.
         */
        internal const val CLIMATE_CACHE_NAMESPACE = "era5-v1"

        internal fun cacheCityId(cityId: String): String =
            "$CLIMATE_CACHE_NAMESPACE:$cityId"

        /**
         * Agrégation : pour chaque (month, day) rencontré dans la série, moyenne
         * arithmétique de Tmax/Tmin sur toutes les années où la paire existe.
         * Les NULL et valeurs non finies sont ignorés par paire thermique.
         *
         * Les champs `precipMeanNormal` et `windMeanNormal` restent volontairement
         * null : des agrégats journaliers pluie/vent ne sont pas superposables à
         * la bande horaire sans changer de fenêtre temporelle et de sémantique.
         */
        internal fun aggregate(response: ArchiveResponseDto): List<DayNormals> {
            data class Acc(
                var sumMax: Double = 0.0,
                var sumMin: Double = 0.0,
                var nTemp: Int = 0
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
                // cette paire ne contribue pas au repère thermique.
                val tempMax = response.daily.tempMax.getOrNull(i)?.takeIf(Double::isFinite)
                val tempMin = response.daily.tempMin?.getOrNull(i)?.takeIf(Double::isFinite)
                if (tempMax != null && tempMin != null) {
                    acc.sumMax += tempMax
                    acc.sumMin += tempMin
                    acc.nTemp += 1
                }
            }

            return byMonthDay.entries
                // DayNormals exige une base thermique exploitable.
                .filter { (_, acc) -> acc.nTemp > 0 }
                .map { (key, acc) ->
                    DayNormals(
                        month = key / 100,
                        day = key % 100,
                        tempMaxNormal = acc.sumMax / acc.nTemp,
                        tempMinNormal = acc.sumMin / acc.nTemp,
                        precipMeanNormal = null,
                        windMeanNormal = null
                    )
                }
                .sortedWith(compareBy({ it.month }, { it.day }))
        }

        /**
         * Refuse les réponses archive manifestement partielles avant toute
         * écriture Room. ERA5 doit être une série quotidienne quasi continue :
         * on exige les dix années demandées et au moins 95 % des dates/paire
         * Tmax-Tmin attendues.
         */
        internal fun isArchivePayloadComplete(
            response: ArchiveResponseDto,
            startDate: LocalDate,
            endDate: LocalDate
        ): Boolean {
            if (endDate < startDate) return false
            val expectedDays = ChronoUnit.DAYS.between(startDate, endDate) + 1L
            if (expectedDays <= 0L) return false

            val validDates = response.daily.time.mapNotNull { raw ->
                runCatching { LocalDate.parse(raw) }.getOrNull()
                    ?.takeIf { it in startDate..endDate }
            }.distinct()
            val years = validDates.map(LocalDate::getYear).distinct()
            val minCoverage = ceil(expectedDays * MIN_ARCHIVE_COVERAGE_RATIO).toLong()
            if (years.size < YEARS_OF_HISTORY || validDates.size.toLong() < minCoverage) return false

            val tempMin = response.daily.tempMin ?: return false
            val pairedTemperatureDays = response.daily.time.indices.count { index ->
                val date = response.daily.time.getOrNull(index)?.let { raw ->
                    runCatching { LocalDate.parse(raw) }.getOrNull()
                } ?: return@count false
                if (date !in startDate..endDate) return@count false
                response.daily.tempMax.getOrNull(index)?.isFinite() == true &&
                    tempMin.getOrNull(index)?.isFinite() == true
            }
            return pairedTemperatureDays.toLong() >= minCoverage
        }

        private const val MIN_ARCHIVE_COVERAGE_RATIO = 0.95
    }

    override suspend fun getNormalsForCity(city: City): ApiResult<List<DayNormals>> =
        withContext(io) {
            // 1. Vérifie d'abord le cache ERA5 versionné. Les lignes des
            // versions précédentes utilisaient directement `city.id` et sont
            // volontairement exclues du calcul de fraîcheur : leur source
            // « Best Match » n'est pas homogène sur 10 ans.
            val cacheId = cacheCityId(city.id)
            val cached = dao.getForCity(cacheId)
            if (cached.isNotEmpty()) {
                val oldest = dao.getOldestComputedAt(cacheId) ?: 0L
                val ageMs = clock.millis() - oldest
                val isFresh = ageMs >= 0L && ageMs < CACHE_FRESHNESS_MS
                if (isFresh) {
                    return@withContext ApiResult.Success(cached.map { it.toDomain() })
                }
            }

            // Ancien cache sans provenance : il ne peut jamais être considéré
            // « frais » par cette version, mais reste un fallback de disponibilité
            // si le réseau est absent ou si la réanalyse ne répond pas.
            val legacyCached = if (cached.isEmpty()) dao.getForCity(city.id) else emptyList()
            val fallbackCache = cached.ifEmpty { legacyCached }

            // 2. Cache absent ou stale → fetch + agrégation
            if (!networkMonitor.isOnline()) {
                return@withContext if (fallbackCache.isNotEmpty()) {
                    ApiResult.Success(fallbackCache.map { it.toDomain() })
                } else {
                    ApiResult.Error(
                        IOException("No network"),
                        context.getString(R.string.error_no_network)
                    )
                }
            }

            val today = clock.instant().localDateIn(city.timezone)
            val endDate = today.withDayOfYear(1).minusDays(1) // 31 déc N-1
            val startDate = endDate.minusYears(YEARS_OF_HISTORY.toLong() - 1)
                .withDayOfYear(1)                              // 1 jan N-10

            val result = apiCall(context) {
                api.archive(
                    latitude = city.latitude,
                    longitude = city.longitude,
                    startDate = startDate.toString(),
                    endDate = endDate.toString(),
                    daily = ClimateArchiveApi.NORMALS_DAILY_VARS,
                    models = NORMALS_REANALYSIS_MODEL
                )
            }

            when (result) {
                is ApiResult.Error -> {
                    if (fallbackCache.isNotEmpty()) {
                        ApiResult.Success(fallbackCache.map { it.toDomain() })
                    } else {
                        result
                    }
                }
                is ApiResult.Success -> {
                    // Un HTTP 200 ne suffit pas : une réponse vide/partielle ne
                    // doit jamais remplacer un cache ERA5 10 ans déjà valide.
                    if (!isArchivePayloadComplete(result.data, startDate, endDate)) {
                        return@withContext if (fallbackCache.isNotEmpty()) {
                            ApiResult.Success(fallbackCache.map { it.toDomain() })
                        } else {
                            ApiResult.Error(
                                IllegalStateException("Incomplete ERA5 archive payload"),
                                context.getString(R.string.error_unknown)
                            )
                        }
                    }

                    val normals = withContext(computation) { aggregate(result.data) }
                    val now = clock.millis()
                    val entities = normals.map { it.toEntity(cacheId, now) }
                    dao.replaceForCity(cacheId, entities)
                    // Une fois la source ERA5 matérialisée, l'ancien cache sans
                    // provenance n'a plus de rôle de fallback et peut être purgé.
                    if (legacyCached.isNotEmpty()) dao.deleteForCity(city.id)
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
