package com.meteocompare.app.notification

import com.meteocompare.app.core.units.WeatherUnit
import com.meteocompare.app.core.units.WeatherUnits

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.meteocompare.app.MainActivity
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.applyPersistedLocale
import com.meteocompare.app.core.locale.evolutionHighlightTitleRes
import com.meteocompare.app.core.locale.weatherConditionLabelRes
import com.meteocompare.app.domain.model.ForecastEvolutionTrend
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherNotification
import com.meteocompare.app.widget.WidgetWeatherIconRenderer
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Construit et publie les notifications Android à partir des contenus
 * [WeatherNotification] calculés par le domaine.
 *
 * Le rendu reste volontairement basé sur le template système Android : il
 * respecte ainsi Material You, la taille de police, l'écran verrouillé et les
 * adaptations OEM. La personnalité MeteoCompare vient de l'accent sémantique
 * et d'une hiérarchie de contenu commune aux cartes de l'application :
 * information essentielle en premier, métriques ensuite, contexte en dernier.
 *
 * Les textes sont résolus avec la langue choisie dans l'application (et non
 * celle du système) via [applyPersistedLocale], comme les widgets.
 */
internal class WeatherNotifier(context: Context, private val units: WeatherUnits = WeatherUnits()) {

    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)
    private val platformManager = appContext.getSystemService(NotificationManager::class.java)

    /** Faux si l'utilisateur a bloqué les notifications ou refusé la permission (Android 13+). */
    fun canPost(): Boolean = manager.areNotificationsEnabled() && hasPostPermission()

    /**
     * Publie [notification] et retourne un résultat explicite. Le worker ne doit
     * enregistrer la clé de déduplication qu'après [PostResult.POSTED].
     *
     * La permission, le réglage global et le canal sont revérifiés ici, juste
     * avant `notify()`, afin de couvrir un changement système survenu après le
     * `canPost()` effectué au début du cycle.
     */
    @SuppressLint("MissingPermission")
    fun post(notification: WeatherNotification): PostResult {
        if (!hasPostPermission()) return PostResult.BLOCKED_PERMISSION
        if (!manager.areNotificationsEnabled()) return PostResult.BLOCKED_APP

        val res = applyPersistedLocale(appContext)
        createChannels(res)
        val content = render(notification, res)
        if (!isChannelEnabled(content.channelId)) return PostResult.BLOCKED_CHANNEL

        val built = buildNotification(notification, content, res)
        manager.notify(notificationId(notification), built)
        return PostResult.POSTED
    }

    /**
     * Rendu textuel final séparé de la publication, afin de pouvoir tester la
     * lisibilité/localisation sans dépendre de l'état des canaux Android.
     */
    internal fun render(notification: WeatherNotification): RenderedContent =
        render(notification, applyPersistedLocale(appContext))

    /**
     * Construit la Notification et expose les RemoteViews exactes utilisées par
     * le builder. Les tests instrumentés peuvent ainsi valider le rendu sans
     * dépendre des champs Notification.contentView/bigContentView dépréciés.
     */
    internal fun buildForTest(notification: WeatherNotification): BuiltNotificationForTest {
        val res = applyPersistedLocale(appContext)
        val content = render(notification, res)
        val views = nativeDecoratedViews(notification, content, res)
        return BuiltNotificationForTest(
            notification = buildNotification(content, views),
            compactView = views.compact,
            expandedView = views.expanded
        )
    }

    private fun buildNotification(
        notification: WeatherNotification,
        content: RenderedContent,
        res: Context
    ): Notification = buildNotification(
        content = content,
        views = nativeDecoratedViews(notification, content, res)
    )

    private fun buildNotification(
        content: RenderedContent,
        views: NativeDecoratedViews
    ): Notification {
        return NotificationCompat.Builder(appContext, content.channelId)
            .setSmallIcon(R.drawable.ic_stat_meteocompare)
            .setColor(ContextCompat.getColor(appContext, content.accentColorRes))
            .setColorized(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Fallbacks utiles pour Wear/Auto/accessibilité et pour les hôtes qui
            // choisissent de ne pas afficher les RemoteViews personnalisées.
            .setContentTitle(content.title)
            .setContentText(content.text)
            // Le système conserve l'en-tête, l'icône, le nom de l'app et les
            // affordances. Seule la zone météo est structurée par MeteoCompare.
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views.compact)
            .setCustomBigContentView(views.expanded)
            .setCustomHeadsUpContentView(views.compact)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun render(notification: WeatherNotification, res: Context): RenderedContent = when (notification) {
        is WeatherNotification.DailySummary -> dailySummary(res, notification)
        is WeatherNotification.ModelDivergence -> divergence(res, notification)
        is WeatherNotification.ForecastChange -> forecastChange(res, notification)
    }

    private fun hasPostPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun isChannelEnabled(channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val channel = platformManager.getNotificationChannel(channelId) ?: return false
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun createChannels(res: Context) {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_DAILY_SUMMARY, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(res.getString(R.string.notification_channel_daily))
                    .setDescription(res.getString(R.string.notification_channel_daily_desc))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_DIVERGENCE, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(res.getString(R.string.notification_channel_divergence))
                    .setDescription(res.getString(R.string.notification_channel_divergence_desc))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_FORECAST_CHANGE, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(res.getString(R.string.notification_channel_change))
                    .setDescription(res.getString(R.string.notification_channel_change_desc))
                    .build()
            )
        )
    }

    private fun dailySummary(res: Context, summary: WeatherNotification.DailySummary): RenderedContent {
        val locale = res.currentLocale()
        val title = res.getString(
            if (summary.isToday) R.string.notification_daily_title_today
            else R.string.notification_daily_title_tomorrow,
            summary.city.name
        )
        val condition = summary.condition
            ?.takeUnless { it == WeatherCondition.UNKNOWN }
            ?.let { res.getString(weatherConditionLabelRes(it)) }
        val temperatures = res.getString(
            R.string.notification_daily_temperatures,
            summary.tempMin.formatDegrees(units = units),
            summary.tempMax.formatDegrees(units = units)
        )
        val precipitation = precipitationText(res, summary, locale)
        val wind = windText(res, summary)
        val agreement = summary.convergencePercent?.let {
            res.getString(R.string.notification_daily_agreement, it)
        }

        val tempMin = summary.tempMin.formatDegrees(units = units)
        val tempMax = summary.tempMax.formatDegrees(units = units)
        val weatherColor = ContextCompat.getColor(res, weatherTextColorRes(summary.condition))
        val tempMinColor = ContextCompat.getColor(res, R.color.notification_text_temperature_min)
        val tempMaxColor = ContextCompat.getColor(res, R.color.notification_text_temperature)
        val precipitationColor = ContextCompat.getColor(res, R.color.notification_text_precipitation)
        val windColor = ContextCompat.getColor(res, R.color.notification_text_wind)
        val agreementColor = ContextCompat.getColor(
            res,
            if ((summary.convergencePercent ?: 100) < LOW_CONFIDENCE_PERCENT) {
                R.color.notification_text_low_confidence
            } else {
                R.color.notification_text_info
            }
        )

        val compact = joinStyled(
            listOfNotNull(
                condition?.let { styledText(it, bold = true, color = weatherColor) },
                styleTemperatureValues(temperatures, tempMin, tempMax, tempMinColor, tempMaxColor),
                precipitation?.let {
                    styleMetricLine(
                        raw = it,
                        emphasizedValues = precipitationValues(summary, locale),
                        valueColor = precipitationColor
                    )
                },
                wind?.let {
                    styleMetricLine(
                        raw = it,
                        emphasizedValues = listOf(summary.windKmh.formatWindValue(units = units)),
                        valueColor = windColor
                    )
                }
            ),
            PART_SEPARATOR
        )
        val expanded = joinStyled(
            listOfNotNull(
                condition?.let { styledText(it, bold = true, color = weatherColor) },
                styleTemperatureValues(
                    raw = res.getString(
                        R.string.notification_daily_temperature_range,
                        tempMin,
                        tempMax
                    ),
                    tempMin = tempMin,
                    tempMax = tempMax,
                    tempMinColor = tempMinColor,
                    tempMaxColor = tempMaxColor
                ),
                precipitation?.let {
                    styleMetricLine(
                        raw = it,
                        emphasizedValues = precipitationValues(summary, locale),
                        valueColor = precipitationColor
                    )
                },
                wind?.let {
                    styleMetricLine(
                        raw = it,
                        emphasizedValues = listOf(summary.windKmh.formatWindValue(units = units)),
                        valueColor = windColor
                    )
                },
                agreement?.let {
                    styleMetricLine(
                        raw = it,
                        emphasizedValues = listOf(summary.convergencePercent.toString()),
                        valueColor = agreementColor
                    )
                }
            ),
            LINE_SEPARATOR
        )

        return RenderedContent(
            channelId = CHANNEL_DAILY_SUMMARY,
            title = title,
            text = compact,
            bigText = expanded,
            accentColorRes = weatherAccentColorRes(summary.condition)
        )
    }

    private fun precipitationText(
        res: Context,
        summary: WeatherNotification.DailySummary,
        locale: Locale
    ): String? {
        val amount = summary.precipitationAmountMm
            ?.takeIf { it >= MIN_DISPLAYED_PRECIPITATION_MM }
            ?.let { units.value(it, WeatherUnit.PRECIPITATION, 1, locale) }
        val probability = summary.precipitationProbabilityPercent
        return when {
            probability != null && amount != null -> res.getString(
                R.string.notification_daily_precipitation_with_amount,
                probability,
                amount,
                units.precipitationUnit
            )
            probability != null -> res.getString(R.string.notification_daily_precipitation, probability)
            amount != null -> res.getString(R.string.notification_daily_precipitation_amount, amount, units.precipitationUnit)
            else -> null
        }
    }

    private fun precipitationValues(
        summary: WeatherNotification.DailySummary,
        locale: Locale
    ): List<String> = buildList {
        summary.precipitationProbabilityPercent?.let { add(it.toString()) }
        summary.precipitationAmountMm
            ?.takeIf { it >= MIN_DISPLAYED_PRECIPITATION_MM }
            ?.let { add(units.value(it, WeatherUnit.PRECIPITATION, 1, locale)) }
    }

    private fun windText(
        res: Context,
        summary: WeatherNotification.DailySummary
    ): String? = summary.windKmh
        ?.takeIf(Double::isFinite)
        ?.let { res.getString(R.string.notification_daily_wind, units.value(it, WeatherUnit.WIND_SPEED, locale = res.currentLocale()), units.windUnit) }

    private fun divergence(res: Context, divergence: WeatherNotification.ModelDivergence): RenderedContent {
        val day = res.getString(
            if (divergence.isToday) R.string.notification_day_today
            else R.string.notification_day_tomorrow
        )
        val title = res.getString(R.string.notification_divergence_title, divergence.city.name)
        val agreement = res.getString(R.string.notification_divergence_agreement, divergence.convergencePercent)
        val lowConfidenceColor = ContextCompat.getColor(res, R.color.notification_text_low_confidence)
        val compact = styleValues(
            raw = res.getString(
                R.string.notification_divergence_compact,
                day,
                divergence.convergencePercent
            ),
            values = listOf(day, divergence.convergencePercent.toString()),
            color = lowConfidenceColor,
            firstValueBoldOnly = true
        )
        val expanded = joinStyled(
            listOf(
                styledText(day, bold = true),
                styleMetricLine(
                    raw = agreement,
                    emphasizedValues = listOf(divergence.convergencePercent.toString()),
                    valueColor = lowConfidenceColor
                ),
                styledText(res.getString(R.string.notification_divergence_explanation), italic = true)
            ),
            LINE_SEPARATOR
        )
        return RenderedContent(
            channelId = CHANNEL_DIVERGENCE,
            title = title,
            text = compact,
            bigText = expanded,
            accentColorRes = R.color.notification_accent_low_confidence
        )
    }

    private fun forecastChange(res: Context, change: WeatherNotification.ForecastChange): RenderedContent {
        val highlight = change.highlight
        val locale = res.currentLocale()
        val shortDate = highlight.targetDate.format(
            DateTimeFormatter.ofPattern(TARGET_DATE_SHORT_PATTERN, locale)
        )
        val longDate = highlight.targetDate.format(
            DateTimeFormatter.ofPattern(TARGET_DATE_LONG_PATTERN, locale)
        )
        val variable = res.getString(variableLabelRes(highlight.variable))
        val title = res.getString(R.string.notification_change_title, change.city.name)

        val revisionLine: String
        val compactRaw: String
        val consensus: String
        val delta: String?
        if (highlight.trend == ForecastEvolutionTrend.VOLATILE) {
            revisionLine = res.getString(R.string.notification_change_volatile_line, variable)
            compactRaw = res.getString(R.string.notification_change_volatile_compact, shortDate, variable)
            consensus = res.getString(
                R.string.notification_change_consensus_volatile,
                highlight.comparedModels
            )
            delta = null
        } else {
            delta = formatSignedDelta(highlight.medianDelta, highlight.variable, locale, units = units)
            revisionLine = res.getString(
                R.string.notification_change_revision_line,
                res.getString(evolutionHighlightTitleRes(highlight)),
                delta
            )
            compactRaw = res.getString(R.string.notification_change_compact, shortDate, variable, delta)
            consensus = res.getString(
                R.string.notification_change_consensus,
                highlight.dominantModels,
                highlight.comparedModels
            )
        }

        val evolutionColor = ContextCompat.getColor(res, evolutionTextColorRes(highlight.variable))
        val lowConfidenceColor = ContextCompat.getColor(res, R.color.notification_text_low_confidence)
        val compact = if (delta != null) {
            styleForecastChangeCompact(
                raw = compactRaw,
                shortDate = shortDate,
                variable = variable,
                delta = delta,
                valueColor = evolutionColor
            )
        } else {
            styleVolatileCompact(
                raw = compactRaw,
                shortDate = shortDate,
                variable = variable,
                warningColor = lowConfidenceColor
            )
        }
        val styledRevision = if (delta != null) {
            styleRevisionLine(
                raw = revisionLine,
                delta = delta,
                valueColor = evolutionColor
            )
        } else {
            styleVolatileRevisionLine(
                raw = revisionLine,
                variable = variable,
                warningColor = lowConfidenceColor
            )
        }
        val expanded = joinStyled(
            listOf(
                styledText(longDate, bold = true),
                styledRevision,
                styleModelConsensus(
                    raw = consensus,
                    values = if (highlight.trend == ForecastEvolutionTrend.VOLATILE) {
                        listOf(highlight.comparedModels.toString())
                    } else {
                        listOf(
                            highlight.dominantModels.toString(),
                            highlight.comparedModels.toString()
                        )
                    },
                    italic = highlight.trend == ForecastEvolutionTrend.VOLATILE
                ),
                styledText(
                    res.getString(R.string.notification_change_reference, highlight.previousAgeHours),
                    italic = true
                )
            ),
            LINE_SEPARATOR
        )

        return RenderedContent(
            channelId = CHANNEL_FORECAST_CHANGE,
            title = title,
            text = compact,
            bigText = expanded,
            accentColorRes = evolutionAccentColorRes(highlight.variable)
        )
    }


    /**
     * Utilise DecoratedCustomViewStyle : contrairement aux spans dans BigTextStyle,
     * ces TextView sont réellement rendues par RemoteViews. Android garde néanmoins
     * toute la décoration système de la notification.
     */
    private fun nativeDecoratedViews(
        notification: WeatherNotification,
        content: RenderedContent,
        res: Context
    ): NativeDecoratedViews {
        val compact = RemoteViews(appContext.packageName, R.layout.notification_weather_compact)
        val expanded = RemoteViews(appContext.packageName, R.layout.notification_weather_expanded)
        compact.setTextViewText(R.id.notification_custom_title, content.title)
        expanded.setTextViewText(R.id.notification_custom_title, content.title)

        val presentation = remotePresentation(notification, res)
        bindCompact(compact, presentation)
        bindExpanded(expanded, presentation)
        return NativeDecoratedViews(compact, expanded)
    }

    private fun remotePresentation(
        notification: WeatherNotification,
        res: Context
    ): RemotePresentation = when (notification) {
        is WeatherNotification.DailySummary -> {
            val locale = res.currentLocale()
            val condition = notification.condition
                ?.takeUnless { it == WeatherCondition.UNKNOWN }
                ?.let { res.getString(weatherConditionLabelRes(it)) }
                ?: res.getString(R.string.notification_channel_daily)
            val precipitation = precipitationText(res, notification, locale)
            val wind = windText(res, notification)
            val temperatureLine = res.getString(
                R.string.notification_daily_temperature_range,
                notification.tempMin.formatDegrees(units = units),
                notification.tempMax.formatDegrees(units = units)
            )
            val agreementLine = notification.convergencePercent?.let {
                res.getString(R.string.notification_daily_agreement, it)
            }
            val weatherColor = ContextCompat.getColor(res, weatherTextColorRes(notification.condition))
            val tempMinColor = ContextCompat.getColor(res, R.color.notification_text_temperature_min)
            val tempMaxColor = ContextCompat.getColor(res, R.color.notification_text_temperature)
            val precipitationColor = ContextCompat.getColor(res, R.color.notification_text_precipitation)
            val windColor = ContextCompat.getColor(res, R.color.notification_text_wind)
            val agreementColor = ContextCompat.getColor(
                res,
                if ((notification.convergencePercent ?: 100) < LOW_CONFIDENCE_PERCENT) {
                    R.color.notification_text_low_confidence
                } else {
                    R.color.notification_text_info
                }
            )
            val temperatureRange = TemperatureRange(
                min = notification.tempMin.formatDegrees(units = units),
                max = notification.tempMax.formatDegrees(units = units),
                minColor = tempMinColor,
                maxColor = tempMaxColor
            )
            RemotePresentation(
                compact = listOfNotNull(
                    RemoteToken(
                        text = condition,
                        color = weatherColor,
                        icon = RemoteIcon.Condition(notification.condition)
                    ),
                    RemoteToken(
                        text = "",
                        color = null,
                        icon = RemoteIcon.Drawable(R.drawable.ic_notification_temperature),
                        temperatureRange = temperatureRange
                    ),
                    precipitation?.let {
                        RemoteToken(
                            text = compactPrecipitationMetricValue(it),
                            color = precipitationColor,
                            icon = RemoteIcon.Drawable(R.drawable.ic_notification_rain)
                        )
                    },
                    wind?.let {
                        RemoteToken(
                            text = metricValue(it),
                            color = windColor,
                            icon = RemoteIcon.Drawable(R.drawable.ic_notification_wind)
                        )
                    }
                ),
                hero = RemoteToken(
                    text = condition,
                    color = weatherColor,
                    icon = RemoteIcon.Condition(notification.condition)
                ),
                rows = listOfNotNull(
                    MetricRow(
                        label = metricLabel(temperatureLine),
                        value = null,
                        valueColor = null,
                        icon = RemoteIcon.Drawable(R.drawable.ic_notification_temperature),
                        temperatureRange = temperatureRange
                    ),
                    precipitation?.toPrecipitationMetricRow(
                        valueColor = precipitationColor,
                        icon = RemoteIcon.Drawable(R.drawable.ic_notification_rain)
                    ),
                    wind?.toMetricRow(
                        valueColor = windColor,
                        icon = RemoteIcon.Drawable(R.drawable.ic_notification_wind)
                    ),
                    agreementLine?.toMetricRow(agreementColor)
                ),
                detail = null
            )
        }

        is WeatherNotification.ModelDivergence -> {
            val day = res.getString(
                if (notification.isToday) R.string.notification_day_today
                else R.string.notification_day_tomorrow
            )
            val agreement = res.getString(
                R.string.notification_divergence_agreement,
                notification.convergencePercent
            )
            val warningColor = ContextCompat.getColor(res, R.color.notification_text_low_confidence)
            RemotePresentation(
                compact = listOf(
                    RemoteToken(day, null),
                    RemoteToken(agreement, warningColor)
                ),
                hero = RemoteToken(day, null),
                rows = listOf(agreement.toMetricRow(warningColor)),
                detail = res.getString(R.string.notification_divergence_explanation)
            )
        }

        is WeatherNotification.ForecastChange -> {
            val highlight = notification.highlight
            val locale = res.currentLocale()
            val longDate = highlight.targetDate.format(
                DateTimeFormatter.ofPattern(TARGET_DATE_LONG_PATTERN, locale)
            )
            val variable = res.getString(variableLabelRes(highlight.variable))
            val valueColor = ContextCompat.getColor(
                res,
                if (highlight.trend == ForecastEvolutionTrend.VOLATILE) {
                    R.color.notification_text_low_confidence
                } else {
                    evolutionTextColorRes(highlight.variable)
                }
            )
            val value = if (highlight.trend == ForecastEvolutionTrend.VOLATILE) {
                val volatile = res.getString(R.string.notification_change_volatile_compact, "", variable)
                volatile.substringAfterLast(PART_SEPARATOR).trim()
            } else {
                formatSignedDelta(highlight.medianDelta, highlight.variable, locale, units = units)
            }
            val hero = if (highlight.trend == ForecastEvolutionTrend.VOLATILE) {
                res.getString(R.string.notification_change_volatile_line, variable).substringAfter(PART_SEPARATOR)
            } else {
                res.getString(evolutionHighlightTitleRes(highlight))
            }
            val consensus = if (highlight.trend == ForecastEvolutionTrend.VOLATILE) {
                res.getString(R.string.notification_change_consensus_volatile, highlight.comparedModels)
            } else {
                res.getString(
                    R.string.notification_change_consensus,
                    highlight.dominantModels,
                    highlight.comparedModels
                )
            }
            val reference = res.getString(
                R.string.notification_change_reference,
                highlight.previousAgeHours
            )
            val variableIcon = RemoteIcon.Drawable(variableIconRes(highlight.variable))
            RemotePresentation(
                compact = listOf(
                    RemoteToken(variable, null, icon = variableIcon),
                    RemoteToken(value, valueColor)
                ),
                hero = RemoteToken(hero, valueColor),
                rows = listOf(
                    MetricRow(variable, value, valueColor, icon = variableIcon),
                    MetricRow(consensus, null, null)
                ),
                detail = "$longDate\n$reference"
            )
        }
    }

    private fun bindCompact(remoteViews: RemoteViews, presentation: RemotePresentation) {
        val groups = listOf(
            Triple(R.id.notification_compact_group_1, R.id.notification_compact_primary, R.id.notification_compact_icon_1),
            Triple(R.id.notification_compact_group_2, R.id.notification_compact_secondary, R.id.notification_compact_icon_2),
            Triple(R.id.notification_compact_group_3, R.id.notification_compact_tertiary, R.id.notification_compact_icon_3),
            Triple(R.id.notification_compact_group_4, R.id.notification_compact_quaternary, R.id.notification_compact_icon_4)
        )
        groups.forEachIndexed { index, ids ->
            val token = presentation.compact.getOrNull(index)
            remoteViews.setViewVisibility(ids.first, if (token == null) View.GONE else View.VISIBLE)
            if (token == null) return@forEachIndexed

            bindIcon(remoteViews, ids.third, token.icon, compact = true)
            val isTemperatureRange = index == 1 && token.temperatureRange != null
            remoteViews.setViewVisibility(ids.second, if (isTemperatureRange) View.GONE else View.VISIBLE)
            if (!isTemperatureRange) {
                remoteViews.setTextViewText(ids.second, token.text)
                token.color?.let { color -> remoteViews.setTextColor(ids.second, color) }
            }
            if (index == 1) {
                bindCompactTemperatureRange(remoteViews, token.temperatureRange)
            }
        }
        if (presentation.compact.size < 2) {
            bindCompactTemperatureRange(remoteViews, null)
        }
    }

    private fun bindCompactTemperatureRange(
        remoteViews: RemoteViews,
        range: TemperatureRange?
    ) {
        remoteViews.setViewVisibility(
            R.id.notification_compact_temperature_range,
            if (range == null) View.GONE else View.VISIBLE
        )
        if (range == null) return
        remoteViews.setTextViewText(R.id.notification_compact_temp_min, range.min)
        remoteViews.setTextViewText(R.id.notification_compact_temp_max, range.max)
        remoteViews.setTextColor(R.id.notification_compact_temp_min, range.minColor)
        remoteViews.setTextColor(R.id.notification_compact_temp_max, range.maxColor)
        remoteViews.setTextColor(
            R.id.notification_compact_temp_separator,
            ContextCompat.getColor(appContext, R.color.notification_text_separator)
        )
    }

    private fun bindExpanded(remoteViews: RemoteViews, presentation: RemotePresentation) {
        remoteViews.setViewVisibility(
            R.id.notification_custom_hero_container,
            if (presentation.hero == null) View.GONE else View.VISIBLE
        )
        presentation.hero?.let { hero ->
            remoteViews.setTextViewText(R.id.notification_custom_hero, hero.text)
            hero.color?.let { remoteViews.setTextColor(R.id.notification_custom_hero, it) }
            bindIcon(remoteViews, R.id.notification_custom_hero_icon, hero.icon, compact = false)
        }

        val rowIds = listOf(
            RowViewIds(
                R.id.notification_custom_row_1,
                R.id.notification_custom_row_1_icon,
                R.id.notification_custom_row_1_label,
                R.id.notification_custom_row_1_value
            ),
            RowViewIds(
                R.id.notification_custom_row_2,
                R.id.notification_custom_row_2_icon,
                R.id.notification_custom_row_2_label,
                R.id.notification_custom_row_2_value
            ),
            RowViewIds(
                R.id.notification_custom_row_3,
                R.id.notification_custom_row_3_icon,
                R.id.notification_custom_row_3_label,
                R.id.notification_custom_row_3_value
            ),
            RowViewIds(
                R.id.notification_custom_row_4,
                R.id.notification_custom_row_4_icon,
                R.id.notification_custom_row_4_label,
                R.id.notification_custom_row_4_value
            )
        )
        rowIds.forEachIndexed { index, ids ->
            val row = presentation.rows.getOrNull(index)
            remoteViews.setViewVisibility(ids.container, if (row == null) View.GONE else View.VISIBLE)
            if (row == null) return@forEachIndexed

            bindIcon(remoteViews, ids.icon, row.icon, compact = false)
            remoteViews.setTextViewText(ids.label, row.label)

            val isTemperatureRange = index == 0 && row.temperatureRange != null
            remoteViews.setViewVisibility(ids.value, if (row.value == null || isTemperatureRange) View.GONE else View.VISIBLE)
            if (!isTemperatureRange) {
                row.value?.let { value -> remoteViews.setTextViewText(ids.value, value) }
                row.valueColor?.let { color -> remoteViews.setTextColor(ids.value, color) }
            }
            if (index == 0) {
                bindExpandedTemperatureRange(remoteViews, row.temperatureRange)
            }
        }
        if (presentation.rows.isEmpty()) {
            bindExpandedTemperatureRange(remoteViews, null)
        }

        remoteViews.setViewVisibility(
            R.id.notification_custom_detail,
            if (presentation.detail.isNullOrBlank()) View.GONE else View.VISIBLE
        )
        presentation.detail?.let { remoteViews.setTextViewText(R.id.notification_custom_detail, it) }
    }

    private fun bindExpandedTemperatureRange(
        remoteViews: RemoteViews,
        range: TemperatureRange?
    ) {
        remoteViews.setViewVisibility(
            R.id.notification_expanded_temperature_range,
            if (range == null) View.GONE else View.VISIBLE
        )
        if (range == null) return
        remoteViews.setTextViewText(R.id.notification_expanded_temp_min, range.min)
        remoteViews.setTextViewText(R.id.notification_expanded_temp_max, range.max)
        remoteViews.setTextColor(R.id.notification_expanded_temp_min, range.minColor)
        remoteViews.setTextColor(R.id.notification_expanded_temp_max, range.maxColor)
        remoteViews.setTextColor(
            R.id.notification_expanded_temp_separator,
            ContextCompat.getColor(appContext, R.color.notification_text_separator)
        )
    }

    private fun bindIcon(
        remoteViews: RemoteViews,
        viewId: Int,
        icon: RemoteIcon?,
        compact: Boolean
    ) {
        remoteViews.setViewVisibility(viewId, if (icon == null) View.GONE else View.VISIBLE)
        when (icon) {
            null -> Unit
            is RemoteIcon.Drawable -> remoteViews.setImageViewResource(viewId, icon.resId)
            is RemoteIcon.Condition -> remoteViews.setImageViewBitmap(
                viewId,
                WidgetWeatherIconRenderer.render(
                    condition = icon.condition,
                    sizePx = dpToPx(if (compact) 18 else 22)
                )
            )
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * appContext.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)

    private data class NativeDecoratedViews(val compact: RemoteViews, val expanded: RemoteViews)
    private data class RemoteToken(
        val text: String,
        val color: Int?,
        val icon: RemoteIcon? = null,
        val temperatureRange: TemperatureRange? = null
    )
    internal data class MetricRow(
        val label: String,
        val value: String?,
        val valueColor: Int?,
        val icon: RemoteIcon? = null,
        val temperatureRange: TemperatureRange? = null
    )
    private data class RemotePresentation(
        val compact: List<RemoteToken>,
        val hero: RemoteToken?,
        val rows: List<MetricRow>,
        val detail: String?
    )
    internal data class TemperatureRange(
        val min: String,
        val max: String,
        val minColor: Int,
        val maxColor: Int
    )
    private data class RowViewIds(
        val container: Int,
        val icon: Int,
        val label: Int,
        val value: Int
    )

    /**
     * Même comportement que l'icône du lanceur : ramène la tâche existante au
     * premier plan sans recréer l'activité, ou démarre l'application.
     */
    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    internal enum class PostResult {
        POSTED,
        BLOCKED_PERMISSION,
        BLOCKED_APP,
        BLOCKED_CHANNEL
    }

    internal data class RenderedContent(
        val channelId: String,
        val title: String,
        val text: CharSequence,
        val bigText: CharSequence,
        @param:ColorRes val accentColorRes: Int
    )

    internal data class BuiltNotificationForTest(
        val notification: Notification,
        val compactView: RemoteViews,
        val expandedView: RemoteViews
    )

    companion object {
        const val CHANNEL_DAILY_SUMMARY = "weather_daily_summary"
        const val CHANNEL_DIVERGENCE = "weather_model_divergence"
        const val CHANNEL_FORECAST_CHANGE = "weather_forecast_change"

        private const val PART_SEPARATOR = " · "
        private const val LINE_SEPARATOR = "\n"
        private const val TARGET_DATE_SHORT_PATTERN = "EEE d MMM"
        private const val TARGET_DATE_LONG_PATTERN = "EEEE d MMMM"
        private const val MIN_DISPLAYED_PRECIPITATION_MM = 0.1
        private const val LOW_CONFIDENCE_PERCENT = 50

        /**
         * Un identifiant stable par (nature, ville) : une nouvelle alerte du
         * même type pour la même ville remplace la précédente au lieu de s'empiler.
         */
        internal fun notificationId(notification: WeatherNotification): Int {
            val kind = when (notification) {
                is WeatherNotification.DailySummary -> "daily"
                is WeatherNotification.ModelDivergence -> "divergence"
                is WeatherNotification.ForecastChange -> "change"
            }
            return "$kind|${notification.city.id}".hashCode()
        }
    }
}

