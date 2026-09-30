package com.meteocompare.app.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.ColorRes
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
internal class WeatherNotifier(context: Context) {

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

        val built = NotificationCompat.Builder(appContext, content.channelId)
            .setSmallIcon(R.drawable.ic_stat_meteocompare)
            .setColor(ContextCompat.getColor(appContext, content.accentColorRes))
            .setColorized(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(content.title)
                    .bigText(content.bigText)
            )
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(notificationId(notification), built)
        return PostResult.POSTED
    }

    /**
     * Rendu textuel final séparé de la publication, afin de pouvoir tester la
     * lisibilité/localisation sans dépendre de l'état des canaux Android.
     */
    internal fun render(notification: WeatherNotification): RenderedContent =
        render(notification, applyPersistedLocale(appContext))

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
            summary.tempMin.formatDegrees(),
            summary.tempMax.formatDegrees()
        )
        val precipitation = precipitationText(res, summary, locale)
        val agreement = summary.convergencePercent?.let {
            res.getString(R.string.notification_daily_agreement, it)
        }

        val compact = listOfNotNull(condition, temperatures, precipitation)
            .joinToString(PART_SEPARATOR)
        val expanded = listOfNotNull(
            condition,
            res.getString(
                R.string.notification_daily_temperature_range,
                summary.tempMin.formatDegrees(),
                summary.tempMax.formatDegrees()
            ),
            precipitation,
            agreement
        ).joinToString(LINE_SEPARATOR)

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
            ?.let { String.format(locale, "%.1f", it) }
        val probability = summary.precipitationProbabilityPercent
        return when {
            probability != null && amount != null -> res.getString(
                R.string.notification_daily_precipitation_with_amount,
                probability,
                amount
            )
            probability != null -> res.getString(R.string.notification_daily_precipitation, probability)
            amount != null -> res.getString(R.string.notification_daily_precipitation_amount, amount)
            else -> null
        }
    }

    private fun divergence(res: Context, divergence: WeatherNotification.ModelDivergence): RenderedContent {
        val day = res.getString(
            if (divergence.isToday) R.string.notification_day_today
            else R.string.notification_day_tomorrow
        )
        val title = res.getString(R.string.notification_divergence_title, divergence.city.name)
        val agreement = res.getString(R.string.notification_divergence_agreement, divergence.convergencePercent)
        val compact = res.getString(R.string.notification_divergence_compact, day, divergence.convergencePercent)
        val expanded = listOf(
            day,
            agreement,
            res.getString(R.string.notification_divergence_explanation)
        ).joinToString(LINE_SEPARATOR)
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
        val compact: String
        val consensus: String
        if (highlight.trend == ForecastEvolutionTrend.VOLATILE) {
            revisionLine = res.getString(R.string.notification_change_volatile_line, variable)
            compact = res.getString(R.string.notification_change_volatile_compact, shortDate, variable)
            consensus = res.getString(
                R.string.notification_change_consensus_volatile,
                highlight.comparedModels
            )
        } else {
            val delta = formatSignedDelta(highlight.medianDelta, highlight.variable, locale)
            revisionLine = res.getString(
                R.string.notification_change_revision_line,
                res.getString(evolutionHighlightTitleRes(highlight)),
                delta
            )
            compact = res.getString(R.string.notification_change_compact, shortDate, variable, delta)
            consensus = res.getString(
                R.string.notification_change_consensus,
                highlight.dominantModels,
                highlight.comparedModels
            )
        }

        val expanded = listOf(
            longDate,
            revisionLine,
            consensus,
            res.getString(R.string.notification_change_reference, highlight.previousAgeHours)
        ).joinToString(LINE_SEPARATOR)

        return RenderedContent(
            channelId = CHANNEL_FORECAST_CHANGE,
            title = title,
            text = compact,
            bigText = expanded,
            accentColorRes = evolutionAccentColorRes(highlight.variable)
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

    internal enum class PostResult {
        POSTED,
        BLOCKED_PERMISSION,
        BLOCKED_APP,
        BLOCKED_CHANNEL
    }

    internal data class RenderedContent(
        val channelId: String,
        val title: String,
        val text: String,
        val bigText: String,
        @ColorRes val accentColorRes: Int
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

private fun formatSignedDelta(
    value: Double,
    variable: ForecastEvolutionVariable,
    locale: Locale
): String {
    val sign = when {
        value > 0.0 -> "+"
        value < 0.0 -> "−"
        else -> ""
    }
    val magnitude = abs(value)
    return when (variable) {
        ForecastEvolutionVariable.TEMPERATURE ->
            "$sign${String.format(locale, "%.1f", magnitude)} °C"
        ForecastEvolutionVariable.PRECIPITATION ->
            "$sign${String.format(locale, "%.1f", magnitude)} mm"
        ForecastEvolutionVariable.WIND -> "$sign${magnitude.roundToInt()} km/h"
    }
}

private fun variableLabelRes(variable: ForecastEvolutionVariable): Int = when (variable) {
    ForecastEvolutionVariable.TEMPERATURE -> R.string.notification_change_variable_temperature
    ForecastEvolutionVariable.PRECIPITATION -> R.string.notification_change_variable_precipitation
    ForecastEvolutionVariable.WIND -> R.string.notification_change_variable_wind
}

@ColorRes
private fun evolutionAccentColorRes(variable: ForecastEvolutionVariable): Int = when (variable) {
    ForecastEvolutionVariable.TEMPERATURE -> R.color.notification_accent_temperature
    ForecastEvolutionVariable.PRECIPITATION -> R.color.notification_accent_precipitation
    ForecastEvolutionVariable.WIND -> R.color.notification_accent_wind
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
