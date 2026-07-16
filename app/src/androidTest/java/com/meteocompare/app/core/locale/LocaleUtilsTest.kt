package com.meteocompare.app.core.locale

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meteocompare.app.MainActivity
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
        preferences().edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences().edit().clear().commit()
        Locale.setDefault(originalLocale)
    }

    @Test
    fun no_persisted_language_returns_original_context() {
        assertSame(context, applyPersistedLocale(context))
    }

    @Test
    fun persisted_language_updates_context_resources_and_default_locale() {
        preferences().edit().putString(MainActivity.LOCALE_KEY, "en").commit()

        val localized = applyPersistedLocale(context)

        assertEquals("en", localized.resources.configuration.locales[0].language)
        assertEquals("en", Locale.getDefault().language)
    }

    private fun preferences() = context.getSharedPreferences(
        MainActivity.LOCALE_PREFS,
        Context.MODE_PRIVATE
    )
}
