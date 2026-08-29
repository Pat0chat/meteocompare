package com.meteocompare.app.ui.citylist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.preview.MeteoComponentPreview
import com.meteocompare.app.ui.preview.MeteoPreviewSurface
import com.meteocompare.app.ui.preview.MeteoScreenPreview
import com.meteocompare.app.ui.preview.PreviewFixtures

private fun loadedForecast(
    temp: Double = 22.0,
    condition: WeatherCondition = WeatherCondition.PARTLY_CLOUDY
): ForecastState.Loaded = ForecastState.Loaded(
    today = PreviewFixtures.dayConfidence(),
    currentTemp = temp,
    currentCondition = condition,
    currentCloudCover = 58,
    fetchedAt = PreviewFixtures.now.minusSeconds(8 * 60L),
    sourceModels = PreviewFixtures.models.toSet(),
    next12hTemps = PreviewFixtures.next12hTemps,
    next12hPrecipProb = PreviewFixtures.next12hPrecipProb,
    next12hPrecipMm = PreviewFixtures.next12hPrecipMm,
    next12hConditions = PreviewFixtures.next12hConditions,
    next12hScenarios = PreviewFixtures.scenarios,
    hourlyStartTime = PreviewFixtures.hourlyStartTime,
    sunrise = PreviewFixtures.sunrise,
    sunset = PreviewFixtures.sunset
)

private fun homeItems(): List<CityCardState> = listOf(
    CityCardState(
        city = PreviewFixtures.city,
        forecast = loadedForecast(),
        vigilance = PreviewFixtures.vigilance,
        isMarineAvailable = false
    ),
    CityCardState(
        city = PreviewFixtures.coastalCity,
        forecast = loadedForecast(19.0, WeatherCondition.RAIN_SHOWERS),
        vigilance = PreviewFixtures.coastalVigilance,
        isMarineAvailable = true
    ),
    CityCardState(
        city = City(
            id = "lyon",
            name = "Lyon",
            admin1 = "Auvergne-Rhône-Alpes",
            country = "France",
            latitude = 45.76,
            longitude = 4.84,
            timezone = "Europe/Paris"
        ),
        forecast = ForecastState.Loading
    ),
    CityCardState(
        city = City(
            id = "nice",
            name = "Nice",
            admin1 = "Provence-Alpes-Côte d’Azur",
            country = "France",
            latitude = 43.70,
            longitude = 7.27,
            timezone = "Europe/Paris"
        ),
        forecast = ForecastState.Error("Données temporairement indisponibles"),
        isMarineAvailable = true
    )
)

@MeteoScreenPreview
@Composable
private fun CityListScreenCompletePreview() {
    MeteoPreviewSurface {
        CityListContent(
            uiState = CityListUiState(items = homeItems()),
            onCityClick = {},
            onAddClick = {},
            onDonateClick = {},
            onHelpClick = {},
            onSettingsClick = {},
            onRemoveCity = {},
            onRetry = {},
            onRefresh = {},
            onMarineAction = {}
        )
    }
}

@MeteoScreenPreview
@Composable
private fun CityListScreenOfflinePreview() {
    MeteoPreviewSurface {
        CityListContent(
            uiState = CityListUiState(
                items = homeItems().take(2),
                isOnline = false
            ),
            onCityClick = {},
            onAddClick = {},
            onDonateClick = {},
            onHelpClick = {},
            onSettingsClick = {},
            onRemoveCity = {},
            onRetry = {},
            onRefresh = {},
            onMarineAction = {}
        )
    }
}

@MeteoScreenPreview
@Composable
private fun CityListEmptyStatePreview() {
    MeteoPreviewSurface {
        CityListContent(
            uiState = CityListUiState(),
            onCityClick = {},
            onAddClick = {},
            onDonateClick = {},
            onHelpClick = {},
            onSettingsClick = {},
            onRemoveCity = {},
            onRetry = {},
            onRefresh = {}
        )
    }
}

@MeteoComponentPreview
@Composable
private fun CityCardStatesPreview() {
    MeteoPreviewSurface {
        Column(Modifier.padding(12.dp)) {
            CityCard(
                state = CityCardState(PreviewFixtures.city, loadedForecast(), vigilance = PreviewFixtures.vigilance),
                onClick = {},
                onRemove = {},
                onRetry = {}
            )
            Spacer(Modifier.height(12.dp))
            CityCard(
                state = CityCardState(PreviewFixtures.coastalCity, ForecastState.Loading, isMarineAvailable = true),
                onClick = {},
                onRemove = {},
                onRetry = {}
            )
            Spacer(Modifier.height(12.dp))
            CityCard(
                state = CityCardState(
                    PreviewFixtures.city.copy(id = "error", name = "Bordeaux"),
                    ForecastState.Error("Connexion impossible")
                ),
                onClick = {},
                onRemove = {},
                onRetry = {}
            )
        }
    }
}

@MeteoScreenPreview
@Composable
private fun AddCitySheetPreview() {
    MeteoPreviewSurface {
        AddCitySheet(
            state = AddCityUiState(
                query = "Bor",
                results = listOf(
                    City("bordeaux", "Bordeaux", "Nouvelle-Aquitaine", "France", 44.84, -0.58, "Europe/Paris"),
                    City("borgo", "Borgo", "Corse", "France", 42.55, 9.43, "Europe/Paris"),
                    City("borne", "Borne", "Overijssel", "Pays-Bas", 52.30, 6.75, "Europe/Amsterdam")
                )
            ),
            onQueryChanged = {},
            onCitySelected = {},
            onDismiss = {}
        )
    }
}

@MeteoComponentPreview
@Composable
private fun MiniForecastStripPreview() {
    MeteoPreviewSurface {
        Surface(Modifier.padding(12.dp)) {
            MiniForecastStrip(
                hourlyTemps = PreviewFixtures.next12hTemps,
                hourlyPrecipProb = PreviewFixtures.next12hPrecipProb,
                hourlyPrecipMm = PreviewFixtures.next12hPrecipMm,
                hourlyConditions = PreviewFixtures.next12hConditions,
                startTime = PreviewFixtures.hourlyStartTime
            )
        }
    }
}

@MeteoComponentPreview
@Composable
private fun SunTimesRowPreview() {
    MeteoPreviewSurface {
        Surface(Modifier.padding(16.dp)) {
            SunTimesRow(
                sunrise = PreviewFixtures.sunrise,
                sunset = PreviewFixtures.sunset
            )
        }
    }
}

@MeteoComponentPreview
@Composable
private fun CityResultRowPreview() {
    MeteoPreviewSurface {
        CityResultRow(
            city = City(
                id = "preview-result",
                name = "La Rochelle",
                admin1 = "Nouvelle-Aquitaine",
                country = "France",
                latitude = 46.16,
                longitude = -1.15,
                timezone = "Europe/Paris"
            ),
            onClick = {}
        )
    }
}

@MeteoComponentPreview
@Composable
private fun EmptyStateStandalonePreview() {
    MeteoPreviewSurface {
        EmptyState(onAddClick = {})
    }
}
