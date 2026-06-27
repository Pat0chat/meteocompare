package com.meteocompare.app.domain.repository

import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import kotlinx.coroutines.flow.Flow

/**
 * Préférences utilisateur persistantes — modèles sélectionnés, thème, langue.
 */
interface UserPreferencesRepository {

    fun observeEnabledModels(): Flow<List<WeatherModel>>
    suspend fun setEnabledModels(models: List<WeatherModel>)

    fun observeThemePreference(): Flow<ThemePreference>
    suspend fun setThemePreference(preference: ThemePreference)

    fun observeLanguagePreference(): Flow<LanguagePreference>
    suspend fun setLanguagePreference(preference: LanguagePreference)
}
