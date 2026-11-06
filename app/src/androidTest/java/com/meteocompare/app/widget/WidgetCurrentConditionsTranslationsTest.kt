package com.meteocompare.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meteocompare.app.R
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetCurrentConditionsTranslationsTest {
    @Test
    fun option_and_explanation_exist_in_all_supported_languages() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        listOf("fr", "en", "de", "es", "it").forEach { language ->
            val configuration = android.content.res.Configuration(base.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val translated = base.createConfigurationContext(configuration)
            assertFalse("Option missing for $language",
                translated.getString(R.string.widget_config_show_current_conditions).isBlank())
            assertFalse("Explanation missing for $language",
                translated.getString(R.string.widget_config_show_current_conditions_note).isBlank())
        }
    }
}
