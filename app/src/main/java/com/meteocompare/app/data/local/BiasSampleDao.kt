package com.meteocompare.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO unique pour le suivi de biais — regroupe les accès aux tables
 * `forecast_samples` et `observation_samples`.
 *
 * Un seul DAO plutôt que deux (Forecast + Observation) parce que la
 * requête la plus utilisée ([observeJoinedSamples]) fait un JOIN entre les
 * deux : elle doit naturellement vivre à un endroit, et la SRP est plus
 * respectée par "tout le suivi de biais est ici" que par une répartition
 * arbitraire par table.
 */
@Dao
interface BiasSampleDao {

    // ─── Écritures ────────────────────────────────────────────────────────

    /**
     * Idempotent sur la clé composite. REPLACE plutôt que IGNORE parce que si
     * une même clé de journée d'émission est réinsérée, le refresh le plus
     * récent de cette journée doit remplacer la valeur précédente.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForecast(sample: ForecastSampleEntity)

    /** Une transaction Room pour le snapshot J+1 de tous les modèles. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForecasts(samples: List<ForecastSampleEntity>)

    /**
     * REPLACE pour accepter une éventuelle révision rétroactive de la
     * référence historique Open-Meteo.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservation(sample: ObservationSampleEntity)

    /** Une transaction Room pour tout un delta de références historiques. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservations(samples: List<ObservationSampleEntity>)

    // ─── Lectures ─────────────────────────────────────────────────────────

    /**
     * Requête cœur du feature : joint prévision et référence historique par (cityId,
     * variable, targetDate) et filtre sur la fenêtre glissante fournie.
     *
     * INNER JOIN — on n'émet que les jours pour lesquels ON A LES DEUX :
     * une prévision sans référence (jour futur ou archive pas encore
     * récupérée) n'entre pas dans le calcul de biais, et une prévision qu'on
     * n'a jamais snapshotté ne peut pas devenir un jour de biais.
     *
     * L'ORDER BY `issuedAtEpochMs DESC` reste défensif pour les anciennes
     * bases qui peuvent contenir plusieurs captures du même `targetDate`.
     * Le repository sélectionne ensuite strictement la capture émise la veille.
     *
     * Bornes de la fenêtre : `[startEpochDay, endEpochDay)`. Semi-ouverte
     * pour matcher la sémantique du use case (asOf exclu).
     *
     * Retourne un [Flow] : Room re-émet automatiquement à chaque insertion
     * dans l'une ou l'autre table qui matcherait ce JOIN.
     */
    @Query(
        """
        SELECT
          f.targetDateEpochDay AS targetDateEpochDay,
          f.value              AS forecast,
          o.value              AS observation,
          f.issuedAtEpochMs    AS issuedAtEpochMs
        FROM forecast_samples f
        INNER JOIN observation_samples o
          ON f.cityId            = o.cityId
          AND f.variable          = o.variable
          AND f.targetDateEpochDay = o.targetDateEpochDay
        WHERE f.cityId              = :cityId
          AND f.modelKey            = :modelKey
          AND f.variable            = :variable
          AND f.targetDateEpochDay >= :startEpochDay
          AND f.targetDateEpochDay <  :endEpochDay
        ORDER BY f.targetDateEpochDay ASC, f.issuedAtEpochMs DESC
        """
    )
    fun observeJoinedSamples(
        cityId: String,
        modelKey: String,
        variable: String,
        startEpochDay: Long,
        endEpochDay: Long
    ): Flow<List<BiasSampleRow>>

    /**
     * Première date cible possédant une prévision mais aucune référence
     * historique correspondante, toutes variables confondues.
     *
     * Le LEFT JOIN détecte aussi les trous internes : une référence manquante
     * au milieu d'une série n'est pas masquée par une date plus récente déjà
     * présente. `upToEpochDay` exclut les prévisions encore futures.
     */
    @Query(
        """
        SELECT MIN(f.targetDateEpochDay)
        FROM forecast_samples f
        LEFT JOIN observation_samples o
          ON f.cityId = o.cityId
          AND f.variable = o.variable
          AND f.targetDateEpochDay = o.targetDateEpochDay
        WHERE f.cityId = :cityId
          AND f.targetDateEpochDay <= :upToEpochDay
          AND o.targetDateEpochDay IS NULL
        """
    )
    suspend fun getEarliestMissingReferenceEpochDay(
        cityId: String,
        upToEpochDay: Long
    ): Long?

    // ─── Housekeeping ─────────────────────────────────────────────────────

    /**
     * Purge des forecasts obsolètes. À appeler périodiquement (worker
     * quotidien) avec `beforeEpochDay = today - 35` pour maintenir la table
     * bornée à ~35 jours × nombre_villes × nombre_modèles × nombre_variables.
     */
    @Query("DELETE FROM forecast_samples WHERE targetDateEpochDay < :beforeEpochDay")
    suspend fun purgeForecastsBefore(beforeEpochDay: Long)

    @Query("DELETE FROM observation_samples WHERE targetDateEpochDay < :beforeEpochDay")
    suspend fun purgeObservationsBefore(beforeEpochDay: Long)
}

/**
 * POJO résultat du JOIN. Room mappe par nom de colonne — les alias `AS
 * forecast`, `AS observation` dans la requête matchent les noms de champs.
 *
 * Visibilité `public` (par défaut) parce que le code d'implémentation généré
 * par KSP-Room pour ce DAO est lui-même `public` et ne peut pas exposer un
 * type `internal` dans sa signature de retour (`fun ... : Flow<List<Row>>`).
 * En mono-module ça n'a aucun impact pratique sur l'encapsulation — les
 * autres modules ne dépendent pas de la couche `data.local`.
 */
data class BiasSampleRow(
    val targetDateEpochDay: Long,
    val forecast: Double,
    val observation: Double,
    val issuedAtEpochMs: Long
)
