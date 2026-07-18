package com.meteocompare.app.domain.usecase

import com.meteocompare.app.data.remote.HistoricalForecastApi
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.ForecastBiasRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backfill historical-forecast — comble le "trou du démarrage" du suivi de biais.
 *
 * ## Problème résolu
 *
 * `snapshotForecast` (piggybacké sur les fetches utilisateur) ne peut capturer
 * QUE des prévisions pour aujourd'hui + le futur (7 jours). Le calcul de biais
 * a besoin du croisement `forecast_samples ∩ observation_samples` sur des dates
 * PASSÉES. Sans backfill, il faut minimum 14 jours pour que le premier chip
 * apparaisse (temps d'accumuler le recouvrement).
 *
 * Ce use case fait un unique appel HTTP à
 * `historical-forecast-api.open-meteo.com` avec `start=today-30, end=today-1`
 * et enregistre TOUS les modèles fournis avec leur valeur "au moment de la
 * prévision d'origine". Croisé avec les observations que
 * [FetchBiasObservationsUseCase] récupère en parallèle, on démarre avec 30
 * jours de couverture → premier chip dès le lendemain de l'installation.
 *
 * ## Idempotence
 *
 * Vérifie [BiasSampleRepository.countPastForecastSamples] pour chaque modèle.
 * Les modèles déjà suffisamment couverts sont exclus de l'appel ; un modèle
 * activé récemment reste backfillé même si la ville possède déjà l'historique
 * des autres familles. Coût du no-op : une petite requête SQL par modèle.
 *
 * ## Sémantique du `issuedAt`
 *
 * Pour les rows backfillées on utilise `Instant.now()` — "moment où on a
 * appris cette valeur historique". Aucun conflit possible avec les rows
 * snapshottées organiquement puisque celles-ci sont pour du futur, jamais du
 * passé. La PK composite reste unique.
 *
 * ## Robustesse
 *
 * - Si la liste de modèles est vide : no-op silencieux.
 * - Si l'API renvoie des `null` pour un jour donné : ce jour × cette variable
 *   n'est pas enregistrée, mais les autres jours × variables du même modèle
 *   passent.
 * - Si un modèle n'a pas de données (variables suffixées absentes) : skippé,
 *   le reste continue. Utile pour AROME HD qui n'a que 3-4 jours d'historique
 *   (petit modèle régional) : la clé peut simplement ne pas apparaître.
 * - Erreurs HTTP → exception propagée. Le worker s'en occupe en logguant et
 *   en passant à la ville suivante.
 */
@Singleton
class BackfillHistoricalForecastUseCase @Inject constructor(
    private val historicalApi: HistoricalForecastApi,
    private val biasRepository: BiasSampleRepository,
    @param:IoDispatcher private val io: CoroutineDispatcher
) {

    /**
     * @param city ville pour laquelle backfiller.
     * @param models modèles à backfiller (typiquement la liste des modèles
     *   activés en préférences). Si vide → no-op.
     * @param today référence "aujourd'hui" pour la fenêtre. Le fetch couvre
     *   `[today - WINDOW_DAYS, today - 1]`.
     * @return nombre total de rows insérées (0 si backfill skippé ou aucune
     *   valeur exploitable retournée).
     */
    suspend operator fun invoke(
        city: City,
        models: List<WeatherModel>,
        today: LocalDate = LocalDate.now()
    ): Int = withContext(io) {
        if (models.isEmpty()) return@withContext 0

        // Guard idempotence PAR MODÈLE. Un modèle activé récemment doit être
        // backfillé même si les autres modèles de la ville ont déjà assez de
        // samples passés.
        val modelsToBackfill = models.filter { model ->
            biasRepository.countPastForecastSamples(city.id, model, today) < SKIP_THRESHOLD
        }
        if (modelsToBackfill.isEmpty()) return@withContext 0

        val end = today.minusDays(1)
        val start = end.minusDays((WINDOW_DAYS - 1).toLong())

        val response = historicalApi.getHistoricalForecast(
            latitude = city.latitude,
            longitude = city.longitude,
            models = modelsToBackfill.joinToString(",") { it.apiKey },
            startDate = start.format(ISO_DATE),
            endDate = end.format(ISO_DATE)
        )

        val daily = response.daily ?: return@withContext 0

        // Extract la liste des dates du champ `time`. Filtre les parse errors
        // silencieusement (si Open-Meteo renvoie un jour au format bizarre,
        // on le skip plutôt que de tout planter).
        val timeArray = (daily["time"] as? JsonArray) ?: return@withContext 0
        val dates: List<LocalDate?> = timeArray.map { elem ->
            val s = (elem as? JsonPrimitive)?.contentOrNullSafe() ?: return@map null
            runCatching { LocalDate.parse(s, ISO_DATE) }.getOrNull()
        }

        val issuedAt = Instant.now()
        val records = ArrayList<ForecastBiasRecord>(modelsToBackfill.size * dates.size * 3)

        for (model in modelsToBackfill) {
            val tempValues = daily.doubleArrayOrNull("temperature_2m_max_${model.apiKey}")
            val precipValues = daily.doubleArrayOrNull("precipitation_sum_${model.apiKey}")
            val windValues = daily.doubleArrayOrNull("wind_speed_10m_max_${model.apiKey}")

            for (i in dates.indices) {
                val date = dates[i] ?: continue
                tempValues?.getOrNull(i)?.let { value ->
                    records += ForecastBiasRecord(
                        city.id, model, BiasVariable.TEMPERATURE, date, issuedAt, value
                    )
                }
                precipValues?.getOrNull(i)?.let { value ->
                    records += ForecastBiasRecord(
                        city.id, model, BiasVariable.PRECIPITATION, date, issuedAt, value
                    )
                }
                windValues?.getOrNull(i)?.let { value ->
                    records += ForecastBiasRecord(
                        city.id, model, BiasVariable.WIND_SPEED, date, issuedAt, value
                    )
                }
            }
        }
        biasRepository.recordForecasts(records)
        records.size
    }

    /**
     * Extrait `daily["<key>"]` comme List<Double?>. Retourne `null` si la clé
     * n'existe pas OU si le champ n'est pas un array. Preserve les `null`
     * inline pour maintenir l'alignement d'index avec `time`.
     */
    private fun kotlinx.serialization.json.JsonObject.doubleArrayOrNull(
        key: String
    ): List<Double?>? {
        val arr = (this[key] as? JsonArray) ?: return null
        return arr.map { elem ->
            when {
                elem is JsonNull -> null
                elem is JsonPrimitive -> elem.doubleOrNull
                else -> null
            }
        }
    }

    /** Contenu d'un JsonPrimitive comme String, ou null si le primitive est null. */
    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (this is JsonNull) null else content

    companion object {
        /**
         * Nombre de jours de backfill. Aligné sur la fenêtre du biais (30j) —
         * un seul appel suffit à remplir toute la fenêtre du premier coup.
         */
        private const val WINDOW_DAYS = 30

        /**
         * Seuil de skip. Si la ville a déjà autant de forecast passées, on
         * considère le backfill inutile. Bas volontairement : 5 rows =
         * "quelques jours ont déjà été snapshottés organiquement", pas la
         * peine de forcer un fetch supplémentaire.
         */
        private const val SKIP_THRESHOLD = 5

        private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
