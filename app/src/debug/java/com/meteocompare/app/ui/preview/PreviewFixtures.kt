package com.meteocompare.app.ui.preview

import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.DayForecastEvolution
import com.meteocompare.app.domain.model.DayNormals
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.ForecastEvolutionHighlight
import com.meteocompare.app.domain.model.ForecastEvolutionReport
import com.meteocompare.app.domain.model.ForecastEvolutionSnapshot
import com.meteocompare.app.domain.model.ForecastEvolutionTrend
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.ForecastRevision
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyConfidenceBand
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.MarineDaily
import com.meteocompare.app.domain.model.MarineForecast
import com.meteocompare.app.domain.model.MarineGrid
import com.meteocompare.app.domain.model.MarineHourly
import com.meteocompare.app.domain.model.ModelBias
import com.meteocompare.app.domain.model.ModelReliability
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.domain.model.ReliabilityLevel
import com.meteocompare.app.domain.model.ReliabilityRank
import com.meteocompare.app.domain.model.ReliabilityTrend
import com.meteocompare.app.domain.model.VariableForecastEvolution
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.model.WeatherScenario
import com.meteocompare.app.domain.model.WeatherScenarioKind
import com.meteocompare.app.domain.model.WeatherScenarioTiming
import com.meteocompare.app.domain.model.VigilanceColor
import com.meteocompare.app.domain.model.VigilanceForecast
import com.meteocompare.app.domain.model.VigilanceInterval
import com.meteocompare.app.domain.model.VigilancePeriod
import com.meteocompare.app.domain.model.VigilancePhenomenon
import com.meteocompare.app.domain.model.VigilancePhenomenonAlert
import com.meteocompare.app.domain.model.VigilanceScope
import com.meteocompare.app.domain.usecase.DayCellExtras
import com.meteocompare.app.domain.usecase.DayConditionsRow
import com.meteocompare.app.domain.usecase.EngineComparisonDay
import com.meteocompare.app.domain.usecase.EngineComparisonValues
import com.meteocompare.app.domain.usecase.EngineDivergence
import com.meteocompare.app.domain.usecase.EngineDivergenceLevel
import com.meteocompare.app.ui.citydetail.BiasScreenState
import com.meteocompare.app.ui.citydetail.BiasSelection
import com.meteocompare.app.ui.citydetail.DivergenceReason
import com.meteocompare.app.ui.citydetail.ForecastInsight
import com.meteocompare.app.ui.citydetail.ForecastInsightKind
import com.meteocompare.app.ui.citydetail.ForecastInsightLevel
import com.meteocompare.app.ui.citydetail.ForecastMetric
import com.meteocompare.app.ui.citydetail.LocalModelRankingEntry
import com.meteocompare.app.ui.citydetail.LocalModelRankings
import com.meteocompare.app.ui.citydetail.LocalVariableRanking
import com.meteocompare.app.ui.citydetail.MetricConsensus
import com.meteocompare.app.ui.citydetail.ModelConsensusLevel
import com.meteocompare.app.ui.citydetail.PrecipitationSignalSource
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import com.meteocompare.app.ui.citydetail.VariableBiasState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.sin

private val BASE_INSTANT: Instant = Instant.parse("2026-08-28T12:00:00Z")
private val BASE_DATE: LocalDate = LocalDate.of(2026, 8, 28)

internal object PreviewFixtures {
    val city = City(
        id = "preview-paris",
        name = "Paris",
        admin1 = "Île-de-France",
        country = "France",
        latitude = 48.8566,
        longitude = 2.3522,
        timezone = "Europe/Paris",
        marineEnabled = false,
        countryCode = "FR",
        departmentName = "Paris",
        departmentCode = "75"
    )

    val coastalCity = City(
        id = "preview-biarritz",
        name = "Biarritz",
        admin1 = "Nouvelle-Aquitaine",
        country = "France",
        latitude = 43.4832,
        longitude = -1.5586,
        timezone = "Europe/Paris",
        marineEnabled = true,
        countryCode = "FR",
        departmentName = "Pyrénées-Atlantiques",
        departmentCode = "64"
    )

