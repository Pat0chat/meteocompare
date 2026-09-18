package com.meteocompare.app.ui.graphicview

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GraphicForecastContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun seven_day_timeline_displays_every_hour_and_every_weather_condition() {
        val start = Instant.parse("2026-09-16T00:00:00Z")
        val points = List(168) { index ->
            SimplifiedTimelinePoint(
                instant = start.plusSeconds(index * 3_600L),
                temperatureC = 12.0 + (index % 24) * 0.4,
                temperatureMinAcrossModels = 11.0 + (index % 24) * 0.4,
                temperatureMaxAcrossModels = 13.0 + (index % 24) * 0.4,
                precipitationPercent = (index * 7) % 100,
                precipitationMm = if (index % 9 == 0) 1.2 else 0.0,
                precipitationAmountConvergencePercent = if (index == 0) 83 else 72,
                windKmh = 12.0 + index % 10,
                windGustKmh = 20.0 + index % 12,
                windDirectionDeg = (index * 15) % 360,
                condition = when (index % 5) {
                    0 -> WeatherCondition.CLEAR
                    1 -> WeatherCondition.PARTLY_CLOUDY
                    2 -> WeatherCondition.RAIN
                    3 -> WeatherCondition.OVERCAST
                    else -> null // La vue doit quand même réserver/rendre un pictogramme UNKNOWN.
                },
                modelCount = 3
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
            MeteoCompareTheme {
                GraphicForecastContent(state = state)
            }
        }

        composeRule.onAllNodesWithTag(TAG_GRAPHIC_HOUR_CELL, useUnmergedTree = true)
            .assertCountEquals(168)
        composeRule.onAllNodesWithTag(TAG_GRAPHIC_DAY_HEADER, useUnmergedTree = true)
            .assertCountEquals(7)
        composeRule.onAllNodesWithTag(TAG_GRAPHIC_CONDITION_ICON, useUnmergedTree = true)
            .assertCountEquals(168)
        val legendSymbolCount = composeRule
            .onAllNodesWithTag(TAG_GRAPHIC_LEGEND_SYMBOL, useUnmergedTree = true)
            .fetchSemanticsNodes().size
        assertTrue(
            "La légende doit afficher au moins les 6 symboles permanents",
            legendSymbolCount >= 6
        )

        // 168 h = exactement 7 occurrences de chacune des 24 heures.
        (0..23).forEach { hour ->
            composeRule.onAllNodesWithText("%02dh".format(hour), useUnmergedTree = true)
                .assertCountEquals(7)
        }

        composeRule.onNodeWithTag(TAG_GRAPHIC_CHART_PANEL, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_SELECTION_HEADER, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_RAIN_SELECTION_CONVERGENCE, useUnmergedTree = true)
            .assertTextContains("83%", substring = true)
        composeRule.onNodeWithTag(TAG_GRAPHIC_TEMPERATURE_PLOT).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_RAIN_PLOT).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_WIND_PLOT).assertExists()

        val panelBounds = composeRule
            .onNodeWithTag(TAG_GRAPHIC_CHART_PANEL, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val selectionBounds = composeRule
            .onNodeWithTag(TAG_GRAPHIC_SELECTION_HEADER, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val temperaturePlotBounds = composeRule
            .onNodeWithTag(TAG_GRAPHIC_TEMPERATURE_PLOT, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Le résumé de l'heure sélectionnée et les graphiques doivent appartenir au même panneau visuel",
            selectionBounds.top >= panelBounds.top &&
                    selectionBounds.bottom <= panelBounds.bottom &&
                    temperaturePlotBounds.top >= panelBounds.top &&
                    temperaturePlotBounds.bottom <= panelBounds.bottom
        )

        composeRule.onAllNodesWithTag(TAG_GRAPHIC_AXIS_ICON, useUnmergedTree = true)
            .assertCountEquals(3)
        composeRule.onNodeWithTag(TAG_GRAPHIC_TEMPERATURE_TOOLTIP, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_TEMPERATURE_TOOLTIP_VALUE, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_GRAPHIC_TEMPERATURE_TOOLTIP_RANGE, useUnmergedTree = true).assertExists()
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

        composeRule.onAllNodesWithTag(TAG_GRAPHIC_WIND_DIRECTION_ARROW, useUnmergedTree = true)
            .assertCountEquals(168)
    }
}
