package com.meteocompare.app.ui.citydetail.confidence

import androidx.compose.runtime.Composable
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.preview.MeteoPreviewSurface
import com.meteocompare.app.ui.preview.MeteoScreenPreview
import com.meteocompare.app.ui.preview.PreviewFixtures

@MeteoScreenPreview
@Composable
private fun ConfidenceExplanationLoadedPreview() {
    val breakdowns = listOf(
        VariableBreakdown(
            VariableKind.TEMP_MAX,
            listOf(
                ModelValue(WeatherModel.AROME_FRANCE_HD, 25.1),
                ModelValue(WeatherModel.ICON_EU, 25.8),
                ModelValue(WeatherModel.ECMWF, 26.0),
                ModelValue(WeatherModel.GFS, 26.5)
            )
        ),
        VariableBreakdown(
            VariableKind.TEMP_MIN,
            listOf(
                ModelValue(WeatherModel.AROME_FRANCE_HD, 15.3),
                ModelValue(WeatherModel.ICON_EU, 16.0),
                ModelValue(WeatherModel.ECMWF, 16.2),
                ModelValue(WeatherModel.GFS, 16.7)
            )
        ),
        VariableBreakdown(
            VariableKind.PRECIPITATION,
            listOf(
                ModelValue(WeatherModel.AROME_FRANCE_HD, 2.1),
                ModelValue(WeatherModel.ICON_EU, 1.4),
                ModelValue(WeatherModel.ECMWF, 3.0),
                ModelValue(WeatherModel.GFS, 0.3)
            )
        ),
        VariableBreakdown(
            VariableKind.WIND_MAX,
            listOf(
                ModelValue(WeatherModel.AROME_FRANCE_HD, 20.0),
                ModelValue(WeatherModel.ICON_EU, 23.0),
                ModelValue(WeatherModel.ECMWF, 22.0),
                ModelValue(WeatherModel.GFS, 26.0)
            )
        )
    )
    MeteoPreviewSurface {
        ConfidenceExplanationContent(
            state = ConfidenceExplanationUiState.Loaded(
                city = PreviewFixtures.city,
                date = PreviewFixtures.today,
                dayConfidence = PreviewFixtures.dayConfidence(),
                variableBreakdowns = breakdowns,
                contributingModels = PreviewFixtures.models
            ),
            onBack = {}
        )
    }
}

@MeteoScreenPreview
@Composable
private fun ConfidenceExplanationLoadingPreview() {
    MeteoPreviewSurface {
        ConfidenceExplanationContent(
            state = ConfidenceExplanationUiState.Loading,
            onBack = {}
        )
    }
}

@MeteoScreenPreview
@Composable
private fun ConfidenceExplanationErrorPreview() {
    MeteoPreviewSurface {
        ConfidenceExplanationContent(
            state = ConfidenceExplanationUiState.Error("Les données de convergence ne sont pas disponibles."),
            onBack = {}
        )
    }
}
