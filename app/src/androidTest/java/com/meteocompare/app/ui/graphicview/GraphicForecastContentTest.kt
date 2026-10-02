package com.meteocompare.app.ui.graphicview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsProperties
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