internal sealed interface RemoteIcon {
    data class Drawable(@param:DrawableRes val resId: Int) : RemoteIcon
    data class Condition(val condition: WeatherCondition?) : RemoteIcon
}

private fun String.toMetricRow(
    valueColor: Int,
    icon: RemoteIcon? = null
): WeatherNotifier.MetricRow =
    WeatherNotifier.MetricRow(metricLabel(this), metricValue(this), valueColor, icon = icon)

private fun String.toPrecipitationMetricRow(
    valueColor: Int,
    icon: RemoteIcon? = null
): WeatherNotifier.MetricRow =
    WeatherNotifier.MetricRow(
        substringBefore(' ').trim(),
        precipitationMetricValue(this),
        valueColor,
        icon = icon
    )

private fun precipitationMetricValue(raw: String): String =
    raw.substringAfter(' ', raw).trim()

private fun compactPrecipitationMetricValue(raw: String): String =
    precipitationMetricValue(raw).substringBefore(" · ").trim()

private fun metricLabel(raw: String): String {
    val separator = raw.indexOf(" · ")
    if (separator > 0) return raw.substring(0, separator).trim()
    val firstSpace = raw.indexOf(' ')
    return if (firstSpace > 0) raw.substring(0, firstSpace).trim() else raw.trim()
}

