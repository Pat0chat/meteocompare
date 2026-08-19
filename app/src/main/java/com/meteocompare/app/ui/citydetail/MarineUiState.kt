package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.MarineForecast

sealed interface MarineUiState {
    data object Idle : MarineUiState
    data object Loading : MarineUiState
    data class Loaded(val data: MarineForecast, val isRefreshing: Boolean = false) : MarineUiState
    data class Error(val message: String) : MarineUiState
}
