package com.meteocompare.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class ForecastCacheDao {

    /** Retourne les entrées en cache pour une ville (1 par modèle). */
    @Query("SELECT * FROM forecast_cache WHERE cityId = :cityId")
    abstract suspend fun getForCity(cityId: String): List<ForecastCacheEntity>

    /** Insère ou remplace les entrées d'un refresh. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(entities: List<ForecastCacheEntity>)

    /** Retourne uniquement les modèles concernés, pour arbitrer les écritures concurrentes. */
    @Query("SELECT * FROM forecast_cache WHERE cityId = :cityId AND modelKey IN (:modelKeys)")
    abstract suspend fun getForCityModels(
        cityId: String,
        modelKeys: List<String>
    ): List<ForecastCacheEntity>

    /** Supprime uniquement les modèles concernés par le refresh courant. */
    @Query("DELETE FROM forecast_cache WHERE cityId = :cityId AND modelKey IN (:modelKeys)")
    abstract suspend fun deleteModels(cityId: String, modelKeys: List<String>)

    /**
     * Remplace atomiquement le sous-ensemble de modèles demandé.
     *
     * Important : si l'API ne renvoie plus un modèle auparavant présent, sa
     * vieille ligne doit disparaître. Sinon elle réapparaît au prochain read
     * Room et mélange des données de deux refreshs différents.
     */
    @Transaction
    open suspend fun replaceRequestedModels(
        cityId: String,
        requestedModelKeys: List<String>,
        entities: List<ForecastCacheEntity>,
        incomingFetchedAtEpochMs: Long
    ) {
        if (requestedModelKeys.isEmpty()) return

        // Deux jeux de modèles qui se chevauchent peuvent être téléchargés en
        // parallèle (par exemple un écran encore sur l'ancienne sélection et
        // Settings qui vient d'activer la nouvelle). Un fetch démarré plus tôt
        // peut terminer plus tard : il ne doit jamais écraser une ligne Room
        // portant déjà un timestamp plus récent.
        val protectedKeys = getForCityModels(cityId, requestedModelKeys)
            .asSequence()
            .filter { it.fetchedAtEpochMs > incomingFetchedAtEpochMs }
            .map(ForecastCacheEntity::modelKey)
            .toSet()
        val replaceableKeys = requestedModelKeys.filterNot(protectedKeys::contains)
        if (replaceableKeys.isEmpty()) return

        deleteModels(cityId, replaceableKeys)
        val replaceableEntities = entities.filter { it.modelKey in replaceableKeys }
        if (replaceableEntities.isNotEmpty()) upsertAll(replaceableEntities)
    }

    /** Nettoyage quand une ville n'est plus en favoris. */
    @Query("DELETE FROM forecast_cache WHERE cityId = :cityId")
    abstract suspend fun deleteForCity(cityId: String)

    /** Nettoyage périodique des entrées vraiment trop vieilles (> 7 jours). */
    @Query("DELETE FROM forecast_cache WHERE fetchedAtEpochMs < :cutoff")
    abstract suspend fun deleteOlderThan(cutoff: Long)
}
