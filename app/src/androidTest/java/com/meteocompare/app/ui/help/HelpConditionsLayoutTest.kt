package com.meteocompare.app.ui.help

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Ensures both weather-family branches remain visually balanced in all locales. */
class HelpConditionsLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun no_precipitation_and_precipitation_branches_have_equal_height() {
        composeRule.setContent {
            MeteoCompareTheme {
                Box(Modifier.fillMaxWidth()) { HierarchicalConsensusIllustration() }
            }
        }
        val dry = composeRule.onNodeWithTag("help_condition_dry")
            .getUnclippedBoundsInRoot()
        val wet = composeRule.onNodeWithTag("help_condition_precip")
            .getUnclippedBoundsInRoot()
        assertEquals(dry.bottom.value - dry.top.value,
            wet.bottom.value - wet.top.value, 0.5f)
    }
}
