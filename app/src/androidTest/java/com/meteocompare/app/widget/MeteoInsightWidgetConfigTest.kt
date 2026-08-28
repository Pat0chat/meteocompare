package com.meteocompare.app.widget

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Rule
import org.junit.Test

class MeteoInsightWidgetConfigTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fixed_24h_horizon_is_shown_in_the_common_configuration_section() {
        composeRule.setContent {
            MeteoCompareTheme {
                FixedInsightHorizonRow()
            }
        }

        composeRule.onNodeWithTag(TAG_WIDGET_INSIGHT_HORIZON).assertIsDisplayed()
    }
}
