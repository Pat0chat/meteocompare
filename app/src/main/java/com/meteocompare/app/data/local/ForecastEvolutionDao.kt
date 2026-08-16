package com.meteocompare.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class ForecastEvolutionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(samples: List<ForecastEvolutionEntity>)

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM forecast_evolution_samples
            WHERE cityId = :cityId AND snapshotBucket = :snapshotBucket
            LIMIT 1
        )
        """
    )
    abstract suspend fun snapshotBucketExists(cityId: String, snapshotBucket: Long): Boolean

    /**
     * Capture au plus un snapshot par ville et par tranche de 3 h.
     *
     * Garder le premier refresh frais du bucket évite de réécrire plusieurs
     * centaines de lignes toutes les 15/30 minutes. La transaction sérialise
     * aussi deux fetches concurrents : ils ne peuvent pas fusionner deux jeux
     * de modèles différents dans un même snapshot historique.
     */
    @Transaction
    open suspend fun insertSnapshotBucketIfAbsent(
        cityId: String,
        snapshotBucket: Long,
        samples: List<ForecastEvolutionEntity>
    ): Boolean {
        if (samples.isEmpty() || snapshotBucketExists(cityId, snapshotBucket)) return false
        insertAll(samples)
        return true
    }

    @Query(
        """
        SELECT * FROM forecast_evolution_samples
        WHERE cityId = :cityId
          AND modelKey IN (:modelKeys)
          AND targetDateEpochDay BETWEEN :startEpochDay AND :endEpochDay
          AND snapshotBucket BETWEEN :minSnapshotBucket AND :maxSnapshotBucket
        ORDER BY snapshotBucket ASC, targetDateEpochDay ASC, modelKey ASC
        """
    )
    abstract suspend fun getHistoryWindow(
        cityId: String,
        modelKeys: List<String>,
        startEpochDay: Long,
        endEpochDay: Long,
        minSnapshotBucket: Long,
        maxSnapshotBucket: Long
    ): List<ForecastEvolutionEntity>

    @Query(
        """
        SELECT MIN(snapshotAtEpochMs) FROM forecast_evolution_samples
        WHERE cityId = :cityId AND modelKey IN (:modelKeys)
        """
    )
    abstract suspend fun oldestSnapshotAt(
        cityId: String,
        modelKeys: List<String>
    ): Long?

    @Query("DELETE FROM forecast_evolution_samples WHERE snapshotAtEpochMs < :beforeEpochMs")
    abstract suspend fun purgeCapturedBefore(beforeEpochMs: Long)

    @Query("DELETE FROM forecast_evolution_samples WHERE cityId = :cityId")
    abstract suspend fun deleteForCity(cityId: String)
}
