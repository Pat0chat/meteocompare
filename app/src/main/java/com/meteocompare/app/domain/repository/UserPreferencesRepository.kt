package com.meteocompare.app.domain.repository

import com.meteocompare.app.domain.model.WeatherModel
import kotlinx.coroutines.flow.Flow

/**
 * Préférences utilisateur persistantes — sélection de modèles uniquement
 * pour le MVP. À étendre avec unités (°F), thème, etc.
 */
interface UserPreferencesRepository {

    /**
     * Modèles activés pour les comparaisons. Si le user n'a jamais customisé,
     * renvoie [WeatherModel.MVP_SELECTION].
     */
    fun observeEnabledModels(): Flow<List<WeatherModel>>

    suspend fun setEnabledModels(models: List<WeatherModel>)
}
