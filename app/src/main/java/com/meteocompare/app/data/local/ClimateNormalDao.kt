package com.meteocompare.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ClimateNormalDao {

    @Query("SELECT * FROM climate_normals WHERE cityId = :cityId ORDER BY month, day")
    suspend fun getForCity(cityId: String): List<ClimateNormalEntity>

    @Query("SELECT MIN(computedAt) FROM climate_normals WHERE cityId = :cityId")
    suspend fun getOldestComputedAt(cityId: String): Long?

    /**
     * Remplace toutes les normales d'une ville en une transaction atomique.
     * Évite l'état "moitié cache ancien / moitié cache nouveau" en cas de
     * coupure réseau au milieu du replace.
     */
    @Transaction
    suspend fun replaceForCity(cityId: String, normals: List<ClimateNormalEntity>) {
        deleteForCity(cityId)
        insertAll(normals)
    }

    @Query("DELETE FROM climate_normals WHERE cityId = :cityId")
    suspend fun deleteForCity(cityId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ClimateNormalEntity>)
}
