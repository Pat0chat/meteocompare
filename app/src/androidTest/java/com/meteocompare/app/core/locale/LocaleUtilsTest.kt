package com.meteocompare.app.core.locale

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meteocompare.app.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.util.Locale

class LocaleUtilsTest {
    private lateinit var context: Context
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalLocale = Locale.getDefault()
        localePreferences(context).edit().clear().commit()
        refreshPersistedLocaleCache(context)
    }

    @After
    fun tearDown() {
        localePreferences(context).edit().clear().commit()
        refreshPersistedLocaleCache(context)
        Locale.setDefault(originalLocale)
    }

    @Test
    fun no_persisted_language_returns_original_context() {
        assertSame(context, applyPersistedLocale(context))
    }

    @Test
    fun persisted_language_updates_context_resources_and_default_locale() {
        localePreferences(context).edit()
            .putString(LOCALE_LANGUAGE_TAG_KEY, "en")
            .commit()
        refreshPersistedLocaleCache(context)

        val localized = applyPersistedLocale(context)

        assertEquals("en", localized.resources.configuration.locales[0].language)
        assertEquals("en", Locale.getDefault().language)
    }

    @Test
    fun persisted_language_localizes_detailed_forecast_title() {
        persistLocalePreference(context, "fr")
        val french = applyPersistedLocale(context)
        assertEquals(
            "Prévisions détaillées",
            french.getString(R.string.forecast_tables_section)
        )

        persistLocalePreference(context, "en")
        val english = applyPersistedLocale(context)
        assertEquals(
            "Detailed forecasts",
            english.getString(R.string.forecast_tables_section)
        )
    }

    @Test
    fun persisted_language_updates_memory_cache_immediately() {
        persistLocalePreference(context, "en")

        // Modifier le disque directement simule une valeur devenue différente
        // sans passer par l'API de persistance. applyPersistedLocale doit garder
        // le cache "en" et ne pas relire SharedPreferences sur ce chemin.
        localePreferences(context).edit()
            .putString(LOCALE_LANGUAGE_TAG_KEY, "fr")
            .commit()

        val localized = applyPersistedLocale(context)

        assertEquals("en", localized.resources.configuration.locales[0].language)
    }

    @Test
    fun forced_language_then_system_restores_context_default_locale() {
        val systemLanguage = context.resources.configuration.locales[0].language
        val forcedTag = if (systemLanguage == "fr") "en" else "fr"

        persistLocalePreference(context, forcedTag)
        applyPersistedLocale(context)
        assertEquals(forcedTag, Locale.getDefault().language)

        persistLocalePreference(context, null)
        val restored = applyPersistedLocale(context)

        assertSame(context, restored)
        assertEquals(systemLanguage, Locale.getDefault().language)
    }
}
