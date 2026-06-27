package com.meteocompare.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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
     * Applique la langue choisie. Double effet :
     *   1. DataStore est mis à jour (source de vérité pour l'UI du toggle)
     *   2. AppCompatDelegate.setApplicationLocales déclenche la recréation
     *      de l'Activity avec la nouvelle locale, et la persiste pour les
     *      prochains démarrages à froid (AppCompat gère sa propre persistance
     *      indépendamment, mais on garde DataStore en synchro pour que le
     *      bouton segmenté reflète l'état actuel).
     */
    fun onLanguageSelected(preference: LanguagePreference) {
        viewModelScope.launch {
            prefs.setLanguagePreference(preference)
            val locales = preference.bcp47Tag?.let { LocaleListCompat.forLanguageTags(it) }
                ?: LocaleListCompat.getEmptyLocaleList()  // SYSTEM = laisser à l'OS
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
