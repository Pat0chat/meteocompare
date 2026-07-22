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

class LocalModelRankingSummaryCardTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun summary_displays_winners_and_opens_selected_variable() {
        var opened: BiasVariable? = null
        val rankings = buildLocalModelRankings(
            BiasScreenState(
                temperature = state(WeatherModel.ECMWF, 0.4),
                precipitation = state(WeatherModel.GFS, 0.3),
                wind = state(WeatherModel.ICON_GLOBAL, 1.0)
            )
        )

        composeRule.setContent {
            MeteoCompareTheme {
                LocalModelRankingSummaryCard(
                    rankings = rankings,
                    onOpenRanking = { opened = it }
                )
            }
        }

        composeRule.onNodeWithTag(TAG_LOCAL_RANKING_CARD).assertIsDisplayed()
        composeRule.onNodeWithText("ECMWF").assertIsDisplayed()
        composeRule.onNodeWithText("GFS").performClick()
        assertEquals(BiasVariable.PRECIPITATION, opened)
    }


    @Test
    fun summary_header_collapses_and_hides_winners() {
        val rankings = buildLocalModelRankings(
            BiasScreenState(
                temperature = state(WeatherModel.ECMWF, 0.4),
                precipitation = state(WeatherModel.GFS, 0.3),
                wind = state(WeatherModel.ICON_GLOBAL, 1.0)
            )
        )

        composeRule.setContent {
            MeteoCompareTheme {
                var expanded by remember { mutableStateOf(true) }
                LocalModelRankingSummaryCard(
                    rankings = rankings,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onOpenRanking = {}
                )
            }
        }

        composeRule.onNodeWithText("ECMWF").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_LOCAL_RANKING_HEADER).performClick()
        composeRule.onNodeWithText("ECMWF").assertDoesNotExist()
    }

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