    val vigilance = VigilanceForecast(
        source = "Météo-France",
        department = "75",
        includeCoast = false,
        updateTime = BASE_INSTANT.minusSeconds(900),
        productDatetime = BASE_INSTANT.minusSeconds(900),
        generationTimestamp = BASE_INSTANT.minusSeconds(840),
        periods = listOf(
            VigilancePeriod(
                term = "J",
                begin = BASE_INSTANT.minusSeconds(2 * 3600L),
                end = BASE_INSTANT.plusSeconds(18 * 3600L),
                maxColor = VigilanceColor.ORANGE,
                departmentMaxColor = VigilanceColor.ORANGE,
                coastMaxColor = null,
                phenomena = listOf(
                    VigilancePhenomenonAlert(
                        phenomenon = VigilancePhenomenon.THUNDERSTORMS,
                        maxColor = VigilanceColor.ORANGE,
                        intervals = listOf(
                            VigilanceInterval(
                                begin = BASE_INSTANT.plusSeconds(2 * 3600L),
                                end = BASE_INSTANT.plusSeconds(8 * 3600L),
                                color = VigilanceColor.ORANGE,
                                scope = VigilanceScope.DEPARTMENT
                            )
                        )
                    ),
                    VigilancePhenomenonAlert(
                        phenomenon = VigilancePhenomenon.WIND,
                        maxColor = VigilanceColor.YELLOW,
                        intervals = listOf(
                            VigilanceInterval(
                                begin = BASE_INSTANT,
                                end = BASE_INSTANT.plusSeconds(12 * 3600L),
                                color = VigilanceColor.YELLOW,
                                scope = VigilanceScope.DEPARTMENT
                            )
                        )
                    )
                )
            )
        ),
        fetchedAt = BASE_INSTANT
    )

    val coastalVigilance = vigilance.copy(
        department = "64",
        includeCoast = true,
        periods = vigilance.periods.map { period ->
            period.copy(
                phenomena = period.phenomena + VigilancePhenomenonAlert(
                    phenomenon = VigilancePhenomenon.COASTAL_FLOODING,
                    maxColor = VigilanceColor.ORANGE,
                    intervals = listOf(
                        VigilanceInterval(
                            begin = BASE_INSTANT.plusSeconds(3600),
                            end = BASE_INSTANT.plusSeconds(7 * 3600L),
                            color = VigilanceColor.ORANGE,
                            scope = VigilanceScope.COAST
                        )
                    )
                )
            )
        }
    )

    val models = listOf(
        WeatherModel.AROME_FRANCE_HD,
        WeatherModel.ICON_EU,
        WeatherModel.ECMWF,
        WeatherModel.GFS
    )

    val now: Instant = BASE_INSTANT
    val today: LocalDate = BASE_DATE

    fun dayConfidence(date: LocalDate = today): DayConfidence = DayConfidence(
        date = date,
        tempMax = ConfidenceScore(88, 24.0, 27.0, 25.6, 0.9, 4, familyCount = 4),
        tempMin = ConfidenceScore(84, 15.0, 17.0, 16.1, 0.7, 4, familyCount = 4),
        precipitation = PrecipitationConfidence.Divided(
            percent = 72,
            modelCount = 4,
            modelsForRain = 3,
            modelsAgainstRain = 1,
            rainMinMm = 0.5,
            rainMaxMm = 3.4,
            rainMeanMm = 1.6
        ),
        windMax = ConfidenceScore(79, 18.0, 27.0, 22.0, 2.8, 4, familyCount = 4),
        windGustMax = ConfidenceScore(76, 31.0, 44.0, 37.0, 4.1, 4, familyCount = 4)
    )

