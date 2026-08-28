package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.BiasDirection
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.CityDetailContentTab
import com.meteocompare.app.domain.model.CityDetailViewMode
import com.meteocompare.app.domain.model.ModelBias
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.preview.MeteoComponentPreview
import com.meteocompare.app.ui.preview.MeteoPreviewSurface
import com.meteocompare.app.ui.preview.MeteoScreenPreview
import com.meteocompare.app.ui.preview.PreviewFixtures

@MeteoScreenPreview
@Composable
private fun CityDetailScreenLoadedPreview() {
    val snackbar = remember { SnackbarHostState() }
    MeteoPreviewSurface {
        CityDetailContent(
            state = CityDetailUiState.Loaded(
                forecast = PreviewFixtures.forecast(),
                weeklyConfidence = List(7) { PreviewFixtures.dayConfidence(PreviewFixtures.today.plusDays(it.toLong())) },
                hourlyBands = PreviewFixtures.confidenceBands(),
                hourlyPrecipBands = PreviewFixtures.confidenceBands(base = 1.2, spread = 1.4, percent = 74),
                hourlyWindBands = PreviewFixtures.confidenceBands(base = 22.0, spread = 7.0, percent = 78),
                currentTemp = 22.0,
                currentCondition = com.meteocompare.app.domain.model.WeatherCondition.PARTLY_CLOUDY,
                currentCloudCover = 58,
                dailyConditions = PreviewFixtures.dailyConditions(),
                normals = PreviewFixtures.normals(),
                calculatedAt = PreviewFixtures.now,
                fetchedAt = PreviewFixtures.now.minusSeconds(8 * 60L)
            ),
            isRefreshing = false,
            isOnline = true,
            biasState = PreviewFixtures.biasScreenState(),
            evolutionState = ForecastEvolutionState.Loaded(
                PreviewFixtures.evolutionReport(),
                PreviewFixtures.evolutionHighlight()
            ),
            marineState = MarineUiState.Loaded(PreviewFixtures.marineForecast()),
            collapsedSections = emptySet(),
            detailViewMode = CityDetailViewMode.HOURLY,
            detailContentTab = CityDetailContentTab.DEFAULT,
            snackbarHostState = snackbar,
            onBack = {},
            onRefresh = {},
            onRefreshMarine = {}
        )
    }
}

@MeteoScreenPreview
@Composable
private fun CityDetailScreenLoadingPreview() {
    MeteoPreviewSurface {
        CityDetailContent(
            state = CityDetailUiState.Loading,
            isRefreshing = false,
            biasState = BiasScreenState.EMPTY,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onRefresh = {}
        )
    }
}

@MeteoComponentPreview
@Composable
private fun TodaySummaryCardPreview() {
    MeteoPreviewSurface {
        Surface(Modifier.padding(12.dp)) {
            TodaySummaryCard(
                today = PreviewFixtures.dayConfidence(),
                modelCount = 4,
                currentTemp = 22.0,
                currentCondition = com.meteocompare.app.domain.model.WeatherCondition.PARTLY_CLOUDY,
                currentCloudCover = 58,
                fetchedAt = PreviewFixtures.now.minusSeconds(8 * 60L),
                forecast = PreviewFixtures.forecast()
            )
        }
    }
}

@MeteoComponentPreview
@Composable
private fun BiasSparklinePreview() {
    val samples = PreviewFixtures.biasSamples(0.4, BiasVariable.TEMPERATURE)
    MeteoPreviewSurface {
        Surface(Modifier.padding(16.dp)) {
            BiasSparkline(
                forecast = samples.map { it.forecast },
                observation = samples.map { it.observation },
                direction = BiasDirection.WARM,
                yDomainMin = 15.0,
                yDomainMax = 27.0,
                modifier = Modifier.fillMaxWidth().height(130.dp),
                animate = false
            )
        }
    }
}

