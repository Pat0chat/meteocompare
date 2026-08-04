package com.meteocompare.app.widget

import android.content.Context
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meteocompare.app.R
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Vérifie que les aperçus évolutifs du picker restent compatibles RemoteViews.
 * Une vue générique (`<View>`) est parfaitement valide dans un layout Android
 * classique, mais elle est refusée par le filtre d'inflation RemoteViews et le
 * launcher n'affiche alors qu'un rectangle gris.
 */
@RunWith(AndroidJUnit4::class)
class WidgetPreviewLayoutTest {

    @Test
    fun scalable_previews_can_be_inflated_as_remote_views() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val layouts = listOf(
            R.layout.widget_preview_5x1,
            R.layout.widget_preview,
            R.layout.widget_preview_large
        )

        layouts.forEach { layoutRes ->
            val parent = FrameLayout(context)
            RemoteViews(context.packageName, layoutRes).apply(context, parent)
        }
    }
}
