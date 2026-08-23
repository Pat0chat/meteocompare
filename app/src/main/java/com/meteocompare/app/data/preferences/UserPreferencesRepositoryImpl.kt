package com.meteocompare.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meteocompare.app.core.locale.LOCALE_LANGUAGE_TAG_KEY
import com.meteocompare.app.core.locale.localePreferences
import com.meteocompare.app.core.locale.persistLocalePreference
import com.meteocompare.app.core.locale.currentPersistedLanguagePreference
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.CityDetailSection
import com.meteocompare.app.domain.model.CityDetailContentTab
import com.meteocompare.app.domain.model.CityDetailViewMode
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.preferencesDataStore by preferencesDataStore(name = "user_prefs")

private val ENABLED_MODELS_KEY = stringSetPreferencesKey("enabled_models")
private val THEME_PREFERENCE_KEY = stringPreferencesKey("theme_preference")
private val REFRESH_INTERVAL_KEY = stringPreferencesKey("refresh_interval")
private val FORECAST_ENGINE_KEY = stringPreferencesKey("forecast_engine")
private val COLLAPSED_CITY_DETAIL_SECTIONS_KEY =
    stringSetPreferencesKey("collapsed_city_detail_sections")
private val CITY_DETAIL_VIEW_MODES_KEY =
    stringSetPreferencesKey("city_detail_view_modes")
