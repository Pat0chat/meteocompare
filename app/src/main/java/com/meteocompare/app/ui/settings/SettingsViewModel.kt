package com.meteocompare.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
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
}
