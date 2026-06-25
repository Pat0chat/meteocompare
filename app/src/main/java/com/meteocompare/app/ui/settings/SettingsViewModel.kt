package com.meteocompare.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    fun onModelToggled(model: WeatherModel, enabled: Boolean) {
        viewModelScope.launch {
            val current = enabledModels.value
            val next = if (enabled) current + model else current - model
            // Sécurité : on garantit qu'au moins 1 modèle reste activé.
            // Sinon l'app n'aurait aucune source de données.
            if (next.isNotEmpty()) {
                prefs.setEnabledModels(next.toList())
            }
        }
    }
}
