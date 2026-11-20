package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun DailyRangeLabel(
    value: String,
    range: String?,
    rangeTag: String,
    valueTag: String? = null,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.labelMedium,
    rangeStyle: TextStyle = MaterialTheme.typography.labelSmall,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    rangeColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueWeight: FontWeight? = FontWeight.SemiBold
) {
    // This fast path is shared by the hourly timeline and the default daily view.
    // No text measurement or layout negotiation is needed when ranges are hidden.
    if (range.isNullOrBlank()) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = valueStyle, color = valueColor, fontWeight = valueWeight,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = if (valueTag != null) Modifier.testTag(valueTag) else Modifier)
        }
        return
    }
    val measure = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier) {
        val available = constraints.maxWidth
        val inline = dailyRangeFitsInline(
            measure.measure(AnnotatedString(value), valueStyle.copy(fontWeight = valueWeight),
                maxLines = 1).size.width,
            measure.measure(AnnotatedString(range), rangeStyle, maxLines = 1).size.width,
            available,
            with(androidx.compose.ui.platform.LocalDensity.current) { 7.dp.roundToPx() }
        )
        if (inline) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(value, style = valueStyle, color = valueColor, fontWeight = valueWeight,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = if (valueTag != null) Modifier.testTag(valueTag) else Modifier)
                Text(range!!, style = rangeStyle, color = rangeColor,
                    maxLines = 1, modifier = Modifier.padding(start = 5.dp).testTag(rangeTag))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(value, style = valueStyle, color = valueColor, fontWeight = valueWeight,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = if (valueTag != null) Modifier.testTag(valueTag) else Modifier)
                if (!range.isNullOrBlank()) {
                    Text(range, style = rangeStyle, color = rangeColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(rangeTag))
                }
            }
        }
    }
}
