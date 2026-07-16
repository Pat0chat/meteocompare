package com.meteocompare.app.ui.citydetail

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConfidenceBadgeClickTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun badge_is_clickable_and_invokes_callback() {
        val sampleDay = DayConfidence(
            date = TestFixtures.today,
            tempMax = ConfidenceScore(85, 21.0, 24.0, 22.5, 0.8, 5),
            tempMin = ConfidenceScore(78, 14.0, 17.0, 15.5, 1.0, 5),
            precipitation = PrecipitationConfidence.NoRain(100, 5, 0.0),
            windMax = ConfidenceScore(72, 12.0, 18.0, 15.0, 2.5, 5)
        )
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
        composeRule.onNodeWithTag(TAG_CONFIDENCE_BADGE, useUnmergedTree = true)
            .assertHasClickAction()
            .performClick()
        assertTrue(clicked)
    }
}
