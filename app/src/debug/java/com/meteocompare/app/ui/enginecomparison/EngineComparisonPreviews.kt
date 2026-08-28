package com.meteocompare.app.ui.enginecomparison

import androidx.compose.runtime.Composable
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.ui.preview.MeteoPreviewSurface
import com.meteocompare.app.ui.preview.MeteoScreenPreview
import com.meteocompare.app.ui.preview.PreviewFixtures

@MeteoScreenPreview
@Composable
private fun EngineComparisonLoadedPreview() {
    MeteoPreviewSurface {
        EngineComparisonContent(
            state = EngineComparisonUiState.Loaded(
                cityName = PreviewFixtures.city.name,
                selectedEngine = ForecastEngine.MULTI_CONSENSUS,
                days = PreviewFixtures.engineComparisonDays()
            )
        )
    }
}
