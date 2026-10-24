package com.meteocompare.app.ui.citydetail

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.locale.WeatherTimeFormat
import com.meteocompare.app.domain.model.ClockFormatPreference
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.PrecipitationConfidence
import java.time.ZoneId
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TodaySummaryCardTest {
    @get:Rule val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(
        today: DayConfidence,
        modelCount: Int = 5,
        forecast: CityForecast? = null,
        uvIndex: Double? = null,
        apparent: Double? = null,
        currentTemp: Double? = null
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalClockFormatPreference provides ClockFormatPreference.HOUR_24) {
                MeteoCompareTheme {
                    Surface {
                        TodaySummaryCard(
                            today = today,
                            modelCount = modelCount,
                            currentTemp = currentTemp,
                            currentApparentTemp = apparent,
                            forecast = forecast,
                            uvIndex = uvIndex
                        )
                    }
                }
            }
        }
    }

    @Test
    fun today_summary_shows_estimated_felt_temperature_when_available() {
        render(today = DayConfidence(TestFixtures.today, null, null, null, null), currentTemp = 20.0, apparent = 16.0)
        val feels = composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_APPARENT, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.apparent_temperature_summary, "16°"))
            .getUnclippedBoundsInRoot()
        val temperature = composeRule.onNodeWithText("20°", useUnmergedTree = true)
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Feels-like must be to the right of the air temperature", feels.left >= temperature.right)
        assertTrue("Both values must stay on the same row", kotlin.math.abs(
            (feels.top.value + feels.bottom.value) / 2f -
                (temperature.top.value + temperature.bottom.value) / 2f
        ) <= 12f)
    }

    @Test
    fun feels_like_daily_extremes_use_the_existing_min_and_max_rails() {
        val zone = ZoneId.of("Europe/Paris")
        val start = TestFixtures.today.atStartOfDay(zone).toInstant()
        val end = TestFixtures.today.plusDays(1).atStartOfDay(zone).toInstant()
        val hours = java.time.Duration.between(start, end).toHours().toInt()
        val forecast = TestFixtures.forecast().copy(
            seriesByModel = TestFixtures.forecast().seriesByModel.mapValues { (model, series) ->
                series.copy(hourly = series.hourly.copy(
                    timestamps = List(hours) { start.plusSeconds(it * 3600L) },
                    apparentTemperature = List(hours) { 8.0 + it * 0.3 + model.ordinal * 0.2 }
                ))
            }
        )
        render(
            today = DayConfidence(
                date = TestFixtures.today,
                tempMax = ConfidenceScore(90, 23.0, 27.0, 25.0, 1.0, 3),
                tempMin = ConfidenceScore(85, 11.0, 15.0, 13.0, 1.0, 3),
                precipitation = null,
                windMax = null
            ),
            forecast = forecast
        )

        // Une seule barre de dispersion pour chaque extrême, avec le ressenti
        // intégré dans le même Canvas plutôt qu'en ligne de graphique séparée.
        composeRule.onAllNodesWithTag("today_apparent_min_inline_bar", useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onAllNodesWithTag("today_apparent_max_inline_bar", useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onNodeWithTag("today_apparent_min_central", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("today_apparent_max_central", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_TEMP_MIN_CENTRAL, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_TEMP_MAX_CENTRAL, useUnmergedTree = true).assertExists()

        // Les bornes de l'air sont au-dessus des rails; la valeur ressentie est
        // immédiatement sous son point. Aucun chevauchement, même proche.
        for (extreme in listOf("min", "max")) {
            val tag = "today_apparent_${extreme}"
            val rail = composeRule.onNodeWithTag("${tag}_inline_bar", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            val airLower = composeRule.onNodeWithTag("${tag}_air_lower", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            val airUpper = composeRule.onNodeWithTag("${tag}_air_upper", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            val feltValue = composeRule.onNodeWithTag("${tag}_central", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            assertTrue("Air bounds must be above the ${extreme} chart", airLower.bottom <= rail.top)
            assertTrue("Air bounds must be above the ${extreme} chart", airUpper.bottom <= rail.top)
            assertTrue("Felt value must be under the ${extreme} marker", feltValue.top >= rail.bottom)
            assertTrue(
                "Air bounds must never overlap",
                airLower.right <= airUpper.left || airLower.bottom <= airUpper.top
            )
            assertTrue("Felt value must not overflow left", feltValue.left >= rail.left)
            assertTrue("Felt value must not overflow right", feltValue.right <= rail.right)
        }
    }

    @Test
    fun today_summary_does_not_invent_missing_or_invalid_felt_temperature() {
        render(today = DayConfidence(TestFixtures.today, null, null, null, null), apparent = Double.NaN)
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_APPARENT, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun shows_solar_curve_with_api_sunrise_and_sunset() {
        val zone = ZoneId.of("Europe/Paris")
        val sunriseTime = TestFixtures.today.atTime(6, 42)
        val sunsetTime = TestFixtures.today.atTime(20, 18)
        val original = TestFixtures.forecast()
        val forecast = original.copy(
            seriesByModel = original.seriesByModel.mapValues { (_, series) ->
                series.copy(daily = series.daily.copy(
                    sunrise = listOf(sunriseTime.atZone(zone).toInstant()),
                    sunset = listOf(sunsetTime.atZone(zone).toInstant())
                ))
            }
        )
        render(
            today = DayConfidence(
                date = TestFixtures.today,
                tempMax = null,
                tempMin = null,
                precipitation = null,
                windMax = null
            ),
            forecast = forecast
        )
        val format = WeatherTimeFormat.clock(
            context.resources.configuration.locales[0], ClockFormatPreference.HOUR_24
        )
        composeRule.onNodeWithTag(TAG_DETAIL_SOLAR_CARD, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(sunriseTime.format(format), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(sunsetTime.format(format), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun uv_graph_is_below_sun_times_within_the_solar_section() {
        render(
            today = DayConfidence(
                date = TestFixtures.today,
                tempMax = null,
                tempMin = null,
                precipitation = null,
                windMax = null
            ),
            forecast = TestFixtures.forecast(),
            uvIndex = 6.5
        )

        val solar = composeRule.onNodeWithTag(TAG_DETAIL_SOLAR_CARD, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val sunset = composeRule.onNodeWithTag(TAG_DETAIL_SOLAR_SUNSET_ICON, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val uv = composeRule.onNodeWithTag("uv_gauge", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

        assertTrue("Le graphique UV doit être sous les heures solaires", uv.top > sunset.bottom)
        assertTrue("Le graphique UV doit rester dans la section solaire", uv.bottom <= solar.bottom)
        composeRule.onAllNodesWithTag("uv_gauge", useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onAllNodesWithTag(
            TAG_TODAY_SUMMARY_SECTION_DIVIDER, useUnmergedTree = true
        ).assertCountEquals(0)
    }

    @Test
    fun solar_section_omits_uv_graph_when_uv_is_missing() {
        render(
            today = DayConfidence(
                date = TestFixtures.today,
                tempMax = null,
                tempMin = null,
                precipitation = null,
                windMax = null
            ),
            forecast = TestFixtures.forecast()
        )
        composeRule.onNodeWithTag(TAG_DETAIL_SOLAR_CARD, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("uv_gauge", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun uv_graph_remains_visible_when_solar_forecast_is_missing() {
        render(
            today = DayConfidence(
                date = TestFixtures.today,
                tempMax = null,
                tempMin = null,
                precipitation = null,
                windMax = null
            ),
            uvIndex = 4.0
        )
        composeRule.onNodeWithTag(TAG_DETAIL_SOLAR_CARD, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag("uv_gauge", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun solar_sunrise_and_sunset_icons_align_with_curve_endpoints() {
        val zone = ZoneId.of("Europe/Paris")
        val original = TestFixtures.forecast()
        val forecast = original.copy(
            seriesByModel = original.seriesByModel.mapValues { (_, series) ->
                series.copy(daily = series.daily.copy(
                    sunrise = listOf(TestFixtures.today.atTime(6, 42).atZone(zone).toInstant()),
                    sunset = listOf(TestFixtures.today.atTime(20, 18).atZone(zone).toInstant())
                ))
            }
        )
        render(
            today = DayConfidence(
                date = TestFixtures.today,
                tempMax = null,
                tempMin = null,
                precipitation = null,
                windMax = null
            ),
            forecast = forecast
        )
        val curve = composeRule.onNodeWithTag(TAG_DETAIL_SOLAR_CURVE, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val sunrise = composeRule.onNodeWithTag(TAG_DETAIL_SOLAR_SUNRISE_ICON, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val sunset = composeRule.onNodeWithTag(TAG_DETAIL_SOLAR_SUNSET_ICON, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val sunriseCenter = (sunrise.left.value + sunrise.right.value) / 2f
        val sunsetCenter = (sunset.left.value + sunset.right.value) / 2f
        assertEquals(curve.left.value + 12f, sunriseCenter, 1.5f)
        assertEquals(curve.right.value - 12f, sunsetCenter, 1.5f)
    }

    @Test
    fun solar_uses_the_same_section_style_and_short_dividers_as_weather_metrics() {
        render(
            today = DayConfidence(
                date = TestFixtures.today,
                tempMax = ConfidenceScore(85, 21.0, 24.0, 22.5, 0.8, 5),
                tempMin = ConfidenceScore(78, 14.0, 17.0, 15.5, 1.0, 5),
                precipitation = PrecipitationConfidence.NoRain(100, 5, 0.0),
                windMax = ConfidenceScore(72, 12.0, 18.0, 15.0, 2.5, 5)
            ),
            forecast = TestFixtures.forecast()
        )

        composeRule.onNodeWithTag(TAG_DETAIL_SOLAR_CARD, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.detail_solar_title), useUnmergedTree = true
        ).assertExists()
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_TEMP_PANEL, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_PRECIP_PANEL, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_WIND_PANEL, useUnmergedTree = true).assertExists()
        // Solar + trois sections météo : trois séparateurs, centrés et plus courts.
        composeRule.onAllNodesWithTag(
            TAG_TODAY_SUMMARY_SECTION_DIVIDER, useUnmergedTree = true
        ).assertCountEquals(3)
        val sectionBounds = composeRule.onNodeWithTag(
            TAG_DETAIL_SOLAR_CARD, useUnmergedTree = true
        ).getUnclippedBoundsInRoot()
        val dividerBounds = composeRule.onAllNodesWithTag(
            TAG_TODAY_SUMMARY_SECTION_DIVIDER, useUnmergedTree = true
        )[0].getUnclippedBoundsInRoot()
        // getUnclippedBoundsInRoot() renvoie un DpRect : calculer la largeur
        // avec right - left (la propriété width n'existe pas sur DpRect).
        val sectionWidth = (sectionBounds.right - sectionBounds.left).value
        val dividerWidth = (dividerBounds.right - dividerBounds.left).value
        assertTrue(dividerWidth < sectionWidth * 0.85f)
        assertTrue(dividerWidth > sectionWidth * 0.65f)
    }

    @Test
    fun displays_all_present_variables() {
        render(
            DayConfidence(
                date = TestFixtures.today,
                tempMax = ConfidenceScore(85, 21.0, 24.0, 22.5, 0.8, 5),
                tempMin = ConfidenceScore(78, 14.0, 17.0, 15.5, 1.0, 5),
                precipitation = PrecipitationConfidence.NoRain(100, 5, 0.0),
                windMax = ConfidenceScore(72, 12.0, 18.0, 15.0, 2.5, 5),
                windGustMax = ConfidenceScore(68, 28.0, 36.0, 32.0, 3.2, 5)
            )
        )

        // Les trois nouvelles tuiles restent présentes sans sacrifier les valeurs.
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_TEMP_PANEL, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_PRECIP_PANEL, useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_WIND_PANEL, useUnmergedTree = true)
            .assertExists()

        composeRule.onNodeWithText(
            context.getString(R.string.models_analysed_many, 5),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.metric_temperature),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.today_summary_temp_min_short),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.today_summary_temp_max_short),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.metric_summary_range, "14.0° – 17.0°"),
            useUnmergedTree = true
        ).assertDoesNotExist()
        composeRule.onNodeWithText(
            context.getString(R.string.var_precipitation),
            useUnmergedTree = true
        ).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.metric_detail_wind),
            useUnmergedTree = true
        ).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.metric_detail_gusts),
            useUnmergedTree = true
        ).assertExists()
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_GUST_CENTRAL, useUnmergedTree = true)
            .assertTextEquals("32 km/h")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_PRECIP_CENTRAL, useUnmergedTree = true)
            .assertTextEquals("0.0 mm")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_PRECIP_CONVERGENCE, useUnmergedTree = true)
            .assertDoesNotExist()
        // Chaque frise possède son propre libellé « Convergence ». On cible donc
        // le pourcentage de la métrique attendue plutôt qu'un texte global ambigu.
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_TEMP_MAX_CONVERGENCE, useUnmergedTree = true)
            .assertTextEquals("85%")
    }

    @Test
    fun dispersion_uses_raw_model_values_from_forecast() {
        val forecast = TestFixtures.forecast()
        render(
            today = DayConfidence(
                date = TestFixtures.today,
                tempMax = null,
                tempMin = ConfidenceScore(
                    percent = 90,
                    minValue = 10.0,
                    maxValue = 20.0,
                    meanValue = 15.8,
                    stdDev = 0.6,
                    modelCount = 3
                ),
                precipitation = null,
                windMax = null
            ),
            modelCount = 3,
            forecast = forecast
        )

        // Les fixtures donnent 15.0 / 15.8 / 16.6 °C aujourd'hui. Les bornes
        // visibles de la frise doivent donc venir des modèles bruts, pas du
        // fallback 10–20 °C. Le range textuel et le nombre de modèles sont
        // volontairement supprimés pour compacter la card.
        composeRule.onNodeWithText("15.0°", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("16.6°", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.metric_summary_range, "15.0° – 16.6°"),
            useUnmergedTree = true
        ).assertDoesNotExist()
        composeRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.summary_dispersion_models, 3, 3),
            useUnmergedTree = true
        ).assertDoesNotExist()
    }

    @Test
    fun omits_absent_variables() {
        render(
            DayConfidence(
                date = TestFixtures.today,
                tempMax = ConfidenceScore(85, 21.0, 24.0, 22.5, 0.8, 3),
                tempMin = null,
                precipitation = null,
                windMax = null
            ),
            modelCount = 3
        )
        composeRule.onNodeWithText(
            context.getString(R.string.metric_temperature),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.today_summary_temp_max_short),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.today_summary_temp_min_short),
            useUnmergedTree = true
        ).assertDoesNotExist()
        composeRule.onNodeWithText(
            context.getString(R.string.var_precipitation),
            useUnmergedTree = true
        ).assertDoesNotExist()
        composeRule.onNodeWithText(
            context.getString(R.string.metric_detail_wind),
            useUnmergedTree = true
        ).assertDoesNotExist()
    }

    @Test
    fun rain_state_separates_probability_and_conditional_amount() {
        render(
            DayConfidence(
                date = TestFixtures.today,
                tempMax = null,
                tempMin = null,
                precipitation = PrecipitationConfidence.Rain(75, 5, 2.0, 6.0, 4.0),
                windMax = null
            )
        )
        composeRule.onNodeWithText(
            context.getString(R.string.metric_precip_probability_only, 100),
            substring = true,
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_PRECIP_CONVERGENCE, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithText(
            context.getString(R.string.metric_precip_if_rain, "4.0 mm"),
            substring = true,
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.metric_summary_range, "2.0 mm – 6.0 mm"),
            useUnmergedTree = true
        ).assertDoesNotExist()
    }

    @Test
    fun divided_state_shows_model_split() {
        render(
            DayConfidence(
                date = TestFixtures.today,
                tempMax = null,
                tempMin = null,
                precipitation = PrecipitationConfidence.Divided(
                    percent = 20,
                    modelCount = 5,
                    modelsForRain = 3,
                    modelsAgainstRain = 2,
                    rainMinMm = 1.5,
                    rainMaxMm = 3.0,
                    rainMeanMm = 2.2
                ),
                windMax = null
            )
        )
        composeRule.onNodeWithText(
            context.getString(R.string.metric_precip_if_rain, "2.2 mm"),
            substring = true,
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.metric_precip_models_rain, 3, 5),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.metric_precip_probability_only, 60),
            substring = true,
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_PRECIP_CONVERGENCE, useUnmergedTree = true)
            .assertDoesNotExist()
    }
    @Test
    fun dispersion_central_value_at_min_remains_prominent() {
        render(
            DayConfidence(
                date = TestFixtures.today,
                tempMax = null,
                tempMin = ConfidenceScore(
                    percent = 88,
                    minValue = 12.0,
                    maxValue = 18.0,
                    meanValue = 12.0,
                    stdDev = 1.2,
                    modelCount = 4
                ),
                precipitation = null,
                windMax = null
            ),
            modelCount = 4
        )

        // La valeur centrale est désormais portée par l'en-tête de la frise :
        // elle reste donc lisible même lorsqu'elle coïncide avec une borne.
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_TEMP_MIN_CENTRAL, useUnmergedTree = true)
            .assertTextEquals("12.0°")
            .assertIsDisplayed()
        composeRule.onNodeWithText("18.0°", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun dispersion_central_value_at_max_remains_prominent() {
        render(
            DayConfidence(
                date = TestFixtures.today,
                tempMax = ConfidenceScore(
                    percent = 88,
                    minValue = 20.0,
                    maxValue = 26.0,
                    meanValue = 26.0,
                    stdDev = 1.2,
                    modelCount = 4
                ),
                tempMin = null,
                precipitation = null,
                windMax = null
            ),
            modelCount = 4
        )

        // Même garantie lorsque le consensus coïncide avec la borne haute.
        composeRule.onNodeWithText("20.0°", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_TEMP_MAX_CENTRAL, useUnmergedTree = true)
            .assertTextEquals("26.0°")
            .assertIsDisplayed()
    }

    @Test
    fun gust_only_dispersion_uses_raw_gust_values_from_forecast() {
        val base = TestFixtures.forecast()
        val gustForecast = base.copy(
            seriesByModel = base.seriesByModel.entries.mapIndexed { modelIndex, (model, series) ->
                model to series.copy(
                    daily = series.daily.copy(
                        windSpeedMax = emptyList(),
                        windGustsMax = series.daily.dates.indices.map { 30.0 + modelIndex * 2.0 }
                    )
                )
            }.toMap()
        )

        render(
            today = DayConfidence(
                date = TestFixtures.today,
                tempMax = null,
                tempMin = null,
                precipitation = null,
                windMax = null,
                windGustMax = ConfidenceScore(
                    percent = 82,
                    minValue = 30.0,
                    maxValue = 34.0,
                    meanValue = 32.0,
                    stdDev = 1.6,
                    modelCount = 3
                )
            ),
            modelCount = 3,
            forecast = gustForecast
        )

        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_WIND_PANEL, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_TEMP_PANEL, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_PRECIP_PANEL, useUnmergedTree = true)
            .assertDoesNotExist()

        composeRule.onNodeWithText("30 km/h", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("34 km/h", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_TODAY_SUMMARY_WIND_CENTRAL, useUnmergedTree = true)
            .assertTextEquals("32 km/h")
            .assertIsDisplayed()
    }

}
