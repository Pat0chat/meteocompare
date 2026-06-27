package com.meteocompare.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel racine de l'activité — expose les préférences thème + langue.
 *
 * Pourquoi `Eagerly` ? Le thème doit être disponible dès la 1ère composition
 * pour éviter le flash blanc en mode sombre. La langue est appliquée encore
 * plus tôt (avant setContent) via AppCompatDelegate, donc le StateFlow est
 * juste de la commodité pour observer les changements en cours d'utilisation.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    userPreferences: UserPreferencesRepository
) : ViewModel() {

    val themePreference: StateFlow<ThemePreference> =
        userPreferences.observeThemePreference()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ThemePreference.SYSTEM
            )

    val languagePreference: StateFlow<LanguagePreference> =
        userPreferences.observeLanguagePreference()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = LanguagePreference.SYSTEM
            )
}