@MeteoComponentPreview
@Composable
private fun FrozenDetailTableLayoutPreview() {
    MeteoPreviewSurface {
        Surface(Modifier.padding(12.dp)) {
            FrozenDetailTableLayout(
                modelColumnWidth = 92.dp,
                temporalColumnCount = 4,
                headerHeight = 42.dp,
                rowHeight = 46.dp,
                rowCount = 3,
                palette = detailTablePalette(),
                cornerHeader = {
                    Box(Modifier.width(92.dp).height(42.dp), contentAlignment = Alignment.Center) { Text("Modèle") }
                },
                temporalHeaders = {
                    listOf("14h", "15h", "16h", "17h").forEach { label ->
                        Box(Modifier.width(72.dp).height(42.dp), contentAlignment = Alignment.Center) { Text(label) }
                    }
                },
                modelRows = {
                    listOf("AROME", "ECMWF", "GFS").forEach { label ->
                        Box(Modifier.width(92.dp).height(46.dp), contentAlignment = Alignment.Center) { Text(label) }
                    }
                },
                temporalColumns = {
                    repeat(4) { col ->
                        Column(Modifier.width(72.dp)) {
                            repeat(3) { row ->
                                Box(Modifier.height(46.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text("${20 + row + col}°")
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}

@MeteoComponentPreview
@Composable
private fun DetailedComparisonControlsPreview() {
    MeteoPreviewSurface {
        DetailedComparisonControls(
            mode = DisplayMode.HOURLY,
            selectedTab = CityDetailContentTab.TEMPERATURE,
            onModeChange = {},
            onTabChange = {},
            modifier = Modifier.padding(12.dp)
        )
    }
}

@MeteoScreenPreview
@Composable
private fun ForecastEvolutionSectionPreview() {
    MeteoPreviewSurface {
        ForecastEvolutionSection(
            state = ForecastEvolutionState.Loaded(
                report = PreviewFixtures.evolutionReport(),
                highlight = PreviewFixtures.evolutionHighlight()
            ),
            expanded = true,
            onExpandedChange = {},
            modifier = Modifier.padding(12.dp)
        )
    }
}

@MeteoScreenPreview
@Composable
private fun ForecastInsightsSectionPreview() {
    MeteoPreviewSurface {
        ForecastInsightsSection(
            insights = PreviewFixtures.insights(),
            timezone = "Europe/Paris",
            evolutionHighlight = PreviewFixtures.evolutionHighlight(),
            modelCount = 4,
            referencePoint = PreviewFixtures.timelinePoints().first(),
            onInsightClick = {},
            modifier = Modifier.padding(12.dp)
        )
    }
}

@MeteoScreenPreview
@Composable
private fun ForecastTablePreview() {
    val forecast = PreviewFixtures.forecast()
    MeteoPreviewSurface {
        ForecastTable(
            forecast = forecast,
            valueExtractor = { daily, index -> daily.tempMax.getOrNull(index) },
            valueFormatter = { "%.1f °C".format(it) },
            now = PreviewFixtures.now,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@MeteoScreenPreview
@Composable
private fun HourlyConfidenceChartPreview() {
    MeteoPreviewSurface {
        HourlyConfidenceChart(
            bands = PreviewFixtures.confidenceBands(),
            timezone = "Europe/Paris",
            metric = ConfidenceMetric.TEMPERATURE,
            normals = PreviewFixtures.normals(),
            modifier = Modifier.padding(12.dp)
        )
    }
}

@MeteoScreenPreview
@Composable
private fun HourlyForecastTablePreview() {
    val forecast = PreviewFixtures.forecast()
    MeteoPreviewSurface {
        HourlyForecastTable(
            forecast = forecast,
            valueExtractor = { hourly, index -> hourly.temperature2m.getOrNull(index) },
            valueFormatter = { "%.1f°".format(it) },
            now = PreviewFixtures.now,
            heatmapStyler = { hourlyTemperatureHeatmap(it) },
            modifier = Modifier.padding(8.dp)
        )
    }
}

@MeteoScreenPreview
@Composable
private fun HourlyWeatherByModelTablePreview() {
    MeteoPreviewSurface {
        HourlyWeatherByModelTable(
            forecast = PreviewFixtures.forecast(),
            now = PreviewFixtures.now,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@MeteoScreenPreview
@Composable
private fun LocalModelRankingSheetPreview() {
    MeteoPreviewSurface {
        LocalModelRankingSheet(
            rankings = PreviewFixtures.rankings(),
            cityLabel = PreviewFixtures.city.shortLabel,
            initialVariable = BiasVariable.TEMPERATURE,
            highlightedModel = WeatherModel.ECMWF,
            onDismiss = {}
        )
    }
}

@MeteoScreenPreview
@Composable
private fun LocalReliabilitySectionPreview() {
    MeteoPreviewSurface {
        LocalReliabilitySection(
            overallConfidencePercent = 82,
            familyCount = 4,
            rankings = PreviewFixtures.rankings(),
            tempBands = PreviewFixtures.confidenceBands(),
            precipBands = PreviewFixtures.confidenceBands(base = 1.2, spread = 1.4, percent = 74),
            windBands = PreviewFixtures.confidenceBands(base = 22.0, spread = 7.0, percent = 78),
            timezone = "Europe/Paris",
            normals = PreviewFixtures.normals(),
            expanded = true,
            onExpandedChange = {},
            onOpenRanking = {},
            modifier = Modifier.padding(12.dp)
        )
    }
}

@MeteoScreenPreview
@Composable
private fun MarineSectionPreview() {
    MeteoPreviewSurface {
        MarineSection(
            state = MarineUiState.Loaded(PreviewFixtures.marineForecast()),
            onRefresh = {},
            expanded = true,
            onExpandedChange = {},
            modifier = Modifier.padding(12.dp)
        )
    }
}

@MeteoScreenPreview
@Composable
private fun MinMaxForecastTablePreview() {
    MeteoPreviewSurface {
        Column(Modifier.padding(8.dp)) {
            MinMaxForecastLegend(normalsAvailable = true)
            MinMaxForecastTable(
                forecast = PreviewFixtures.forecast(),
                normals = PreviewFixtures.normals(),
                now = PreviewFixtures.now
            )
        }
    }
}

@MeteoComponentPreview
@Composable
private fun ModelBiasChipPreview() {
    MeteoPreviewSurface {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModelBiasChip(
                bias = ModelBias(BiasVariable.TEMPERATURE, 1.2, 0.55, 28),
                onClick = {}
            )
            ModelBiasChip(
                bias = ModelBias(BiasVariable.TEMPERATURE, -1.1, 0.5, 28),
                onClick = {}
            )
            CalibratingChip(sampleCount = 9)
        }
    }
}

@MeteoScreenPreview
@Composable
private fun ModelBiasDetailSheetPreview() {
    MeteoPreviewSurface {
        ModelBiasDetailSheet(
            selection = PreviewFixtures.biasSelection(),
            onDismiss = {},
            onOpenRanking = { _, _ -> }
        )
    }
}

@MeteoScreenPreview
@Composable
private fun SimplifiedTimelinePreview() {
    val points = PreviewFixtures.timelinePoints()
    MeteoPreviewSurface {
        SimplifiedTimelineCard(
            points = points,
            mode = DisplayMode.HOURLY,
            timezone = "Europe/Paris",
            events = emptyList(),
            focusPoint = points[6],
            onModeChange = {},
            availableModes = DisplayMode.entries.toSet(),
            now = PreviewFixtures.now,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@MeteoScreenPreview
@Composable
private fun WeatherByModelTablePreview() {
    MeteoPreviewSurface {
        Column(Modifier.padding(8.dp)) {
            WeatherLegend()
            WeatherByModelTable(
                rows = PreviewFixtures.dailyConditions(),
                modelOrder = PreviewFixtures.models,
                today = PreviewFixtures.today
            )
        }
    }
}

@MeteoComponentPreview
@Composable
private fun HourlyHeatmapStylesPreview() {
    MeteoPreviewSurface {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(-8.0, 2.0, 12.0, 18.0, 24.0, 31.0).forEach { value ->
                val style = hourlyTemperatureHeatmap(value)
                Surface(color = style.background) {
                    Text(
                        text = "Température %.0f °C".format(value),
                        color = style.contentColor,
                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                    )
                }
            }
        }
    }
}

@MeteoComponentPreview
@Composable
private fun DisplayModeMenuPreview() {
    MeteoPreviewSurface {
        Surface(Modifier.padding(16.dp)) {
            DisplayModeMenu(
                mode = DisplayMode.HOURLY,
                onModeChange = {},
                availableModes = DisplayMode.entries.toSet()
            )
        }
    }
}
