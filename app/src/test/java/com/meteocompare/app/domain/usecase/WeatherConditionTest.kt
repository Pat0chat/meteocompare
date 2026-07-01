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
        calculator = ConfidenceCalculator(InverseSqrtResolutionWeighting())
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
        val now = Instant.now()
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
        assertNull(calculator.currentWeatherCondition(forecast))
    }

    @Test
    fun `currentWeatherCondition picks the weighted mode at the closest hour`() {
        val now = Instant.now()
        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                // AROME HD (1.5km) dit CLEAR — pèse lourd
                WeatherModel.AROME_FRANCE_HD to seriesWithWeatherCode(now, code = 0),
                // GFS (13km) dit RAIN — pèse moins
                WeatherModel.GFS to seriesWithWeatherCode(now, code = 61),
                // ECMWF (25km) dit CLEAR — encore moins
                WeatherModel.ECMWF to seriesWithWeatherCode(now, code = 0)
            )
        )
        // 2 modèles disent CLEAR (dont le plus pondéré AROME) → CLEAR gagne
        assertEquals(WeatherCondition.CLEAR, calculator.currentWeatherCondition(forecast))
    }

    @Test
    fun `currentWeatherCondition breaks ties towards the more severe condition`() {
        // Pour forcer une vraie égalité de poids, on injecte un calculateur
        // avec EqualWeighting → chaque modèle pèse 1.0, donc 1 modèle CLEAR
        // contre 1 modèle RAIN = égalité parfaite. Le tie-breaker doit alors
        // remonter la condition la plus sévère (ordinal max).
        val equalCalc = ConfidenceCalculator(EqualWeighting())
        val now = Instant.now()
        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                WeatherModel.ICON_EU to seriesWithWeatherCode(now, code = 0),    // CLEAR
                WeatherModel.ARPEGE_EUROPE to seriesWithWeatherCode(now, code = 61) // RAIN
            )
        )
        assertEquals(WeatherCondition.RAIN, equalCalc.currentWeatherCondition(forecast))
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
        // Scénario réel : AROME HD n'expose pas weather_code, seulement les
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
                weatherCode = emptyList() // AROME HD n'expose pas
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
