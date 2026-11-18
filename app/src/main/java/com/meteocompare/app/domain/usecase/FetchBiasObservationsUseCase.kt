package com.meteocompare.app.domain.usecase

import android.util.Log
import com.meteocompare.app.core.util.apiTimezoneOrAuto
import com.meteocompare.app.core.util.localDateIn
import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.core.util.runSuspendCatching
import com.meteocompare.app.core.util.validZoneOrNull
import com.meteocompare.app.data.remote.ClimateArchiveApi
import com.meteocompare.app.data.remote.MetarArchiveApi
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.CalibrationPoint
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ForecastPhysicalLimits
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.ObservationBiasRecord
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Récupère les données historiques de référence manquantes pour une ville et les
 * enregistre dans le repo de biais.
 *
 * ## Références
 *
 * Avec une station de calibration ([CalibrationPoint.station]), la Tmax vient
 * de ses METAR : face aux stations, la réanalyse est trop froide d'environ
 * 0,5 °C en Tmax, et la corriger vers elle dégradait la Tmax à J+1 (voir
 * `scripts/backtest/results`). Un jour sans METAR suffisants reste sans Tmax :
 * il n'est jamais complété par la réanalyse, pour ne pas mélanger deux
 * références dans la même fenêtre.
 *
 * Le cumul de pluie et le vent max restent sur la réanalyse Open-Meteo, prise
 * au même point que les prévisions archivées : les METAR hors États-Unis ne
 * donnent pas la pluie, et le vent d'un anémomètre d'aéroport, plus exposé,
 * gonflerait le vent affiché en ville.
 *
 * ## Stratégie delta
 *
 * L'endpoint archive Open-Meteo transfère une série quotidienne complète.
 * On minimise le trafic :
 *   1. Lecture de [BiasSampleRepository.earliestMissingReferenceDate]. La
 *      requête Room repère le premier forecast passé sans référence, y compris
 *      un trou interne au milieu d'une série.
 *   2. Tant qu’aucune prévision Previous Runs J+1 n’est vérifiable, aucun
 *      appel archive n’est effectué.
 *   3. La fenêtre est bornée à 30 jours maximum et se termine hier.
 *   4. Un seul appel HTTP couvre les 3 variables (l'API archive prend
 *      `daily=temperature_2m_max,precipitation_sum,wind_speed_10m_max` en une
 *      passe).
 *
 * ## Bootstrap
 *
 * Première utilisation sans bootstrap : aucune prévision passée n'existe
 * encore, donc le use case retourne immédiatement sans réseau. Le bouton des
 * réglages peut d'abord insérer des prévisions J+1 archivées via
 * [BootstrapBiasHistoryUseCase], puis ce use case récupère leurs références.
 * Le bootstrap manuel et l'entretien quotidien utilisent tous deux Previous Runs.
 *
 * ## Robustesse
 *
 * La référence historique peut ne pas exposer immédiatement les jours les plus
 * récents. On enregistre
 * uniquement les jours effectivement retournés par l'API — le tableau `time`
 * de la réponse fait foi, pas le tableau `[start, end]` qu'on a demandé.
 *
 * Valeurs `null` dans les listes (jour sans référence exploitable) skippées.
 *
 * ## Erreurs
 *
 * Les erreurs réseau/HTTP sont propagées en exception à l'appelant (le
 * [com.meteocompare.app.data.worker.BiasRefreshWorker] les traite comme un
 * `Result.retry()`). Ce use case ne s'en occupe pas : sa responsabilité est
 * "fetch + persist", pas la politique de retry.
 */
