package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.ui.preview.MeteoPreviewSurface
import com.meteocompare.app.ui.preview.MeteoScreenPreview
import com.meteocompare.app.ui.preview.PreviewFixtures

@MeteoScreenPreview
@Composable
private fun ForecastEvolutionTemperatureChartPreview() {
    ForecastEvolutionChartPreview(ForecastEvolutionVariable.TEMPERATURE)
}

@MeteoScreenPreview
@Composable
private fun ForecastEvolutionPrecipitationChartPreview() {
    ForecastEvolutionChartPreview(ForecastEvolutionVariable.PRECIPITATION)
}

@MeteoScreenPreview
@Composable
private fun ForecastEvolutionWindChartPreview() {
    ForecastEvolutionChartPreview(ForecastEvolutionVariable.WIND)
}

@Composable
private fun ForecastEvolutionChartPreview(variable: ForecastEvolutionVariable) {
    MeteoPreviewSurface {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
            ForecastEvolutionSection(
                state = ForecastEvolutionState.Loaded(
                    report = PreviewFixtures.evolutionReport(primaryVariable = variable),
                    highlight = null
                ),
                expanded = true,
                onExpandedChange = {}
            )
        }
    }
}