    val next12hTemps: List<Double?> = listOf(
        22.0, 23.2, 24.4, 25.5, 26.0, 25.8,
        24.9, 23.7, 22.4, 21.2, 20.3, 19.7
    )
    val next12hPrecipProb: List<Int?> = listOf(5, 5, 10, 20, 45, 70, 85, 65, 35, 15, 5, 0)
    val next12hPrecipMm: List<Double?> = listOf(0.0, 0.0, 0.0, 0.0, 0.2, 0.8, 2.6, 1.1, 0.3, 0.0, 0.0, 0.0)
    val next12hConditions: List<WeatherCondition?> = listOf(
        WeatherCondition.MAINLY_CLEAR,
        WeatherCondition.MAINLY_CLEAR,
        WeatherCondition.PARTLY_CLOUDY,
        WeatherCondition.PARTLY_CLOUDY,
        WeatherCondition.OVERCAST,
        WeatherCondition.DRIZZLE,
        WeatherCondition.RAIN,
        WeatherCondition.RAIN_SHOWERS,
        WeatherCondition.PARTLY_CLOUDY,
        WeatherCondition.MAINLY_CLEAR,
        WeatherCondition.CLEAR,
        WeatherCondition.CLEAR
    )

    val scenarios = listOf(
        WeatherScenario(
            kind = WeatherScenarioKind.SHOWERS,
            timing = WeatherScenarioTiming.MIDDLE,
            modelCount = 2,
            totalModelCount = 4,
            temperatureMinC = 22.0,
            temperatureMaxC = 26.0,
            precipitationMinMm = 1.1,
            precipitationMaxMm = 3.4,
            gustMinKmh = 31.0,
            gustMaxKmh = 42.0,
            voteSharePercent = 50,
            familyCount = 2,
            totalFamilyCount = 4
        ),
        WeatherScenario(
            kind = WeatherScenarioKind.VARIABLE_SKY,
            timing = WeatherScenarioTiming.THROUGHOUT,
            modelCount = 1,
            totalModelCount = 4,
            temperatureMinC = 21.0,
            temperatureMaxC = 25.0,
            precipitationMinMm = 0.0,
            precipitationMaxMm = 0.2,
            voteSharePercent = 25,
            familyCount = 1,
            totalFamilyCount = 4
        ),
        WeatherScenario(
            kind = WeatherScenarioKind.RAIN,
            timing = WeatherScenarioTiming.LATE,
            modelCount = 1,
            totalModelCount = 4,
            temperatureMinC = 20.0,
            temperatureMaxC = 24.0,
            precipitationMinMm = 3.8,
            precipitationMaxMm = 5.2,
            voteSharePercent = 25,
            familyCount = 1,
            totalFamilyCount = 4
        )
    )

