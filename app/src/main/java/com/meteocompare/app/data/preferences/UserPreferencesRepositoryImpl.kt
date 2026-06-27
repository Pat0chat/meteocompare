package com.meteocompare.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.preferencesDataStore by preferencesDataStore(name = "user_prefs")

private val ENABLED_MODELS_KEY = stringSetPreferencesKey("enabled_models")
private val THEME_PREFERENCE_KEY = stringPreferencesKey("theme_preference")
private val LANGUAGE_PREFERENCE_KEY = stringPreferencesKey("language_preference")

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : UserPreferencesRepository {

    override fun observeEnabledModels(): Flow<List<WeatherModel>> =
        context.preferencesDataStore.data.map { prefs ->
            val apiKeys = prefs[ENABLED_MODELS_KEY]
            if (apiKeys == null) {
                WeatherModel.MVP_SELECTION
            } else {
                WeatherModel.entries
                    .filter { it.apiKey in apiKeys }
                    .ifEmpty { WeatherModel.MVP_SELECTION }
            }
        }

    override suspend fun setEnabledModels(models: List<WeatherModel>) =
        withContext(ioDispatcher) {
            context.preferencesDataStore.edit { prefs ->
                prefs[ENABLED_MODELS_KEY] = models.map { it.apiKey }.toSet()
            }
            Unit
        }

    override fun observeThemePreference(): Flow<ThemePreference> =
        context.preferencesDataStore.data.map { prefs ->
            ThemePreference.fromString(prefs[THEME_PREFERENCE_KEY])
        }

    override suspend fun setThemePreference(preference: ThemePreference) =
        withContext(ioDispatcher) {
            context.preferencesDataStore.edit { prefs ->
                prefs[THEME_PREFERENCE_KEY] = preference.name
            }
            Unit
        }

    override fun observeLanguagePreference(): Flow<LanguagePreference> =
        context.preferencesDataStore.data.map { prefs ->
            LanguagePreference.fromString(prefs[LANGUAGE_PREFERENCE_KEY])
        }

    override suspend fun setLanguagePreference(preference: LanguagePreference) =
        withContext(ioDispatcher) {
            context.preferencesDataStore.edit { prefs ->
                prefs[LANGUAGE_PREFERENCE_KEY] = preference.name
            }
            Unit
        }
}
