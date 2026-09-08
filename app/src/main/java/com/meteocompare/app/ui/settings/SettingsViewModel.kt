package com.meteocompare.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.R
import com.meteocompare.app.core.util.runSuspendCatching
import com.meteocompare.app.data.worker.BiasRefreshScheduler
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.ui.components.AppToastEvent
import com.meteocompare.app.widget.WidgetRefreshScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val prefs: UserPreferencesRepository
) : ViewModel() {

    private val modelUpdateMutex = Mutex()
    private val _feedback = Channel<AppToastEvent>(capacity = Channel.BUFFERED)
    val feedback = _feedback.receiveAsFlow()

    val enabledModels: StateFlow<Set<WeatherModel>> = prefs.observeEnabledModels()
        .map { it.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeatherModel.MVP_SELECTION.toSet()
        )

    val themePreference: StateFlow<ThemePreference> = prefs.observeThemePreference()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemePreference.SYSTEM
        )

    val languagePreference: StateFlow<LanguagePreference> = prefs.observeLanguagePreference()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LanguagePreference.SYSTEM
        )

    val refreshInterval: StateFlow<RefreshInterval> = prefs.observeRefreshInterval()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RefreshInterval.DEFAULT
        )

    val forecastEngine: StateFlow<ForecastEngine> = prefs.observeForecastEngine()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ForecastEngine.DEFAULT
        )

    fun onModelToggled(model: WeatherModel, enabled: Boolean) {
        viewModelScope.launch {
            // Les taps peuvent arriver plus vite que la réémission DataStore.
            // On sérialise donc les mutations et on relit la source de vérité
            // dans la section critique, sinon deux toggles rapprochés peuvent
            // se réécrire mutuellement à partir d'un StateFlow encore ancien.
            val feedback = runSuspendCatching {
                modelUpdateMutex.withLock {
                    val current = prefs.observeEnabledModels().first().toSet()
                    val next = if (enabled) current + model else current - model
                    if (next.isNotEmpty()) {
                        prefs.setEnabledModels(next.toList())
                        triggerWidgetRefreshSafely()
                        AppToastEvent.success(
                            if (enabled) R.string.toast_model_enabled
                            else R.string.toast_model_disabled,
                            model.displayName
                        )
                    } else {
                        AppToastEvent.warning(R.string.settings_models_min_warning)
                    }
                }
            }.getOrElse { AppToastEvent.error(R.string.toast_settings_save_error) }
            _feedback.send(feedback)
        }
    }

    /**
     * Demande un cycle exceptionnel de collecte des biais. Le scheduler
     * conserve les contraintes réseau/batterie, le mutex global et la
     * déduplication WorkManager ; ce bouton ne modifie pas la cadence
     * quotidienne normale.
     */
    fun onBiasRefreshRequested() {
        val feedback = runCatching {
            BiasRefreshScheduler.triggerManualRefresh(appContext)
        }.fold(
            onSuccess = { AppToastEvent.info(R.string.settings_bias_refresh_queued) },
            onFailure = { AppToastEvent.error(R.string.toast_action_error) }
        )
        _feedback.trySend(feedback)
    }

    fun onThemeSelected(preference: ThemePreference) {
        viewModelScope.launch {
            val feedback = runSuspendCatching {
                prefs.setThemePreference(preference)
            }.fold(
                onSuccess = { AppToastEvent.success(R.string.toast_theme_updated) },
                onFailure = { AppToastEvent.error(R.string.toast_settings_save_error) }
            )
            _feedback.send(feedback)
        }
    }

    /**
     * Persiste la langue dans l'unique stockage canonique. Cette fonction est
     * suspendue afin que l'écran puisse attendre la fin de l'écriture avant
     * `Activity.recreate()` et éviter toute course avec attachBaseContext().
     */
    suspend fun onLanguageSelected(preference: LanguagePreference): Boolean {
        val result = runSuspendCatching { prefs.setLanguagePreference(preference) }
        if (result.isFailure) {
            _feedback.send(AppToastEvent.error(R.string.toast_settings_save_error))
        }
        // Le succès est directement matérialisé par la recréation de l'activité.
        // Une notification lancée juste avant recreate() serait détruite avec elle.
        return result.isSuccess
    }

    /**
     * Persiste le nouvel intervalle de rafraîchissement et propage
     * immédiatement le changement au widget.
     *
     * La cadence de présentation du widget reste fixe à 15 minutes ; ce
     * réglage pilote uniquement `maxCacheAgeMs`. Le tick est déclenché après
     * la persistance afin qu'il relise immédiatement la nouvelle politique.
     */
    fun onRefreshIntervalSelected(interval: RefreshInterval) {
        viewModelScope.launch {
            val feedback = runSuspendCatching {
                prefs.setRefreshInterval(interval)
            }.fold(
                onSuccess = {
                    triggerWidgetRefreshSafely()
                    AppToastEvent.success(R.string.toast_refresh_interval_updated)
                },
                onFailure = { AppToastEvent.error(R.string.toast_settings_save_error) }
            )
            _feedback.send(feedback)
        }
    }

    /**
     * Change uniquement la stratégie de centrale : aucune requête réseau n'est
     * nécessaire. Le widget est toutefois rafraîchi immédiatement pour relire
     * la préférence et recalculer ses valeurs depuis le cache partagé.
     */
    fun onForecastEngineSelected(engine: ForecastEngine) {
        viewModelScope.launch {
            val feedback = runSuspendCatching {
                prefs.setForecastEngine(engine)
            }.fold(
                onSuccess = {
                    triggerWidgetRefreshSafely()
                    AppToastEvent.success(R.string.toast_forecast_engine_updated)
                },
                onFailure = { AppToastEvent.error(R.string.toast_settings_save_error) }
            )
            _feedback.send(feedback)
        }
    }

    /**
     * L'écriture DataStore est le résultat métier. La propagation immédiate au
     * widget est best-effort : une panne WorkManager ne doit pas faire croire
     * que le réglage n'a pas été enregistré. Le prochain tick le relira.
     */
    private fun triggerWidgetRefreshSafely() {
        runCatching {
            WidgetRefreshScheduler.triggerImmediateRefresh(appContext)
        }.onFailure { error ->
            android.util.Log.w(
                "MeteoCompare/Widget",
                "Unable to propagate settings to widgets immediately",
                error
            )
        }
    }
}
