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
 * ViewModel racine de l'activité — porte uniquement la préférence de thème
 * pour l'instant. Existe pour offrir un lifecycle-safe StateFlow plutôt que
 * de faire collecter directement la Flow dans `setContent`.
 *
 * Pourquoi `Eagerly` ? Le thème doit être disponible dès la 1ère composition
 * pour éviter le flash blanc en mode sombre. Le coût (un Job actif quand
 * l'app est en background) est négligeable car la Flow DataStore ne ré-émet
 * que sur changement.
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
