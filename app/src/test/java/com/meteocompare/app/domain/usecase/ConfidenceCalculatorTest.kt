package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ConfidenceLevel
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.domain.model.WeatherModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ConfidenceCalculatorTest {

    private lateinit var calculator: ConfidenceCalculator
    private val today: LocalDate = LocalDate.of(2026, 6, 23)

    private val paris = City(
        id = "1",
        name = "Paris",
        country = "France",
        latitude = 48.85,
        longitude = 2.35
    )

    @Before
    fun setUp() {
        // Tests avec EqualWeighting → résultats prédictibles (moyennes arithmétiques pures).
        calculator = ConfidenceCalculator(EqualWeighting())
    }

    // ──────────────────── Température ────────────────────

    @Test
    fun `temperature - tous les modèles convergent à 22 - confiance maximale`() {
        val forecast = buildForecast(
            tempMaxByModel = mapOf(
                WeatherModel.AROME_FRANCE_HD to 22.0,
                WeatherModel.ARPEGE_EUROPE to 22.0,
                WeatherModel.ICON_EU to 22.0,
                WeatherModel.GFS to 22.0,
                WeatherModel.ECMWF to 22.0
            )
        )

        val confidence = calculator.dayConfidence(forecast, today)

        assertNotNull(confidence.tempMax)
        assertEquals(100, confidence.tempMax!!.percent)
        assertEquals(22.0, confidence.tempMax.meanValue, 0.001)
        assertEquals(ConfidenceLevel.HIGH, confidence.tempMax.level)
    }

    @Test
    fun `temperature - faible spread - confiance haute`() {
        // Spread de 1°C : 21, 21.5, 22, 22.5, 23
        val forecast = buildForecast(
            tempMaxByModel = mapOf(
                WeatherModel.AROME_FRANCE_HD to 21.0,
                WeatherModel.ARPEGE_EUROPE to 21.5,
                WeatherModel.ICON_EU to 22.0,
                WeatherModel.GFS to 22.5,
                WeatherModel.ECMWF to 23.0
            )
        )

        val confidence = calculator.dayConfidence(forecast, today).tempMax!!

        assertEquals(22.0, confidence.meanValue, 0.001)
        assertEquals(21.0, confidence.minValue, 0.001)
        assertEquals(23.0, confidence.maxValue, 0.001)
        // σ ≈ 0.71 → entre tight (0.5) et wide (3.0), donc <100% mais > 50%
        assertTrue("Expected HIGH confidence, got ${confidence.percent}%", confidence.percent in 70..99)
    }

    @Test
    fun `temperature - forte divergence - confiance faible`() {
        // Spread de 12°C : 18, 22, 25, 28, 30
        val forecast = buildForecast(
            tempMaxByModel = mapOf(
                WeatherModel.AROME_FRANCE_HD to 18.0,
                WeatherModel.ARPEGE_EUROPE to 22.0,
                WeatherModel.ICON_EU to 25.0,
                WeatherModel.GFS to 28.0,
                WeatherModel.ECMWF to 30.0
            )
        )

        val confidence = calculator.dayConfidence(forecast, today).tempMax!!

        assertTrue("Expected LOW confidence, got ${confidence.percent}%", confidence.percent < 50)
        assertEquals(ConfidenceLevel.LOW, confidence.level)
    }

    @Test
    fun `temperature - un seul modèle - pas de confidence calculable`() {
        val forecast = buildForecast(
            tempMaxByModel = mapOf(WeatherModel.GFS to 22.0)
        )

        // Avec 1 seul modèle, pas d'écart-type significatif → on retourne null
        assertNull(calculator.dayConfidence(forecast, today).tempMax)
    }

    // ──────────────────── Pluie ────────────────────

    @Test
    fun `pluie - tous les modèles annoncent sec à 0mm - NoRain confiance 100`() {
        val forecast = buildForecast(
            precipByModel = mapOf(
                WeatherModel.AROME_FRANCE_HD to 0.0,
                WeatherModel.ICON_EU to 0.0,
                WeatherModel.GFS to 0.0
            )
        )

        val precip = calculator.dayConfidence(forecast, today).precipitation
        assertTrue(precip is PrecipitationConfidence.NoRain)
        precip as PrecipitationConfidence.NoRain
        assertEquals(100, precip.percent)
    }

    @Test
    fun `pluie - tous d'accord pour sec mais traces - NoRain confiance légèrement réduite`() {
        // Quelques traces de pluie (toutes < 1mm seuil) — pas vraiment de la pluie
        val forecast = buildForecast(
            precipByModel = mapOf(
                WeatherModel.AROME_FRANCE_HD to 0.0,
                WeatherModel.ICON_EU to 0.3,
                WeatherModel.GFS to 0.5
            )
        )

        val precip = calculator.dayConfidence(forecast, today).precipitation
        assertTrue(precip is PrecipitationConfidence.NoRain)
        assertEquals(90, (precip as PrecipitationConfidence.NoRain).percent)
    }

    @Test
    fun `pluie - tous annoncent pluie avec spread faible - Rain confiance haute`() {
        // Tous > 1mm seuil, avec spread étroit (σ ≈ 0.5mm)
        val forecast = buildForecast(
            precipByModel = mapOf(
                WeatherModel.AROME_FRANCE_HD to 2.0,
                WeatherModel.ICON_EU to 2.5,
                WeatherModel.GFS to 3.0,
                WeatherModel.ECMWF to 2.5
            )
        )

        val precip = calculator.dayConfidence(forecast, today).precipitation
        assertTrue(precip is PrecipitationConfidence.Rain)
        precip as PrecipitationConfidence.Rain
        assertEquals(2.0, precip.minMm, 0.001)
        assertEquals(3.0, precip.maxMm, 0.001)
        assertTrue("Expected high rain confidence, got ${precip.percent}%", precip.percent >= 70)
    }

    @Test
    fun `pluie - modèles divisés 3-2 - Divided`() {
        val forecast = buildForecast(
            precipByModel = mapOf(
                WeatherModel.AROME_FRANCE_HD to 3.0,
                WeatherModel.ARPEGE_EUROPE to 2.0,
                WeatherModel.ICON_EU to 1.5,
                WeatherModel.GFS to 0.0,
                WeatherModel.ECMWF to 0.2
            )
        )

        val precip = calculator.dayConfidence(forecast, today).precipitation
        assertTrue("Expected Divided, got $precip", precip is PrecipitationConfidence.Divided)
        precip as PrecipitationConfidence.Divided
        assertEquals(3, precip.modelsForRain)
        assertEquals(2, precip.modelsAgainstRain)
        // 3/5 = 60% d'agreement → (0.6 - 0.5) * 200 = 20%
        assertEquals(20, precip.percent)
    }

    @Test
    fun `pluie - division stricte 50-50 - confiance 0`() {
        val forecast = buildForecast(
            precipByModel = mapOf(
                WeatherModel.AROME_FRANCE_HD to 3.0,
                WeatherModel.ICON_EU to 2.0,
                WeatherModel.GFS to 0.0,
                WeatherModel.ECMWF to 0.0
            )
        )

        val precip = calculator.dayConfidence(forecast, today).precipitation
        assertTrue(precip is PrecipitationConfidence.Divided)
        assertEquals(0, (precip as PrecipitationConfidence.Divided).percent)
    }

    // ──────────────────── Alignement par date ────────────────────

    @Test
    fun `alignement - les modèles avec dates différentes sont correctement matchés`() {
        // AROME HD ne couvre que J et J+1, GFS couvre J, J+1, J+2
        val tomorrow = today.plusDays(1)
        val afterTomorrow = today.plusDays(2)

        val aromeSeries = ForecastSeries(
            model = WeatherModel.AROME_FRANCE_HD,
            hourly = emptyHourly(),
            daily = DailyForecast(
                dates = listOf(today, tomorrow),
                tempMax = listOf(22.0, 24.0),
                tempMin = listOf(15.0, 16.0),
                precipitationSum = listOf(0.0, 0.0),
                windSpeedMax = listOf(10.0, 12.0)
            )
        )
        val gfsSeries = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = emptyHourly(),
            daily = DailyForecast(
                dates = listOf(today, tomorrow, afterTomorrow),
                tempMax = listOf(23.0, 25.0, 27.0),
                tempMin = listOf(16.0, 17.0, 18.0),
                precipitationSum = listOf(0.0, 0.0, 5.0),
                windSpeedMax = listOf(11.0, 13.0, 15.0)
            )
        )
        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                WeatherModel.AROME_FRANCE_HD to aromeSeries,
                WeatherModel.GFS to gfsSeries
            )
        )

        // J : les deux modèles contribuent → confiance calculable
        val day0 = calculator.dayConfidence(forecast, today)
        assertNotNull(day0.tempMax)
        assertEquals(2, day0.tempMax!!.modelCount)

        // J+2 : seul GFS contribue → tempMax null (1 seul modèle)
        val day2 = calculator.dayConfidence(forecast, afterTomorrow)
        assertNull(day2.tempMax)
    }

    @Test
    fun `weeklyConfidence retourne un DayConfidence trié par date`() {
        val tomorrow = today.plusDays(1)
        val forecast = buildForecast(
            dates = listOf(today, tomorrow),
            tempMaxByModel = mapOf(
                WeatherModel.GFS to 22.0,
                WeatherModel.ICON_EU to 23.0
            )
        )

        val week = calculator.weeklyConfidence(forecast)
        assertEquals(2, week.size)
        assertEquals(today, week[0].date)
        assertEquals(tomorrow, week[1].date)
    }

    // ──────────────────── Confiance horaire ────────────────────

    @Test
    fun `hourly - convergence à 22 sur 3 instants - confiance maximale`() {
        val t0 = java.time.Instant.parse("2026-06-23T00:00:00Z")
        val forecast = buildHourlyForecast(
            timestamps = listOf(t0, t0.plusSeconds(3600), t0.plusSeconds(7200)),
            tempsByModel = mapOf(
                WeatherModel.AROME_FRANCE_HD to listOf(22.0, 22.0, 22.0),
                WeatherModel.ICON_EU to listOf(22.0, 22.0, 22.0),
                WeatherModel.GFS to listOf(22.0, 22.0, 22.0)
            )
        )

        val bands = calculator.hourlyTemperatureConfidence(forecast)
        assertEquals(3, bands.size)
        bands.forEach { band ->
            assertEquals(100, band.percent)
            assertEquals(0.0, band.spread, 0.001)
        }
    }

    @Test
    fun `hourly - bande s'élargit quand les modèles divergent`() {
        val t0 = java.time.Instant.parse("2026-06-23T00:00:00Z")
        val forecast = buildHourlyForecast(
            timestamps = listOf(t0, t0.plusSeconds(3600), t0.plusSeconds(7200)),
            tempsByModel = mapOf(
                // Spread initial 0 (tous à 20), spread 4 (18-22), spread 10 (15-25)
                WeatherModel.AROME_FRANCE_HD to listOf(20.0, 19.0, 17.0),
                WeatherModel.ICON_EU to listOf(20.0, 21.0, 23.0),
                WeatherModel.GFS to listOf(20.0, 22.0, 25.0)
            )
        )

        val bands = calculator.hourlyTemperatureConfidence(forecast)
        assertEquals(3, bands.size)

        // L'écart-type doit croître monotone (avec EqualWeighting)
        assertTrue(
            "Le spread doit augmenter dans le temps",
            bands[0].spread < bands[1].spread && bands[1].spread < bands[2].spread
        )

        // La confiance doit décroître
        assertTrue(
            "Confiance doit décroître : ${bands[0].percent} → ${bands[1].percent} → ${bands[2].percent}",
            bands[0].percent >= bands[1].percent && bands[1].percent >= bands[2].percent
        )
        assertEquals(100, bands[0].percent)
    }

    @Test
    fun `hourly - modèles à horizons différents - bande contient les instants couverts par au moins 2 modèles`() {
        val t0 = java.time.Instant.parse("2026-06-23T00:00:00Z")
        val t1 = t0.plusSeconds(3600)
        val t2 = t0.plusSeconds(7200)
        val t3 = t0.plusSeconds(10800)

        val aromeSeries = ForecastSeries(
            model = WeatherModel.AROME_FRANCE_HD,
            hourly = HourlyForecast(
                timestamps = listOf(t0, t1), // arrête à t1
                temperature2m = listOf(20.0, 21.0),
                precipitation = listOf(0.0, 0.0),
                windSpeed10m = listOf(10.0, 10.0)
            ),
            daily = emptyDaily()
        )
        val gfsSeries = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = HourlyForecast(
                timestamps = listOf(t0, t1, t2, t3),
                temperature2m = listOf(22.0, 23.0, 24.0, 25.0),
                precipitation = listOf(0.0, 0.0, 0.0, 0.0),
                windSpeed10m = listOf(15.0, 15.0, 15.0, 15.0)
            ),
            daily = emptyDaily()
        )
        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                WeatherModel.AROME_FRANCE_HD to aromeSeries,
                WeatherModel.GFS to gfsSeries
            )
        )

        val bands = calculator.hourlyTemperatureConfidence(forecast)
        // t0 et t1 : 2 modèles → bande générée
        // t2 et t3 : 1 modèle seul → exclus
        assertEquals(2, bands.size)
        assertEquals(t0, bands[0].timestamp)
        assertEquals(t1, bands[1].timestamp)
        bands.forEach { assertEquals(2, it.modelCount) }
    }

    // ──────────────────── Précipitation horaire ────────────────────

    @Test
    fun `hourly precip - modèles convergent à 0mm - confiance max`() {
        val t0 = java.time.Instant.parse("2026-06-23T00:00:00Z")
        val forecast = buildHourlyPrecipForecast(
            timestamps = listOf(t0, t0.plusSeconds(3600)),
            precipByModel = mapOf(
                WeatherModel.AROME_FRANCE_HD to listOf(0.0, 0.0),
                WeatherModel.ICON_EU to listOf(0.0, 0.0),
                WeatherModel.GFS to listOf(0.0, 0.0)
            )
        )

        val bands = calculator.hourlyPrecipitationConfidence(forecast)
        assertEquals(2, bands.size)
        // Convergence parfaite → confiance élevée (~100). Seuils PRECIP moins
        // stricts que TEMP mais 0 == 0 == 0 doit rester au max.
        bands.forEach {
            assertEquals(0.0, it.meanValue, 0.001)
            assertEquals(100, it.percent)
        }
    }

    @Test
    fun `hourly precip - désaccord entre modèles - confiance faible`() {
        val t0 = java.time.Instant.parse("2026-06-23T00:00:00Z")
        val forecast = buildHourlyPrecipForecast(
            timestamps = listOf(t0),
            precipByModel = mapOf(
                // Un modèle voit gros orage, les autres voient sec — cas
                // convectif classique.
                WeatherModel.AROME_FRANCE_HD to listOf(0.0),
                WeatherModel.ICON_EU to listOf(0.0),
                WeatherModel.GFS to listOf(15.0)
            )
        )

        val bands = calculator.hourlyPrecipitationConfidence(forecast)
        assertEquals(1, bands.size)
        // Fort désaccord → confiance nettement dégradée
        assertTrue(
            "Divergence forte doit dégrader la confiance : ${bands[0].percent}%",
            bands[0].percent < 80
        )
        assertEquals(0.0, bands[0].minValue, 0.001)
        assertEquals(15.0, bands[0].maxValue, 0.001)
    }

    @Test
    fun `hourly precip - modèle sans donnée précipitation - ignoré`() {
        val t0 = java.time.Instant.parse("2026-06-23T00:00:00Z")
        // Un modèle sans variable précipitation. Ne doit pas crasher — le null
        // filter du helper doit l'exclure proprement.
        val aromeSeries = ForecastSeries(
            model = WeatherModel.AROME_FRANCE_HD,
            hourly = HourlyForecast(
                timestamps = listOf(t0),
                temperature2m = listOf(20.0),
                precipitation = listOf(null),   // pas de donnée pluie
                windSpeed10m = listOf(10.0)
            ),
            daily = emptyDaily()
        )
        val gfsSeries = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = HourlyForecast(
                timestamps = listOf(t0),
                temperature2m = listOf(22.0),
                precipitation = listOf(2.0),
                windSpeed10m = listOf(12.0)
            ),
            daily = emptyDaily()
        )
        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                WeatherModel.AROME_FRANCE_HD to aromeSeries,
                WeatherModel.GFS to gfsSeries
            )
        )

        val bands = calculator.hourlyPrecipitationConfidence(forecast)
        // Un seul modèle avec la variable → pas de bande (règle : min 2 modèles)
        assertEquals(0, bands.size)
    }

    // ──────────────────── Vent horaire ────────────────────

    @Test
    fun `hourly wind - modèles convergent - confiance max`() {
        val t0 = java.time.Instant.parse("2026-06-23T00:00:00Z")
        val forecast = buildHourlyWindForecast(
            timestamps = listOf(t0, t0.plusSeconds(3600)),
            windByModel = mapOf(
                WeatherModel.AROME_FRANCE_HD to listOf(15.0, 15.0),
                WeatherModel.ICON_EU to listOf(15.0, 15.0),
                WeatherModel.GFS to listOf(15.0, 15.0)
            )
        )

        val bands = calculator.hourlyWindConfidence(forecast)
        assertEquals(2, bands.size)
        bands.forEach {
            assertEquals(15.0, it.meanValue, 0.001)
            assertEquals(100, it.percent)
        }
    }

    @Test
    fun `hourly wind - divergence progressive avec l'horizon`() {
        val t0 = java.time.Instant.parse("2026-06-23T00:00:00Z")
        val forecast = buildHourlyWindForecast(
            timestamps = listOf(t0, t0.plusSeconds(3600), t0.plusSeconds(7200)),
            windByModel = mapOf(
                // Spread 0 initial (tous à 15), puis divergence
                WeatherModel.AROME_FRANCE_HD to listOf(15.0, 14.0, 12.0),
                WeatherModel.ICON_EU to listOf(15.0, 16.0, 18.0),
                WeatherModel.GFS to listOf(15.0, 17.0, 22.0)
            )
        )

        val bands = calculator.hourlyWindConfidence(forecast)
        assertEquals(3, bands.size)
        // Spread croissant
        assertTrue(bands[0].spread < bands[1].spread && bands[1].spread < bands[2].spread)
        // Confiance décroissante
        assertTrue(bands[0].percent >= bands[1].percent && bands[1].percent >= bands[2].percent)
    }

    private fun buildHourlyPrecipForecast(
        timestamps: List<java.time.Instant>,
        precipByModel: Map<WeatherModel, List<Double>>
    ): CityForecast = buildHourlyMetricForecast(
        timestamps = timestamps,
        byModel = precipByModel,
        picker = HourlyMetric.PRECIP
    )

    private fun buildHourlyWindForecast(
        timestamps: List<java.time.Instant>,
        windByModel: Map<WeatherModel, List<Double>>
    ): CityForecast = buildHourlyMetricForecast(
        timestamps = timestamps,
        byModel = windByModel,
        picker = HourlyMetric.WIND
    )

    private enum class HourlyMetric { PRECIP, WIND }

    private fun buildHourlyMetricForecast(
        timestamps: List<java.time.Instant>,
        byModel: Map<WeatherModel, List<Double>>,
        picker: HourlyMetric
    ): CityForecast {
        val series = byModel.mapValues { (model, values) ->
            ForecastSeries(
                model = model,
                hourly = HourlyForecast(
                    timestamps = timestamps,
                    // Les variables non testées sont neutres pour ne pas
                    // interférer avec le calcul de la bande demandée.
                    temperature2m = List(values.size) { 20.0 },
                    precipitation = if (picker == HourlyMetric.PRECIP) values
                        else List(values.size) { 0.0 },
                    windSpeed10m = if (picker == HourlyMetric.WIND) values
                        else List(values.size) { 10.0 }
                ),
                daily = emptyDaily()
            )
        }
        return CityForecast(city = paris, seriesByModel = series)
    }

    private fun emptyDaily() = DailyForecast(
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList()
    )

    private fun buildHourlyForecast(
        timestamps: List<java.time.Instant>,
        tempsByModel: Map<WeatherModel, List<Double>>
    ): CityForecast {
        val series = tempsByModel.mapValues { (model, temps) ->
            ForecastSeries(
                model = model,
                hourly = HourlyForecast(
                    timestamps = timestamps,
                    temperature2m = temps,
                    precipitation = List(temps.size) { 0.0 },
                    windSpeed10m = List(temps.size) { 10.0 }
                ),
                daily = emptyDaily()
            )
        }
        return CityForecast(city = paris, seriesByModel = series)
    }

    // ──────────────────── Pondération ────────────────────

    @Test
    fun `pondération - InverseSqrtResolution donne plus de poids à AROME HD`() {
        val weighted = ConfidenceCalculator(InverseSqrtResolutionWeighting())

        // 4 modèles à 22°, AROME HD à 18° → la moyenne pondérée doit être tirée vers 18
        val forecast = buildForecast(
            tempMaxByModel = mapOf(
                WeatherModel.AROME_FRANCE_HD to 18.0, // weight ≈ 0.82
                WeatherModel.ARPEGE_EUROPE to 22.0,   // weight ≈ 0.30
                WeatherModel.ICON_EU to 22.0,         // weight ≈ 0.38
                WeatherModel.GFS to 22.0,             // weight ≈ 0.28
                WeatherModel.ECMWF to 22.0            // weight ≈ 0.20
            )
        )

        val weightedMean = weighted.dayConfidence(forecast, today).tempMax!!.meanValue
        val equalMean = calculator.dayConfidence(forecast, today).tempMax!!.meanValue

        // Moyenne arithmétique : (18 + 22*4) / 5 = 21.2
        assertEquals(21.2, equalMean, 0.01)

        // Moyenne pondérée : tirée vers 18 car AROME HD pèse plus
        assertTrue(
            "Weighted mean ($weightedMean) should be < equal mean ($equalMean)",
            weightedMean < equalMean
        )
    }

    // ──────────────────── Couverture nuageuse "maintenant" ────────────────────

    @Test
    fun `currentCloudCover - moyenne pondérée simple entre 2 modèles`() {
        // 2 modèles, weights égaux (EqualWeighting) → moyenne arithmétique.
        // 50% + 80% → 65%. Un timestamp calé pile sur "maintenant" pour que
        // le closest-to-now lookup soit déterministe (pas de dépendance à
        // Instant.now() qui bougerait pendant le test).
        val now = java.time.Instant.now()

        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                WeatherModel.ARPEGE_EUROPE to hourlyOnly(now, cloudCover = 50),
                WeatherModel.GFS to hourlyOnly(now, cloudCover = 80)
            )
        )

        assertEquals(65, calculator.currentCloudCover(forecast))
    }

    @Test
    fun `currentCloudCover - retourne null si aucun modèle ne fournit cloud_cover`() {
        // Cache pré-feature ou modèles sans la variable → null, l'UI cachera
        // le badge côté carte plutôt que d'afficher une valeur inventée.
        val now = java.time.Instant.now()

        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                WeatherModel.ARPEGE_EUROPE to hourlyOnly(now, cloudCover = null),
                WeatherModel.GFS to hourlyOnly(now, cloudCover = null)
            )
        )

        assertNull(calculator.currentCloudCover(forecast))
    }

    @Test
    fun `currentCloudCover - ignore les modèles sans cloud_cover et moyenne les autres`() {
        // Un modèle sans donnée ne DOIT PAS tirer la moyenne vers 0 en étant
        // compté avec cloud=0. Il doit être exclu du calcul — sinon un modèle
        // manquant biaiserait vers un ciel trop dégagé (danger éditorial).
        val now = java.time.Instant.now()

        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                WeatherModel.ARPEGE_EUROPE to hourlyOnly(now, cloudCover = 60),
                WeatherModel.GFS to hourlyOnly(now, cloudCover = null), // ignoré
                WeatherModel.ICON_EU to hourlyOnly(now, cloudCover = 80)
            )
        )

        // (60 + 80) / 2 = 70, PAS (60 + 0 + 80) / 3 = 46.67
        assertEquals(70, calculator.currentCloudCover(forecast))
    }

    // ──────────────────── Vitesse du vent "maintenant" ────────────────────

    @Test
    fun `currentWindSpeed - moyenne pondérée simple entre 2 modèles`() {
        // 10 km/h + 20 km/h avec EqualWeighting → 15 km/h.
        val now = java.time.Instant.now()

        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                WeatherModel.ARPEGE_EUROPE to hourlyOnlyWind(now, windKmh = 10.0),
                WeatherModel.GFS to hourlyOnlyWind(now, windKmh = 20.0)
            )
        )

        assertEquals(15.0, calculator.currentWindSpeed(forecast)!!, 0.001)
    }

    @Test
    fun `currentWindSpeed - retourne null si aucun modèle ne fournit windSpeed10m`() {
        // Cache pré-feature ou modèles sans la variable → null, l'UI cachera
        // le badge côté widget plutôt que d'afficher "0 km/h" trompeur.
        val now = java.time.Instant.now()

        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                WeatherModel.ARPEGE_EUROPE to hourlyOnlyWind(now, windKmh = null),
                WeatherModel.GFS to hourlyOnlyWind(now, windKmh = null)
            )
        )

        assertNull(calculator.currentWindSpeed(forecast))
    }

    @Test
    fun `currentWindSpeed - ignore les modèles sans donnée et moyenne les autres`() {
        // Symétrique du test cloud_cover : un modèle sans donnée ne DOIT PAS
        // être compté comme 0 km/h — biaiserait vers un temps trop calme.
        val now = java.time.Instant.now()

        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                WeatherModel.ARPEGE_EUROPE to hourlyOnlyWind(now, windKmh = 12.0),
                WeatherModel.GFS to hourlyOnlyWind(now, windKmh = null), // ignoré
                WeatherModel.ICON_EU to hourlyOnlyWind(now, windKmh = 18.0)
            )
        )

        // (12 + 18) / 2 = 15.0, PAS (12 + 0 + 18) / 3 = 10.0
        assertEquals(15.0, calculator.currentWindSpeed(forecast)!!, 0.001)
    }

    /**
     * Variante de [hourlyOnly] paramétrée sur windSpeed10m. Les autres variables
     * ont des valeurs neutres pour ne tester ici que le pipeline vent.
     */
    private fun hourlyOnlyWind(
        timestamp: java.time.Instant,
        windKmh: Double?
    ): ForecastSeries = ForecastSeries(
        model = WeatherModel.GFS, // ignoré
        hourly = HourlyForecast(
            timestamps = listOf(timestamp),
            temperature2m = listOf(20.0),
            precipitation = listOf(0.0),
            windSpeed10m = listOf(windKmh),
            cloudCover = listOf(50)
        ),
        daily = emptyDaily()
    )

    /**
     * Construit un ForecastSeries avec UNE heure de données à [timestamp].
     * Les autres variables sont mises à des valeurs neutres — on ne teste ici
     * que le pipeline cloudCover, pas les températures ou précipitations.
     */
    private fun hourlyOnly(
        timestamp: java.time.Instant,
        cloudCover: Int?
    ): ForecastSeries = ForecastSeries(
        model = WeatherModel.GFS, // ignoré, le model est fourni par la Map extérieure
        hourly = HourlyForecast(
            timestamps = listOf(timestamp),
            temperature2m = listOf(20.0),
            precipitation = listOf(0.0),
            windSpeed10m = listOf(5.0),
            cloudCover = listOf(cloudCover)
        ),
        daily = emptyDaily()
    )

    // ──────────────────── Helpers ────────────────────

    private fun emptyHourly() = HourlyForecast(emptyList(), emptyList(), emptyList(), emptyList())

    /**
     * Construit un CityForecast avec une seule date (today) et les valeurs de tempMax/precip
     * fournies. Les autres variables sont laissées vides.
     */
    private fun buildForecast(
        tempMaxByModel: Map<WeatherModel, Double> = emptyMap(),
        tempMinByModel: Map<WeatherModel, Double> = emptyMap(),
        precipByModel: Map<WeatherModel, Double> = emptyMap(),
        windMaxByModel: Map<WeatherModel, Double> = emptyMap(),
        dates: List<LocalDate> = listOf(today)
    ): CityForecast {
        val allModels = (tempMaxByModel.keys + tempMinByModel.keys +
            precipByModel.keys + windMaxByModel.keys).distinct()

        val series = allModels.associateWith { model ->
            ForecastSeries(
                model = model,
                hourly = emptyHourly(),
                daily = DailyForecast(
                    dates = dates,
                    tempMax = dates.map { tempMaxByModel[model] },
                    tempMin = dates.map { tempMinByModel[model] },
                    precipitationSum = dates.map { precipByModel[model] },
                    windSpeedMax = dates.map { windMaxByModel[model] }
                )
            )
        }

        return CityForecast(city = paris, seriesByModel = series)
    }
}
