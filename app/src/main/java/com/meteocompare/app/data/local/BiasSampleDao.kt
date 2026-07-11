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
     * un même `(issuedAtEpochMs, ...)` est réinséré, c'est vraisemblablement
     * une nouvelle valeur (bug côté fetch, ou modèle qui a changé sa valeur
     * pour une même issuedAt — cas théorique) — on prend la plus fraîche.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForecast(sample: ForecastSampleEntity)

    /**
     * REPLACE pour supporter les révisions rétroactives ERA5 (l'archive
     * Open-Meteo peut mettre à jour une observation d'il y a quelques jours
     * quand la réanalyse est raffinée).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservation(sample: ObservationSampleEntity)

    // ─── Lectures ─────────────────────────────────────────────────────────

    /**
     * Requête cœur du feature : joint forecast et observation par (cityId,
     * variable, targetDate) et filtre sur la fenêtre glissante fournie.
     *
     * INNER JOIN — on n'émet que les jours pour lesquels ON A LES DEUX :
     * un forecast sans observation (jour futur ou observation pas encore
     * fetchée) n'entre pas dans le calcul de biais, et un forecast qu'on
     * n'a jamais snapshotté ne peut pas devenir un jour de biais.
     *
     * L'ORDER BY `issuedAtEpochMs DESC` en second critère garantit que si
     * plusieurs snapshots existent pour le même `targetDate`, le plus récent
     * arrive en tête — le `ComputeBiasUseCase.dedupByDate` gardera celui-là.
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
          o.value              AS observation
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
     * Latest observed date for delta fetch. `MAX` renvoie null si aucune
     * observation n'existe pour la clé (première utilisation).
     */
    @Query(
        """
        SELECT MAX(targetDateEpochDay)
        FROM observation_samples
        WHERE cityId = :cityId AND variable = :variable
        """
    )
    suspend fun getLatestObservationEpochDay(cityId: String, variable: String): Long?

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
    val observation: Double
)