package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

            Text("Apparent temperature: independent min/max ranges", style = MaterialTheme.typography.labelSmall)
            Box(Modifier.width(136.dp)) {
                DailyApparentRangeLabels(
                    point = SimplifiedTimelinePoint(date = java.time.LocalDate.of(2026, 11, 25),
                        apparentMinC = 9.0, apparentMaxC = 33.0,
                        apparentMinMinAcrossModels = 5.0, apparentMinMaxAcrossModels = 14.0, apparentMinModelCount = 3,
                        apparentMaxMinAcrossModels = 27.0, apparentMaxMaxAcrossModels = 36.0, apparentMaxModelCount = 3),
                    units = com.meteocompare.app.core.units.WeatherUnits(), locale = java.util.Locale.US,
                    tagPrefix = "preview", showTitle = true,
                    modifier = Modifier.padding(start = 22.dp, end = 5.dp),
                    horizontalAlignment = Alignment.Start)
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
