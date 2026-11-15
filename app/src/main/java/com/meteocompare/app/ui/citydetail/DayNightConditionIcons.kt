package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.util.ConditionDecision
import com.meteocompare.app.ui.components.ConditionStatusIcon
import com.meteocompare.app.ui.components.semanticTint

/** Icônes jour/nuit communes aux deux mises en page de la timeline daily. */
@Composable
internal fun DayNightConditionIcons(
    day: WeatherCondition?,
    night: WeatherCondition?,
    iconSize: Dp = 32.dp,
    dayDiagnostic: ConditionDecision? = null,
    nightDiagnostic: ConditionDecision? = null
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Triple(false, day, dayDiagnostic), Triple(true, night, nightDiagnostic)).forEach { (isNight, condition, diagnostic) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(if (isNight) R.string.condition_period_night else R.string.condition_period_day),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier.width(iconSize).height(iconSize)
                        .testTag(if (isNight) TAG_DAILY_NIGHT_ICON else TAG_DAILY_DAY_ICON),
                    contentAlignment = Alignment.Center
                ) {
                    ConditionStatusIcon(
                        condition = condition, decision = diagnostic, size = iconSize,
                        tint = if (isNight && condition == WeatherCondition.CLEAR)
                            Color.Unspecified else condition?.semanticTint() ?: Color.Unspecified,
                        isNight = isNight
                    )
                }
            }
        }
    }
}

/** Repères stables pour vérifier la taille des icônes dans les deux timelines. */
internal const val TAG_DAILY_DAY_ICON = "daily_day_condition_icon"
internal const val TAG_DAILY_NIGHT_ICON = "daily_night_condition_icon"