private fun metricValue(raw: String): String {
    val separator = raw.indexOf(" · ")
    if (separator > 0) return raw.substring(separator + 3).trim()
    val firstSpace = raw.indexOf(' ')
    return if (firstSpace > 0) raw.substring(firstSpace + 1).trim() else raw.trim()
}

private fun styledText(
    text: CharSequence,
    bold: Boolean = false,
    italic: Boolean = false,
    color: Int? = null
): CharSequence = SpannableStringBuilder(text).apply {
    if (isEmpty()) return@apply
    if (bold) setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    if (italic) setSpan(StyleSpan(Typeface.ITALIC), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    if (color != null) setSpan(ForegroundColorSpan(color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

private fun joinStyled(parts: List<CharSequence>, separator: String): CharSequence =
    SpannableStringBuilder().apply {
        parts.forEachIndexed { index, part ->
            if (index > 0) append(separator)
            append(part)
        }
    }

/**
 * Donne une hiérarchie visuelle à une ligne de métrique sans dépendre de la
 * langue : le libellé avant le premier séparateur (ou premier espace) est en
 * gras, et les valeurs transmises sont en gras + couleur sémantique.
 */
private fun styleTemperatureValues(
    raw: String,
    tempMin: String,
    tempMax: String,
    tempMinColor: Int,
    tempMaxColor: Int
): CharSequence = SpannableStringBuilder(raw).apply {
    val labelEnd = metricLabelEnd(raw)
    if (labelEnd > 0 && raw.contains(" · ")) {
        setSpan(StyleSpan(Typeface.BOLD), 0, labelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    val minStart = raw.indexOf(tempMin)
    if (minStart >= 0) {
        val minEnd = minStart + tempMin.length
        setSpan(StyleSpan(Typeface.BOLD), minStart, minEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(ForegroundColorSpan(tempMinColor), minStart, minEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val maxStart = raw.indexOf(tempMax, minEnd)
        if (maxStart >= 0) {
            val maxEnd = maxStart + tempMax.length
            setSpan(StyleSpan(Typeface.BOLD), maxStart, maxEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(tempMaxColor), maxStart, maxEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}

private fun styleMetricLine(
    raw: String,
    emphasizedValues: List<String>,
    valueColor: Int
): CharSequence = SpannableStringBuilder(raw).apply {
    val labelEnd = metricLabelEnd(raw)
    if (labelEnd > 0) {
        setSpan(StyleSpan(Typeface.BOLD), 0, labelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    emphasizedValues.filter { it.isNotBlank() }.forEach { value ->
        applyToOccurrences(value) { start, end ->
            setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(valueColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}

/** Compact : toutes les valeurs sont colorées, la première peut rester neutre. */
private fun styleValues(
    raw: String,
    values: List<String>,
    color: Int,
    firstValueBoldOnly: Boolean = false
): CharSequence = SpannableStringBuilder(raw).apply {
    values.filter { it.isNotBlank() }.forEachIndexed { index, value ->
        applyToOccurrences(value) { start, end ->
            setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (!(firstValueBoldOnly && index == 0)) {
                setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}

private fun styleForecastChangeCompact(
    raw: String,
    shortDate: String,
    variable: String,
    delta: String,
    valueColor: Int
): CharSequence = SpannableStringBuilder(raw).apply {
    applyToOccurrences(shortDate) { start, end ->
        setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    applyToOccurrences(variable) { start, end ->
        setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    applyToOccurrences(delta) { start, end ->
        setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(ForegroundColorSpan(valueColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private fun styleVolatileCompact(
    raw: String,
    shortDate: String,
    variable: String,
    warningColor: Int
): CharSequence = SpannableStringBuilder(raw).apply {
    applyToOccurrences(shortDate) { start, end ->
        setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    applyToOccurrences(variable) { start, end ->
        setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    val warningStart = toString().lastIndexOf(" · ").takeIf { it >= 0 }?.plus(3) ?: -1
    if (warningStart in 0 until length) {
        setSpan(StyleSpan(Typeface.BOLD), warningStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(ForegroundColorSpan(warningColor), warningStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private fun styleRevisionLine(raw: String, delta: String, valueColor: Int): CharSequence =
    SpannableStringBuilder(raw).apply {
        val separator = toString().indexOf(" · ")
        if (separator > 0) {
            setSpan(StyleSpan(Typeface.BOLD), 0, separator, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        applyToOccurrences(delta) { start, end ->
            setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(valueColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

private fun styleVolatileRevisionLine(
    raw: String,
    variable: String,
    warningColor: Int
): CharSequence = SpannableStringBuilder(raw).apply {
    applyToOccurrences(variable) { start, end ->
        setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    val separator = toString().indexOf(" · ")
    if (separator >= 0 && separator + 3 < length) {
        setSpan(StyleSpan(Typeface.ITALIC), separator + 3, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(ForegroundColorSpan(warningColor), separator + 3, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private fun styleModelConsensus(raw: String, values: List<String>, italic: Boolean): CharSequence =
    SpannableStringBuilder(raw).apply {
        if (italic && isNotEmpty()) {
            setSpan(StyleSpan(Typeface.ITALIC), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        values.filter { it.isNotBlank() }.forEach { value ->
            applyToOccurrences(value) { start, end ->
                setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

private fun metricLabelEnd(raw: String): Int {
    val separator = raw.indexOf(" · ")
    if (separator > 0) return separator
    val firstSpace = raw.indexOf(' ')
    return if (firstSpace > 0) firstSpace else raw.length
}

private inline fun SpannableStringBuilder.applyToOccurrences(
    value: String,
    action: (start: Int, end: Int) -> Unit
) {
    if (value.isEmpty()) return
    var fromIndex = 0
    while (fromIndex < length) {
        val start = toString().indexOf(value, fromIndex)
        if (start < 0) break
        val end = start + value.length
        action(start, end)
        fromIndex = end
    }
}

private fun Double?.formatDegrees(units: WeatherUnits): String = units.temp(this)

private fun Double?.formatWindValue(units: WeatherUnits): String =
    units.value(this, WeatherUnit.WIND_SPEED)

private fun formatSignedDelta(
    value: Double,
    variable: ForecastEvolutionVariable,
    locale: Locale,
    units: WeatherUnits
): String {
    val unit = when (variable) {
        ForecastEvolutionVariable.TEMPERATURE -> WeatherUnit.TEMPERATURE
        ForecastEvolutionVariable.PRECIPITATION -> WeatherUnit.PRECIPITATION
        ForecastEvolutionVariable.WIND -> WeatherUnit.WIND_SPEED
    }
    return units.signedDelta(value, unit,
        if (variable == ForecastEvolutionVariable.WIND) 0 else 1, locale)
}

private fun variableLabelRes(variable: ForecastEvolutionVariable): Int = when (variable) {
    ForecastEvolutionVariable.TEMPERATURE -> R.string.notification_change_variable_temperature
    ForecastEvolutionVariable.PRECIPITATION -> R.string.notification_change_variable_precipitation
    ForecastEvolutionVariable.WIND -> R.string.notification_change_variable_wind
}

@DrawableRes
private fun variableIconRes(variable: ForecastEvolutionVariable): Int = when (variable) {
    ForecastEvolutionVariable.TEMPERATURE -> R.drawable.ic_notification_temperature
    ForecastEvolutionVariable.PRECIPITATION -> R.drawable.ic_notification_rain
    ForecastEvolutionVariable.WIND -> R.drawable.ic_notification_wind
}

@ColorRes
private fun evolutionAccentColorRes(variable: ForecastEvolutionVariable): Int = when (variable) {
    ForecastEvolutionVariable.TEMPERATURE -> R.color.notification_accent_temperature
    ForecastEvolutionVariable.PRECIPITATION -> R.color.notification_accent_precipitation
    ForecastEvolutionVariable.WIND -> R.color.notification_accent_wind
}

@ColorRes
private fun evolutionTextColorRes(variable: ForecastEvolutionVariable): Int = when (variable) {
    ForecastEvolutionVariable.TEMPERATURE -> R.color.notification_text_temperature
    ForecastEvolutionVariable.PRECIPITATION -> R.color.notification_text_precipitation
    ForecastEvolutionVariable.WIND -> R.color.notification_text_wind
}

@ColorRes
private fun weatherTextColorRes(condition: WeatherCondition?): Int = when (condition) {
    WeatherCondition.RAIN,
    WeatherCondition.RAIN_SHOWERS,
    WeatherCondition.DRIZZLE,
    WeatherCondition.FREEZING_RAIN,
    WeatherCondition.SNOW,
    WeatherCondition.SNOW_SHOWERS -> R.color.notification_text_precipitation
    WeatherCondition.THUNDERSTORM -> R.color.notification_text_low_confidence
    else -> R.color.notification_text_weather
}

/** Palette cohérente avec WeatherAccent, utilisée ici par le template système. */
@ColorRes
private fun weatherAccentColorRes(condition: WeatherCondition?): Int = when (condition) {
    WeatherCondition.CLEAR,
    WeatherCondition.MAINLY_CLEAR -> R.color.notification_accent_sunny
    WeatherCondition.PARTLY_CLOUDY -> R.color.notification_accent_partly_cloudy
    WeatherCondition.OVERCAST -> R.color.notification_accent_overcast
    WeatherCondition.FOG -> R.color.notification_accent_fog
    WeatherCondition.DRIZZLE,
    WeatherCondition.RAIN,
    WeatherCondition.RAIN_SHOWERS -> R.color.notification_accent_rain
    WeatherCondition.FREEZING_RAIN -> R.color.notification_accent_freezing_rain
    WeatherCondition.SNOW,
    WeatherCondition.SNOW_SHOWERS -> R.color.notification_accent_snow
    WeatherCondition.THUNDERSTORM -> R.color.notification_accent_thunderstorm
    null,
    WeatherCondition.UNKNOWN -> R.color.notification_accent_default
}

private fun Context.currentLocale(): Locale = resources.configuration.locales[0]
