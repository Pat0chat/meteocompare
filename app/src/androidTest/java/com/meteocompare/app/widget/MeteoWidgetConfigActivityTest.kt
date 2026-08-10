package com.meteocompare.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.meteocompare.app.testutil.FakeCityRepository
import com.meteocompare.app.testutil.TestFixtures
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class MeteoWidgetConfigActivityTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    @Inject lateinit var cities: FakeCityRepository
    private lateinit var scenario: ActivityScenario<MeteoWidgetConfigActivity>

    @Before
    fun setUp() {
        hiltRule.inject()
        cities.reset()
        cities.setFavorites(listOf(TestFixtures.paris, TestFixtures.lyon))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MeteoWidgetConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 4242)
            .putExtra(
                MeteoWidgetConfigActivity.EXTRA_DEBUG_PROVIDER_CLASS_NAME,
                MeteoWidgetReceiver2x1::class.java.name
            )
        scenario = ActivityScenario.launch(intent)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasTestTag("$TAG_WIDGET_CITY${TestFixtures.paris.id}")
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun tearDown() = scenario.close()

    @Test
    fun first_city_is_selected_and_save_is_enabled() {
        composeRule.onNodeWithTag("$TAG_WIDGET_CITY${TestFixtures.paris.id}").assertIsSelected()
        composeRule.onNodeWithTag(TAG_WIDGET_SAVE).assertIsEnabled()
        composeRule.onNodeWithTag("$TAG_WIDGET_MODE${ForecastMode.HOURLY.name}").assertIsSelected()
    }

    @Test
    fun city_selection_is_mutually_exclusive() {
        val parisTag = "$TAG_WIDGET_CITY${TestFixtures.paris.id}"
        val lyonTag = "$TAG_WIDGET_CITY${TestFixtures.lyon.id}"

        composeRule.onNodeWithTag(lyonTag).performClick()

        composeRule.onNodeWithTag(lyonTag).assertIsSelected()
        composeRule.onNodeWithTag(parisTag).assertIsNotSelected()
    }

    @Test
    fun forecast_modes_are_mutually_selectable() {
        val hourlyTag = "$TAG_WIDGET_MODE${ForecastMode.HOURLY.name}"
        val confidenceTag = "$TAG_WIDGET_MODE${ForecastMode.CONFIDENCE_ALL.name}"
        val miniTag = "$TAG_WIDGET_MODE${ForecastMode.MINI_FORECAST_12H.name}"

        composeRule.onNodeWithTag(confidenceTag).performScrollTo().performClick()
        composeRule.onNodeWithTag(confidenceTag).assertIsSelected()
        composeRule.onNodeWithTag(hourlyTag).assertIsNotSelected()

        composeRule.onNodeWithTag(miniTag).performScrollTo().performClick()
        composeRule.onNodeWithTag(miniTag).assertIsSelected()
        composeRule.onNodeWithTag(confidenceTag).assertIsNotSelected()
    }

    @Test
    fun cancel_closes_configuration_without_saving() {
        composeRule.onNodeWithTag(TAG_WIDGET_CANCEL).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            scenario.state == Lifecycle.State.DESTROYED
        }
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }
}
