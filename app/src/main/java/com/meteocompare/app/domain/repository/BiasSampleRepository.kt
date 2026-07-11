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
 *   - **Observations historiques** : la valeur réellement mesurée pour un
 *     jour donné, une variable donnée, dans une ville donnée. Clé :
 *     (city, variable, targetDate).
 *
 * ## API surface
 *
 * Une seule méthode "read" côté UI : [observeSamples]. Elle joint les deux
 * tables et émet la liste des couples (forecast, observation) dès qu'un des
 * deux côtés change. C'est ce que consomme [ComputeBiasUseCase].
 *
 * Les méthodes "write" sont appelées par la couche fetch (repository
 * forecasts existant pour snapshotter ce qui est déjà en mémoire, et le
 * worker archive-fetch pour les observations rétrospectives).
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
interface BiasSampleRepository {

    /**
     * Flow des samples couplés (forecast, observation) pour une
     * (ville, modèle, variable) donnée, restreints à la fenêtre de
     * [windowDays] jours précédant [LocalDate.now]. Émet à chaque changement
     * de l'une ou l'autre table.
     *
     * Ordre : par `targetDate` croissante puis `issuedAt` DÉCROISSANTE — de
     * sorte qu'une éventuelle déduplication en aval (`ComputeBiasUseCase`)
     * conserve le forecast le plus récent pour chaque jour.
     */
    fun observeSamples(
        cityId: String,
        model: WeatherModel,
        variable: BiasVariable,
        windowDays: Int = 30
    ): Flow<List<BiasSample>>

    /**
     * Persiste un forecast qu'un modèle a émis à l'instant [issuedAt] pour
     * la date [targetDate].
     *
     * Idempotent sur la clé (city, model, variable, targetDate, issuedAt) —
     * un même snapshot ré-inséré est un no-op. C'est le fetch layer qui
     * appelle après avoir refreshi le forecast et validé qu'il faut le
     * conserver historiquement.
     */
    suspend fun recordForecast(
        cityId: String,
        model: WeatherModel,
        variable: BiasVariable,
        targetDate: LocalDate,
        issuedAt: Instant,
        value: Double
    )

    /**
     * Persiste une observation pour [targetDate]. Idempotent sur
     * (city, variable, targetDate) — écrase silencieusement (une nouvelle
     * mesure ERA5 remplace la précédente pour la même date).
     */
    suspend fun recordObservation(
        cityId: String,
        variable: BiasVariable,
        targetDate: LocalDate,
        value: Double
    )

    /**
     * Retourne la date la plus récente pour laquelle on a déjà une observation
     * de [variable] dans la ville [cityId]. Utilisé par le worker fetch pour
     * calculer le delta à récupérer depuis l'archive (`[latest + 1, yesterday]`).
     *
     * `null` si aucune observation n'est encore stockée (première utilisation).
     */
    suspend fun latestObservationDate(
        cityId: String,
        variable: BiasVariable
    ): LocalDate?

    /**
     * Housekeeping : supprime tous les samples (forecasts et observations)
     * antérieurs à [beforeDate]. Appelé une fois par jour par le worker de
     * refresh pour maintenir la DB dans un budget de ~35 jours de données.
     */
    suspend fun purgeOlderThan(beforeDate: LocalDate)
}
