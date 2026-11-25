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
import androidx.compose.ui.unit.sp
import com.meteocompare.app.R
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.core.units.weatherStringResource as stringResource
import java.util.Locale

@Composable
internal fun DailyRangeLabel(
    value: String,
    range: String?,
    rangeTag: String,
    valueTag: String? = null,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.labelMedium,
    rangeStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 9.sp, lineHeight = 11.sp
    ),
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    rangeColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueWeight: FontWeight? = FontWeight.SemiBold,
    singleLine: Boolean = false,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally
) {
    // This fast path is shared by the hourly timeline and the default daily view.
    // No text measurement or layout negotiation is needed when ranges are hidden.
    if (range.isNullOrBlank()) {
        Row(modifier = modifier,
            horizontalArrangement = if (horizontalAlignment == Alignment.Start) Arrangement.Start else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = valueStyle, color = valueColor, fontWeight = valueWeight,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = if (valueTag != null) Modifier.testTag(valueTag) else Modifier)
        }
        return
    }
    // The daily temperature heatmap has a fixed height. Keep both readings on
    // one line even with ranges, truncating only the secondary range as needed.
    if (singleLine) {
        Row(
            modifier = modifier,
            horizontalArrangement = if (horizontalAlignment == Alignment.Start)
                Arrangement.Start else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, style = valueStyle, color = valueColor, fontWeight = valueWeight,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = if (valueTag != null) Modifier.testTag(valueTag) else Modifier)
            Text(range, style = rangeStyle, color = rangeColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 3.dp).weight(1f, fill = false).testTag(rangeTag))
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
            with(androidx.compose.ui.platform.LocalDensity.current) { 5.dp.roundToPx() }
        )
        if (inline) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (horizontalAlignment == Alignment.Start) Arrangement.Start else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(value, style = valueStyle, color = valueColor, fontWeight = valueWeight,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = if (valueTag != null) Modifier.testTag(valueTag) else Modifier)
                Text(range, style = rangeStyle, color = rangeColor,
                    maxLines = 1, modifier = Modifier.padding(start = 5.dp).testTag(rangeTag))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = horizontalAlignment,
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

/** Two independent model spreads, in the same order as the usual min/max reading. */
@Composable
internal fun DailyApparentRangeLabels(
    point: SimplifiedTimelinePoint,
    units: WeatherUnits,
    locale: Locale,
    tagPrefix: String,
    modifier: Modifier = Modifier,
    showTitle: Boolean = false
) {
    val style = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        if (showTitle) {
            Text(stringResource(R.string.apparent_temperature_widget_prefix),
                style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DailyRangeLabel(
            value = stringResource(R.string.today_summary_temp_min_short) + " " + units.temp(point.apparentMinC, locale = locale),
            range = dailyMetricRange(point, DailyRangeMetric.LOW_APPARENT_TEMPERATURE, units, locale),
            rangeTag = "${tagPrefix}_apparent_min_range", valueTag = "${tagPrefix}_apparent_min_value",
            modifier = Modifier.fillMaxWidth(), valueStyle = style, valueColor = color
        )
        DailyRangeLabel(
            value = stringResource(R.string.today_summary_temp_max_short) + " " + units.temp(point.apparentMaxC, locale = locale),
            range = dailyMetricRange(point, DailyRangeMetric.HIGH_APPARENT_TEMPERATURE, units, locale),
            rangeTag = "${tagPrefix}_apparent_max_range", valueTag = "${tagPrefix}_apparent_max_value",
            modifier = Modifier.fillMaxWidth(), valueStyle = style, valueColor = color
        )
    }
}
