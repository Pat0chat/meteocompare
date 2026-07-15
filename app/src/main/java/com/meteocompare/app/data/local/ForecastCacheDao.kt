package com.meteocompare.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ForecastCacheDao {

    /** Retourne les entrées en cache pour une ville (1 par modèle). */
    @Query("SELECT * FROM forecast_cache WHERE cityId = :cityId")
    suspend fun getForCity(cityId: String): List<ForecastCacheEntity>

    /** Insère ou remplace les entrées d'un refresh en une transaction Room. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ForecastCacheEntity>)

    /** Nettoyage quand une ville n'est plus en favoris. */
    @Query("DELETE FROM forecast_cache WHERE cityId = :cityId")
    suspend fun deleteForCity(cityId: String)

    /** Nettoyage périodique des entrées vraiment trop vieilles (> 7 jours). */
    @Query("DELETE FROM forecast_cache WHERE fetchedAtEpochMs < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
