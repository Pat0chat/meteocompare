package com.meteocompare.app.widget

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.meteocompare.app.testutil.FakeCityRepository
import com.meteocompare.app.testutil.TestFixtures
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 29)
@HiltAndroidTest
class MeteoWidgetConfigActivityTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    @Inject lateinit var cities: FakeCityRepository
    private lateinit var scenario: ActivityScenario<MeteoWidgetConfigActivity>

    private lateinit var context: Context
    private lateinit var host: AppWidgetHost
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @Before
    fun setUp() {
        hiltRule.inject()
        cities.reset()
        cities.setFavorites(listOf(TestFixtures.paris, TestFixtures.lyon))
        context = ApplicationProvider.getApplicationContext()
        host = AppWidgetHost(context, 116042)
        widgetId = allocateWidget()
        launchConfiguration(widgetId)
    }

    private fun allocateWidget(): Int {
        val id = host.allocateAppWidgetId()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
        try {
            assertTrue(AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(
                id, ComponentName(context, MeteoWeatherWidgetReceiver::class.java)
            ))
        } finally {
            automation.dropShellPermissionIdentity()
        }
        return id
    }

    private fun launchConfiguration(id: Int) {
        val intent = Intent(context, MeteoWidgetConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        scenario = ActivityScenario.launch(intent)
        waitForForm()
    }

    private fun waitForForm() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasTestTag("$TAG_WIDGET_CITY${TestFixtures.paris.id}")
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        if (::host.isInitialized) host.deleteHost()
    }

    @Test
    fun current_conditions_toggle_is_enabled_by_default() {
        composeRule.onNodeWithTag(TAG_WIDGET_SHOW_CURRENT_CONDITIONS)
            .performScrollTo().assertIsOn()
    }

    @Test
    fun hidden_current_conditions_are_saved_and_restored_for_this_widget() {
        val toggle = composeRule.onNodeWithTag(TAG_WIDGET_SHOW_CURRENT_CONDITIONS)
        toggle.performScrollTo().assertIsOn().performClick().assertIsOff()
        // Editing must not save before pressing Save.
        assertEquals(true, WidgetConfiguration.fromPreferences(readPreferences(widgetId)).showCurrentConditions)
        composeRule.onNodeWithTag(TAG_WIDGET_SAVE).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { scenario.state == Lifecycle.State.DESTROYED }
        assertEquals(false, WidgetConfiguration.fromPreferences(readPreferences(widgetId)).showCurrentConditions)
        launchConfiguration(widgetId)
        composeRule.onNodeWithTag(TAG_WIDGET_SHOW_CURRENT_CONDITIONS)
            .performScrollTo().assertIsOff()
    }

    @Test
    fun saving_configuration_marks_one_dispatch_and_preserves_fifteen_minute_guard() {
        composeRule.onNodeWithTag(TAG_WIDGET_SAVE).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            scenario.state == Lifecycle.State.DESTROYED
        }
        val prefs = readPreferences(widgetId)
        val dispatch = prefs[WidgetPreferences.LastDispatchAtKey] ?: 0L
        val tick = prefs[WidgetPreferences.RefreshTickKey] ?: 0L
        assertTrue("La configuration doit enregistrer le rendu", dispatch > 0L)
        assertTrue("Un tick doit forcer la lecture des nouvelles préférences", tick > 0L)
        assertEquals(false, isWidgetDispatchDue(dispatch, dispatch, force = false))
    }

    @Test
    fun cancel_does_not_persist_a_current_conditions_toggle() {
        composeRule.onNodeWithTag(TAG_WIDGET_SHOW_CURRENT_CONDITIONS)
            .performScrollTo().performClick().assertIsOff()
        composeRule.onNodeWithTag(TAG_WIDGET_CANCEL).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { scenario.state == Lifecycle.State.DESTROYED }
        assertEquals(true, WidgetConfiguration.fromPreferences(readPreferences(widgetId)).showCurrentConditions)
    }

    @Test
    fun rotation_restores_the_unsaved_visibility_choice() {
        composeRule.onNodeWithTag(TAG_WIDGET_SHOW_CURRENT_CONDITIONS)
            .performScrollTo().performClick().assertIsOff()
        scenario.recreate()
        waitForForm()
        composeRule.onNodeWithTag(TAG_WIDGET_SHOW_CURRENT_CONDITIONS)
            .performScrollTo().assertIsOff()
        assertEquals(true, WidgetConfiguration.fromPreferences(readPreferences(widgetId)).showCurrentConditions)
    }

    @Test
    fun resize_guidance_is_visible_before_the_weather_widget_configuration() {
        composeRule.onNodeWithTag(TAG_WIDGET_RESIZE_TIP)
            .assertIsDisplayed()
            .assertTextContains(context.getString(com.meteocompare.app.R.string.widget_resize_tip_body))
    }

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

    @Test
    fun reopening_and_saving_preserves_every_setting_of_the_widget() {
        reopenSavedWidget()
        assertSavedSelections()
        composeRule.onNodeWithTag(TAG_WIDGET_SAVE).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            scenario.state == Lifecycle.State.DESTROYED
        }
        assertEquals(savedConfiguration, WidgetConfiguration.fromPreferences(readPreferences(widgetId)))
    }

    @Test
    fun rotation_preserves_unsaved_changes() {
        reopenSavedWidget()
        composeRule.onNodeWithTag("$TAG_WIDGET_CITY${TestFixtures.paris.id}").performClick()
        composeRule.onNodeWithTag(TAG_WIDGET_OPACITY).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        composeRule.onNodeWithTag("$TAG_WIDGET_MODE${ForecastMode.HOURLY.name}")
            .performScrollTo().performClick()
        scenario.recreate()
        waitForForm()
        composeRule.onNodeWithTag("$TAG_WIDGET_CITY${TestFixtures.paris.id}").assertIsSelected()
        assertOpacity(60f)
        composeRule.onNodeWithTag("$TAG_WIDGET_MODE${ForecastMode.HOURLY.name}").assertIsSelected()
        // Editing a draft must not write to the widget before Save.
        assertEquals(savedConfiguration, WidgetConfiguration.fromPreferences(readPreferences(widgetId)))
    }

    @Test
    fun tapping_opacity_input_grants_edit_focus() {
        composeRule.onNodeWithTag(TAG_WIDGET_OPACITY_INPUT)
            .performScrollTo().performClick().assertIsFocused()
    }

    @Test
    fun tapping_color_hex_grants_edit_focus_in_dialog() {
        composeRule.onNodeWithTag(TAG_WIDGET_CUSTOM_BG).performScrollTo().performClick()
        composeRule.onNodeWithTag(TAG_WIDGET_COLOR_HEX)
            .performClick().assertIsFocused()
    }

    @Test
    fun hex_color_and_one_percent_opacity_survive_reopening() {
        reopenSavedWidget()
        composeRule.onNodeWithTag(TAG_WIDGET_CUSTOM_BG).performScrollTo().performClick()
        composeRule.onNodeWithTag(TAG_WIDGET_COLOR_HEX).performTextReplacement("#A13F7C")
        composeRule.onNodeWithTag(TAG_WIDGET_COLOR_APPLY).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(TAG_WIDGET_OPACITY_INPUT).performScrollTo()
            .performTextReplacement("37")
        // The previously stored preferences are untouched until Save.
        assertEquals(savedConfiguration, WidgetConfiguration.fromPreferences(readPreferences(widgetId)))
        composeRule.onNodeWithTag(TAG_WIDGET_SAVE).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            scenario.state == Lifecycle.State.DESTROYED
        }
        val saved = WidgetConfiguration.fromPreferences(readPreferences(widgetId))
        assertEquals(0xFFA13F7C.toInt(), saved.backgroundColorArgb)
        assertEquals(37, saved.opacityPct)
        assertEquals(savedConfiguration.cornerStyle, saved.cornerStyle)
        assertEquals(savedConfiguration.cityId, saved.cityId)
        launchConfiguration(widgetId)
        composeRule.onNodeWithTag(TAG_WIDGET_OPACITY_INPUT).performScrollTo()
            .assertTextContains("37")
        composeRule.onNodeWithTag(TAG_WIDGET_CUSTOM_BG).performScrollTo().performClick()
        composeRule.onNodeWithTag(TAG_WIDGET_COLOR_HEX).assertTextContains("#A13F7C")
        composeRule.onNodeWithTag(TAG_WIDGET_COLOR_APPLY).performClick()
        composeRule.onNodeWithTag(TAG_WIDGET_SAVE).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            scenario.state == Lifecycle.State.DESTROYED
        }
        assertEquals(saved, WidgetConfiguration.fromPreferences(readPreferences(widgetId)))
    }

    @Test
    fun invalid_hex_and_opacity_cannot_be_saved() {
        reopenSavedWidget()
        composeRule.onNodeWithTag(TAG_WIDGET_CUSTOM_BG).performScrollTo().performClick()
        composeRule.onNodeWithTag(TAG_WIDGET_COLOR_HEX).performTextReplacement("#F12")
        composeRule.onNodeWithTag(TAG_WIDGET_COLOR_APPLY).assertIsNotEnabled()
        composeRule.onNodeWithTag(TAG_WIDGET_COLOR_HEX).performTextReplacement("#FF1240")
        composeRule.onNodeWithTag(TAG_WIDGET_COLOR_APPLY).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(TAG_WIDGET_OPACITY_INPUT).performScrollTo()
            .performTextReplacement("101")
        composeRule.onNodeWithTag(TAG_WIDGET_SAVE).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(TAG_WIDGET_OPACITY_INPUT).performScrollTo()
            .performTextReplacement("1")
        composeRule.onNodeWithTag(TAG_WIDGET_SAVE).performScrollTo().assertIsEnabled()
        assertEquals(savedConfiguration, WidgetConfiguration.fromPreferences(readPreferences(widgetId)))
    }

    @Test
    fun wheel_can_choose_an_arbitrary_color_and_reopening_preserves_it() {
        reopenSavedWidget()
        composeRule.onNodeWithTag(TAG_WIDGET_CUSTOM_BG).performScrollTo().performClick()
        composeRule.onNodeWithTag(TAG_WIDGET_COLOR_WHEEL)
            .performTouchInput { click(percentOffset(0.82f, 0.5f)) }
        composeRule.onNodeWithTag(TAG_WIDGET_COLOR_BRIGHTNESS)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.73f) }
        composeRule.onNodeWithTag(TAG_WIDGET_COLOR_APPLY).performClick()
        // Aucune mutation avant le Save, y compris des anciennes préférences.
        assertEquals(savedConfiguration, WidgetConfiguration.fromPreferences(readPreferences(widgetId)))
        composeRule.onNodeWithTag(TAG_WIDGET_SAVE).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            scenario.state == Lifecycle.State.DESTROYED
        }
        val updated = WidgetConfiguration.fromPreferences(readPreferences(widgetId))
        assertTrue(updated.backgroundColorArgb != savedConfiguration.backgroundColorArgb)
        assertEquals(savedConfiguration.cornerStyle, updated.cornerStyle)
        assertEquals(savedConfiguration.cityId, updated.cityId)
        assertEquals(savedConfiguration.forecastMode, updated.forecastMode)
        assertEquals(savedConfiguration.opacityPct, updated.opacityPct)
        launchConfiguration(widgetId)
        composeRule.onNodeWithTag(TAG_WIDGET_CORNER_SQUARE).performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag(TAG_WIDGET_CUSTOM_BG).performScrollTo().performClick()
        composeRule.onNodeWithTag(TAG_WIDGET_COLOR_APPLY).performClick()
        composeRule.onNodeWithTag(TAG_WIDGET_SAVE).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            scenario.state == Lifecycle.State.DESTROYED
        }
        assertEquals(updated, WidgetConfiguration.fromPreferences(readPreferences(widgetId)))
    }

    @Test
    fun custom_wheel_and_corner_selection_are_drafts_until_save() {
        reopenSavedWidget()
        composeRule.onNodeWithTag(TAG_WIDGET_CUSTOM_BG).performScrollTo().performClick()
        composeRule.onNodeWithTag(TAG_WIDGET_COLOR_WHEEL).assertExists()
        // Current custom color must reappear when editing an existing widget.
        composeRule.onNodeWithTag(TAG_WIDGET_COLOR_APPLY).performClick()
        composeRule.onNodeWithTag(TAG_WIDGET_CORNER_ROUNDED).performScrollTo().performClick()
        composeRule.onNodeWithTag(TAG_WIDGET_CORNER_ROUNDED).assertIsSelected()
        assertEquals(savedConfiguration, WidgetConfiguration.fromPreferences(readPreferences(widgetId)))
        composeRule.onNodeWithTag(TAG_WIDGET_SAVE).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            scenario.state == Lifecycle.State.DESTROYED
        }
        assertEquals(savedConfiguration.copy(cornerStyle = WidgetCornerStyle.ROUNDED),
            WidgetConfiguration.fromPreferences(readPreferences(widgetId)))
    }

    @Test
    fun shape_and_spacing_are_independent_and_saved_per_widget() {
        reopenSavedWidget()
        composeRule.onNodeWithTag(TAG_WIDGET_CORNER_CUSTOM).performScrollTo().performClick()
        composeRule.onNodeWithTag(TAG_WIDGET_CORNER_RADIUS).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(36f) }
        composeRule.onNodeWithTag("${TAG_WIDGET_SPACING}NONE").performScrollTo().performClick()
        assertEquals(savedConfiguration, WidgetConfiguration.fromPreferences(readPreferences(widgetId)))
        composeRule.onNodeWithTag(TAG_WIDGET_SAVE).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { scenario.state == Lifecycle.State.DESTROYED }
        val updated = WidgetConfiguration.fromPreferences(readPreferences(widgetId))
        assertEquals(WidgetCornerStyle.CUSTOM, updated.cornerStyle)
        assertEquals(36, updated.cornerRadiusDp)
        assertEquals(WidgetContentSpacing.NONE, updated.contentSpacing)
        launchConfiguration(widgetId)
        composeRule.onNodeWithTag(TAG_WIDGET_CORNER_CUSTOM).performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("${TAG_WIDGET_SPACING}NONE").performScrollTo().assertIsSelected()
    }

    @Test
    fun large_spacing_is_available_and_persists_after_reopening_configuration() {
        reopenSavedWidget()
        composeRule.onNodeWithTag("${TAG_WIDGET_SPACING}LARGE").performScrollTo().performClick()
        assertEquals(savedConfiguration, WidgetConfiguration.fromPreferences(readPreferences(widgetId)))
        composeRule.onNodeWithTag(TAG_WIDGET_SAVE).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { scenario.state == Lifecycle.State.DESTROYED }
        assertEquals(WidgetContentSpacing.LARGE,
            WidgetConfiguration.fromPreferences(readPreferences(widgetId)).contentSpacing)
        launchConfiguration(widgetId)
        composeRule.onNodeWithTag("${TAG_WIDGET_SPACING}LARGE").performScrollTo().assertIsSelected()
    }

    @Test
    fun cancel_keeps_previously_saved_configuration() {
        reopenSavedWidget()
        composeRule.onNodeWithTag("$TAG_WIDGET_CITY${TestFixtures.paris.id}").performClick()
        composeRule.onNodeWithTag(TAG_WIDGET_CANCEL).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { scenario.state == Lifecycle.State.DESTROYED }
        assertEquals(savedConfiguration, WidgetConfiguration.fromPreferences(readPreferences(widgetId)))
    }

    @Test
    fun hidden_header_setting_is_independent_for_two_widgets() {
        scenario.close()
        writePreferences(widgetId, savedConfiguration.copy(showCurrentConditions = false))
        val secondId = allocateWidget()
        writePreferences(secondId, savedConfiguration.copy(showCurrentConditions = true))
        launchConfiguration(secondId)
        composeRule.onNodeWithTag(TAG_WIDGET_SHOW_CURRENT_CONDITIONS)
            .performScrollTo().assertIsOn()
        assertEquals(false, WidgetConfiguration.fromPreferences(readPreferences(widgetId)).showCurrentConditions)
        assertEquals(true, WidgetConfiguration.fromPreferences(readPreferences(secondId)).showCurrentConditions)
    }

    @Test
    fun each_widget_loads_its_own_configuration() {
        reopenSavedWidget()
        assertSavedSelections()
        scenario.close()
        val otherId = allocateWidget()
        val other = savedConfiguration.copy(cityId = TestFixtures.paris.id,
            opacityPct = 80, forecastMode = ForecastMode.HOURLY)
        writePreferences(otherId, other)
        launchConfiguration(otherId)
        composeRule.onNodeWithTag("$TAG_WIDGET_CITY${TestFixtures.paris.id}").assertIsSelected()
        assertOpacity(80f)
        composeRule.onNodeWithTag("$TAG_WIDGET_MODE${ForecastMode.HOURLY.name}").assertIsSelected()
        assertEquals(savedConfiguration, WidgetConfiguration.fromPreferences(readPreferences(widgetId)))
    }

    private val savedConfiguration get() = WidgetConfiguration(
        cityId = TestFixtures.lyon.id, opacityPct = 35, forecastMode = ForecastMode.DAILY,
        backgroundColorArgb = 0xFF123456.toInt(), textColorArgb = 0xFFFFFFFF.toInt(),
        cornerStyle = WidgetCornerStyle.SQUARE
    )

    private fun reopenSavedWidget() {
        scenario.close()
        writePreferences(widgetId, savedConfiguration)
        launchConfiguration(widgetId)
    }

    private fun assertSavedSelections() {
        composeRule.onNodeWithTag("$TAG_WIDGET_CITY${TestFixtures.lyon.id}").assertIsSelected()
        assertOpacity(35f)
        composeRule.onNodeWithTag("$TAG_WIDGET_MODE${ForecastMode.DAILY.name}").assertIsSelected()
        composeRule.onNodeWithTag(TAG_WIDGET_CORNER_SQUARE).performScrollTo().assertIsSelected()
    }

    private fun assertOpacity(value: Float) {
        val rangeInfo = composeRule.onNodeWithTag(TAG_WIDGET_OPACITY)
            .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]

        // Slider values are Floats and can be restored with a tiny rounding drift
        // (for example 60f -> 60.000004f). Assert the semantic value with a
        // tolerance while still checking the slider contract itself.
        assertEquals(value, rangeInfo.current, 0.01f)
        assertEquals(0f..100f, rangeInfo.range)
        // 99 intermediate ticks allow choosing every integer value (0..100).
        assertEquals(99, rangeInfo.steps)
    }

    private fun readPreferences(id: Int): Preferences = runBlocking {
        getAppWidgetState(context, PreferencesGlanceStateDefinition,
            GlanceAppWidgetManager(context).getGlanceIdBy(id))
    }

    private fun writePreferences(id: Int, configuration: WidgetConfiguration) = runBlocking {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition,
            GlanceAppWidgetManager(context).getGlanceIdBy(id)) { prefs ->
            prefs.toMutablePreferences().apply {
                this[WidgetPreferences.CityIdKey] = checkNotNull(configuration.cityId)
                this[WidgetPreferences.OpacityPctKey] = configuration.opacityPct
                this[WidgetPreferences.ForecastModeKey] = configuration.forecastMode.name
                this[WidgetPreferences.ShowCurrentConditionsKey] = configuration.showCurrentConditions
                this[WidgetPreferences.BackgroundColorKey] = checkNotNull(configuration.backgroundColorArgb)
                this[WidgetPreferences.TextColorKey] = checkNotNull(configuration.textColorArgb)
                this[WidgetPreferences.CornerStyleKey] = configuration.cornerStyle.name
            }
        }
    }

}
