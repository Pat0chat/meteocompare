package com.meteocompare.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.meteocompare.app.MainActivity
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.applyPersistedLocale
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.citydetail.ForecastInsightLevel
import kotlin.math.roundToInt

/**
 * Widget éditorial « À retenir ». Il partage les mêmes préférences de ville,
 * couleur et rafraîchissement que le widget historique.
 */
internal class MeteoInsightWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = applyPersistedLocale(context.applicationContext)
        provideContent {
            val prefs = currentState<Preferences>()
            val cityId = prefs[WidgetPreferences.CityIdKey]
            val opacityPct = (prefs[WidgetPreferences.OpacityPctKey]
                ?: WidgetPreferences.DEFAULT_OPACITY_PCT).coerceIn(0, 100)
            val refreshTick = prefs[WidgetPreferences.RefreshTickKey] ?: 0L
            val customBackground = prefs[WidgetPreferences.BackgroundColorKey]
            val customText = prefs[WidgetPreferences.TextColorKey]

            var data by remember {
                mutableStateOf(
                    if (cityId == null) WidgetData.NotConfigured else WidgetData.Loading
                )
            }
            LaunchedEffect(cityId, refreshTick) {
                // Le widget éditorial repose toujours sur un horizon horaire de 24 h.
                // L'écran de configuration l'indique explicitement comme horizon fixe.
                data = loadWidgetData(
                    context = appContext,
                    cityId = cityId,
                    forecastMode = ForecastMode.HOURLY,
                    includeValueSnapshot = true
                )
            }

            CompositionLocalProvider(LocalContext provides appContext) {
                GlanceTheme {
                    InsightWidgetContent(
                        data = data,
                        opacityPct = opacityPct,
                        customBackgroundArgb = customBackground,
                        customTextArgb = customText
                    )
                }
            }
        }
    }
}

private data class ValueWidgetColors(
    val container: ColorProvider,
    val foreground: ColorProvider,
    val muted: ColorProvider,
    val surface: ColorProvider,
    val raisedSurface: ColorProvider,
    val accent: ColorProvider,
    val warning: ColorProvider,
    val positive: ColorProvider
)

@Composable
private fun InsightWidgetContent(
    data: WidgetData,
    opacityPct: Int,
    customBackgroundArgb: Int?,
    customTextArgb: Int?
) {
    val context = LocalContext.current
    val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val colors = valueWidgetColors(
        night = night,
        opacityPct = opacityPct,
        customBackgroundArgb = customBackgroundArgb,
        customTextArgb = customTextArgb
    )
    val size = LocalSize.current
    val compact = size.width.value < 220f || size.height.value < 126f

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.container)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .appWidgetBackground()
            .clickable(actionStartActivity<MainActivity>())
            .padding(
                horizontal = if (compact) 13.dp else 17.dp,
                vertical = if (compact) 11.dp else 13.dp
            )
    ) {
        when (data.error) {
            null -> InsightWidgetLayout(data, colors, compact)
            else -> ValueWidgetError(data, colors, compact)
        }
    }
}

@Composable
private fun valueWidgetColors(
    night: Boolean,
    opacityPct: Int,
    customBackgroundArgb: Int?,
    customTextArgb: Int?
): ValueWidgetColors {
    val context = LocalContext.current
    val theme = GlanceTheme.colors
    val useCustomPalette = customBackgroundArgb != null || customTextArgb != null

    val dynamicBackground = theme.widgetBackground.getColor(context)
    val dynamicForeground = theme.onSurface.getColor(context)
    val dynamicMuted = theme.onSurfaceVariant.getColor(context)
    val dynamicSurface = theme.surfaceVariant.getColor(context)
    val dynamicRaised = theme.secondaryContainer.getColor(context)
    val dynamicAccent = theme.primary.getColor(context)
    val dynamicWarning = theme.tertiary.getColor(context)

    val baseBackground = customBackgroundArgb?.let(::Color) ?: dynamicBackground
    val baseText = when {
        customTextArgb != null -> Color(customTextArgb)
        customBackgroundArgb != null -> if (baseBackground.luminance() > 0.5f) Color.Black else Color.White
        else -> dynamicForeground
    }
    val muted = if (useCustomPalette) baseText.copy(alpha = 0.68f) else dynamicMuted
    val surface = if (useCustomPalette) {
        baseText.copy(alpha = if (night) 0.10f else 0.075f)
    } else {
        dynamicSurface.copy(alpha = if (night) 0.72f else 0.82f)
    }
    val raised = if (useCustomPalette) {
        baseText.copy(alpha = if (night) 0.17f else 0.12f)
    } else {
        dynamicRaised.copy(alpha = if (night) 0.78f else 0.86f)
    }

    return ValueWidgetColors(
        container = ColorProvider(baseBackground.copy(alpha = opacityPct / 100f)),
        foreground = ColorProvider(baseText),
        muted = ColorProvider(muted),
        surface = ColorProvider(surface),
        raisedSurface = ColorProvider(raised),
        accent = ColorProvider(if (useCustomPalette) {
            if (night) Color(0xFF9DB5FF) else Color(0xFF315DA8)
        } else dynamicAccent),
        warning = ColorProvider(if (useCustomPalette) {
            if (night) Color(0xFFFFC46B) else Color(0xFF9A5A00)
        } else dynamicWarning),
        positive = ColorProvider(if (night) Color(0xFF8ED6A0) else Color(0xFF237A3B))
    )
}

