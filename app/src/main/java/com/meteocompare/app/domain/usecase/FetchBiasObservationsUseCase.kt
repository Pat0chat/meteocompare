package com.meteocompare.app.domain.usecase

import com.meteocompare.app.core.util.localDateIn
import com.meteocompare.app.data.remote.ClimateArchiveApi
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.ObservationBiasRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Récupère les observations historiques manquantes pour une ville et les
 * enregistre dans le repo de biais.
 *
 * ## Stratégie delta
 *
 * L'endpoint archive Open-Meteo est cher (~10-50ko par ville pour 30 jours).
 * On minimise le trafic :
 *   1. Lecture de [BiasSampleRepository.latestObservationDate] pour chacune
 *      des trois variables. La date la plus ancienne est l'ancre du delta :
 *      une série partiellement absente lors d'un précédent fetch est ainsi
 *      retentée au lieu d'être définitivement masquée par la température.
 *   2. Calcul de la fenêtre `[start, end]` :
 *      - `end = today - 1` (hier — l'archive n'a pas encore la journée en
 *        cours).
 *      - `start = max(latest + 1, end - MAX_BOOTSTRAP + 1)` — on borne
 *        supérieurement pour ne pas fetcher 6 mois d'un coup si l'utilisateur
 *        n'a pas ouvert l'app depuis longtemps.
 *   3. Si `start > end` → rien à fetcher, retour immédiat.
 *   4. Sinon un seul appel HTTP couvre les 3 variables (l'API archive prend
 *      `daily=temperature_2m_max,precipitation_sum,wind_speed_10m_max` en une
 *      passe).
 *
 * ## Bootstrap
 *
 * Première utilisation (repo vide) → `latestObservationDate` renvoie null →
 * on fetche `MAX_BOOTSTRAP` jours (30 par défaut = la fenêtre du biais). Un
 * seul call HTTP, pas de risque d'exploser.
 *
 * ## Robustesse
 *
 * L'archive peut avoir un lag de 1-3 jours sur les données les plus récentes
 * (dépend du modèle de réanalyse : ERA5 non-final vs final). On enregistre
 * uniquement les jours effectivement retournés par l'API — le tableau `time`
 * de la réponse fait foi, pas le tableau `[start, end]` qu'on a demandé.
 *
 * Valeurs `null` dans les listes (jour sans mesure exploitable) skippées.
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
        today: LocalDate = clock.instant().localDateIn(city.timezone)
    ): Int = withContext(io) {
        val end = today.minusDays(1) // hier — l'archive n'a pas aujourd'hui
        val latestDates = BiasVariable.entries.map { variable ->
            biasRepository.latestObservationDate(city.id, variable)
        }
        // Si une variable n'a encore aucune observation, on reboucle sur la
        // fenêtre bootstrap. Sinon on repart après la plus ancienne des trois
        // dates, afin de combler les trous d'une série partielle.
        val latestCompleteDate = latestDates
            .takeIf { dates -> dates.all { it != null } }
            ?.filterNotNull()
            ?.minOrNull()

        val requestedStart = when {
            latestCompleteDate == null ->
                end.minusDays((MAX_BOOTSTRAP_DAYS - 1).toLong()) // bootstrap
            else -> latestCompleteDate.plusDays(1)
        }
        // Cap supérieur : jamais plus de MAX_BOOTSTRAP jours en une passe,
        // même si l'app n'a pas été ouverte depuis longtemps.
        val cappedStart = maxOf(requestedStart, end.minusDays((MAX_BOOTSTRAP_DAYS - 1).toLong()))

        if (cappedStart > end) return@withContext 0

        val response = archiveApi.archive(
            latitude = city.latitude,
            longitude = city.longitude,
            startDate = cappedStart.format(ISO_DATE),
            endDate = end.format(ISO_DATE)
        )

        val timeStrs = response.daily.time
        val tempMax = response.daily.tempMax
        val precipSum = response.daily.precipSum
        val windMax = response.daily.windSpeedMax

        // Chaque série est lue indépendamment. Toutes les lignes sont ensuite
        // persistées en une transaction Room pour éviter 30 × 3 transactions.
        val fetchedAt = clock.instant()
        val records = ArrayList<ObservationBiasRecord>(timeStrs.size * 3)
        var recordedDays = 0
        for (i in timeStrs.indices) {
            val date = runCatching { LocalDate.parse(timeStrs[i], ISO_DATE) }.getOrNull()
                ?: continue
            var recordedForDate = false

            tempMax.getOrNull(i)?.let { value ->
                records += ObservationBiasRecord(
                    city.id, BiasVariable.TEMPERATURE, date, value, fetchedAt
                )
                recordedForDate = true
            }
            precipSum?.getOrNull(i)?.let { value ->
                records += ObservationBiasRecord(
                    city.id, BiasVariable.PRECIPITATION, date, value, fetchedAt
                )
                recordedForDate = true
            }
            windMax?.getOrNull(i)?.let { value ->
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

    companion object {
        /**
         * Nombre max de jours à fetcher en une passe. Aligné sur la fenêtre du
         * biais (30) : au premier lancement, on remplit exactement ce qu'il
         * faut pour un calcul complet dès qu'un modèle a 14 jours de forecasts
         * historiques.
         */
        private const val MAX_BOOTSTRAP_DAYS = 30

        private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}