package com.meteocompare.app.data.local

import androidx.room.Entity

/**
 * Une entrée de cache = une requête Open-Meteo pour un (cityId, modelKey).
 *
 * On stocke le JSON brut plutôt que le ForecastSeries parsé pour deux raisons :
 *   - ForecastResponseDto sérialise nativement (que des types primitifs).
 *     ForecastSeries contiendrait Instant et LocalDate, nécessitant des
 *     custom serializers kotlinx.
 *   - On reste découplé du modèle métier — si on enrichit ForecastSeries
 *     avec de nouveaux champs, on n'invalide pas le cache existant.
 *
 * Le re-parsing au read est négligeable (~1 ms par modèle).
 */
@Entity(
    tableName = "forecast_cache",
    primaryKeys = ["cityId", "modelKey"]
)
data class ForecastCacheEntity(
    val cityId: String,
    val modelKey: String,
    val fetchedAtEpochMs: Long,
    val responseJson: String
)
