package com.meteocompare.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.meteocompare.app.testutil.FakeCityRepository
import com.meteocompare.app.testutil.FakeClimateNormalsRepository
import com.meteocompare.app.testutil.FakeForecastRepository
import com.meteocompare.app.testutil.FakeUserPreferencesRepository
import com.meteocompare.app.testutil.TestFixtures
import com.meteocompare.app.ui.citydetail.TAG_CONFIDENCE_BADGE
import com.meteocompare.app.ui.citydetail.TAG_DETAIL_LOADED
import com.meteocompare.app.ui.citydetail.confidence.TAG_CONFIDENCE_EXPLANATION_BACK
import com.meteocompare.app.ui.citydetail.confidence.TAG_CONFIDENCE_EXPLANATION_ROOT
import com.meteocompare.app.ui.citylist.TAG_ADD_CITY_RESULT
import com.meteocompare.app.ui.citylist.TAG_ADD_FAB
import com.meteocompare.app.ui.citylist.TAG_ADD_CITY_SEARCH_FIELD
import com.meteocompare.app.ui.citylist.TAG_CITY_CARD
import com.meteocompare.app.ui.citylist.TAG_DONATE_BUTTON
import com.meteocompare.app.ui.citylist.TAG_EMPTY_STATE
import com.meteocompare.app.ui.citylist.TAG_SETTINGS_BUTTON
import com.meteocompare.app.ui.settings.TAG_SETTINGS_BACK
import com.meteocompare.app.ui.settings.TAG_SETTINGS_ROOT
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Parcours instrumentés de l'application complète.
 *
 * Les repositories sont remplacés par des fakes Hilt déterministes : aucun
 * réseau, aucune base persistante et aucune dépendance à l'état laissé par un
 * test précédent. Ces tests valident donc réellement navigation + ViewModels +
 * composition, sans flakiness liée à Open-Meteo ou à l'émulateur.
 */
@HiltAndroidTest
class MainActivityNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var cities: FakeCityRepository
    @Inject lateinit var forecasts: FakeForecastRepository
    @Inject lateinit var preferences: FakeUserPreferencesRepository
    @Inject lateinit var normals: FakeClimateNormalsRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        cities.reset()
        forecasts.reset()
        preferences.reset()
        normals.reset()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasTestTag(TAG_EMPTY_STATE)
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun launch_displays_empty_city_list() {
        composeRule.onNodeWithTag(TAG_EMPTY_STATE).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.empty_favorites_title))
            .assertIsDisplayed()
    }


    @Test
    fun home_donation_button_opens_shared_donation_dialog() {
        composeRule.onNodeWithTag(TAG_DONATE_BUTTON).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.donations_dialog_title)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.donations_dialog_close)
        ).performClick()
        composeRule.onNodeWithTag(TAG_EMPTY_STATE).assertIsDisplayed()
    }

    @Test
    fun settings_round_trip_keeps_city_list_available() {
        composeRule.onNodeWithTag(TAG_SETTINGS_BUTTON).performClick()
        composeRule.onNodeWithTag(TAG_SETTINGS_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SETTINGS_BACK).performClick()
        composeRule.onNodeWithTag(TAG_EMPTY_STATE).assertIsDisplayed()
    }

    @Test
    fun add_city_search_select_and_open_detail() {
        composeRule.onNodeWithTag(TAG_ADD_FAB).performClick()
        composeRule.onNodeWithTag(TAG_ADD_CITY_SEARCH_FIELD).performTextInput("Paris")

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasTestTag("$TAG_ADD_CITY_RESULT${TestFixtures.paris.id}")
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("$TAG_ADD_CITY_RESULT${TestFixtures.paris.id}").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasTestTag("$TAG_CITY_CARD${TestFixtures.paris.id}")
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("$TAG_CITY_CARD${TestFixtures.paris.id}").performClick()
        composeRule.onNodeWithTag(TAG_DETAIL_LOADED).assertIsDisplayed()
    }

    @Test
    fun detail_confidence_explanation_and_back_navigation() {
        cities.setFavorites(listOf(TestFixtures.paris))
        forecasts.setForecast(TestFixtures.paris)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasTestTag("$TAG_CITY_CARD${TestFixtures.paris.id}")
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("$TAG_CITY_CARD${TestFixtures.paris.id}").performClick()
        composeRule.onNodeWithTag(TAG_DETAIL_LOADED).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_CONFIDENCE_BADGE, useUnmergedTree = true).performClick()

        composeRule.onNodeWithTag(TAG_CONFIDENCE_EXPLANATION_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_CONFIDENCE_EXPLANATION_BACK).performClick()
        composeRule.onNodeWithTag(TAG_DETAIL_LOADED).assertIsDisplayed()
    }

    @Test
    fun removing_a_city_returns_to_empty_state() {
        cities.setFavorites(listOf(TestFixtures.paris))
        forecasts.setForecast(TestFixtures.paris)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasTestTag("$TAG_CITY_CARD${TestFixtures.paris.id}")
            ).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.action_more_options),
            useUnmergedTree = true
        ).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.action_remove_from_favorites))
            .performClick()

        composeRule.onNodeWithTag(TAG_EMPTY_STATE).assertIsDisplayed()
    }
}
