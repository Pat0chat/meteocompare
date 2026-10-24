package com.meteocompare.app.widget

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.R
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Teste les différents retours du GPS sans réclamer de permission réelle. */
class WidgetLocationRefreshContentTest {
    @get:Rule val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(status: Int?, inFlight: Boolean = false, pending: Boolean = false,
                       retry: () -> Unit = {}, close: () -> Unit = {}) {
        composeRule.setContent {
            MeteoCompareTheme {
                WidgetLocationRefreshContent(status, inFlight, pending, retry, close)
            }
        }
    }

    @Test fun searching_shows_progress_and_no_retry() {
        render(status = null, inFlight = true)
        composeRule.onNodeWithTag("widget_location_progress").assertExists()
        composeRule.onNodeWithTag("widget_location_retry").assertDoesNotExist()
        composeRule.onNodeWithTag("widget_location_status_title")
            .assertTextEquals(context.getString(R.string.widget_location_locating_title))
    }

    @Test fun permission_dialog_has_its_own_visible_message() {
        render(status = null, pending = true)
        composeRule.onNodeWithTag("widget_location_status_title")
            .assertTextEquals(context.getString(R.string.widget_location_permission_title))
        composeRule.onNodeWithTag("widget_location_progress").assertExists()
    }

    @Test fun success_is_explicit_and_closable() {
        var closed = 0
        render(status = R.string.current_location_updated, close = { closed++ })
        composeRule.onNodeWithTag("widget_location_status_title")
            .assertTextEquals(context.getString(R.string.widget_location_success_title))
        composeRule.onNodeWithTag("widget_location_progress").assertDoesNotExist()
        composeRule.onNodeWithTag("widget_location_retry").assertDoesNotExist()
        composeRule.onNodeWithTag("widget_location_close").performClick()
        assertEquals(1, closed)
    }

    @Test fun error_explains_the_issue_and_can_retry() {
        var retries = 0
        render(status = R.string.current_location_services_disabled, retry = { retries++ })
        composeRule.onNodeWithTag("widget_location_status_title")
            .assertTextEquals(context.getString(R.string.widget_location_error_title))
        composeRule.onNodeWithTag("widget_location_status_description")
            .assertTextEquals(context.getString(R.string.current_location_services_disabled))
        composeRule.onNodeWithTag("widget_location_retry").performClick()
        assertEquals(1, retries)
    }
}
