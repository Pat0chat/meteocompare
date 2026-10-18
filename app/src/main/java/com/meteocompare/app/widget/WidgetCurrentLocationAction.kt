package com.meteocompare.app.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteocompare.app.R

/** Only rendered when this particular widget is configured for My location. */
internal fun shouldShowWidgetLocationAction(cityId: String?): Boolean =
    cityId == com.meteocompare.app.data.repository.CurrentLocationStore.WIDGET_CITY_ID

@Composable
internal fun WidgetCurrentLocationAction(color: ColorProvider, compact: Boolean) {
    val context = LocalContext.current
    val intent = Intent(context, WidgetLocationRefreshActivity::class.java)
    Text(
        text = context.getString(
            if (compact) R.string.widget_current_location_refresh_short
            else R.string.widget_current_location_refresh
        ),
        style = TextStyle(color = color, fontSize = if (compact) 9.sp else 11.sp),
        modifier = GlanceModifier.clickable(actionStartActivity(intent))
            .padding(start = 5.dp, end = if (compact) 7.dp else 8.dp,
                top = 2.dp, bottom = 2.dp),
        maxLines = 1
    )
}