@Composable
private fun InsightWidgetLayout(
    data: WidgetData,
    colors: ValueWidgetColors,
    compact: Boolean
) {
    val context = LocalContext.current
    val insight = data.keyInsight
    Column(modifier = GlanceModifier.fillMaxSize()) {
        ValueWidgetHeader(
            title = data.cityName.orEmpty(),
            trailing = if (data.modelCount > 0) {
                context.getString(R.string.widget_value_models, data.modelCount)
            } else null,
            colors = colors
        )
        Spacer(GlanceModifier.height(if (compact) 6.dp else 9.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ValueWeatherGlyph(data.currentCondition, if (compact) 36 else 46)
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = data.currentTemp?.let { "${it.roundToInt()}°" } ?: "—",
                style = TextStyle(
                    color = colors.foreground,
                    fontSize = if (compact) 27.sp else 34.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = context.getString(valueWeatherDescriptionRes(data.currentCondition)),
                    style = TextStyle(
                        color = colors.muted,
                        fontSize = if (compact) 10.sp else 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                val minMax = widgetMinMaxLabel(data)
                if (minMax != null) {
                    Text(
                        text = minMax,
                        style = TextStyle(color = colors.muted, fontSize = 10.sp),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(GlanceModifier.height(if (compact) 7.dp else 10.dp))
        InsightSurface(
            insight = insight,
            colors = colors,
            compact = compact,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight()
        )

        if (!compact) {
            val metrics = data.comparisonSnapshot?.metrics.orEmpty().take(3)
            if (metrics.isNotEmpty()) {
                Spacer(GlanceModifier.height(8.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    metrics.forEachIndexed { index, metric ->
                        MetricConsensusPill(metric, colors, GlanceModifier.defaultWeight())
                        if (index != metrics.lastIndex) Spacer(GlanceModifier.width(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightSurface(
    insight: WidgetKeyInsight?,
    colors: ValueWidgetColors,
    compact: Boolean,
    modifier: GlanceModifier
) {
    val context = LocalContext.current
    val tone = when (insight?.level) {
        ForecastInsightLevel.ALERT,
        ForecastInsightLevel.WATCH -> colors.warning
        ForecastInsightLevel.POSITIVE -> colors.positive
        else -> colors.accent
    }
    Row(
        modifier = modifier
            .background(colors.raisedSurface)
            .cornerRadius(if (compact) 18.dp else 22.dp)
            .padding(horizontal = if (compact) 11.dp else 13.dp, vertical = if (compact) 8.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .width(if (compact) 30.dp else 36.dp)
                .height(if (compact) 30.dp else 36.dp)
                .background(tone)
                .cornerRadius(if (compact) 11.dp else 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(insightIconRes(insight?.icon)),
                contentDescription = null,
                modifier = GlanceModifier.width(19.dp).height(19.dp)
            )
        }
        Spacer(GlanceModifier.width(9.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = insight?.title ?: context.getString(R.string.widget_value_no_comparison),
                style = TextStyle(
                    color = colors.foreground,
                    fontSize = if (compact) 11.sp else 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            insight?.detail?.takeIf(String::isNotBlank)?.let { detail ->
                Text(
                    text = detail,
                    style = TextStyle(color = colors.muted, fontSize = if (compact) 9.sp else 10.sp),
                    maxLines = 1
                )
            }
        }
        insight?.timeLabel?.let { time ->
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = time,
                style = TextStyle(
                    color = tone,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ValueWidgetHeader(
    title: String,
    trailing: String?,
    colors: ValueWidgetColors
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = colors.foreground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
        trailing?.let {
            Text(
                text = it,
                style = TextStyle(
                    color = colors.muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MetricConsensusPill(
    metric: WidgetMetricSnapshot,
    colors: ValueWidgetColors,
    modifier: GlanceModifier
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .background(colors.surface)
            .cornerRadius(16.dp)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            provider = ImageProvider(metricIconRes(metric.type)),
            contentDescription = context.getString(metricLabelRes(metric.type)),
            modifier = GlanceModifier.width(13.dp).height(13.dp)
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = metric.consensusPercent?.let {
                context.getString(R.string.widget_value_agreement_short, it)
            } ?: "—",
            style = TextStyle(
                color = if (metric.divergent) colors.warning else colors.foreground,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun ValueWidgetError(
    data: WidgetData,
    colors: ValueWidgetColors,
    compact: Boolean
) {
    val context = LocalContext.current
    val (title, detail) = when (data.error) {
        WidgetError.NotConfigured ->
            context.getString(R.string.widget_error_not_configured) to
                context.getString(R.string.widget_value_open_app)
        WidgetError.Loading ->
            context.getString(R.string.widget_error_loading) to data.cityName.orEmpty()
        WidgetError.CityNoLongerInFavorites ->
            context.getString(R.string.widget_error_city_gone) to
                context.getString(R.string.widget_value_open_app)
        is WidgetError.Fetch ->
            (data.cityName ?: context.getString(R.string.app_name)) to
                context.getString(R.string.widget_error_fetch)
        null -> "" to ""
    }
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = GlanceModifier
                .width(if (compact) 34.dp else 42.dp)
                .height(if (compact) 34.dp else 42.dp)
                .background(colors.raisedSurface)
                .cornerRadius(21.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_value_compare),
                contentDescription = null,
                modifier = GlanceModifier.width(22.dp).height(22.dp)
            )
        }
        Spacer(GlanceModifier.height(7.dp))
        Text(
            text = title,
            style = TextStyle(color = colors.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            maxLines = 1
        )
        Text(
            text = detail,
            style = TextStyle(color = colors.muted, fontSize = 9.sp),
            maxLines = 2
        )
    }
}

@Composable
private fun ValueWeatherGlyph(condition: WeatherCondition?, sizeDp: Int) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density.coerceAtMost(2f)
    val bitmap = remember(condition, sizeDp, density) {
        WidgetWeatherIconRenderer.render(
            condition = condition,
            sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        )
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = context.getString(valueWeatherDescriptionRes(condition)),
        modifier = GlanceModifier.width(sizeDp.dp).height(sizeDp.dp)
    )
}

private fun widgetMinMaxLabel(data: WidgetData): String? {
    val min = data.tempMin?.roundToInt()
    val max = data.tempMax?.roundToInt()
    return if (min != null && max != null) "$min° / $max°" else null
}

private fun insightIconRes(icon: WidgetInsightIcon?): Int = when (icon) {
    WidgetInsightIcon.RAIN -> R.drawable.ic_widget_value_rain
    WidgetInsightIcon.WIND -> R.drawable.ic_widget_value_wind
    WidgetInsightIcon.TEMPERATURE -> R.drawable.ic_widget_value_temperature
    WidgetInsightIcon.WEATHER -> R.drawable.ic_widget_value_weather
    WidgetInsightIcon.UNCERTAINTY -> R.drawable.ic_widget_value_alert
    WidgetInsightIcon.STABLE,
    null -> R.drawable.ic_widget_value_check
}

private fun metricIconRes(type: WidgetMetricType): Int = when (type) {
    WidgetMetricType.TEMPERATURE -> R.drawable.ic_widget_value_temperature
    WidgetMetricType.PRECIPITATION -> R.drawable.ic_widget_value_rain
    WidgetMetricType.WIND -> R.drawable.ic_widget_value_wind
}

private fun metricLabelRes(type: WidgetMetricType): Int = when (type) {
    WidgetMetricType.TEMPERATURE -> R.string.widget_value_metric_temperature
    WidgetMetricType.PRECIPITATION -> R.string.widget_value_metric_precipitation
    WidgetMetricType.WIND -> R.string.widget_value_metric_wind
}

private fun valueWeatherDescriptionRes(condition: WeatherCondition?): Int = when (condition) {
    WeatherCondition.CLEAR -> R.string.weather_clear
    WeatherCondition.MAINLY_CLEAR -> R.string.weather_mainly_clear
    WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
    WeatherCondition.OVERCAST -> R.string.weather_overcast
    WeatherCondition.FOG -> R.string.weather_fog
    WeatherCondition.DRIZZLE -> R.string.weather_drizzle
    WeatherCondition.RAIN -> R.string.weather_rain
    WeatherCondition.FREEZING_RAIN -> R.string.weather_freezing_rain
    WeatherCondition.SNOW -> R.string.weather_snow
    WeatherCondition.RAIN_SHOWERS -> R.string.weather_rain_showers
    WeatherCondition.SNOW_SHOWERS -> R.string.weather_snow_showers
    WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
    WeatherCondition.UNKNOWN,
    null -> R.string.weather_unknown
}
