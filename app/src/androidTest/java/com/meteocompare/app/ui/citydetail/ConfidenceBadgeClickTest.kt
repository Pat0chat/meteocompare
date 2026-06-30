package com.meteocompare.app.ui.citydetail

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ConfidenceBadgeClickTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleDay = DayConfidence(
        date = LocalDate.of(2026, 6, 24),
        tempMax = ConfidenceScore(85, 21.0, 24.0, 22.5, 0.8, 5),
        tempMin = ConfidenceScore(78, 14.0, 17.0, 15.5, 1.0, 5),
        precipitation = PrecipitationConfidence.NoRain(100, 5, 0.0),
        windMax = ConfidenceScore(72, 12.0, 18.0, 15.0, 2.5, 5)
    )

    @Test
    fun badge_is_clickable_and_invokes_callback() {
        var clicked = false
        composeRule.setContent {
            MeteoCompareTheme {
                Surface {
                    TodaySummaryCard(
                        today = sampleDay,
                        modelCount = 5,
                        currentTemp = null,
                        onConfidenceClick = { clicked = true }
                    )
                }
            }
        }

        // overallPercent = mean(85, 78, 100, 72) = 83. Le contentDescription
        // du badge contient ce % suivi de "ouvrir l'explication détaillée".
        // Recherche par substring du sous-string stable au lieu de la string
        // entière (qui dépend de la locale du device qui exécute le test).
        composeRule
            .onNode(hasContentDescription("83", substring = true))
            .assertHasClickAction()
            .performClick()

        assertTrue("Le callback du badge devrait être invoqué", clicked)
    }
}
