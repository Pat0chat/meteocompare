package com.meteocompare.app.ui.components

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.meteocompare.app.R
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.domain.model.UnitSystem
import com.meteocompare.app.ui.localitycompare.CompareChartMetric
import com.meteocompare.app.ui.localitycompare.chartAxisUnit
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real Android resource selection must agree with every sunshine entry point. */
class SunshineDurationLocalizationTest {
    @get:Rule val composeRule = createComposeRule()

    private val appContext: Context get() = ApplicationProvider.getApplicationContext()

    private fun languageContext(language: String): Context {
        val configuration = Configuration(appContext.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return appContext.createConfigurationContext(configuration)
    }

    private fun formats(context: Context) = SunshineDurationFormats(
        context.getString(R.string.sunshine_duration_minutes),
        context.getString(R.string.sunshine_duration_hours),
        context.getString(R.string.sunshine_duration_hours_minutes),
        context.getString(R.string.sunshine_duration_minute_unit)
    )

    @Test fun realTranslatedResourcesFormatAllFiveSupportedLanguages() {
        data class Expected(val minute: String, val shortDaily: String, val mixedDaily: String,
            val wholeDaily: String, val axis: String)
        val expectations = mapOf(
            "fr" to Expected("18 min", "18 min", "1 h 12 min", "6 h", "min"),
            "en" to Expected("18 min", "18 min", "1 hr 12 min", "6 hr", "min"),
            "de" to Expected("18 Min.", "18 Min.", "1 Std. 12 Min.", "6 Std.", "Min."),
            "es" to Expected("18 min", "18 min", "1 h 12 min", "6 h", "min"),
            "it" to Expected("18 min", "18 min", "1 h 12 min", "6 h", "min")
        )
        for ((language, expected) in expectations) {
            val context = languageContext(language)
            val locale = context.resources.configuration.locales[0]
            val translated = formats(context)
            for (system in UnitSystem.entries) {
                val units = WeatherUnits(system)
                assertEquals("$language hourly", expected.minute,
                    formatSunshineDuration(0.3, true, units, locale, translated))
                assertEquals("$language daily minutes", expected.shortDaily,
                    formatSunshineDuration(0.3, false, units, locale, translated))
                assertEquals("$language daily mixed", expected.mixedDaily,
                    formatSunshineDuration(1.2, false, units, locale, translated))
                assertEquals("$language daily whole", expected.wholeDaily,
                    formatSunshineDuration(6.0, false, units, locale, translated))
                assertEquals("$language chart axis", expected.axis,
                    chartAxisUnit(CompareChartMetric.SUNSHINE, units, translated.minuteUnit))
                assertEquals("$language missing", "—",
                    formatSunshineDuration(null, true, units, locale, translated))
            }
        }
    }

    @Test fun unsupportedSystemLanguageFallsBackToFrenchBaseStrings() {
        val context = languageContext("pt")
        val translated = formats(context)
        assertEquals("18 min", formatSunshineDuration(0.3, true, WeatherUnits(),
            context.resources.configuration.locales[0], translated))
        assertEquals("1 h 12 min", formatSunshineDuration(1.2, false, WeatherUnits(),
            context.resources.configuration.locales[0], translated))
    }

    @Test fun composeReadsCurrentConfiguredLanguageInsteadOfHardCodedFrench() {
        val germanContext = languageContext("de")
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides germanContext,
                LocalConfiguration provides germanContext.resources.configuration
            ) {
                val pattern = localizedSunshineDurationFormats()
                val locale = germanContext.resources.configuration.locales[0]
                Text(formatSunshineDuration(1.2, false, WeatherUnits(), locale, pattern))
            }
        }
        composeRule.onNodeWithText("1 Std. 12 Min.").assertExists()
    }
}
