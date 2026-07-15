package com.meteocompare.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel racine de l'activité — expose la préférence de thème.
 *
 * `SharingStarted.Eagerly` évite un flash de thème clair avant la première
 * valeur DataStore lorsque l'utilisateur a choisi le mode sombre.
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
}
