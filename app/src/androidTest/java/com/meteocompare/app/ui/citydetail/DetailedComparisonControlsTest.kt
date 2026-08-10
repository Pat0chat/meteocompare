package com.meteocompare.app.ui.citydetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.CityDetailContentTab
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DetailedComparisonControlsTest {
    @get:Rule val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun mode_menu_forwards_hourly_selection() {
        var selectedMode: DisplayMode? = null

        composeRule.setContent {
            MeteoCompareTheme {
                DetailedComparisonControls(
                    mode = DisplayMode.DAILY,
                    selectedTab = CityDetailContentTab.CONDITIONS,
                    onModeChange = { selectedMode = it },
                    onTabChange = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.forecast_tables_section))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.display_mode_daily))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.display_mode_hourly))
            .assertIsDisplayed()
            .performClick()

        assertEquals(DisplayMode.HOURLY, selectedMode)
    }

    @Test
    fun flat_variable_tabs_forward_selection() {
        var selectedTab: CityDetailContentTab? = null

        composeRule.setContent {
            MeteoCompareTheme {
                DetailedComparisonControls(
                    mode = DisplayMode.DAILY,
                    selectedTab = CityDetailContentTab.CONDITIONS,
                    onModeChange = {},
                    onTabChange = { selectedTab = it }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.detail_tab_wind))
            .assertIsDisplayed()
            .performClick()

        assertEquals(CityDetailContentTab.WIND, selectedTab)
    }
}
