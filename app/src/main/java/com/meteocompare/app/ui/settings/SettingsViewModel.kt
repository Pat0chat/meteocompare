package com.meteocompare.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.widget.WidgetRefreshScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val prefs: UserPreferencesRepository
) : ViewModel() {

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

    fun onModelToggled(model: WeatherModel, enabled: Boolean) {
        viewModelScope.launch {
            val current = enabledModels.value
            val next = if (enabled) current + model else current - model
            if (next.isNotEmpty()) {
                prefs.setEnabledModels(next.toList())
            }
        }
    }

    fun onThemeSelected(preference: ThemePreference) {
        viewModelScope.launch {
            prefs.setThemePreference(preference)
        }
    }

    /**
     * Persiste la préférence de langue dans DataStore.
     *
     * ⚠ L'application effective de la locale (AppCompatDelegate.setApplicationLocales
     * + Activity.recreate) est faite côté Composable, SYNCHRONEMENT, AVANT que
     * l'Activity ne soit recréée. Sinon on a un race condition :
     *
     *   - viewModelScope.launch { ... } est async (coroutine sur Dispatchers.Main)
     *   - Si le Composable appelle recreate() juste après onLanguageSelected(),
     *     la coroutine de la VM n'a pas encore exécuté setApplicationLocales()
     *   - Donc attachBaseContext() lit l'ANCIENNE locale persistée par AppCompat
     *   - Résultat : aucun changement visible
     *
     * Solution : on découple. DataStore est purement pour notre UI (état du
     * SegmentedButton). AppCompat est la source de vérité pour la locale
     * effective, et son appel doit être synchrone côté UI.
     */
    fun onLanguageSelected(preference: LanguagePreference) {
        viewModelScope.launch {
            prefs.setLanguagePreference(preference)
        }
    }

    /**
     * Persiste le nouvel intervalle de rafraîchissement ET re-programme
     * immédiatement le worker WorkManager du widget avec la nouvelle cadence.
     *
     * ─── Pourquoi re-programmer ici, pas dans le repository ? ────────────
     * Le repository est un pur data holder ; il ne connaît pas WorkManager.
     * On garde ce couplage explicit du côté de la VM (couche présentation)
     * plutôt que d'introduire une dépendance repository → WorkManager qui
     * casserait la testabilité pure du DataStore layer.
     *
     * L'appel `WidgetRefreshScheduler.schedule` est idempotent grâce à
     * `ExistingPeriodicWorkPolicy.UPDATE` : si l'utilisateur fait plusieurs
     * clics d'affilée sur les segments, on ne crée pas de doublons de jobs.
     *
     * Note : on ne fait un update qu'APRÈS que le DataStore ait persisté la
     * valeur, sinon le prochain widget refresh (déclenché par le worker qu'on
     * vient de re-programmer) lirait l'ancienne valeur pour le seuil de
     * fraîcheur cache.
     */
    fun onRefreshIntervalSelected(interval: RefreshInterval) {
        viewModelScope.launch {
            prefs.setRefreshInterval(interval)
            WidgetRefreshScheduler.schedule(appContext, interval)
        }
    }
}
