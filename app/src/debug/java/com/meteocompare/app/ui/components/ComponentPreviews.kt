package com.meteocompare.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.preview.MeteoComponentPreview
import com.meteocompare.app.ui.preview.MeteoPreviewSurface
import com.meteocompare.app.ui.preview.PreviewFixtures

@MeteoComponentPreview
@Composable
private fun CollapsibleSectionHeaderPreview() {
    MeteoPreviewSurface {
        Surface(Modifier.padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CollapsibleSectionHeader(
                    text = "Prévisions détaillées",
                    subtitle = "4 modèles · actualisé il y a 8 min",
                    expanded = true,
                    onToggle = {}
                )
                CollapsibleSectionHeader(
                    text = "Fiabilité locale",
                    subtitle = "Historique J+1 sur 30 jours",
                    expanded = false,
                    onToggle = {}
                )
            }
        }
    }
}

private enum class PreviewChoice { HOURLY, DAILY, WEEKLY }

@MeteoComponentPreview
@Composable
private fun ModernStateSelectorPreview() {
    MeteoPreviewSurface {
        var selected by remember { mutableStateOf(PreviewChoice.HOURLY) }
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ModernSlidingSelector(
                options = PreviewChoice.entries,
                selected = selected,
                onSelected = { selected = it },
                label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )
            ModernInlineSelector(
                options = PreviewChoice.entries.take(2),
                selected = selected.takeIf { it != PreviewChoice.WEEKLY } ?: PreviewChoice.HOURLY,
                onSelected = { selected = it },
                label = { if (it == PreviewChoice.HOURLY) "Par heure" else "Par jour" },
                accent = MaterialTheme.colorScheme.secondary
            )
            ModernTextTabs(
                options = PreviewChoice.entries,
                selected = selected,
                onSelected = { selected = it },
                label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModernStateChip(
                    selected = true,
                    onClick = {},
                    label = "Consensus",
                    accent = MaterialTheme.colorScheme.primary
                )
                ModernStateChip(
                    selected = false,
                    onClick = {},
                    label = "Adaptatif",
                    accent = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@MeteoComponentPreview
@Composable
private fun OfflineDataBannerPreview() {
    MeteoPreviewSurface {
        OfflineDataBanner(
            fetchedAt = PreviewFixtures.now.minusSeconds(42 * 60L),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@MeteoComponentPreview
@Composable
private fun OpenMeteoAttributionPreview() {
    MeteoPreviewSurface {
        OpenMeteoAttribution(Modifier.padding(16.dp))
    }
}

@MeteoComponentPreview
@Composable
private fun ShimmerBoxPreview() {
    MeteoPreviewSurface {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ShimmerBox(Modifier.fillMaxWidth().height(22.dp), cornerRadius = 8.dp)
            ShimmerBox(Modifier.fillMaxWidth().height(110.dp), cornerRadius = 16.dp)
        }
    }
}

@MeteoComponentPreview
@Composable
private fun WeatherIconPreview() {
    MeteoPreviewSurface {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            listOf(
                WeatherCondition.CLEAR,
                WeatherCondition.PARTLY_CLOUDY,
                WeatherCondition.RAIN,
                WeatherCondition.THUNDERSTORM,
                WeatherCondition.SNOW
            ).forEach { condition ->
                WeatherIconDecorative(condition = condition, size = 34.dp)
            }
        }
    }
}

@MeteoComponentPreview
@Composable
private fun WeatherMetricPreview() {
    MeteoPreviewSurface {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WeatherMetric(
                label = "Température",
                icon = Icons.Outlined.Thermostat,
                value = "22",
                unit = "°C",
                supporting = "20–24 °C selon les modèles",
                confidence = 88,
                accent = MaterialTheme.colorScheme.error
            )
            WeatherMetric(
                label = "Précipitations",
                icon = Icons.Outlined.WaterDrop,
                value = "72",
                unit = "%",
                supporting = "1,6 mm si pluie",
                confidence = 72,
                accent = MaterialTheme.colorScheme.primary
            )
            WeatherMetric(
                label = "Vent",
                icon = Icons.Outlined.Air,
                value = "22",
                unit = "km/h",
                supporting = "Rafales 37 km/h",
                confidence = 79,
                accent = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@MeteoComponentPreview
@Composable
private fun WindArrowPreview() {
    MeteoPreviewSurface {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            listOf(0, 45, 90, 180, 270, 315).forEach { direction ->
                WindArrow(directionDegrees = direction, size = 24.dp)
            }
        }
    }
}

@MeteoComponentPreview
@Composable
private fun WeatherHeatmapPalettePreview() {
    MeteoPreviewSurface {
        Row(Modifier.padding(16.dp)) {
            listOf(-5.0, 5.0, 15.0, 22.0, 30.0, 38.0).forEach { temp ->
                Surface(
                    modifier = Modifier.size(width = 56.dp, height = 70.dp),
                    color = blendedHeatmapColor(
                        MaterialTheme.colorScheme.surface,
                        temperatureHeatmapColor(temp),
                        0.55f
                    )
                ) {}
            }
        }
    }
}

@MeteoComponentPreview
@Composable
private fun VigilanceCardsPreview() {
    MeteoPreviewSurface {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            VigilanceCompactBanner(
                vigilance = PreviewFixtures.vigilance,
                timezone = "Europe/Paris"
            )
            VigilanceDetailCard(
                vigilance = PreviewFixtures.vigilance,
                timezone = "Europe/Paris"
            )
            MarineCoastalVigilanceBanner(
                alert = PreviewFixtures.coastalVigilance.coastalFloodingAlert,
                timezone = "Europe/Paris"
            )
        }
    }
}
