package com.meteocompare.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import java.time.Duration
import java.time.Instant
import androidx.compose.ui.res.stringResource

/** Niveau visuel d'ancienneté d'une donnée conservée hors connexion. */
internal enum class OfflineDataAgeLevel { RECENT, AGING, STALE, UNKNOWN }

/** Pure et testable : moins de 6 h, 6–24 h, puis plus de 24 h. */
internal fun offlineDataAgeLevel(
    fetchedAt: Instant?,
    now: Instant = Instant.now()
): OfflineDataAgeLevel {
    if (fetchedAt == null) return OfflineDataAgeLevel.UNKNOWN
    val age = Duration.between(fetchedAt, now).coerceAtLeast(Duration.ZERO)
    return when {
        age < Duration.ofHours(6) -> OfflineDataAgeLevel.RECENT
        age < Duration.ofHours(24) -> OfflineDataAgeLevel.AGING
        else -> OfflineDataAgeLevel.STALE
    }
}

/**
 * Bannière compacte affichée quand l'écran fonctionne avec les données Room
 * sans connexion validée. L'âge se met à jour automatiquement grâce au même
 * formateur que les cartes météo.
 */
@Composable
fun OfflineDataBanner(
    fetchedAt: Instant?,
    modifier: Modifier = Modifier
) {
    val level = offlineDataAgeLevel(fetchedAt)
    val scheme = MaterialTheme.colorScheme
    val containerColor = when (level) {
        OfflineDataAgeLevel.RECENT -> scheme.secondaryContainer.copy(alpha = 0.62f)
        OfflineDataAgeLevel.AGING -> scheme.tertiaryContainer.copy(alpha = 0.70f)
        OfflineDataAgeLevel.STALE -> scheme.errorContainer.copy(alpha = 0.78f)
        OfflineDataAgeLevel.UNKNOWN -> scheme.surfaceContainerHigh
    }
    val contentColor = when (level) {
        OfflineDataAgeLevel.RECENT -> scheme.onSecondaryContainer
        OfflineDataAgeLevel.AGING -> scheme.onTertiaryContainer
        OfflineDataAgeLevel.STALE -> scheme.onErrorContainer
        OfflineDataAgeLevel.UNKNOWN -> scheme.onSurface
    }

    val ageText = if (fetchedAt != null) {
        rememberFormattedLastUpdated(fetchedAt)
    } else {
        stringResource(R.string.offline_data_age_unknown)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.offline_data_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.offline_data_message, ageText),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
