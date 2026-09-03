package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WeatherConditionTest {

    private lateinit var calculator: ConfidenceCalculator

    private val paris = City("1", "Paris", null, "France", 48.85, 2.35)

    @Before
    fun setUp() {
        calculator = ConfidenceCalculator(EqualWeighting())
    }

    // ─── WMO code mapping ────────────────────────────────────────────────────

    @Test
    fun `WMO 0 maps to CLEAR`() {
        assertEquals(WeatherCondition.CLEAR, WeatherCondition.fromWmoCode(0))
    }

    @Test
    fun `WMO 3 maps to OVERCAST`() {
        assertEquals(WeatherCondition.OVERCAST, WeatherCondition.fromWmoCode(3))
    }

    @Test
    fun `WMO drizzle codes group into DRIZZLE`() {
        listOf(51, 53, 55).forEach { code ->
            assertEquals(
                "code $code should map to DRIZZLE",
                WeatherCondition.DRIZZLE,
                WeatherCondition.fromWmoCode(code)
            )
        }
    }

    @Test
    fun `WMO freezing codes group into FREEZING_RAIN`() {
        listOf(56, 57, 66, 67).forEach { code ->
            assertEquals(
                "code $code should map to FREEZING_RAIN — distinct from RAIN (sécurité routière)",
                WeatherCondition.FREEZING_RAIN,
                WeatherCondition.fromWmoCode(code)
            )
        }
    }

    @Test
    fun `WMO null returns null`() {
        assertNull(WeatherCondition.fromWmoCode(null))
    }

    @Test
    fun `unmapped WMO code falls back to UNKNOWN`() {
        // Le code 4 n'existe pas dans le WMO 4677, ni 100 — on doit retourner
        // UNKNOWN plutôt que crasher, par robustesse au cas où Open-Meteo
        // étendrait la liste sans qu'on rafraîchisse le mapping.
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromWmoCode(4))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromWmoCode(100))
    }

    // ─── Fallback inférence depuis précip + temp ────────────────────────────

    @Test
    fun `inferFromPrecipAndTemp returns null when precip is null`() {
        assertNull(WeatherCondition.inferFromPrecipAndTemp(null, 15.0))
    }

    @Test
    fun `inferFromPrecipAndTemp returns null on dry days`() {
        // Choix éditorial : on ne peut pas distinguer clair vs couvert sans
        // donnée de nébulosité — mieux vaut afficher "—" que d'inventer.
        assertNull(WeatherCondition.inferFromPrecipAndTemp(0.0, 15.0))
    }

    @Test
    fun `inferFromPrecipAndTemp yields RAIN for heavy precip when warm`() {
        assertEquals(
            WeatherCondition.RAIN,
            WeatherCondition.inferFromPrecipAndTemp(precipMm = 10.0, tempMinC = 10.0)
        )
    }

    @Test
    fun `inferFromPrecipAndTemp yields SNOW for heavy precip when freezing`() {
        assertEquals(
            WeatherCondition.SNOW,
            WeatherCondition.inferFromPrecipAndTemp(precipMm = 10.0, tempMinC = -2.0)
        )
    }

    @Test
    fun `inferFromPrecipAndTemp yields DRIZZLE for light precip when warm`() {
        assertEquals(
            WeatherCondition.DRIZZLE,
            WeatherCondition.inferFromPrecipAndTemp(precipMm = 0.3, tempMinC = 12.0)
        )
    }

    @Test
    fun `inferFromPrecipAndTemp yields RAIN_SHOWERS for moderate precip`() {
        assertEquals(
            WeatherCondition.RAIN_SHOWERS,
            WeatherCondition.inferFromPrecipAndTemp(precipMm = 2.0, tempMinC = 8.0)
        )
    }

    @Test
    fun `inferFromPrecipAndTemp treats missing temp as warm`() {
        // Défaut = pas de gel — évite de basculer par erreur en SNOW
        // pour des jours d'été où tempMin serait absent des données.
        assertEquals(
            WeatherCondition.RAIN,
            WeatherCondition.inferFromPrecipAndTemp(precipMm = 8.0, tempMinC = null)
        )
    }

    // ─── currentWeatherCondition ─────────────────────────────────────────────

    @Test
    fun `currentWeatherCondition is null when no data allows any conclusion`() {
        // No weather_code AND no precip → ni le code natif ni le fallback
        // ne peuvent conclure. On veut null plutôt que d'inventer.
        val now = Instant.parse("2026-09-03T12:00:00Z")
        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                WeatherModel.GFS to ForecastSeries(
                    model = WeatherModel.GFS,
                    hourly = HourlyForecast(
                        timestamps = listOf(now),
                        temperature2m = listOf(15.0),
                        precipitation = listOf(0.0), // sec + pas de code = pas d'info
                        windSpeed10m = listOf(5.0),
                        weatherCode = emptyList() // pré-feature cache
                    ),
                    daily = emptyDaily()
                )
            )
        )
        assertNull(calculator.currentWeatherCondition(forecast, now))
    }

    @Test
    fun `currentWeatherCondition picks the weighted mode at the exact hour`() {
        val now = Instant.parse("2026-09-03T12:00:00Z")
        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                // AROME HD (1.5km) dit CLEAR — pèse lourd
                WeatherModel.AROME_FRANCE_HD to seriesWithWeatherCode(now, code = 0),
                // GFS (13km) dit RAIN — pèse moins
                WeatherModel.GFS to seriesWithWeatherCode(now, code = 61),
                // ECMWF IFS HRES (9 km) dit CLEAR — encore moins
                WeatherModel.ECMWF to seriesWithWeatherCode(now, code = 0)
            )
        )
        // 2 modèles disent CLEAR (dont le plus pondéré AROME) → CLEAR gagne
        assertEquals(WeatherCondition.CLEAR, calculator.currentWeatherCondition(forecast, now))
    }

    @Test
    fun `currentWeatherCondition refines dry WMO sky with robust cloud cover`() {
        val now = Instant.parse("2026-09-02T16:00:00Z")

        fun skySeries(model: WeatherModel, code: Int, cloud: Int) = ForecastSeries(
            model = model,
            hourly = HourlyForecast(
                timestamps = listOf(now),
                temperature2m = listOf(24.0),
                precipitation = listOf(0.0),
                precipitationProbability = listOf(0),
                windSpeed10m = listOf(8.0),
                weatherCode = listOf(code),
                cloudCover = listOf(cloud)
            ),
            daily = emptyDaily()
        )

        val forecast = CityForecast(
            city = paris,
            seriesByModel = linkedMapOf(
                WeatherModel.GFS to skySeries(WeatherModel.GFS, code = 3, cloud = 62),
                WeatherModel.ECMWF to skySeries(WeatherModel.ECMWF, code = 3, cloud = 60),
                WeatherModel.ICON_GLOBAL to skySeries(WeatherModel.ICON_GLOBAL, code = 2, cloud = 64)
            )
        )

        assertEquals(WeatherCondition.PARTLY_CLOUDY, calculator.currentWeatherCondition(forecast, now))
    }

    @Test
    fun `currentWeatherCondition breaks ties towards the more severe condition`() {
        // Pour forcer une vraie égalité de poids, on injecte un calculateur
        // avec EqualWeighting → chaque modèle pèse 1.0. Au premier niveau de
        // la hiérarchie, NON_PRECIPITATION et PRECIPITATION sont à égalité ;
        // le tie-break prudent retient alors la branche précipitation.
        val equalCalc = ConfidenceCalculator(EqualWeighting())
        val now = Instant.parse("2026-09-03T12:00:00Z")
        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                WeatherModel.ICON_EU to seriesWithWeatherCode(now, code = 0),    // CLEAR
                WeatherModel.ARPEGE_EUROPE to seriesWithWeatherCode(now, code = 61) // RAIN
            )
        )
        assertEquals(WeatherCondition.RAIN, equalCalc.currentWeatherCondition(forecast, now))
    }

    @Test
    fun `currentWeatherCondition protege le consensus WMO natif contre les fallbacks`() {
        val now = Instant.parse("2026-08-17T10:00:00Z")

        fun series(model: WeatherModel, code: Int?, precip: Double, cloud: Int): ForecastSeries = ForecastSeries(
            model = model,
            hourly = HourlyForecast(
                timestamps = listOf(now),
                temperature2m = listOf(18.0),
                precipitation = listOf(precip),
                windSpeed10m = listOf(8.0),
                weatherCode = listOf(code),
                cloudCover = listOf(cloud)
            ),
            daily = emptyDaily()
        )

        val forecast = CityForecast(
            paris,
            linkedMapOf(
                WeatherModel.GFS to series(WeatherModel.GFS, 61, 2.0, 95),
                WeatherModel.ECMWF to series(WeatherModel.ECMWF, 61, 2.0, 95),
                WeatherModel.ICON_GLOBAL to series(WeatherModel.ICON_GLOBAL, null, 0.0, 10),
                WeatherModel.UKMO_GLOBAL to series(WeatherModel.UKMO_GLOBAL, null, 0.0, 10),
                WeatherModel.GEM_GLOBAL to series(WeatherModel.GEM_GLOBAL, null, 0.0, 10)
            )
        )

        assertEquals(WeatherCondition.RAIN, calculator.currentWeatherCondition(forecast, now))
    }

    @Test
    fun `currentWeatherCondition infere AROME HD depuis cloud cover si weather code absent`() {
        val now = Instant.parse("2026-08-17T10:00:00Z")
        val arome = ForecastSeries(
            model = WeatherModel.AROME_FRANCE_HD,
            hourly = HourlyForecast(
                timestamps = listOf(now),
                temperature2m = listOf(20.0),
                precipitation = listOf(0.0),
                windSpeed10m = listOf(8.0),
                weatherCode = listOf(null),
                cloudCover = listOf(82)
            ),
            daily = emptyDaily()
        )

        val forecast = CityForecast(paris, mapOf(WeatherModel.AROME_FRANCE_HD to arome))

        assertEquals(WeatherCondition.PARTLY_CLOUDY, calculator.currentWeatherCondition(forecast, now))
    }

    // ─── dailyConditionsByModel ──────────────────────────────────────────────

    @Test
    fun `dailyConditionsByModel returns one row per date with all contributing models`() {
        val today = LocalDate.of(2026, 6, 30)
        val tomorrow = today.plusDays(1)

        val arome = ForecastSeries(
            model = WeatherModel.AROME_FRANCE_HD,
            hourly = emptyHourly(),
            daily = DailyForecast(
                dates = listOf(today, tomorrow),
                tempMax = listOf(22.0, 24.0),
                tempMin = listOf(15.0, 16.0),
                precipitationSum = listOf(0.0, 0.0),
                windSpeedMax = listOf(10.0, 12.0),
                weatherCode = listOf(0, 61) // clair J, pluie J+1
            )
        )
        val gfs = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = emptyHourly(),
            // GFS couvre 3 jours, AROME 2 → la dailyConditionsByModel doit
            // retourner 3 lignes, dont la 3e n'aura que GFS.
            daily = DailyForecast(
                dates = listOf(today, tomorrow, tomorrow.plusDays(1)),
                tempMax = listOf(23.0, 25.0, 27.0),
                tempMin = listOf(16.0, 17.0, 18.0),
                precipitationSum = listOf(0.0, 0.0, 5.0),
                windSpeedMax = listOf(11.0, 13.0, 15.0),
                weatherCode = listOf(1, 3, 95) // clair, couvert, orage
            )
        )
        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                WeatherModel.AROME_FRANCE_HD to arome,
                WeatherModel.GFS to gfs
            )
        )

        val rows = calculator.dailyConditionsByModel(forecast)
        assertEquals(3, rows.size)

        // J : les deux modèles ont des données
        assertEquals(WeatherCondition.CLEAR, rows[0].byModel[WeatherModel.AROME_FRANCE_HD])
        assertEquals(WeatherCondition.MAINLY_CLEAR, rows[0].byModel[WeatherModel.GFS])

        // J+1
        assertEquals(WeatherCondition.RAIN, rows[1].byModel[WeatherModel.AROME_FRANCE_HD])
        assertEquals(WeatherCondition.OVERCAST, rows[1].byModel[WeatherModel.GFS])

        // J+2 : seul GFS — la map ne doit pas avoir d'entrée AROME
        assertEquals(WeatherCondition.THUNDERSTORM, rows[2].byModel[WeatherModel.GFS])
        assertNull(rows[2].byModel[WeatherModel.AROME_FRANCE_HD])
    }

    @Test
    fun `dailyConditionsByModel skips days where no model has a weather code`() {
        // Cache pré-feature : weather_code vide partout. Mieux vaut ne RIEN
        // afficher qu'afficher 7 lignes de "—" qui font croire à une erreur.
        val today = LocalDate.of(2026, 6, 30)
        val arome = ForecastSeries(
            model = WeatherModel.AROME_FRANCE_HD,
            hourly = emptyHourly(),
            daily = DailyForecast(
                dates = listOf(today),
                tempMax = listOf(22.0),
                tempMin = listOf(15.0),
                precipitationSum = listOf(0.0),
                windSpeedMax = listOf(10.0),
                weatherCode = emptyList()
            )
        )
        val forecast = CityForecast(paris, mapOf(WeatherModel.AROME_FRANCE_HD to arome))

        val rows = calculator.dailyConditionsByModel(forecast)
        assertTrue("Aucune ligne ne doit ressortir d'un forecast sans weather_code", rows.isEmpty())
    }

    @Test
    fun `dailyConditionsByModel infers condition from precipitation when weather_code missing`() {
        // Cas de compatibilité : réponse partielle / ancien cache sans weather_code, avec les
        // variables physiques. Sur un jour pluvieux, le fallback doit fournir
        // RAIN pour qu'AROME HD apparaisse dans la matrice avec les autres.
        val today = LocalDate.of(2026, 6, 30)
        val aromeHd = ForecastSeries(
            model = WeatherModel.AROME_FRANCE_HD,
            hourly = emptyHourly(),
            daily = DailyForecast(
                dates = listOf(today),
                tempMax = listOf(20.0),
                tempMin = listOf(12.0),
                precipitationSum = listOf(8.0), // pluvieux
                windSpeedMax = listOf(15.0),
                weatherCode = emptyList() // champ absent du cache/réponse
            )
        )
        val gfs = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = emptyHourly(),
            daily = DailyForecast(
                dates = listOf(today),
                tempMax = listOf(21.0),
                tempMin = listOf(13.0),
                precipitationSum = listOf(5.0),
                windSpeedMax = listOf(14.0),
                weatherCode = listOf(63) // pluie modérée — code authoritatif
            )
        )
        val forecast = CityForecast(
            paris,
            mapOf(WeatherModel.AROME_FRANCE_HD to aromeHd, WeatherModel.GFS to gfs)
        )

        val rows = calculator.dailyConditionsByModel(forecast)
        assertEquals(1, rows.size)
        // AROME HD inféré depuis précip 8mm + temp min > 0 → RAIN
        assertEquals(WeatherCondition.RAIN, rows[0].byModel[WeatherModel.AROME_FRANCE_HD])
        // GFS depuis son code WMO 63 → RAIN
        assertEquals(WeatherCondition.RAIN, rows[0].byModel[WeatherModel.GFS])
    }

    // ─── fromCloudCover — fallback local au même modèle ───────────────────

    @Test
    fun `fromCloudCover renvoie CLEAR sous 20 pourcent`() {
        assertEquals(WeatherCondition.CLEAR, WeatherCondition.fromCloudCover(0.0))
        assertEquals(WeatherCondition.CLEAR, WeatherCondition.fromCloudCover(19.99))
    }

    @Test
    fun `fromCloudCover renvoie MAINLY_CLEAR entre 20 et 45 pourcent`() {
        assertEquals(WeatherCondition.MAINLY_CLEAR, WeatherCondition.fromCloudCover(20.0))
        assertEquals(WeatherCondition.MAINLY_CLEAR, WeatherCondition.fromCloudCover(30.0))
        assertEquals(WeatherCondition.MAINLY_CLEAR, WeatherCondition.fromCloudCover(44.99))
    }

    @Test
    fun `fromCloudCover renvoie PARTLY_CLOUDY entre 45 et 90 pourcent`() {
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.fromCloudCover(45.0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.fromCloudCover(55.0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.fromCloudCover(89.99))
    }

    @Test
    fun `fromCloudCover renvoie OVERCAST a partir de 90 pourcent`() {
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.fromCloudCover(85.0))
        assertEquals(WeatherCondition.OVERCAST, WeatherCondition.fromCloudCover(90.0))
        assertEquals(WeatherCondition.OVERCAST, WeatherCondition.fromCloudCover(100.0))
    }

    @Test
    fun `dailyConditionsByModel infere le ciel depuis le cloud cover du meme modele`() {
        val today = LocalDate.of(2026, 6, 30)
        val startOfDay = today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        val hourlyTs = listOf(
            startOfDay.plusSeconds(9 * 3600),
            startOfDay.plusSeconds(13 * 3600),
            startOfDay.plusSeconds(17 * 3600)
        )
        val aromeHd = ForecastSeries(
            model = WeatherModel.AROME_FRANCE_HD,
            hourly = HourlyForecast(
                timestamps = hourlyTs,
                temperature2m = listOf(18.0, 22.0, 20.0),
                precipitation = listOf(0.0, 0.0, 0.0),
                windSpeed10m = listOf(5.0, 5.0, 5.0),
                weatherCode = emptyList(),
                cloudCover = listOf(60, 60, 60)
            ),
            daily = DailyForecast(
                dates = listOf(today),
                tempMax = listOf(22.0),
                tempMin = listOf(14.0),
                precipitationSum = listOf(0.0),
                windSpeedMax = listOf(10.0),
                weatherCode = emptyList()
            )
        )
        val forecast = CityForecast(paris, mapOf(WeatherModel.AROME_FRANCE_HD to aromeHd))

        val row = calculator.dailyConditionsByModel(forecast).single()
        assertEquals(WeatherCondition.PARTLY_CLOUDY, row.byModel[WeatherModel.AROME_FRANCE_HD])
        assertTrue(WeatherModel.AROME_FRANCE_HD in row.inferredByModel)
        assertEquals(60, row.extrasByModel[WeatherModel.AROME_FRANCE_HD]?.cloudCoverMean)
    }

    @Test
    fun `dailyConditionsByModel ne copie jamais le cloud cover des autres modeles`() {
        val today = LocalDate.of(2026, 6, 30)
        val start = today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        val aromeHd = ForecastSeries(
            model = WeatherModel.AROME_FRANCE_HD,
            hourly = emptyHourly(),
            daily = DailyForecast(
                dates = listOf(today),
                tempMax = listOf(22.0),
                tempMin = listOf(14.0),
                precipitationSum = listOf(0.0),
                windSpeedMax = listOf(10.0),
                weatherCode = emptyList()
            )
        )
        val gfs = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = HourlyForecast(
                timestamps = listOf(start.plusSeconds(12 * 3600)),
                temperature2m = listOf(21.0),
                precipitation = listOf(0.0),
                windSpeed10m = listOf(8.0),
                cloudCover = listOf(95)
            ),
            daily = DailyForecast(
                dates = listOf(today),
                tempMax = listOf(22.0),
                tempMin = listOf(14.0),
                precipitationSum = listOf(0.0),
                windSpeedMax = listOf(10.0),
                weatherCode = listOf(3)
            )
        )
        val row = calculator.dailyConditionsByModel(
            CityForecast(paris, mapOf(WeatherModel.AROME_FRANCE_HD to aromeHd, WeatherModel.GFS to gfs))
        ).single()

        assertNull(row.byModel[WeatherModel.AROME_FRANCE_HD])
        assertEquals(WeatherCondition.OVERCAST, row.byModel[WeatherModel.GFS])
        assertTrue(WeatherModel.AROME_FRANCE_HD !in row.inferredByModel)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun emptyHourly() = HourlyForecast(
        emptyList(), emptyList(), emptyList(), emptyList()
    )

    private fun emptyDaily() = DailyForecast(
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList()
    )

    private fun seriesWithWeatherCode(at: Instant, code: Int) = ForecastSeries(
        model = WeatherModel.GFS, // ignoré pour le test (utilisé pour la pondération externe)
        hourly = HourlyForecast(
            timestamps = listOf(at),
            temperature2m = listOf(15.0),
            precipitation = listOf(0.0),
            windSpeed10m = listOf(5.0),
            weatherCode = listOf(code)
        ),
        daily = emptyDaily()
    )
}