    fun forecast(cityValue: City = city): CityForecast {
        val hourlyInstants = List(36) { BASE_INSTANT.plusSeconds(it * 3600L) }
        val dailyDates = List(7) { BASE_DATE.plusDays(it.toLong()) }
        val codes = listOf(1, 1, 2, 2, 3, 51, 61, 80, 2, 1, 0, 0)
        val series = models.mapIndexed { modelIndex, model ->
            val offset = listOf(-0.7, 0.2, 0.5, 1.0)[modelIndex]
            model to ForecastSeries(
                model = model,
                hourly = HourlyForecast(
                    timestamps = hourlyInstants,
                    temperature2m = hourlyInstants.indices.map { i ->
                        20.0 + 4.5 * sin((i + 2) / 5.0) + offset
                    },
                    precipitation = hourlyInstants.indices.map { i ->
                        if (i in 5..8) (0.3 + (i - 5) * 0.45 + modelIndex * 0.08) else 0.0
                    },
                    windSpeed10m = hourlyInstants.indices.map { i -> 14.0 + (i % 6) * 1.5 + modelIndex },
                    weatherCode = hourlyInstants.indices.map { i -> codes[i % codes.size] },
                    windDirection10m = hourlyInstants.indices.map { i -> (210 + i * 8 + modelIndex * 4) % 360 },
                    precipitationProbability = hourlyInstants.indices.map { i ->
                        next12hPrecipProb[i % next12hPrecipProb.size]
                    },
                    cloudCover = hourlyInstants.indices.map { i -> listOf(25, 30, 45, 55, 70, 85, 95, 80, 55, 35, 20, 15)[i % 12] },
                    windGusts10m = hourlyInstants.indices.map { i -> 24.0 + (i % 7) * 2.0 + modelIndex }
                ),
                daily = DailyForecast(
                    dates = dailyDates,
                    tempMax = dailyDates.indices.map { i -> 25.0 + i * 0.7 + offset },
                    tempMin = dailyDates.indices.map { i -> 15.5 + i * 0.4 + offset / 2.0 },
                    precipitationSum = dailyDates.indices.map { i -> listOf(2.8, 0.0, 0.4, 5.2, 1.1, 0.0, 0.0)[i] + modelIndex * 0.1 },
                    windSpeedMax = dailyDates.indices.map { i -> 20.0 + i + modelIndex },
                    weatherCode = dailyDates.indices.map { i -> listOf(61, 2, 1, 80, 3, 1, 0)[i] },
                    windDirection10mDominant = dailyDates.indices.map { i -> (220 + i * 15 + modelIndex * 3) % 360 },
                    precipitationProbabilityMax = dailyDates.indices.map { i -> listOf(78, 10, 25, 85, 45, 10, 5)[i] },
                    windGustsMax = dailyDates.indices.map { i -> 34.0 + i * 1.8 + modelIndex },
                    sunrise = dailyDates.indices.map { i -> BASE_INSTANT.plusSeconds(i * 86400L - 6 * 3600L) },
                    sunset = dailyDates.indices.map { i -> BASE_INSTANT.plusSeconds(i * 86400L + 8 * 3600L) }
                )
            )
        }.toMap()
        return CityForecast(
            city = cityValue,
            seriesByModel = series,
            errors = mapOf(WeatherModel.ARPEGE_EUROPE to "Run momentanément indisponible"),
            fetchedAt = BASE_INSTANT.minusSeconds(9 * 60L)
        )
    }

    fun confidenceBands(
        start: Instant = BASE_INSTANT,
        base: Double = 22.0,
        spread: Double = 2.0,
        percent: Int = 84
    ): List<HourlyConfidenceBand> = List(24) { index ->
        val mean = base + 3.0 * sin(index / 4.0)
        val widening = spread + index / 24.0 * spread
        HourlyConfidenceBand(
            timestamp = start.plusSeconds(index * 3600L),
            meanValue = mean,
            minValue = mean - widening / 2.0,
            maxValue = mean + widening / 2.0,
            stdDev = widening / 3.0,
            percent = (percent - index / 3).coerceAtLeast(52),
            modelCount = 4,
            familyCount = 4
        )
    }

    fun normals(): Map<Int, DayNormals> = (0..6).associate { offset ->
        val date = BASE_DATE.plusDays(offset.toLong())
        DayNormals.key(date.monthValue, date.dayOfMonth) to DayNormals(
            month = date.monthValue,
            day = date.dayOfMonth,
            tempMaxNormal = 24.0 + offset * 0.2,
            tempMinNormal = 15.0 + offset * 0.1,
            precipMeanNormal = 1.4,
            windMeanNormal = 15.5
        )
    }

    fun dailyConditions(): List<DayConditionsRow> = List(5) { day ->
        DayConditionsRow(
            date = BASE_DATE.plusDays(day.toLong()),
            byModel = models.associateWith { model ->
                when ((day + model.ordinal) % 5) {
                    0 -> WeatherCondition.CLEAR
                    1 -> WeatherCondition.PARTLY_CLOUDY
                    2 -> WeatherCondition.OVERCAST
                    3 -> WeatherCondition.RAIN
                    else -> WeatherCondition.RAIN_SHOWERS
                }
            },
            extrasByModel = models.associateWith { model ->
                DayCellExtras(
                    precipProbabilityMax = 20 + (model.ordinal * 11 + day * 13) % 75,
                    cloudCoverMean = 30 + (model.ordinal * 9 + day * 12) % 65
                )
            }
        )
    }

