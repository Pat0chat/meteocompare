package com.meteocompare.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// DataStore séparé du favoritesDataStore — préférences vs données.
private val Context.preferencesDataStore by preferencesDataStore(name = "user_prefs")

private val ENABLED_MODELS_KEY = stringSetPreferencesKey("enabled_models")

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : UserPreferencesRepository {

    override fun observeEnabledModels(): Flow<List<WeatherModel>> =
        context.preferencesDataStore.data.map { prefs ->
            val apiKeys = prefs[ENABLED_MODELS_KEY]
            if (apiKeys == null) {
                // Première utilisation → valeurs par défaut
                WeatherModel.MVP_SELECTION
            } else {
                // On filtre sur les WeatherModel qui existent encore
                // (utile si on a retiré un modèle entre deux versions de l'app)
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
}
