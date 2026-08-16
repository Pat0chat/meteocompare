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
        SELECT * FROM forecast_evolution_samples
        WHERE cityId = :cityId
          AND modelKey IN (:modelKeys)
          AND targetDateEpochDay BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY targetDateEpochDay ASC, daysAgo DESC, modelKey ASC
        """
    )
    abstract suspend fun getForWindow(
        cityId: String,
        modelKeys: List<String>,
        startEpochDay: Long,
        endEpochDay: Long
    ): List<ForecastEvolutionEntity>

    @Query(
        """
        SELECT MAX(fetchedAtEpochMs) FROM forecast_evolution_samples
        WHERE cityId = :cityId
          AND modelKey IN (:modelKeys)
          AND targetDateEpochDay BETWEEN :startEpochDay AND :endEpochDay
        """
    )
    abstract suspend fun latestFetchForWindow(
        cityId: String,
        modelKeys: List<String>,
        startEpochDay: Long,
        endEpochDay: Long
    ): Long?

    @Query(
        """
        DELETE FROM forecast_evolution_samples
        WHERE cityId = :cityId
          AND modelKey IN (:modelKeys)
          AND targetDateEpochDay BETWEEN :startEpochDay AND :endEpochDay
        """
    )
    abstract suspend fun deleteWindow(
        cityId: String,
        modelKeys: List<String>,
        startEpochDay: Long,
        endEpochDay: Long
    )

    @Transaction
    open suspend fun replaceWindow(
        cityId: String,
        modelKeys: List<String>,
        startEpochDay: Long,
        endEpochDay: Long,
        samples: List<ForecastEvolutionEntity>
    ) {
        deleteWindow(cityId, modelKeys, startEpochDay, endEpochDay)
        if (samples.isNotEmpty()) insertAll(samples)
    }

    @Query("DELETE FROM forecast_evolution_samples WHERE fetchedAtEpochMs < :beforeEpochMs")
    abstract suspend fun purgeFetchedBefore(beforeEpochMs: Long)
}
