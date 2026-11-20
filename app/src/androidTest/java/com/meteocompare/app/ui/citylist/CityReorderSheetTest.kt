package com.meteocompare.app.ui.citylist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CityReorderSheetTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun movingWithButtonsThenSavingProvidesTheNewOrder() {
        var result: List<String>? = null
        composeRule.setContent {
            MeteoCompareTheme {
                CityReorderSheet(
                    cities = listOf(TestFixtures.paris, TestFixtures.lyon),
                    onDismiss = {},
                    onSave = { result = it }
                )
            }
        }
        composeRule.onNodeWithTag(TAG_REORDER_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REORDER_DOWN + TestFixtures.paris.id).performClick()
        composeRule.onNodeWithTag(TAG_REORDER_SAVE).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(TestFixtures.lyon.id, TestFixtures.paris.id), result)
        }
    }

    @Test fun saveWithoutMovePreservesTheExistingOrder() {
        var result: List<String>? = null
        composeRule.setContent {
            MeteoCompareTheme {
                CityReorderSheet(
                    cities = listOf(TestFixtures.lyon, TestFixtures.paris),
                    onDismiss = {},
                    onSave = { result = it }
                )
            }
        }
        composeRule.onNodeWithTag(TAG_REORDER_SAVE).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(TestFixtures.lyon.id, TestFixtures.paris.id), result)
        }
    }
}
