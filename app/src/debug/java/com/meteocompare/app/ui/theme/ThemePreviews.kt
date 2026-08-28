package com.meteocompare.app.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteocompare.app.ui.preview.MeteoComponentPreview
import com.meteocompare.app.ui.preview.MeteoPreviewSurface

@MeteoComponentPreview
@Composable
private fun MeteoCompareThemePalettePreview() {
    MeteoPreviewSurface {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Material 3 palette", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.error
                ).forEach { color ->
                    Surface(
                        modifier = Modifier.size(56.dp),
                        color = color,
                        shape = MaterialTheme.shapes.medium
                    ) {}
                }
            }
            Text("Convergence", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 55, 78, 92).forEach { percent ->
                    Surface(
                        color = confidenceColor(percent).copy(alpha = 0.14f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            "$percent%",
                            color = confidenceColor(percent),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Text("Accents météo", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Max" to temperatureMetricAccent(),
                    "Min" to temperatureMinMetricAccent(),
                    "Pluie" to precipitationMetricAccent(),
                    "Vent" to windMetricAccent()
                ).forEach { (label, color) ->
                    Surface(
                        color = color.copy(alpha = 0.13f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            label,
                            color = color,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
