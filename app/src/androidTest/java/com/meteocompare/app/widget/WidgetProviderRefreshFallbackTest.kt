package com.meteocompare.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meteocompare.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** Vérifie le filet AppWidgetManager utilisé quand un OEM retarde WorkManager. */
@RunWith(AndroidJUnit4::class)
class WidgetProviderRefreshFallbackTest {

    @Test
    fun every_widget_provider_requests_the_thirty_minute_system_fallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val providers = listOf(
            R.xml.meteocompare_widget_info_weather,
            R.xml.meteocompare_widget_info_insight
        )

        providers.forEach { providerRes ->
            context.resources.getXml(providerRes).use { parser ->
                while (parser.eventType != XmlPullParser.START_TAG &&
                    parser.eventType != XmlPullParser.END_DOCUMENT
                ) {
                    parser.next()
                }
                assertEquals("appwidget-provider", parser.name)
                assertEquals(
                    "fallback absent pour la ressource $providerRes",
                    SYSTEM_WIDGET_FALLBACK_PERIOD_MS,
                    parser.getAttributeIntValue(
                        ANDROID_NAMESPACE,
                        "updatePeriodMillis",
                        -1
                    )
                )
            }
        }
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val SYSTEM_WIDGET_FALLBACK_PERIOD_MS = 30 * 60 * 1_000
    }
}
