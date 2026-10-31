package com.meteocompare.app.ui.meteogram

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import com.meteocompare.app.ui.graphicview.GraphicForecastUiState
import com.meteocompare.app.ui.graphicview.GraphicModelValue
import com.meteocompare.app.ui.preview.PreviewFixtures
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import java.time.Instant
import kotlin.math.sin

/** Debug-only preview: no network, database or Hilt ViewModel is started. */
@Preview(name = "Météogrammes — smartphone", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun MeteogramPhonePreview() {
    val now = Instant.parse("2026-10-31T08:00:00Z")
    val hours = List(72) { i ->
        val wave = sin(i / 5.0)
        SimplifiedTimelinePoint(
            instant = now.plusSeconds(i * 3600L),
            temperatureC = 12.0 + wave * 5,
            apparentTemperatureC = 11.0 + wave * 5,
            dewPointC = 7.0 + wave * 2,
            humidityPercent = (70 - wave * 14).toInt(),
            pressureHpa = 1010.0 + sin(i / 14.0) * 7,
            cloudCoverPercent = (60 + sin(i / 9.0) * 25).toInt(),
            precipitationMm = if (i % 16 in 3..8) 0.2 + i % 6 * 0.25 else 0.0,
            windKmh = 16.0 + sin(i / 8.0) * 6,
            windGustKmh = 28.0 + sin(i / 8.0) * 8
        )
    }
    val modelRows = hours.associate { point ->
        val instant = requireNotNull(point.instant)
        instant to listOf(GraphicModelValue(
            modelName = "GFS preview",
            temperatureC = point.temperatureC,
            precipitationMm = point.precipitationMm,
            precipitationProbabilityPercent = 50,
            windKmh = point.windKmh,
            windGustKmh = point.windGustKmh,
            windDirectionDeg = 270,
            cloudPercent = point.cloudCoverPercent,
            cloudLowPercent = 45,
            cloudMidPercent = 32,
            cloudHighPercent = 66,
            pressureHpa = point.pressureHpa,
            humidityPercent = point.humidityPercent,
            dewPointC = point.dewPointC,
            apparentTemperatureC = point.apparentTemperatureC
        ))
    }
    MeteoCompareTheme(themePreference = ThemePreference.LIGHT, dynamicColor = false) {
        MeteogramContent(GraphicForecastUiState.Loaded(
            city = PreviewFixtures.city,
            points = hours,
            modelValuesByInstant = modelRows,
            solarByDate = emptyMap(),
            calculatedAt = now
        ))
    }
}
