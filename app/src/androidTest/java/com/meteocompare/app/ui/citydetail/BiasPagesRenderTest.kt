package com.meteocompare.app.ui.citydetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.ModelBias
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Vérifie le rendu final du classement et de la page de biais. */
class BiasPagesRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun ranking_page_displays_models_in_computed_order() {
        val state = variableState()
        val rankings = LocalModelRankings(
            temperature = buildLocalVariableRanking(BiasVariable.TEMPERATURE, state),
            precipitation = LocalVariableRanking(BiasVariable.PRECIPITATION, emptyList()),
            wind = LocalVariableRanking(BiasVariable.WIND_SPEED, emptyList())
        )

        composeRule.setContent {
            MeteoCompareTheme {
                LocalModelRankingSheet(
                    rankings = rankings,
                    cityLabel = "Paris",
                    initialVariable = BiasVariable.TEMPERATURE,
                    highlightedModel = WeatherModel.GFS,
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag(TAG_LOCAL_RANKING_SHEET).assertIsDisplayed()
        val gfsRow = composeRule.onNodeWithTag(localRankingRowTag(WeatherModel.GFS))
            .assertIsDisplayed()
        val ecmwfRow = composeRule.onNodeWithTag(localRankingRowTag(WeatherModel.ECMWF))
            .assertIsDisplayed()
        assertTrue(
            gfsRow.fetchSemanticsNode().boundsInRoot.top <
                ecmwfRow.fetchSemanticsNode().boundsInRoot.top
        )
        composeRule.onNodeWithText(WeatherModel.GFS.displayName).assertIsDisplayed()
        composeRule.onNodeWithText(WeatherModel.ECMWF.displayName).assertIsDisplayed()
        val gfsScore = rankings.temperature.entries.first { it.model == WeatherModel.GFS }
            .reliability.score
        composeRule.onNodeWithTag(localRankingScoreTag(WeatherModel.GFS))
            .assertTextEquals(gfsScore.toString())
            .assertIsDisplayed()
    }

    @Test
    fun variable_bias_page_displays_same_rank_score_and_common_sample_set() {
        val state = variableState()
        val selection = requireNotNull(
            buildBiasSelection(
                model = WeatherModel.GFS,
                variable = BiasVariable.TEMPERATURE,
                state = state
            )
        )

        val localRank = requireNotNull(selection.localRank)

        composeRule.setContent {
            MeteoCompareTheme {
                ModelBiasDetailSheet(selection = selection, onDismiss = {})
            }
        }

        composeRule.onNodeWithTag(TAG_MODEL_BIAS_DETAIL_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_MODEL_BIAS_SCORE)
            .assertTextEquals(selection.reliability.score.toString())
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(
                R.string.bias_reliability_rank,
                localRank.rank,
                localRank.modelCount
            )
        ).assertIsDisplayed()
        composeRule.onNodeWithText(formatBiasLabel(selection.bias)).assertIsDisplayed()
    }


    @Test
    fun variable_bias_page_renders_core_content_in_dark_theme() {
        val state = variableState()
        val selection = requireNotNull(
            buildBiasSelection(
                model = WeatherModel.GFS,
                variable = BiasVariable.TEMPERATURE,
                state = state
            )
        )

        composeRule.setContent {
            MeteoCompareTheme(
                themePreference = ThemePreference.DARK,
                dynamicColor = false
            ) {
                ModelBiasDetailSheet(selection = selection, onDismiss = {})
            }
        }

        composeRule.onNodeWithTag(TAG_MODEL_BIAS_DETAIL_SHEET).assertIsDisplayed()
        val headerNode = composeRule.onNodeWithTag(TAG_MODEL_BIAS_HEADER)
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val headerText = headerNode.config[SemanticsProperties.Text]
            .joinToString(separator = " ") { it.text }
        assertTrue(headerText.contains(WeatherModel.GFS.displayName))
        composeRule.onNodeWithTag(TAG_MODEL_BIAS_SCORE)
            .assertTextEquals(selection.reliability.score.toString())
            .assertIsDisplayed()
    }

    @Test
    fun precipitation_page_displays_rain_diagnostics_and_mm_bias() {
        val state = variableState(BiasVariable.PRECIPITATION)
        val selection = requireNotNull(
            buildBiasSelection(
                model = WeatherModel.GFS,
                variable = BiasVariable.PRECIPITATION,
                state = state
            )
        )

        composeRule.setContent {
            MeteoCompareTheme {
                ModelBiasDetailSheet(selection = selection, onDismiss = {})
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.bias_reliability_section_rain)
        )
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(formatBiasLabel(selection.bias))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun wind_page_displays_wind_score_and_bias() {
        val state = variableState(BiasVariable.WIND_SPEED)
        val selection = requireNotNull(
            buildBiasSelection(
                model = WeatherModel.GFS,
                variable = BiasVariable.WIND_SPEED,
                state = state
            )
        )

        composeRule.setContent {
            MeteoCompareTheme {
                ModelBiasDetailSheet(selection = selection, onDismiss = {})
            }
        }

        composeRule.onNodeWithTag(TAG_MODEL_BIAS_SCORE)
            .assertTextEquals(selection.reliability.score.toString())
            .assertIsDisplayed()
        composeRule.onNodeWithText(formatBiasLabel(selection.bias)).assertIsDisplayed()
    }

    private fun variableState(
        variable: BiasVariable = BiasVariable.TEMPERATURE
    ): VariableBiasState {
        val start = LocalDate.of(2026, 7, 21)
        fun history(error: Double): List<BiasSample> = List(14) { index ->
            val observation = when (variable) {
                BiasVariable.TEMPERATURE -> 20.0 + (index % 3)
                BiasVariable.PRECIPITATION -> if (index % 3 == 0) 4.0 else 0.0
                BiasVariable.WIND_SPEED -> 20.0 + (index % 5)
            }
            BiasSample(
                targetDate = start.plusDays(index.toLong()),
                forecast = observation + error,
                observation = observation
            )
        }
        return VariableBiasState(
            biasByModel = mapOf(
                WeatherModel.GFS to ModelBias(variable, 0.5, 0.0, 14),
                WeatherModel.ECMWF to ModelBias(variable, 1.5, 0.0, 14)
            ),
            historyByModel = mapOf(
                WeatherModel.GFS to history(0.5),
                WeatherModel.ECMWF to history(1.5)
            ),
            yDomainMin = 18.0,
            yDomainMax = 25.0
        )
    }
}