    fun timelinePoints(): List<SimplifiedTimelinePoint> = List(12) { index ->
        val rain = next12hPrecipProb[index] ?: 0
        val temp = next12hTemps[index]
        SimplifiedTimelinePoint(
            instant = BASE_INSTANT.plusSeconds(index * 3600L),
            temperatureC = temp,
            temperatureMinAcrossModels = temp?.minus(1.2),
            temperatureMaxAcrossModels = temp?.plus(1.1),
            precipitationPercent = rain,
            precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
            precipitationModelCount = 4,
            wetModelCount = if (rain >= 50) 3 else 1,
            precipitationMm = next12hPrecipMm[index],
            precipitationConditionalMm = next12hPrecipMm[index]?.takeIf { it > 0.0 }?.plus(0.4),
            precipitationExpectedMm = next12hPrecipMm[index]?.times(rain / 100.0),
            precipitationMinAcrossModelsMm = 0.0,
            precipitationMaxAcrossModelsMm = next12hPrecipMm[index]?.plus(0.8),
            precipitationProbabilityMin = (rain - 15).coerceAtLeast(0),
            precipitationProbabilityMax = (rain + 12).coerceAtMost(100),
            cloudCoverPercent = listOf(25, 30, 40, 50, 70, 85, 95, 80, 55, 35, 20, 15)[index],
            windKmh = 15.0 + index,
            windMinAcrossModels = 12.0 + index,
            windMaxAcrossModels = 20.0 + index,
            windGustKmh = 26.0 + index * 1.4,
            condition = next12hConditions[index],
            modelCount = 4,
            familyCount = 4,
            temperatureModelCount = 4,
            windModelCount = 4,
            conditionModelCount = 4,
            hasMultiModelEvidence = true,
            consensusPercent = if (index in 5..7) 58 else 86,
            consensusLevel = if (index in 5..7) ModelConsensusLevel.MEDIUM else ModelConsensusLevel.HIGH,
            metricConsensus = mapOf(
                ForecastMetric.TEMPERATURE to MetricConsensus(
                    ForecastMetric.TEMPERATURE, 86, 4, ModelConsensusLevel.HIGH,
                    minimum = temp?.minus(1.2), maximum = temp?.plus(1.1)
                ),
                ForecastMetric.PRECIPITATION to MetricConsensus(
                    ForecastMetric.PRECIPITATION,
                    if (index in 5..7) 55 else 82,
                    4,
                    if (index in 5..7) ModelConsensusLevel.MEDIUM else ModelConsensusLevel.HIGH,
                    minimum = 0.0,
                    maximum = next12hPrecipMm[index]?.plus(0.8),
                    isDivergent = index == 5
                )
            ),
            divergenceReasons = if (index == 5) setOf(DivergenceReason.PRECIPITATION) else emptySet()
        )
    }

    fun insights(): List<ForecastInsight> {
        val points = timelinePoints()
        return listOf(
            ForecastInsight(
                kind = ForecastInsightKind.RAIN_LIKELY,
                level = ForecastInsightLevel.WATCH,
                priority = 80,
                point = points[6],
                endPoint = points[8],
                eventPointCount = 3,
                value = 85,
                precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY
            ),
            ForecastInsight(
                kind = ForecastInsightKind.TEMPERATURE_CHANGE,
                level = ForecastInsightLevel.INFO,
                priority = 55,
                point = points[9],
                referencePoint = points[2],
                referenceValue = 24,
                targetValue = 21,
                value = -3
            ),
            ForecastInsight(
                kind = ForecastInsightKind.HIGH_AGREEMENT,
                level = ForecastInsightLevel.POSITIVE,
                priority = 45,
                point = points[2]
            )
        )
    }