@Singleton
class FetchBiasObservationsUseCase @Inject constructor(
    private val archiveApi: ClimateArchiveApi,
    private val metarApi: MetarArchiveApi,
    private val biasRepository: BiasSampleRepository,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    private val clock: Clock = Clock.systemUTC()
) {

    /**
     * @param city ville pour laquelle fetcher.
     * @param today utile pour tests : la date de référence "aujourd'hui".
     * @return nombre de jours effectivement enregistrés (0 si rien à fetcher).
     */
    suspend operator fun invoke(
        city: City,
        today: LocalDate = clock.instant().localDateIn(city.timezone),
        point: CalibrationPoint = CalibrationPoint.reanalysis(city)
    ): Int = withContext(io) {
        val end = today.minusDays(1)
        val earliestMissing = biasRepository.earliestMissingReferenceDate(
            cityId = city.id,
            upToDate = end
        ) ?: return@withContext 0

        // Jamais plus de 30 jours en une passe, même après une longue absence.
        val cappedStart = maxOf(
            earliestMissing,
            end.minusDays((MAX_BOOTSTRAP_DAYS - 1).toLong())
        )

        if (cappedStart > end) return@withContext 0

        val response = archiveApi.archive(
            latitude = point.latitude,
            longitude = point.longitude,
            startDate = cappedStart.format(ISO_DATE),
            endDate = end.format(ISO_DATE),
            daily = ClimateArchiveApi.BIAS_DAILY_VARS,
            timezone = apiTimezoneOrAuto(city.timezone)
        )

        val timeStrs = response.daily.time
        val tempMax = response.daily.tempMax
        val precipSum = response.daily.precipSum
        val windMax = response.daily.windSpeedMax
        val stationTmax = point.station?.let { station ->
            val zone = validZoneOrNull(city.timezone) ?: resolveZoneOrUtc(response.timezone)
            stationDailyTmax(station.icao, cappedStart, end, zone)
        }

        // Chaque série est lue indépendamment. Toutes les lignes sont ensuite
        // persistées en une transaction Room pour éviter 30 × 3 transactions.
        val fetchedAt = clock.instant()
        val records = ArrayList<ObservationBiasRecord>(timeStrs.size * 3)
        var recordedDays = 0
        for (i in timeStrs.indices) {
            val date = runCatching { LocalDate.parse(timeStrs[i], ISO_DATE) }.getOrNull()
                ?: continue
            // Défense contre une réponse serveur contenant un jour hors de la
            // fenêtre demandée : ne jamais polluer la série locale.
            if (date < cappedStart || date > end) continue
            var recordedForDate = false

            val temperature = if (stationTmax != null) stationTmax[date] else tempMax.getOrNull(i)
            ForecastPhysicalLimits.temperature(temperature)?.let { value ->
                records += ObservationBiasRecord(
                    city.id, BiasVariable.TEMPERATURE, date, value, fetchedAt
                )
                recordedForDate = true
            }
            ForecastPhysicalLimits.precipitation(precipSum?.getOrNull(i))?.let { value ->
                records += ObservationBiasRecord(
                    city.id, BiasVariable.PRECIPITATION, date, value, fetchedAt
                )
                recordedForDate = true
            }
            ForecastPhysicalLimits.wind(windMax?.getOrNull(i))?.let { value ->
                records += ObservationBiasRecord(
                    city.id, BiasVariable.WIND_SPEED, date, value, fetchedAt
                )
                recordedForDate = true
            }
            if (recordedForDate) recordedDays++
        }
        biasRepository.recordObservations(records)
        recordedDays
    }

    /**
     * Tmax METAR par jour civil local. La fenêtre UTC est élargie d'un jour de
     * chaque côté pour couvrir toute la journée locale, quel que soit le fuseau.
     * Une erreur (limite de débit, panne) donne une carte vide : les jours
     * restent sans Tmax et seront redemandés au prochain cycle.
     */
    private suspend fun stationDailyTmax(
        icao: String,
        start: LocalDate,
        end: LocalDate,
        zone: java.time.ZoneId
    ): Map<LocalDate, Double> {
        val from = start.minusDays(1)
        val until = end.plusDays(2)
        return runSuspendCatching {
            val csv = metarApi.hourly(
                station = icao,
                startYear = from.year,
                startMonth = from.monthValue,
                startDay = from.dayOfMonth,
                endYear = until.year,
                endMonth = until.monthValue,
                endDay = until.dayOfMonth
            ).use { it.string() }
            MetarDailyMaxTemperature.parse(csv, zone)
        }.getOrElse { error ->
            Log.w(LOG_TAG, "METAR history unavailable for $icao", error)
            emptyMap()
        }
    }

    companion object {
        private const val LOG_TAG = "MeteoCompare/Bias"

        /**
         * Nombre max de jours à fetcher en une passe. Aligné sur la fenêtre du
         * biais (30) : au premier lancement, on remplit exactement ce qu'il
         * faut pour un calcul complet lorsque 14 prévisions J+1 ont été
         * disponibles, par bootstrap manuel ou entretien quotidien Previous Runs.
         */
        private const val MAX_BOOTSTRAP_DAYS = 30

        private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
