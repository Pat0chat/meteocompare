package com.meteocompare.app.domain.repository

import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.WeatherModel
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Contrat de persistance des données de biais. Deux tables conceptuelles :
 *
 *   - **Forecasts historiques** : quel modèle a prévu quoi, pour quel jour,
 *     enregistré à quel moment. Clé fonctionnelle : (city, model, variable,
 *     targetDate, issuedAt).
 *   - **Références historiques** : valeur issue de la réanalyse Open-Meteo pour un
 *     jour donné, une variable donnée, dans une ville donnée. Clé :
 *     (city, variable, targetDate).
 *
 * ## API surface
 *
 * Une seule méthode "read" côté UI : [observeSamples]. Elle joint les deux
 * tables et émet la liste des couples (prévision, référence historique) dès qu'un des
 * deux côtés change. C'est ce que consomme [ComputeBiasUseCase].
 *
 * Les méthodes "write" sont appelées par le worker de suivi : Previous Runs
 * alimente les prévisions à échéances fixes J+1…J+7, puis l’archive historique
 * fournit les références de réanalyse correspondantes.
 *
 * ## Rétention
 *
 * L'implémentation est libre de purger les données au-delà de 35 jours
 * (marge de 5 jours vs la fenêtre de 30). Voir [purgeOlderThan] — appelable
 * depuis le worker quotidien pour maintenir la DB en taille contrôlée.
 *
 * ## Cache
 *
 * Aucune promesse de fraîcheur — le repo lit depuis Room, qui contient ce
 * qu'on a réussi à fetch. Un utilisateur hors ligne verra les samples
 * jusqu'au dernier fetch réussi, ce qui est le comportement attendu.
 */

/** Lot de prévisions de biais prêt à persister en une transaction. */
data class ForecastBiasRecord(
    val cityId: String,
    val model: WeatherModel,
    val variable: BiasVariable,
    val targetDate: LocalDate,
    val issuedAt: Instant,
    val value: Double,
    val leadDay: Int = 1
)

/** Lot de références historiques prêt à persister en une transaction. */
data class ObservationBiasRecord(
    val cityId: String,
    val variable: BiasVariable,
    val targetDate: LocalDate,
    val value: Double,
    val fetchedAt: Instant
)

interface BiasSampleRepository {

    /**
     * Flow des samples couplés (prévision, référence historique) pour une
     * (ville, modèle, variable) donnée, restreints à la fenêtre de
     * [windowDays] jours précédant [asOf]. Émet à chaque changement de l'une
     * ou l'autre table.
     *
     * En production, [timezone] permet de ne conserver que la dernière
     * prévision enregistrée exactement [leadDay] jours civils avant chaque
     * jour cible. Les horizons ne sont donc jamais mélangés.
     */
    fun observeSamples(
        cityId: String,
        model: WeatherModel,
        variable: BiasVariable,
        asOf: LocalDate,
        timezone: String? = null,
        windowDays: Int = 30,
        leadDay: Int = 1
    ): Flow<List<BiasSample>>

    /**
     * Persiste un forecast pour [targetDate]. En production, [issuedAt] sert
     * de marqueur de journée locale d'émission plutôt que d'horodatage exact :
     * les rechargements successifs de la même série Previous Runs remplacent
     * idempotemment la prévision de cette échéance et de cette journée.
     *
     * La clé (city, model, variable, targetDate, issuedAt) est idempotente ;
     * une nouvelle valeur portant la même clé remplace l'ancienne.
     */
    suspend fun recordForecast(
        cityId: String,
        model: WeatherModel,
        variable: BiasVariable,
        targetDate: LocalDate,
        issuedAt: Instant,
        value: Double,
        leadDay: Int = 1
    )

    /**
     * Variante batch. L'implémentation Room surcharge cette méthode pour faire
     * une seule transaction. Le corps par défaut conserve la compatibilité des
     * fakes/tests et des implémentations alternatives.
     */
    suspend fun recordForecasts(records: List<ForecastBiasRecord>) {
        records.forEach { record ->
            recordForecast(
                cityId = record.cityId,
                model = record.model,
                variable = record.variable,
                targetDate = record.targetDate,
                issuedAt = record.issuedAt,
                value = record.value,
                leadDay = record.leadDay
            )
        }
    }

    /**
     * Persiste une référence historique pour [targetDate]. Idempotent sur
     * (city, variable, targetDate) — une réponse plus récente remplace la
     * précédente pour la même date.
     */
    suspend fun recordObservation(
        cityId: String,
        variable: BiasVariable,
        targetDate: LocalDate,
        value: Double
    )

    /** Variante batch des références historiques, avec fallback compatible. */
    suspend fun recordObservations(records: List<ObservationBiasRecord>) {
        records.forEach { record ->
            recordObservation(
                cityId = record.cityId,
                variable = record.variable,
                targetDate = record.targetDate,
                value = record.value
            )
        }
    }

    /**
     * Première date passée ou présente qui possède au moins une prévision J+1 archivée, mais pas encore sa
     * référence historique correspondante.
     *
     * Retourne `null` quand aucune donnée n'est vérifiable ou lorsque toutes
     * les références nécessaires jusqu'à [upToDate] sont déjà en base.
     */
    suspend fun earliestMissingReferenceDate(
        cityId: String,
        upToDate: LocalDate
    ): LocalDate?

    /**
     * Housekeeping : supprime tous les samples (forecasts et observations)
     * antérieurs à [beforeDate]. Appelé une fois par jour par le worker de
     * refresh pour maintenir la DB dans un budget de 35 jours de données.
     */
    suspend fun purgeOlderThan(beforeDate: LocalDate)
}
