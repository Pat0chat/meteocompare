package com.meteocompare.app.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsContentTest {
    @get:Rule val composeRule = createComposeRule()

    private fun content(
        enabled: Set<WeatherModel> = WeatherModel.MVP_SELECTION.toSet(),
        onToggle: (WeatherModel, Boolean) -> Unit = { _, _ -> },
        onTheme: (ThemePreference) -> Unit = {},
        onLanguage: (LanguagePreference) -> Unit = {},
        onRefresh: (RefreshInterval) -> Unit = {},
        forecastEngine: ForecastEngine = ForecastEngine.DEFAULT,
        onForecastEngine: (ForecastEngine) -> Unit = {},
        biasRefreshRequested: Boolean = false,
        onBiasRefresh: () -> Unit = {},
        onDonate: () -> Unit = {}
    ) {
        composeRule.setContent {
            MeteoCompareTheme {
                SettingsContent(
                    enabledModels = enabled,
                    onToggle = onToggle,
                    theme = ThemePreference.SYSTEM,
                    onThemeSelected = onTheme,
                    language = LanguagePreference.SYSTEM,
                    onLanguageSelected = onLanguage,
                    refreshInterval = RefreshInterval.DEFAULT,
                    onRefreshIntervalSelected = onRefresh,
                    forecastEngine = forecastEngine,
                    onForecastEngineSelected = onForecastEngine,
                    biasRefreshRequested = biasRefreshRequested,
                    onBiasRefreshClick = onBiasRefresh,
                    onDonateClick = onDonate,
                    padding = PaddingValues(0.dp)
                )
            }
        }
    }

    private fun scrollTo(tag: String) {
        composeRule.onNodeWithTag(TAG_SETTINGS_ROOT)
            .performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun selected_controls_expose_selected_semantics() {
        content()
        val themeTag = "$TAG_SETTINGS_THEME${ThemePreference.SYSTEM.name}"
        val languageTag = "$TAG_SETTINGS_LANGUAGE${LanguagePreference.SYSTEM.name}"
        val refreshTag = "$TAG_SETTINGS_REFRESH${RefreshInterval.DEFAULT.name}"
        val sortTag = "$TAG_SETTINGS_SORT${ModelSortMode.ZONE.name}"

        scrollTo(themeTag)
        composeRule.onNodeWithTag(themeTag).assertIsSelected()
        scrollTo(languageTag)
        composeRule.onNodeWithTag(languageTag).assertIsSelected()
        scrollTo(refreshTag)
        composeRule.onNodeWithTag(refreshTag).assertIsSelected()
        scrollTo(sortTag)
        composeRule.onNodeWithTag(sortTag).assertIsSelected()
    }

    @Test
    fun model_sort_selection_updates_inside_the_screen() {
        content()
        val zoneTag = "$TAG_SETTINGS_SORT${ModelSortMode.ZONE.name}"
        val finesseTag = "$TAG_SETTINGS_SORT${ModelSortMode.FINESSE.name}"

        scrollTo(finesseTag)
        composeRule.onNodeWithTag(finesseTag).performClick()

        composeRule.onNodeWithTag(finesseTag).assertIsSelected()
        composeRule.onNodeWithTag(zoneTag).assertIsNotSelected()
    }

    @Test
    fun theme_language_refresh_and_model_callbacks_are_forwarded() {
        var theme: ThemePreference? = null
        var language: LanguagePreference? = null
        var refresh: RefreshInterval? = null
        var toggled: Pair<WeatherModel, Boolean>? = null
        content(
            onToggle = { model, enabled -> toggled = model to enabled },
            onTheme = { theme = it },
            onLanguage = { language = it },
            onRefresh = { refresh = it }
        )

        val themeTag = "$TAG_SETTINGS_THEME${ThemePreference.DARK.name}"
        val languageTag = "$TAG_SETTINGS_LANGUAGE${LanguagePreference.ENGLISH.name}"
        val refreshTag = "$TAG_SETTINGS_REFRESH${RefreshInterval.HOURS_3.name}"
        val modelTag = "$TAG_SETTINGS_MODEL${WeatherModel.AROME_FRANCE_HD.name}"
        scrollTo(themeTag)
        composeRule.onNodeWithTag(themeTag).performClick()
        scrollTo(languageTag)
        composeRule.onNodeWithTag(languageTag).performClick()
        scrollTo(refreshTag)
        composeRule.onNodeWithTag(refreshTag).performClick()
        scrollTo(modelTag)
        composeRule.onNodeWithTag(modelTag).performClick()

        assertEquals(ThemePreference.DARK, theme)
        assertEquals(LanguagePreference.ENGLISH, language)
        assertEquals(RefreshInterval.HOURS_3, refresh)
        assertEquals(WeatherModel.AROME_FRANCE_HD, toggled?.first)
        assertFalse(toggled?.second ?: true)
    }


    @Test
    fun spanish_german_and_italian_language_options_are_reachable() {
        var language: LanguagePreference? = null
        content(onLanguage = { language = it })

        listOf(
            LanguagePreference.SPANISH,
            LanguagePreference.GERMAN,
            LanguagePreference.ITALIAN
        ).forEach { preference ->
            val tag = "$TAG_SETTINGS_LANGUAGE${preference.name}"
            scrollTo(tag)
            composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .performClick()
            assertEquals(preference, language)
        }
    }

    @Test
    fun last_enabled_model_cannot_be_disabled() {
        val only = WeatherModel.GFS
        var called = false
        content(enabled = setOf(only), onToggle = { _, _ -> called = true })
        val modelTag = "$TAG_SETTINGS_MODEL${only.name}"
        scrollTo(modelTag)
        composeRule.onNodeWithTag(modelTag).assertHasNoClickAction()
        assertFalse(called)
    }

    @Test
    fun manual_bias_refresh_action_is_reachable_at_bottom_of_list() {
        var requested = false
        content(onBiasRefresh = { requested = true })

        scrollTo(TAG_SETTINGS_BIAS_REFRESH)
        composeRule.onNodeWithTag(TAG_SETTINGS_BIAS_REFRESH)
            .assertIsDisplayed()
            .performClick()

        assertTrue(requested)
    }


    @Test
    fun manual_bias_refresh_action_is_disabled_after_request() {
        var requested = false
        content(
            biasRefreshRequested = true,
            onBiasRefresh = { requested = true }
        )

        scrollTo(TAG_SETTINGS_BIAS_REFRESH)
        composeRule.onNodeWithTag(TAG_SETTINGS_BIAS_REFRESH)
            .assertIsDisplayed()
            .assertIsNotEnabled()

        assertFalse(requested)
    }

    @Test
    fun donation_action_is_reachable_at_bottom_of_list() {
        var donated = false
        content(onDonate = { donated = true })
        scrollTo(TAG_SETTINGS_DONATE)
        composeRule.onNodeWithTag(TAG_SETTINGS_DONATE)
            .assertIsDisplayed()
            .performClick()
        assertTrue(donated)
    }
    @Test
    fun forecast_engine_selection_is_visible_and_forwarded() {
        var selected: ForecastEngine? = null
        content(onForecastEngine = { selected = it })

        val defaultTag = "$TAG_SETTINGS_ENGINE${ForecastEngine.MULTI_CONSENSUS.name}"
        val adaptiveTag = "$TAG_SETTINGS_ENGINE${ForecastEngine.ADAPTIVE.name}"
        scrollTo(defaultTag)
        composeRule.onNodeWithTag(defaultTag).assertIsSelected()
        scrollTo(adaptiveTag)
        composeRule.onNodeWithTag(adaptiveTag).performClick()

        assertEquals(ForecastEngine.ADAPTIVE, selected)
    }

}