    fun marineForecast(): MarineForecast {
        val start = BASE_INSTANT.toEpochMilli()
        val hourlyCount = 36
        val epochs = List(hourlyCount) { start + it * 3_600_000L }
        return MarineForecast(
            fetchedAtEpochMs = start - 8 * 60_000L,
            timezone = "Europe/Paris",
            grid = MarineGrid(latitude = 43.48, longitude = -1.57, distanceKm = 1.8),
            hourly = MarineHourly(
                timestamps = List(hourlyCount) { i -> LocalDateTime.ofInstant(Instant.ofEpochMilli(epochs[i]), ZoneOffset.UTC).toString() },
                timestampEpochMs = epochs.map(Long::toLong),
                waveHeight = List(hourlyCount) { i -> 1.0 + 0.45 * sin(i / 5.0) },
                waveDirection = List(hourlyCount) { i -> 270.0 + 12.0 * sin(i / 7.0) },
                wavePeriod = List(hourlyCount) { i -> 8.0 + 1.1 * sin(i / 8.0) },
                swellHeight = List(hourlyCount) { i -> 0.8 + 0.25 * sin(i / 6.0) },
                swellDirection = List(hourlyCount) { i -> 285.0 + 8.0 * sin(i / 6.0) },
                swellPeriod = List(hourlyCount) { i -> 10.0 + 0.8 * sin(i / 7.0) },
                seaSurfaceTemperature = List(hourlyCount) { i -> 21.4 + 0.3 * sin(i / 9.0) },
                seaLevelHeightMsl = List(hourlyCount) { i -> 0.7 * sin(i / 2.8) }
            ),
            daily = MarineDaily(
                dates = List(7) { BASE_DATE.plusDays(it.toLong()).toString() },
                waveHeightMax = listOf(1.6, 1.9, 2.2, 1.8, 1.4, 1.2, 1.5),
                waveDirectionDominant = listOf(275.0, 280.0, 286.0, 290.0, 285.0, 278.0, 272.0),
                wavePeriodMax = listOf(9.5, 10.1, 10.8, 10.0, 9.2, 8.7, 9.0),
                swellHeightMax = listOf(1.2, 1.4, 1.7, 1.4, 1.0, 0.9, 1.1),
                swellDirectionDominant = listOf(285.0, 288.0, 292.0, 294.0, 290.0, 284.0, 280.0),
                swellPeriodMax = listOf(11.0, 11.5, 12.0, 11.6, 10.8, 10.2, 10.5)
            ),
            usablePoints = hourlyCount,
            coastal = true
        )
    }

    fun reliability(
        variable: BiasVariable,
        score: Int,
        meanBias: Double,
        mae: Double,
        sampleSize: Int = 28
    ): ModelReliability = ModelReliability(
        variable = variable,
        score = score,
        level = when {
            score >= 85 -> ReliabilityLevel.EXCELLENT
            score >= 70 -> ReliabilityLevel.GOOD
            score >= 55 -> ReliabilityLevel.FAIR
            else -> ReliabilityLevel.LIMITED
        },
        meanBias = meanBias,
        meanAbsoluteError = mae,
        rootMeanSquareError = mae * 1.22,
        standardDeviation = mae * 1.35,
        withinToleranceRate = 0.78,
        overestimateRate = 0.47,
        underestimateRate = 0.42,
        closeRate = 0.78,
        overToleranceOverestimateRate = 0.12,
        underToleranceUnderestimateRate = 0.10,
        closeTolerance = when (variable) {
            BiasVariable.TEMPERATURE -> 1.5
            BiasVariable.PRECIPITATION -> 1.0
            BiasVariable.WIND_SPEED -> 5.0
        },
        sampleSize = sampleSize,
        windowDays = 30,
        recentMeanAbsoluteError = mae * 0.88,
        previousMeanAbsoluteError = mae * 1.08,
        trend = ReliabilityTrend.IMPROVING,
        precipitation = null
    )

