package com.meteocompare.app.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.meteocompare.app.MainActivity
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.applyPersistedLocale
import com.meteocompare.app.core.locale.weatherConditionLabelRes
import com.meteocompare.app.domain.model.ForecastEvolutionTrend
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherNotification
import com.meteocompare.app.ui.citydetail.evolutionHighlightTitle
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Construit et publie les notifications Android à partir des contenus
 * [WeatherNotification] calculés par le domaine.
 *
 * Les textes sont résolus avec la langue choisie dans l'application (et non
 * celle du système) via [applyPersistedLocale], comme les widgets.
 */
internal class WeatherNotifier(context: Context) {

    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)

    /** Faux si l'utilisateur a bloqué les notifications ou refusé la permission (Android 13+). */
    fun canPost(): Boolean = manager.areNotificationsEnabled() && hasPostPermission()

    // La permission POST_NOTIFICATIONS est vérifiée en tête de méthode ;
    // Lint ne suit pas cette vérification à travers hasPostPermission().
    @SuppressLint("MissingPermission")
    fun post(notification: WeatherNotification) {
        if (!hasPostPermission()) return
        val res = applyPersistedLocale(appContext)
        createChannels(res)

        val content = when (notification) {
            is WeatherNotification.DailySummary -> dailySummary(res, notification)
            is WeatherNotification.ModelDivergence -> divergence(res, notification)
            is WeatherNotification.ForecastChange -> forecastChange(res, notification)
        }
        val built = NotificationCompat.Builder(appContext, content.channelId)
            .setSmallIcon(R.drawable.ic_stat_meteocompare)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(notificationId(notification), built)
    }

    private fun hasPostPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

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

    private fun dailySummary(res: Context, summary: WeatherNotification.DailySummary): Content {
        val locale = res.currentLocale()
        val title = res.getString(
            if (summary.isToday) R.string.notification_daily_title_today
            else R.string.notification_daily_title_tomorrow,
            summary.city.name
        )
        val precipitationAmount = summary.precipitationAmountMm
            ?.takeIf { it >= MIN_DISPLAYED_PRECIPITATION_MM }
            ?.let { String.format(locale, "%.1f", it) }
        val probability = summary.precipitationProbabilityPercent
        val parts = listOfNotNull(
            summary.condition
                ?.takeUnless { it == WeatherCondition.UNKNOWN }
                ?.let { res.getString(weatherConditionLabelRes(it)) },
            res.getString(
                R.string.notification_daily_temperatures,
                summary.tempMin.formatDegrees(),
                summary.tempMax.formatDegrees()
            ),
            when {
                probability != null && precipitationAmount != null -> res.getString(
                    R.string.notification_daily_precipitation_with_amount,
                    probability,
                    precipitationAmount
                )
                probability != null -> res.getString(R.string.notification_daily_precipitation, probability)
                precipitationAmount != null -> res.getString(
                    R.string.notification_daily_precipitation_amount,
                    precipitationAmount
                )
                else -> null
            },
            summary.convergencePercent?.let { res.getString(R.string.metric_agreement, it) }
        )
        return Content(CHANNEL_DAILY_SUMMARY, title, parts.joinToString(PART_SEPARATOR))
    }

    private fun divergence(res: Context, divergence: WeatherNotification.ModelDivergence): Content =
        Content(
            channelId = CHANNEL_DIVERGENCE,
            title = res.getString(R.string.notification_divergence_title, divergence.city.name),
            text = res.getString(
                if (divergence.isToday) R.string.notification_divergence_text_today
                else R.string.notification_divergence_text_tomorrow,
                divergence.convergencePercent
            )
        )

    private fun forecastChange(res: Context, change: WeatherNotification.ForecastChange): Content {
        val highlight = change.highlight
        val date = highlight.targetDate.format(
            DateTimeFormatter.ofPattern(TARGET_DATE_PATTERN, res.currentLocale())
        )
        val detail = if (highlight.trend == ForecastEvolutionTrend.VOLATILE) {
            res.getString(
                R.string.forecast_evolution_highlight_detail_volatile,
                highlight.comparedModels,
                highlight.previousAgeHours
            )
        } else {
            res.getString(
                R.string.forecast_evolution_highlight_detail,
                highlight.dominantModels,
                highlight.comparedModels,
                highlight.previousAgeHours
            )
        }
        return Content(
            channelId = CHANNEL_FORECAST_CHANGE,
            title = res.getString(R.string.notification_change_title, change.city.name),
            text = res.getString(
                R.string.notification_change_text,
                res.getString(evolutionHighlightTitle(highlight)),
                date,
                detail
            )
        )
    }

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

    private data class Content(val channelId: String, val title: String, val text: String)

    companion object {
        const val CHANNEL_DAILY_SUMMARY = "weather_daily_summary"
        const val CHANNEL_DIVERGENCE = "weather_model_divergence"
        const val CHANNEL_FORECAST_CHANGE = "weather_forecast_change"

        private const val PART_SEPARATOR = " · "
        private const val TARGET_DATE_PATTERN = "EEEE d"
        private const val MIN_DISPLAYED_PRECIPITATION_MM = 0.1

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

private fun Double?.formatDegrees(): String = this?.let { "${it.roundToInt()}°" } ?: "–"

private fun Context.currentLocale(): Locale = resources.configuration.locales[0]
