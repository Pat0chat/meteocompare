package com.meteocompare.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.util.ConditionDecision
import com.meteocompare.app.domain.util.ConditionOrigin
import com.meteocompare.app.domain.util.ConditionReason
import com.meteocompare.app.domain.util.ConditionStatus
import com.meteocompare.app.ui.preview.MeteoComponentPreview
import com.meteocompare.app.ui.preview.MeteoPreviewSurface

@MeteoComponentPreview
@Composable
private fun ConditionStatusRowV5Preview() {
    MeteoPreviewSurface {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ConditionStatusIcon(
                    WeatherCondition.CLEAR,
                    ConditionDecision(WeatherCondition.CLEAR, ConditionStatus.RELIABLE, ConditionOrigin.HOURLY_WMO, 1.0),
                    size = 40.dp
                )
                ConditionStatusIcon(
                    WeatherCondition.RAIN,
                    ConditionDecision(WeatherCondition.RAIN, ConditionStatus.ESTIMATED, ConditionOrigin.SAME_MODEL_VARIABLES, 1.0),
                    size = 40.dp
                )
                ConditionStatusIcon(
                    null,
                    ConditionDecision(null, ConditionStatus.AMBIGUOUS, ConditionOrigin.MODEL_CONSENSUS,
                        alternatives = listOf(WeatherCondition.RAIN, WeatherCondition.CLEAR)),
                    size = 40.dp
                )
                ConditionStatusIcon(
                    WeatherCondition.PARTLY_CLOUDY,
                    ConditionDecision(WeatherCondition.PARTLY_CLOUDY, ConditionStatus.INCOMPLETE,
                        ConditionOrigin.HOURLY_DOMINANT, .75),
                    size = 40.dp
                )
                ConditionStatusIcon(null, null, size = 40.dp)
            }
            ConditionStatusLegend()
        }
    }
}

@MeteoComponentPreview
@Composable
private fun ConditionExplanationV5Preview() {
    MeteoPreviewSurface {
        ConditionExplanationDialog(
            condition = WeatherCondition.PARTLY_CLOUDY,
            decision = ConditionDecision(
                WeatherCondition.PARTLY_CLOUDY,
                ConditionStatus.INCOMPLETE,
                ConditionOrigin.HOURLY_DOMINANT,
                coverage = .75,
                agreement = .65,
                reasons = setOf(ConditionReason.PARTIAL_PERIOD, ConditionReason.IRREVERSIBLE_MAJORITY)
            ),
            isNight = false,
            onDismiss = {}
        )
    }
}

@MeteoComponentPreview
@Composable
private fun ConditionDialogLayoutV5Preview() {
    MeteoPreviewSurface {
        ConditionInfoDialog("Comprendre les prévisions", "condition_preview", onDismiss = {}) {
            Text("Source et couverture des conditions météorologiques")
        }
    }
}
