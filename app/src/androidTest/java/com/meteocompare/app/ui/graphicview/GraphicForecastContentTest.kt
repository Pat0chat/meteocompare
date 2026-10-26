package com.meteocompare.app.ui.graphicview

import androidx.compose.runtime.CompositionLocalProvider
import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.domain.model.ClockFormatPreference
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextEquals
import com.meteocompare.app.core.units.LocalWeatherUnits
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.domain.model.UnitSystem
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GraphicForecastContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun unit_switch_updates_temperature_rain_wind_and_gust_tooltips_without_changing_points() {
        val selection = mutableStateOf(UnitSystem.METRIC)
        val start = Instant.parse("2026-09-16T00:00:00Z")
        val points = List(3) { index -> SimplifiedTimelinePoint(
            instant = start.plusSeconds(index * 3600L), temperatureC = 0.0,
            temperatureMinAcrossModels = -1.0, temperatureMaxAcrossModels = 1.0,
            precipitationMm = 0.05, precipitationPercent = 75,
            windKmh = 16.09344, windGustKmh = 32.18688, windDirectionDeg = 90,
            modelCount = 3
        ) }
        val state = GraphicForecastUiState.Loaded(
            city = City(id = "unit-test", name = "Test", country = "Test", latitude = 0.0, longitude = 0.0, timezone = "UTC", countryCode = "GB"),
            points = points, solarByDate = emptyMap(), modelValuesByInstant = emptyMap(),
            vigilance = null, calculatedAt = start
        )
        val originalPoints = points.map { it.copy() }
        composeRule.setContent {
            CompositionLocalProvider(LocalWeatherUnits provides WeatherUnits(selection.value)) {
                MeteoCompareTheme { GraphicForecastContent(state) }
            }
        }
        val locale = Locale.getDefault()
        val gustLabel = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.graphic_view_gust_short)
        fun assertTooltips(temperature: String, range: String, rain: String, wind: String, gust: String) {
            composeRule.onNodeWithTag(TAG_GRAPHIC_TEMPERATURE_TOOLTIP_VALUE, useUnmergedTree = true)
                .assertTextEquals(temperature)
            composeRule.onNodeWithTag(TAG_GRAPHIC_TEMPERATURE_TOOLTIP_RANGE, useUnmergedTree = true)
                .assertTextEquals(range)
            composeRule.onNodeWithTag(TAG_GRAPHIC_RAIN_TOOLTIP_AMOUNT, useUnmergedTree = true)
                .assertTextEquals(rain)
            composeRule.onNodeWithTag(TAG_GRAPHIC_RAIN_TOOLTIP_PROBABILITY, useUnmergedTree = true)
                .assertTextEquals("75%")
            composeRule.onNodeWithTag(TAG_GRAPHIC_WIND_TOOLTIP_MEAN, useUnmergedTree = true)
                .assertTextEquals(wind)
            composeRule.onNodeWithTag(TAG_GRAPHIC_WIND_TOOLTIP_GUST, useUnmergedTree = true)
                .assertTextEquals("$gustLabel $gust")
        }
        val metricTemperature = String.format(locale, "%.1f °C", 0.0)
        val metricRange = String.format(locale, "%.1f–%.1f °C", -1.0, 1.0)
        val metricRain = String.format(locale, "%.1f mm", 0.1)
        assertTooltips(metricTemperature, metricRange, metricRain, "16 km/h", "32 km/h")
        composeRule.runOnIdle { selection.value = UnitSystem.IMPERIAL }
        assertTooltips(
            String.format(locale, "%.1f °F", 32.0),
            String.format(locale, "%.1f–%.1f °F", 30.2, 33.8),
            String.format(locale, "<%.2f in", 0.01),
            "10 mph", "20 mph"
        )
        composeRule.runOnIdle { selection.value = UnitSystem.METRIC }
        assertTooltips(metricTemperature, metricRange, metricRain, "16 km/h", "32 km/h")
        assertEquals(originalPoints, points)
    }

    @Test
    fun combined_rain_humidity_axis_keeps_precipitation_unit_inside_its_header() {
        val selectedUnits = mutableStateOf(UnitSystem.METRIC)
        val start = Instant.parse("2026-10-26T09:00:00Z")
        val state = GraphicForecastUiState.Loaded(
            city = City(id = "rain-axis", name = "Test", country = "Test",
                latitude = 0.0, longitude = 0.0, timezone = "UTC", countryCode = "GB"),
            points = List(3) { i -> SimplifiedTimelinePoint(
                instant = start.plusSeconds(i * 3600L), temperatureC = 14.0,
                precipitationMm = 1.2, humidityPercent = 87, modelCount = 3
            ) },
            solarByDate = emptyMap(), modelValuesByInstant = emptyMap(),
            calculatedAt = start
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalWeatherUnits provides WeatherUnits(selectedUnits.value)) {
                MeteoCompareTheme { GraphicForecastContent(state) }
            }
        }
        for (system in listOf(UnitSystem.METRIC, UnitSystem.IMPERIAL)) {
            composeRule.runOnIdle { selectedUnits.value = system }
            composeRule.waitForIdle()
            val header = composeRule.onNodeWithTag(
                TAG_GRAPHIC_RAIN_AXIS_HEADER, useUnmergedTree = true
            ).fetchSemanticsNode().boundsInRoot
            val unit = composeRule.onNodeWithTag(
                TAG_GRAPHIC_RAIN_AXIS_UNIT, useUnmergedTree = true
            )
            unit.assertTextEquals(
                WeatherUnits(system).label(com.meteocompare.app.core.units.WeatherUnit.PRECIPITATION_RATE)
            )
            val unitBounds = unit.fetchSemanticsNode().boundsInRoot
            // The rain symbol and its unit must fit in the narrow lane, not
            // disappear underneath the adjacent 0–100 % humidity graduation.
            assertTrue("Rain unit must stay inside the rain header ($system)",
                unitBounds.left >= header.left - 1f && unitBounds.right <= header.right + 1f)
            assertTrue("Rain unit must have visible width", unitBounds.width > 0f)
            assertTrue("Rain unit must stay below the icon and inside the header",
                unitBounds.top >= header.top - 1f && unitBounds.bottom <= header.bottom + 1f)
            val firstTick = composeRule.onNodeWithTag(
                TAG_GRAPHIC_RAIN_AXIS_TOP_TICK, useUnmergedTree = true
            ).fetchSemanticsNode().boundsInRoot
            assertTrue("Rain intensity tick must be completely below the rain header ($system)",
                firstTick.top >= header.bottom + 1f)
        }
    }

    @Test
    fun hourly_axis_respects_explicit_clock_preference_independent_of_language() {
        val points = listOf(0L, 1L, 12L, 16L).map { hour ->
            SimplifiedTimelinePoint(instant = Instant.parse("2026-08-29T00:00:00Z").plusSeconds(hour * 3600L))
        }
        val zone = java.time.ZoneId.of("UTC")
        val french = Locale.FRANCE
        val english = Locale.US
        val twelve = buildGraphicTimeAxisLabels(points, zone, french, ClockFormatPreference.HOUR_12)
        val twentyFour = buildGraphicTimeAxisLabels(points, zone, english, ClockFormatPreference.HOUR_24)
        assertEquals(listOf("12 AM", "1 AM", "12 PM", "4 PM"), twelve.hourLabels)
        assertEquals(listOf("00h", "01h", "12h", "16h"), twentyFour.hourLabels)
    }

    @Test
    fun ten_day_timeline_virtualizes_hours_and_keeps_weather_layers() {
        val start = Instant.parse("2026-09-16T00:00:00Z")
        val points = List(240) { index ->
            SimplifiedTimelinePoint(
                instant = start.plusSeconds(index * 3_600L),
                temperatureC = 12.0 + (index % 24) * 0.4,
                temperatureMinAcrossModels = 11.0 + (index % 24) * 0.4,
                temperatureMaxAcrossModels = 13.0 + (index % 24) * 0.4,
                precipitationPercent = (index * 7) % 100,
                precipitationMm = if (index % 9 == 0) 1.2 else 0.0,
                windKmh = 12.0 + index % 10,
                windGustKmh = 20.0 + index % 12,
                windDirectionDeg = (index * 15) % 360,
                pressureHpa = 1010.0 + index % 4,
                condition = when (index % 5) {
                    0 -> WeatherCondition.CLEAR
                    1 -> WeatherCondition.PARTLY_CLOUDY
                    2 -> WeatherCondition.RAIN
                    3 -> WeatherCondition.OVERCAST
                    else -> null // La vue doit quand même réserver/rendre un pictogramme UNKNOWN.
                },
                modelCount = 3,
                uvIndex = 5.4
            )
        }
        val state = GraphicForecastUiState.Loaded(
            city = City(
                id = "test-city",
                name = "Test",
                country = "Testland",
                latitude = 0.0,
                longitude = 0.0,
                timezone = "UTC",
                countryCode = "GB"
            ),
            points = points,
            solarByDate = emptyMap(),
            modelValuesByInstant = emptyMap(),
            vigilance = null,
            calculatedAt = start
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalClockFormatPreference provides ClockFormatPreference.HOUR_24) {
                MeteoCompareTheme {
                    GraphicForecastContent(state = state)
                }
            }
        }

        val composedHourCells = composeRule
            .onAllNodesWithTag(TAG_GRAPHIC_HOUR_CELL, useUnmergedTree = true)
            .fetchSemanticsNodes().size
        val composedDayHeaders = composeRule
            .onAllNodesWithTag(TAG_GRAPHIC_DAY_HEADER, useUnmergedTree = true)
            .fetchSemanticsNodes().size
        assertTrue(
            "La Chart View doit virtualiser les 240 heures et ne composer que le viewport + overscan",
            composedHourCells in 1 until 40
        )
        assertTrue(
            "Seuls les jours qui intersectent le viewport doivent être composés",
            composedDayHeaders in 1..3
        )
        // Les pictogrammes visibles sont dessinés dans un seul Canvas : la timeline
        // conserve 240 heures de contenu sans créer 240 sous-compositions.
        composeRule.onAllNodesWithTag(TAG_GRAPHIC_CONDITION_ICON, useUnmergedTree = true)
            .assertCountEquals(1)
        val legendSymbolCount = composeRule
            .onAllNodesWithTag(TAG_GRAPHIC_LEGEND_SYMBOL, useUnmergedTree = true)
            .fetchSemanticsNodes().size
        assertTrue(
            "La légende doit afficher au moins les 6 symboles permanents",
            legendSymbolCount >= 6
        )

        // Les libellés temporels suivent eux aussi la virtualisation : on
        // vérifie le début du viewport plutôt que 240 Text simultanés.
        composeRule.onAllNodesWithText("00h", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("01h", useUnmergedTree = true).assertCountEquals(1)

        composeRule.onNodeWithTag(TAG_GRAPHIC_CHART_PANEL, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_SELECTION_HEADER, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_UV_LANE).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_TEMPERATURE_PLOT).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_RAIN_PLOT).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_WIND_PLOT).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_PRESSURE_PLOT).assertExists()

        val panelBounds = composeRule
            .onNodeWithTag(TAG_GRAPHIC_CHART_PANEL, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val selectionBounds = composeRule
            .onNodeWithTag(TAG_GRAPHIC_SELECTION_HEADER, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val temperaturePlotBounds = composeRule
            .onNodeWithTag(TAG_GRAPHIC_TEMPERATURE_PLOT, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val conditionLaneBounds = composeRule
            .onNodeWithTag(TAG_GRAPHIC_UV_LANE, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Les UV doivent être entièrement au-dessus du graphique de température",
            conditionLaneBounds.bottom <= temperaturePlotBounds.top
        )
        val uvLabels = composeRule.onAllNodesWithTag("uv_compact", useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("Seuls les UV visibles doivent être composés", uvLabels.size in 2 until 40)
        assertTrue(
            "Les valeurs UV doivent être rendues dans la bande dédiée",
            uvLabels.any {
                it.boundsInRoot.top >= conditionLaneBounds.top &&
                    it.boundsInRoot.bottom <= conditionLaneBounds.bottom
            }
        )
        assertTrue(
            "Le résumé de l'heure sélectionnée et les graphiques doivent appartenir au même panneau visuel",
            selectionBounds.top >= panelBounds.top &&
                selectionBounds.bottom <= panelBounds.bottom &&
                temperaturePlotBounds.top >= panelBounds.top &&
                temperaturePlotBounds.bottom <= panelBounds.bottom
        )

        composeRule.onAllNodesWithTag(TAG_GRAPHIC_AXIS_ICON, useUnmergedTree = true)
            .assertCountEquals(4) // temperature, rain, wind, pressure
        composeRule.onNodeWithTag(TAG_GRAPHIC_PRESSURE_TOOLTIP, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("graphic_pressure_tooltip_value", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_TEMPERATURE_TOOLTIP, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_TEMPERATURE_TOOLTIP_VALUE, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_TEMPERATURE_TOOLTIP_RANGE, useUnmergedTree = true).assertExists()

        val temperatureValueBeforeTap = composeRule
            .onNodeWithTag(TAG_GRAPHIC_TEMPERATURE_TOOLTIP_VALUE, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text]
            .joinToString(separator = "") { it.text }
        composeRule
            .onNodeWithTag(TAG_GRAPHIC_TEMPERATURE_PLOT, useUnmergedTree = true)
            .performTouchInput { click(Offset(center.x, 8f)) }
        composeRule.waitForIdle()
        val temperatureValueAfterTap = composeRule
            .onNodeWithTag(TAG_GRAPHIC_TEMPERATURE_TOOLTIP_VALUE, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text]
            .joinToString(separator = "") { it.text }
        assertTrue(
            "Un tap dans la zone haute du graphe temperature doit repositionner le ruler",
            temperatureValueAfterTap != temperatureValueBeforeTap
        )

        composeRule.onNodeWithTag(TAG_GRAPHIC_RAIN_TOOLTIP, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_RAIN_TOOLTIP_AMOUNT, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_RAIN_TOOLTIP_PROBABILITY, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_WIND_TOOLTIP, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_WIND_TOOLTIP_MEAN, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_WIND_TOOLTIP_GUST, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_WIND_TOOLTIP_DIRECTION, useUnmergedTree = true).assertExists()

        val temperatureTooltipWidth = composeRule
            .onNodeWithTag(TAG_GRAPHIC_TEMPERATURE_TOOLTIP, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.width
        val windTooltipWidth = composeRule
            .onNodeWithTag(TAG_GRAPHIC_WIND_TOOLTIP, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.width
        assertTrue(
            "Les infobulles doivent s'adapter à leur contenu au lieu de partager une largeur fixe",
            windTooltipWidth > temperatureTooltipWidth
        )

        // Même optimisation pour les directions : un seul calque Canvas dessine
        // uniquement les flèches qui intersectent le viewport.
        composeRule.onAllNodesWithTag(TAG_GRAPHIC_WIND_DIRECTION_ARROW, useUnmergedTree = true)
            .assertCountEquals(1)
    }
}
