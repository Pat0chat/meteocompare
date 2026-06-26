package com.meteocompare.app.domain.repository

import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import kotlinx.coroutines.flow.Flow

/**
 * Préférences utilisateur persistantes — modèles sélectionnés + thème.
 */
interface UserPreferencesRepository {

    /**
     * Modèles activés pour les comparaisons. Si le user n'a jamais customisé,
     * renvoie [WeatherModel.MVP_SELECTION].
     */
    fun observeEnabledModels(): Flow<List<WeatherModel>>

    suspend fun setEnabledModels(models: List<WeatherModel>)

    /**
     * Préférence de thème (SYSTEM/LIGHT/DARK). SYSTEM par défaut.
     * Émis tôt dans le splash pour éviter le flash clair → sombre.
     */
    fun observeThemePreference(): Flow<ThemePreference>

    suspend fun setThemePreference(preference: ThemePreference)
}
