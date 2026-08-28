package com.meteocompare.app.ui.citylist

import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime
import java.util.Locale

class SunTimesRowTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var previousLocale: Locale

    @Before
    fun forceDeterministicLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.FRANCE)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun normal_times_are_visible_and_accessible() {
        composeRule.setContent { SunTimesRow(LocalTime.of(6, 12), LocalTime.of(21, 45)) }
        composeRule.onNodeWithText("06:12").assertIsDisplayed()
        composeRule.onNodeWithText("21:45").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SUN_TIMES_ROW)
            .assertContentDescriptionContains("06:12", substring = true)
            .assertContentDescriptionContains("21:45", substring = true)
    }

    @Test
    fun missing_times_are_rendered_as_dashes_without_crashing() {
        composeRule.setContent { SunTimesRow(null, null) }
        composeRule.onAllNodesWithText("—").assertCountEquals(2)
    }

    @Test
    fun partial_data_keeps_the_available_time() {
        composeRule.setContent { SunTimesRow(null, LocalTime.of(18, 0)) }
        composeRule.onNodeWithText("—").assertIsDisplayed()
        composeRule.onNodeWithText("18:00").assertIsDisplayed()
    }
}
