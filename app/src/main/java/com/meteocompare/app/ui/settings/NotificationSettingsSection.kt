package com.meteocompare.app.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.NotificationSettings
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Section « Notifications » des Réglages : trois types indépendants, heure
 * du résumé quotidien et choix des villes favorites suivies.
 *
 * Composable sans état métier : la persistance, la planification et la
 * demande de permission restent dans l'écran et le ViewModel.
 */
@Composable
internal fun NotificationSettingsSection(
    settings: NotificationSettings,
    favorites: List<City>,
    notificationsBlocked: Boolean,
    onDailySummaryToggled: (Boolean) -> Unit,
    onDailySummaryTimeSelected: (LocalTime) -> Unit,
    onDivergenceAlertsToggled: (Boolean) -> Unit,
    onForecastChangeAlertsToggled: (Boolean) -> Unit,
    onCityToggled: (String, Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.padding(16.dp).testTag(TAG_SETTINGS_NOTIFICATIONS)) {
        Text(
            text = stringResource(R.string.settings_notifications_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_notifications_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (notificationsBlocked && settings.anyEnabled) {
            Spacer(Modifier.height(12.dp))
            BlockedNotificationsBanner(onOpenSystemSettings = onOpenSystemSettings)
        }

        Spacer(Modifier.height(8.dp))
        NotificationToggleRow(
            title = stringResource(R.string.settings_notifications_daily),
            description = stringResource(R.string.settings_notifications_daily_desc),
            checked = settings.dailySummaryEnabled,
            onCheckedChange = onDailySummaryToggled,
            tag = TAG_SETTINGS_NOTIFICATION_DAILY
        )
        if (settings.dailySummaryEnabled) {
            OutlinedButton(
                onClick = { showTimePicker = true },
                modifier = Modifier.testTag(TAG_SETTINGS_NOTIFICATION_TIME)
            ) {
                Text(
                    stringResource(
                        R.string.settings_notifications_daily_time,
                        formatTime(settings.dailySummaryTime)
                    )
                )
            }
        }
        NotificationToggleRow(
            title = stringResource(R.string.settings_notifications_divergence),
            description = stringResource(R.string.settings_notifications_divergence_desc),
            checked = settings.divergenceAlertsEnabled,
            onCheckedChange = onDivergenceAlertsToggled,
            tag = TAG_SETTINGS_NOTIFICATION_DIVERGENCE
        )
        NotificationToggleRow(
            title = stringResource(R.string.settings_notifications_change),
            description = stringResource(R.string.settings_notifications_change_desc),
            checked = settings.forecastChangeAlertsEnabled,
            onCheckedChange = onForecastChangeAlertsToggled,
            tag = TAG_SETTINGS_NOTIFICATION_CHANGE
        )

        if (settings.anyEnabled) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_notifications_cities),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (favorites.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_notifications_no_city),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                favorites.forEach { city ->
                    NotificationCityRow(
                        city = city,
                        checked = city.id in settings.cityIds,
                        onCheckedChange = { followed -> onCityToggled(city.id, followed) }
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        DailySummaryTimeDialog(
            initial = settings.dailySummaryTime,
            onDismiss = { showTimePicker = false },
            onConfirm = { time ->
                showTimePicker = false
                onDailySummaryTimeSelected(time)
            }
        )
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        // onCheckedChange = null : la ligne entière porte l'action et la sémantique.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun NotificationCityRow(
    city: City,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp)
            .testTag(TAG_SETTINGS_NOTIFICATION_CITY_PREFIX + city.id),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(text = city.name, style = MaterialTheme.typography.bodyMedium)
            val location = listOfNotNull(city.admin1, city.country.takeIf(String::isNotBlank))
                .joinToString(", ")
            if (location.isNotEmpty()) {
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BlockedNotificationsBanner(onOpenSystemSettings: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(TAG_SETTINGS_NOTIFICATIONS_BLOCKED),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_notifications_blocked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenSystemSettings) {
                Text(stringResource(R.string.settings_notifications_open_system))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailySummaryTimeDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_notifications_daily)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.settings_notifications_time_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun formatTime(time: LocalTime): String {
    val locale = LocalConfiguration.current.locales[0]
    return time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
}

internal const val TAG_SETTINGS_NOTIFICATIONS = "settings_notifications"
internal const val TAG_SETTINGS_NOTIFICATIONS_BLOCKED = "settings_notifications_blocked"
internal const val TAG_SETTINGS_NOTIFICATION_DAILY = "settings_notification_daily"
internal const val TAG_SETTINGS_NOTIFICATION_TIME = "settings_notification_time"
internal const val TAG_SETTINGS_NOTIFICATION_DIVERGENCE = "settings_notification_divergence"
internal const val TAG_SETTINGS_NOTIFICATION_CHANGE = "settings_notification_change"
internal const val TAG_SETTINGS_NOTIFICATION_CITY_PREFIX = "settings_notification_city_"
