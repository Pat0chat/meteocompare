package com.meteocompare.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.ui.theme.confidenceColor

/**
 * Flat weather metric used by the Home card and Today summary.
 *
 * Unlike a dashboard tile, this component owns no surface, border or shadow:
 * hierarchy comes from typography, whitespace and a small semantic accent.
 */
enum class WeatherMetricLayout {
    Compact,
    Editorial
}

@Composable
fun WeatherMetric(
    label: String,
    icon: ImageVector,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    unit: String? = null,
    supporting: String? = null,
    confidence: Int? = null,
    layout: WeatherMetricLayout = WeatherMetricLayout.Editorial
) {
    when (layout) {
        WeatherMetricLayout.Compact -> CompactWeatherMetric(
            label = label,
            icon = icon,
            value = value,
            unit = unit,
            supporting = supporting,
            accent = accent,
            modifier = modifier
        )

        WeatherMetricLayout.Editorial -> EditorialWeatherMetric(
            label = label,
            icon = icon,
            value = value,
            unit = unit,
            supporting = supporting,
            confidence = confidence,
            accent = accent,
            modifier = modifier
        )
    }
}

@Composable
private fun CompactWeatherMetric(
    label: String,
    icon: ImageVector,
    value: String,
    unit: String?,
    supporting: String?,
    accent: Color,
    modifier: Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(5.dp))

        MetricValue(
            value = value,
            unit = unit,
            compact = true
        )

        if (!supporting.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = supporting,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.84f),
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EditorialWeatherMetric(
    label: String,
    icon: ImageVector,
    value: String,
    unit: String?,
    supporting: String?,
    confidence: Int?,
    accent: Color,
    modifier: Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(9.dp))

        MetricValue(
            value = value,
            unit = unit,
            compact = false
        )

        if (!supporting.isNullOrBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (confidence != null) {
            Spacer(Modifier.height(if (supporting.isNullOrBlank()) 8.dp else 9.dp))
            MetricConfidence(confidence)
        }
    }
}

@Composable
private fun MetricValue(
    value: String,
    unit: String?,
    compact: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = value,
            style = if (compact) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.titleMedium
            },
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (compact) {
                Modifier.alignByBaseline()
            } else {
                Modifier
                    .weight(1f, fill = false)
                    .alignByBaseline()
            }
        )

        if (!unit.isNullOrBlank()) {
            Spacer(Modifier.width(if (compact) 3.dp else 4.dp))
            Text(
                text = unit,
                style = if (compact) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.bodySmall
                },
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.alignByBaseline()
            )
        }
    }
}

@Composable
private fun MetricConfidence(percent: Int) {
    val color = confidenceColor(percent)
    val labelRes = R.string.metric_agreement

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color = color, shape = CircleShape)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(labelRes, percent),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