    fun rankings(): LocalModelRankings {
        fun variable(variable: BiasVariable, base: Int): LocalVariableRanking = LocalVariableRanking(
            variable = variable,
            entries = models.mapIndexed { index, model ->
                LocalModelRankingEntry(
                    rank = index + 1,
                    model = model,
                    reliability = reliability(
                        variable = variable,
                        score = base - index * 7,
                        meanBias = when (variable) {
                            BiasVariable.TEMPERATURE -> listOf(0.2, -0.4, 0.6, -0.8)[index]
                            BiasVariable.PRECIPITATION -> listOf(0.1, 0.5, -0.2, 0.8)[index]
                            BiasVariable.WIND_SPEED -> listOf(-0.8, 1.2, -1.5, 2.0)[index]
                        },
                        mae = when (variable) {
                            BiasVariable.TEMPERATURE -> 0.8 + index * 0.2
                            BiasVariable.PRECIPITATION -> 1.1 + index * 0.35
                            BiasVariable.WIND_SPEED -> 3.2 + index * 0.8
                        }
                    )
                )
            }
        )
        return LocalModelRankings(
            temperature = variable(BiasVariable.TEMPERATURE, 91),
            precipitation = variable(BiasVariable.PRECIPITATION, 84),
            wind = variable(BiasVariable.WIND_SPEED, 87)
        )
    }

    fun biasSamples(modelOffset: Double = 0.0, variable: BiasVariable = BiasVariable.TEMPERATURE): List<BiasSample> =
        List(28) { i ->
            val observation = when (variable) {
                BiasVariable.TEMPERATURE -> 20.0 + 3.0 * sin(i / 4.0)
                BiasVariable.PRECIPITATION -> if (i % 4 == 0) 3.5 + (i % 3) else 0.2
                BiasVariable.WIND_SPEED -> 22.0 + 5.0 * sin(i / 3.0)
            }
            BiasSample(
                targetDate = BASE_DATE.minusDays((27 - i).toLong()),
                forecast = observation + modelOffset + sin(i / 2.0) * 0.35,
                observation = observation,
                issuedAt = BASE_INSTANT.minusSeconds((28L - i) * 86400L)
            )
        }

    fun biasScreenState(): BiasScreenState {
        fun variableState(variable: BiasVariable, domainMin: Double, domainMax: Double): VariableBiasState {
            val offsets = listOf(0.25, -0.35, 0.55, -0.65)
            val history = models.mapIndexed { index, model -> model to biasSamples(offsets[index], variable) }.toMap()
            val biases = models.mapIndexed { index, model ->
                model to ModelBias(
                    variable = variable,
                    meanBias = offsets[index],
                    stdDev = when (variable) {
                        BiasVariable.TEMPERATURE -> 0.55
                        BiasVariable.PRECIPITATION -> 0.85
                        BiasVariable.WIND_SPEED -> 2.8
                    },
                    sampleSize = 28
                )
            }.toMap()
            return VariableBiasState(biases, history, domainMin, domainMax)
        }
        return BiasScreenState(
            temperature = variableState(BiasVariable.TEMPERATURE, 15.0, 27.0),
            precipitation = variableState(BiasVariable.PRECIPITATION, 0.0, 8.0),
            wind = variableState(BiasVariable.WIND_SPEED, 0.0, 38.0)
        )
    }

    fun biasSelection(): BiasSelection {
        val reliability = reliability(BiasVariable.TEMPERATURE, 88, 0.35, 0.92)
        val samples = biasSamples(0.35, BiasVariable.TEMPERATURE)
        return BiasSelection(
            model = WeatherModel.ECMWF,
            bias = ModelBias(BiasVariable.TEMPERATURE, 0.35, 0.58, 28),
            reliability = reliability,
            localRank = ReliabilityRank(rank = 1, modelCount = 4),
            multiModelReliability = reliability(BiasVariable.TEMPERATURE, 79, -0.05, 1.18),
            dailyForecast = samples.map { it.forecast },
            dailyObservation = samples.map { it.observation },
            yDomainMin = 15.0,
            yDomainMax = 27.0
        )
    }