private val CITY_DETAIL_CONTENT_TABS_KEY =
    stringSetPreferencesKey("city_detail_content_tabs")

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : UserPreferencesRepository {

    /** Une erreur I/O DataStore ne doit pas tuer définitivement les collecteurs UI. */
    private val safePreferences = context.preferencesDataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    // ─── distinctUntilChanged sur TOUS les flows ──────────────────────────
    // DataStore émet à CHAQUE écriture dans le fichier preferences, y compris
    // quand la valeur qu'on observe n'a pas bougé (une autre clé a été
    // modifiée). Sans distinctUntilChanged, les collecteurs downstream se
    // ré-exécutent inutilement : dans CityListViewModel, un simple toggle
    // dark/light déclencherait un cancel-then-relaunch de tous les streams
    // de forecast — parce que `observeEnabledModels` réémettrait la MÊME
    // liste, mais suffirait à faire triger le combine amont. Le fix est
    // trivial et évite un pic de CPU/network au moment d'un toggle sans
    // rapport avec les modèles.
    override fun observeEnabledModels(): Flow<List<WeatherModel>> =
        safePreferences.map { prefs ->
            val apiKeys = prefs[ENABLED_MODELS_KEY]
            if (apiKeys == null) {
                WeatherModel.MVP_SELECTION
            } else {
                WeatherModel.entries
                    .filter { model -> apiKeys.any(model::matchesApiKey) }
                    .ifEmpty { WeatherModel.MVP_SELECTION }
            }
        }.distinctUntilChanged()

    override suspend fun setEnabledModels(models: List<WeatherModel>) =
        withContext(ioDispatcher) {
            context.preferencesDataStore.edit { prefs ->
                prefs[ENABLED_MODELS_KEY] = models.map { it.apiKey }.toSet()
            }
            Unit
        }

    override fun observeThemePreference(): Flow<ThemePreference> =
        safePreferences.map { prefs ->
            ThemePreference.fromString(prefs[THEME_PREFERENCE_KEY])
        }.distinctUntilChanged()

    override suspend fun setThemePreference(preference: ThemePreference) =
        withContext(ioDispatcher) {
            context.preferencesDataStore.edit { prefs ->
                prefs[THEME_PREFERENCE_KEY] = preference.name
            }
            Unit
        }

    override fun observeLanguagePreference(): Flow<LanguagePreference> = callbackFlow {
        val preferences = localePreferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == LOCALE_LANGUAGE_TAG_KEY) {
                trySend(
                    LanguagePreference.fromLanguageTag(
                        prefs.getString(LOCALE_LANGUAGE_TAG_KEY, null)
                    )
                )
            }
        }

        preferences.registerOnSharedPreferenceChangeListener(listener)
        // Le cache a été préchargé dans Application.onCreate(), avant StrictMode.
        trySend(currentPersistedLanguagePreference(context))
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    override suspend fun setLanguagePreference(preference: LanguagePreference) =
        withContext(ioDispatcher) {
            persistLocalePreference(context, preference.bcp47Tag)
            Unit
        }

    override fun observeRefreshInterval(): Flow<RefreshInterval> =
        safePreferences.map { prefs ->
            RefreshInterval.fromString(prefs[REFRESH_INTERVAL_KEY])
        }.distinctUntilChanged()

    override suspend fun setRefreshInterval(interval: RefreshInterval) =
        withContext(ioDispatcher) {
            context.preferencesDataStore.edit { prefs ->
                prefs[REFRESH_INTERVAL_KEY] = interval.name
            }
            Unit
        }

    override fun observeForecastEngine(): Flow<ForecastEngine> =
        safePreferences.map { prefs ->
            ForecastEngine.fromString(prefs[FORECAST_ENGINE_KEY])
        }.distinctUntilChanged()

    override suspend fun setForecastEngine(engine: ForecastEngine) =
        withContext(ioDispatcher) {
            context.preferencesDataStore.edit { prefs ->
                prefs[FORECAST_ENGINE_KEY] = engine.name
            }
            Unit
        }

    override fun observeCollapsedCityDetailSections(
        cityId: String
    ): Flow<Set<CityDetailSection>> {
        val cityPrefix = collapsedSectionPrefix(cityId)

        return safePreferences.map { prefs ->
            prefs[COLLAPSED_CITY_DETAIL_SECTIONS_KEY]
                .orEmpty()
                .asSequence()
                .filter { encoded -> encoded.startsWith(cityPrefix) }
                .mapNotNull { encoded ->
                    val sectionName = encoded.removePrefix(cityPrefix)
                    runCatching { CityDetailSection.valueOf(sectionName) }.getOrNull()
                }
                .toSet()
        }.distinctUntilChanged()
    }

    override suspend fun setCityDetailSectionCollapsed(
        cityId: String,
        section: CityDetailSection,
        collapsed: Boolean
    ) = withContext(ioDispatcher) {
        val encodedSection = collapsedSectionPrefix(cityId) + section.name

        context.preferencesDataStore.edit { prefs ->
            val current = prefs[COLLAPSED_CITY_DETAIL_SECTIONS_KEY]
                .orEmpty()
                .toMutableSet()

            if (collapsed) {
                current += encodedSection
            } else {
                current -= encodedSection
            }

            prefs[COLLAPSED_CITY_DETAIL_SECTIONS_KEY] = current.toSet()
        }
        Unit
    }


    override fun observeCityDetailViewMode(cityId: String): Flow<CityDetailViewMode> =
        observeCityChoice(
            cityId = cityId,
            key = CITY_DETAIL_VIEW_MODES_KEY,
            default = CityDetailViewMode.DEFAULT,
            parser = CityDetailViewMode::fromString
        )

    override suspend fun setCityDetailViewMode(
        cityId: String,
        mode: CityDetailViewMode
    ) = setCityChoice(cityId, CITY_DETAIL_VIEW_MODES_KEY, mode.name)

    override fun observeCityDetailContentTab(cityId: String): Flow<CityDetailContentTab> =
        observeCityChoice(
            cityId = cityId,
            key = CITY_DETAIL_CONTENT_TABS_KEY,
            default = CityDetailContentTab.DEFAULT,
            parser = CityDetailContentTab::fromString
        )

    override suspend fun setCityDetailContentTab(
        cityId: String,
        tab: CityDetailContentTab
    ) = setCityChoice(cityId, CITY_DETAIL_CONTENT_TABS_KEY, tab.name)

    private fun <T> observeCityChoice(
        cityId: String,
        key: androidx.datastore.preferences.core.Preferences.Key<Set<String>>,
        default: T,
        parser: (String?) -> T
    ): Flow<T> {
        val prefix = cityPreferencePrefix(cityId)
        return safePreferences.map { prefs ->
            val encoded = prefs[key]
                .orEmpty()
                .firstOrNull { it.startsWith(prefix) }
            if (encoded == null) default else parser(encoded.removePrefix(prefix))
        }.distinctUntilChanged()
    }

    private suspend fun setCityChoice(
        cityId: String,
        key: androidx.datastore.preferences.core.Preferences.Key<Set<String>>,
        value: String
    ) = withContext(ioDispatcher) {
        val prefix = cityPreferencePrefix(cityId)
        context.preferencesDataStore.edit { prefs ->
            val updated = prefs[key]
                .orEmpty()
                .filterNot { it.startsWith(prefix) }
                .toMutableSet()
            updated += prefix + value
            prefs[key] = updated
        }
        Unit
    }

    /**
     * Préfixe sans ambiguïté : la longueur du cityId empêche qu'une ville
     * nommée "12" ne capture les préférences d'une ville "123".
     */
    private fun cityPreferencePrefix(cityId: String): String =
        "${cityId.length}:$cityId:"

    private fun collapsedSectionPrefix(cityId: String): String =
        cityPreferencePrefix(cityId)
}
