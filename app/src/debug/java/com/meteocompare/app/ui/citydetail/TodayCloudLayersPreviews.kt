package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteocompare.app.ui.preview.MeteoComponentPreview
import com.meteocompare.app.ui.preview.MeteoPreviewSurface
import com.meteocompare.app.ui.preview.PreviewFixtures
import java.time.ZoneId
import java.util.Locale

/** Debug-only current-hour summary preview; the detail dialog is opened by tapping it. */
@MeteoComponentPreview
@Composable
private fun TodayCloudLayersPreview() {
    val hours = List(9) { index ->
        CloudLayerHour(
            hour = PreviewFixtures.now.plusSeconds(index * 3600L),
            total = (86 + index).coerceAtMost(100),
            high = (82 + index).coerceAtMost(100),
            mid = 14 + index,
            low = 8 + index,
            readings = emptyList()
        )
    }
    MeteoPreviewSurface {
        Surface(Modifier.padding(16.dp)) {
            TodayCloudLayersCard(hours, ZoneId.of("Europe/Paris"), Locale.FRANCE)
        }
    }
}
