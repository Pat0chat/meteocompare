package com.meteocompare.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ModernSectionSeparator(
    @StringRes textRes: Int,
    modifier: Modifier = Modifier
) {
    val text = stringResource(textRes)
    val scheme = MaterialTheme.colorScheme
    val lineColor = scheme.outlineVariant
    val accent = scheme.primary

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SeparatorLine(
            colors = listOf(
                lineColor.copy(alpha = 0f),
                lineColor.copy(alpha = 0.75f)
            ),
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accent.copy(alpha = 0.09f))
                .padding(
                    horizontal = 12.dp,
                    vertical = 6.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(10.dp))

        SeparatorLine(
            colors = listOf(
                lineColor.copy(alpha = 0.75f),
                lineColor.copy(alpha = 0f)
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SeparatorLine(
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(1.dp)
            .background(
                brush = Brush.horizontalGradient(colors)
            )
    )
}