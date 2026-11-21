package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteocompare.app.ui.preview.MeteoComponentPreview
import com.meteocompare.app.ui.preview.MeteoPreviewSurface

/** Visual coverage for the daily range label with and without available ranges. */
@MeteoComponentPreview
@Composable
private fun DailyRangeLabelPreview() {
    MeteoPreviewSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Without range", style = MaterialTheme.typography.labelSmall)
            Box(Modifier.width(120.dp)) {
                DailyRangeLabel(value = "24°", range = null, rangeTag = "preview-no-range")
            }

            Text("Range alongside value", style = MaterialTheme.typography.labelSmall)
            Box(Modifier.width(136.dp)) {
                DailyRangeLabel(value = "24°", range = "(18–29°)", rangeTag = "preview-inline")
            }

            Text("Narrow column (wrapped range)", style = MaterialTheme.typography.labelSmall)
            Box(Modifier.width(60.dp)) {
                DailyRangeLabel(value = "24°", range = "(18–29°)", rangeTag = "preview-wrapped")
            }

            Text("Fixed-height heatmap cell", style = MaterialTheme.typography.labelSmall)
            Box(Modifier.width(92.dp)) {
                DailyRangeLabel(
                    value = "24°",
                    range = "(18–29°)",
                    rangeTag = "preview-single-line",
                    singleLine = true
                )
            }
        }
    }
}