    fun evolutionReport(): ForecastEvolutionReport {
        val date = BASE_DATE.plusDays(1)
        val previous = listOf(
            ForecastEvolutionSnapshot(3, 23.1, models.associateWith { 23.1 + it.ordinal % 3 * 0.2 }, 72, BASE_INSTANT.minusSeconds(72 * 3600L)),
            ForecastEvolutionSnapshot(2, 23.8, models.associateWith { 23.8 + it.ordinal % 3 * 0.2 }, 48, BASE_INSTANT.minusSeconds(48 * 3600L)),
            ForecastEvolutionSnapshot(1, 24.4, models.associateWith { 24.4 + it.ordinal % 3 * 0.2 }, 24, BASE_INSTANT.minusSeconds(24 * 3600L))
        )
        val current = ForecastEvolutionSnapshot(0, 25.1, models.associateWith { 25.1 + it.ordinal % 3 * 0.2 }, 0, BASE_INSTANT)
        val revision = ForecastRevision(
            previousDaysAgo = 1,
            previousAgeHours = 24,
            medianDelta = 0.7,
            medianAbsoluteDelta = 0.7,
            increasedModels = 3,
            decreasedModels = 0,
            stableModels = 1,
            comparedModels = 4,
            deltasByModel = models.associateWith { 0.5 + (it.ordinal % 3) * 0.15 },
            trend = ForecastEvolutionTrend.INCREASING
        )
        val variable = VariableForecastEvolution(
            variable = ForecastEvolutionVariable.TEMPERATURE,
            targetDate = date,
            current = current,
            previous = previous,
            revision = revision
        )
        return ForecastEvolutionReport(
            days = listOf(DayForecastEvolution(date, mapOf(ForecastEvolutionVariable.TEMPERATURE to variable))),
            fetchedAt = BASE_INSTANT
        )
    }

    fun evolutionHighlight(): ForecastEvolutionHighlight = ForecastEvolutionHighlight(
        targetDate = BASE_DATE.plusDays(1),
        variable = ForecastEvolutionVariable.TEMPERATURE,
        trend = ForecastEvolutionTrend.INCREASING,
        medianDelta = 0.7,
        comparedModels = 4,
        dominantModels = 3,
        previousAgeHours = 24
    )

    fun engineComparisonDays(): List<EngineComparisonDay> = List(7) { index ->
        val date = BASE_DATE.plusDays(index.toLong())
        val values = ForecastEngine.entries.associateWith { engine ->
            val shift = engine.ordinal * 0.45
            EngineComparisonValues(
                tempMax = 25.0 + index * 0.6 + shift,
                tempMin = 15.0 + index * 0.3 + shift / 2,
                precipitationAmountMm = if (index in setOf(0, 3, 4)) 1.5 + shift else 0.0,
                precipitationProbabilityPercent = if (index in setOf(0, 3, 4)) 65 + engine.ordinal * 5 else 15,
                precipitationExpectedMm = if (index in setOf(0, 3, 4)) 1.0 + shift / 2 else 0.0,
                windKmh = 18.0 + index + shift,
                gustKmh = 31.0 + index * 1.4 + shift,
                cloudPercent = 40.0 + (index * 7 + engine.ordinal * 6) % 50,
                condition = if (index in setOf(0, 3, 4)) WeatherCondition.RAIN_SHOWERS else WeatherCondition.PARTLY_CLOUDY
            )
        }
        EngineComparisonDay(
            date = date,
            byEngine = values,
            divergence = EngineDivergence(
                score = 0.18 + index * 0.06,
                level = when {
                    index >= 5 -> EngineDivergenceLevel.HIGH
                    index >= 2 -> EngineDivergenceLevel.MEDIUM
                    else -> EngineDivergenceLevel.LOW
                },
                temperatureDelta = 1.4 + index * 0.2,
                precipitationDelta = if (index in setOf(0, 3, 4)) 1.1 + index * 0.2 else 0.2,
                windDelta = 3.5 + index * 0.5,
                cloudDelta = 12.0 + index * 2.0,
                conditionCount = if (index >= 5) 3 else 2
            )
        )
    }

    val hourlyStartTime: LocalDateTime = LocalDateTime.of(2026, 8, 28, 14, 0)
    val sunrise: LocalTime = LocalTime.of(7, 4)
    val sunset: LocalTime = LocalTime.of(20, 38)
}
