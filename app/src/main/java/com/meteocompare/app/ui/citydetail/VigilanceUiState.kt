package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.VigilanceForecast

sealed interface VigilanceUiState {
    data object Idle : VigilanceUiState
    data object Loading : VigilanceUiState
    data class Loaded(val forecast: VigilanceForecast) : VigilanceUiState
}
