package com.meteocompare.app.ui.graphicview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.VigilanceColor
import com.meteocompare.app.domain.model.VigilanceForecast
import com.meteocompare.app.domain.model.VigilanceInterval
import com.meteocompare.app.domain.model.VigilancePeriod
import com.meteocompare.app.domain.model.VigilancePhenomenon
import com.meteocompare.app.domain.model.VigilancePhenomenonAlert
import com.meteocompare.app.domain.model.VigilanceScope
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.citydetail.DivergenceReason
import com.meteocompare.app.ui.citydetail.ForecastMetric
import com.meteocompare.app.ui.citydetail.MetricConsensus
import com.meteocompare.app.ui.citydetail.ModelConsensusLevel
import com.meteocompare.app.ui.citydetail.PrecipitationSignalSource
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import com.meteocompare.app.ui.theme.WeatherAccentTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Previews de la Chart View avec un jeu de données local couvrant 7 jours / 168 h.
 *
 * Le fichier peut rester dans `src/main/java/.../ui/graphicview/` : il ne déclenche
 * aucun accès réseau et permet de travailler le rendu smartphone/tablette dans
 * Android Studio sans lancer l'application.
 */
@Preview(
    name = "Chart View - Smartphone",
    group = "Chart View",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun GraphicForecastSmartphonePreview() {
    GraphicForecastPreviewPage()
}

@Preview(
    name = "Chart View - Tablette",
    group = "Chart View",
    widthDp = 1024,
    heightDp = 768,
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun GraphicForecastTabletPreview() {
    GraphicForecastPreviewPage()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GraphicForecastPreviewPage() {
    val state = remember { buildGraphicForecastPreviewState() }
    val accentCondition = state.points.firstOrNull()?.condition

    MeteoCompareTheme(
        themePreference = ThemePreference.LIGHT,
        dynamicColor = false
    ) {
        WeatherAccentTheme(condition = accentCondition) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                topBar = {
                    TopAppBar(
                        title = { Text("Vue graphique") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        ),
                        navigationIcon = {
                            IconButton(onClick = {}) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Retour"
                                )
                            }
                        }
                    )
                }
            ) { padding ->
                GraphicForecastContent(
                    state = state,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        }
    }
}

private fun buildGraphicForecastPreviewState(): GraphicForecastUiState.Loaded {
    val zone = ZoneId.of("Europe/Paris")
    val start = LocalDateTime.of(2026, 9, 18, 8, 0)
        .atZone(zone)
        .toInstant()

    val city = City(
        id = "preview-paris",
        name = "Paris",
        admin1 = "Île-de-France",
        country = "France",
        latitude = 48.8566,
        longitude = 2.3522,
        timezone = zone.id,
        countryCode = "FR",
        departmentName = "Paris",
        departmentCode = "75"
    )

    val points = List(168) { index ->
        previewTimelinePoint(
            instant = start.plusSeconds(index * 3_600L),
            index = index,
            zone = zone
        )
    }

    val solarByDate = points
        .mapNotNull { it.instant?.atZone(zone)?.toLocalDate() }
        .distinct()
        .associateWith { date -> previewSolarWindow(date, zone) }

    val modelValuesByInstant = points.associate { point ->
        val instant = requireNotNull(point.instant)
        instant to previewModelValues(point)
    }

    return GraphicForecastUiState.Loaded(
        city = city,
        points = points,
        solarByDate = solarByDate,
        modelValuesByInstant = modelValuesByInstant,
        vigilance = previewVigilance(start),
        calculatedAt = start
    )
}

private fun previewTimelinePoint(
    instant: Instant,
    index: Int,
    zone: ZoneId
): SimplifiedTimelinePoint {
    val local = instant.atZone(zone)
    val hour = local.hour
    val dayIndex = index / 24

    val dailyWave = sin((hour - 6) * PI / 12.0)
    val temperature = 16.0 + 6.5 * dailyWave + dayIndex * 0.25
    val temperatureSpread = 1.3 + (index % 5) * 0.16

    val rainEpisode = (dayIndex == 1 && hour in 15..20) ||
        (dayIndex == 4 && hour in 8..11)
    val rainAmount = if (rainEpisode) {
        0.5 + ((hour + dayIndex) % 4) * 0.8
    } else {
        0.0
    }
    val rainProbability = if (rainEpisode) 72 + (index % 4) * 6 else 8 + (index % 5) * 3
    val rainAmountConvergence = if (rainEpisode) 64 + (index % 4) * 7 else 92

    val wind = 13.0 + abs(sin(index / 8.0)) * 12.0
    val gust = wind + 9.0 + (index % 3) * 2.0
    val windDirection = (215 + index * 7) % 360

    val condition = when {
        rainEpisode && rainAmount >= 2.0 -> WeatherCondition.RAIN
        rainEpisode -> WeatherCondition.RAIN_SHOWERS
        hour < 7 || hour >= 21 -> WeatherCondition.PARTLY_CLOUDY
        hour in 7..10 -> WeatherCondition.MAINLY_CLEAR
        hour in 11..16 -> WeatherCondition.CLEAR
        else -> WeatherCondition.PARTLY_CLOUDY
    }

    val temperatureAgreement = 86 - (index % 4) * 4
    val rainOccurrenceAgreement = if (rainEpisode) 78 else 94
    val windAgreement = 82 - (index % 3) * 5
    val conditionAgreement = if (rainEpisode) 72 else 88

    val metricConsensus = mapOf(
        ForecastMetric.TEMPERATURE to MetricConsensus(
            metric = ForecastMetric.TEMPERATURE,
            percent = temperatureAgreement,
            modelCount = 4,
            level = consensusLevel(temperatureAgreement),
            minimum = temperature - temperatureSpread,
            maximum = temperature + temperatureSpread,
            isDivergent = temperatureAgreement < 60
        ),
        ForecastMetric.PRECIPITATION to MetricConsensus(
            metric = ForecastMetric.PRECIPITATION,
            percent = rainOccurrenceAgreement,
            modelCount = 4,
            level = consensusLevel(rainOccurrenceAgreement),
            minimum = (rainAmount - 0.35).coerceAtLeast(0.0),
            maximum = rainAmount + if (rainEpisode) 1.1 else 0.1,
            isDivergent = rainAmountConvergence < 60
        ),
        ForecastMetric.WIND to MetricConsensus(
            metric = ForecastMetric.WIND,
            percent = windAgreement,
            modelCount = 4,
            level = consensusLevel(windAgreement),
            minimum = (wind - 3.0).coerceAtLeast(0.0),
            maximum = wind + 4.0,
            isDivergent = windAgreement < 60
        ),
        ForecastMetric.CONDITION to MetricConsensus(
            metric = ForecastMetric.CONDITION,
            percent = conditionAgreement,
            modelCount = 4,
            level = consensusLevel(conditionAgreement),
            isDivergent = conditionAgreement < 60
        )
    )

    val globalAgreement = listOf(
        temperatureAgreement,
        rainAmountConvergence,
        windAgreement,
        conditionAgreement
    ).average().roundToInt()

    return SimplifiedTimelinePoint(
        instant = instant,
        date = local.toLocalDate(),
        temperatureC = temperature,
        temperatureMinAcrossModels = temperature - temperatureSpread,
        temperatureMaxAcrossModels = temperature + temperatureSpread,
        precipitationPercent = rainProbability,
        precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
        precipitationModelCount = 4,
        wetModelCount = if (rainEpisode) 3 else 0,
        precipitationMm = rainAmount,
        precipitationConditionalMm = if (rainEpisode) rainAmount + 0.25 else 0.0,
        precipitationExpectedMm = rainAmount * rainProbability / 100.0,
        precipitationMinAcrossModelsMm = (rainAmount - 0.35).coerceAtLeast(0.0),
        precipitationMaxAcrossModelsMm = rainAmount + if (rainEpisode) 1.1 else 0.1,
        precipitationAmountConvergencePercent = rainAmountConvergence,
        precipitationProbabilityMin = (rainProbability - 10).coerceAtLeast(0),
        precipitationProbabilityMax = (rainProbability + 10).coerceAtMost(100),
        cloudCoverPercent = when (condition) {
            WeatherCondition.CLEAR -> 12
            WeatherCondition.MAINLY_CLEAR -> 28
            WeatherCondition.PARTLY_CLOUDY -> 58
            WeatherCondition.RAIN,
            WeatherCondition.RAIN_SHOWERS -> 84
            else -> 68
        },
        windKmh = wind,
        windMinAcrossModels = (wind - 3.0).coerceAtLeast(0.0),
        windMaxAcrossModels = wind + 4.0,
        windGustKmh = gust,
        windGustMinAcrossModels = gust - 4.0,
        windGustMaxAcrossModels = gust + 6.0,
        windGustModelCount = 4,
        windDirectionDeg = windDirection,
        condition = condition,
        modelCount = 4,
        familyCount = 3,
        temperatureModelCount = 4,
        windModelCount = 4,
        conditionModelCount = 4,
        hasMultiModelEvidence = true,
        consensusPercent = globalAgreement,
        consensusLevel = consensusLevel(globalAgreement),
        metricConsensus = metricConsensus,
        divergenceReasons = buildSet {
            if (temperatureAgreement < 60) add(DivergenceReason.TEMPERATURE)
            if (rainAmountConvergence < 60) add(DivergenceReason.PRECIPITATION)
            if (windAgreement < 60) add(DivergenceReason.WIND)
            if (conditionAgreement < 60) add(DivergenceReason.CONDITION)
        }
    )
}

private fun previewSolarWindow(date: LocalDate, zone: ZoneId): GraphicSolarWindow =
    GraphicSolarWindow(
        sunrise = date.atTime(LocalTime.of(7, 20)).atZone(zone).toInstant(),
        sunset = date.atTime(LocalTime.of(19, 55)).atZone(zone).toInstant()
    )

private fun previewModelValues(point: SimplifiedTimelinePoint): List<GraphicModelValue> {
    val temperature = point.temperatureC ?: 18.0
    val rain = point.precipitationMm ?: 0.0
    val probability = point.precipitationPercent ?: 0
    val wind = point.windKmh ?: 12.0
    val gust = point.windGustKmh ?: 22.0
    val direction = point.windDirectionDeg ?: 225

    return listOf(
        GraphicModelValue(
            modelName = "ECMWF IFS",
            temperatureC = temperature - 0.8,
            precipitationMm = (rain * 0.75).coerceAtLeast(0.0),
            precipitationProbabilityPercent = (probability - 6).coerceAtLeast(0),
            windKmh = (wind - 2.0).coerceAtLeast(0.0),
            windGustKmh = (gust - 3.0).coerceAtLeast(0.0),
            windDirectionDeg = (direction + 350) % 360
        ),
        GraphicModelValue(
            modelName = "Météo-France AROME",
            temperatureC = temperature + 0.4,
            precipitationMm = rain * 1.15,
            precipitationProbabilityPercent = (probability + 7).coerceAtMost(100),
            windKmh = wind + 1.0,
            windGustKmh = gust + 2.0,
            windDirectionDeg = (direction + 5) % 360
        ),
        GraphicModelValue(
            modelName = "DWD ICON",
            temperatureC = temperature + 1.0,
            precipitationMm = rain * 1.35,
            precipitationProbabilityPercent = (probability + 2).coerceAtMost(100),
            windKmh = wind + 3.0,
            windGustKmh = gust + 4.0,
            windDirectionDeg = (direction + 12) % 360
        ),
        GraphicModelValue(
            modelName = "GFS",
            temperatureC = temperature - 0.2,
            precipitationMm = rain * 0.9,
            precipitationProbabilityPercent = probability,
            windKmh = wind - 1.0,
            windGustKmh = gust + 1.0,
            windDirectionDeg = (direction + 355) % 360
        )
    )
}

private fun previewVigilance(start: Instant): VigilanceForecast {
    val thunderstormInterval = VigilanceInterval(
        begin = start.plusSeconds(30 * 3_600L),
        end = start.plusSeconds(42 * 3_600L),
        color = VigilanceColor.ORANGE,
        scope = VigilanceScope.DEPARTMENT
    )
    val windInterval = VigilanceInterval(
        begin = start.plusSeconds(82 * 3_600L),
        end = start.plusSeconds(98 * 3_600L),
        color = VigilanceColor.YELLOW,
        scope = VigilanceScope.DEPARTMENT
    )

    val thunderstorm = VigilancePhenomenonAlert(
        phenomenon = VigilancePhenomenon.THUNDERSTORMS,
        maxColor = VigilanceColor.ORANGE,
        intervals = listOf(thunderstormInterval)
    )
    val wind = VigilancePhenomenonAlert(
        phenomenon = VigilancePhenomenon.WIND,
        maxColor = VigilanceColor.YELLOW,
        intervals = listOf(windInterval)
    )

    return VigilanceForecast(
        source = "Preview",
        department = "75",
        includeCoast = false,
        updateTime = start,
        productDatetime = start,
        generationTimestamp = start,
        periods = listOf(
            VigilancePeriod(
                term = "J1-J2",
                begin = start,
                end = start.plusSeconds(7 * 24 * 3_600L),
                maxColor = VigilanceColor.ORANGE,
                departmentMaxColor = VigilanceColor.ORANGE,
                coastMaxColor = null,
                phenomena = listOf(thunderstorm, wind)
            )
        ),
        fetchedAt = start,
        evaluationTime = start
    )
}

private fun consensusLevel(percent: Int): ModelConsensusLevel = when {
    percent >= 80 -> ModelConsensusLevel.HIGH
    percent >= 60 -> ModelConsensusLevel.MEDIUM
    else -> ModelConsensusLevel.LOW
}
