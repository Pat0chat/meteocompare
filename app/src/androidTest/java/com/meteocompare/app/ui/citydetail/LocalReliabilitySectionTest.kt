package com.meteocompare.app.ui.citydetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LocalReliabilitySectionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun summary_displays_winners_and_opens_selected_variable() {
        var opened: BiasVariable? = null
        val rankings = rankings()

        composeRule.setContent {
            MeteoCompareTheme {
                LocalReliabilitySection(
                    overallConfidencePercent = 84,
                    modelCount = 3,
                    rankings = rankings,
                    tempBands = emptyList(),
                    precipBands = emptyList(),
                    windBands = emptyList(),
                    timezone = "Europe/Paris",
                    normals = null,
                    expanded = false,
                    onExpandedChange = {},
                    onOpenRanking = { opened = it }
                )
            }
        }

        composeRule.onNodeWithTag(TAG_LOCAL_RELIABILITY_CARD).assertIsDisplayed()
        composeRule.onNodeWithText("ECMWF").assertIsDisplayed()
        composeRule.onNodeWithText("GFS").performClick()

        assertEquals(BiasVariable.PRECIPITATION, opened)
    }

    @Test
    fun collapsing_hides_only_the_details_and_keeps_the_winners_summary() {
        val rankings = rankings()

        composeRule.setContent {
            MeteoCompareTheme {
                var expanded by remember { mutableStateOf(true) }
                LocalReliabilitySection(
                    overallConfidencePercent = 84,
                    modelCount = 3,
                    rankings = rankings,
                    tempBands = emptyList(),
                    precipBands = emptyList(),
                    windBands = emptyList(),
                    timezone = "Europe/Paris",
                    normals = null,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onOpenRanking = {}
                )
            }
        }

        composeRule.onNodeWithTag(TAG_LOCAL_RELIABILITY_DETAILS).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_LOCAL_RELIABILITY_HEADER).performClick()
        composeRule.onNodeWithTag(TAG_LOCAL_RELIABILITY_DETAILS).assertDoesNotExist()
        composeRule.onNodeWithText("ECMWF").assertIsDisplayed()
    }

    private fun rankings(): LocalModelRankings = buildLocalModelRankings(
        BiasScreenState(
            temperature = state(WeatherModel.ECMWF, 0.4),
            precipitation = state(WeatherModel.GFS, 0.3),
            wind = state(WeatherModel.ICON_GLOBAL, 1.0)
        )
    )

    private fun state(model: WeatherModel, error: Double): VariableBiasState =
        VariableBiasState(
            biasByModel = emptyMap(),
            historyByModel = mapOf(model to samples(error)),
            yDomainMin = null,
            yDomainMax = null
        )

    private fun samples(error: Double): List<BiasSample> = List(30) { index ->
        val observed = 10.0 + index % 2
        BiasSample(
            targetDate = LocalDate.of(2026, 1, 1).plusDays(index.toLong()),
            forecast = observed + error,
            observation = observed
        )
    }
}
