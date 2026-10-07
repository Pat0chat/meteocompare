package com.meteocompare.app.ui.radar

import androidx.compose.runtime.Composable
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.radar.RadarFrame
import com.meteocompare.app.domain.radar.RadarMetadata
import com.meteocompare.app.ui.preview.MeteoPreviewSurface
import com.meteocompare.app.ui.preview.MeteoScreenPreview

@MeteoScreenPreview
@Composable
private fun RadarReadyPreview() {
    val frames = listOf(
        RadarFrame(1_799_750_400L, "/v2/radar/preview-1"),
        RadarFrame(1_799_751_000L, "/v2/radar/preview-2")
    )
    MeteoPreviewSurface {
        RadarContent(
            state = RadarUiState.Ready(
                city = City(
                    id = "preview-lyon",
                    name = "Lyon",
                    country = "France",
                    latitude = 45.7640,
                    longitude = 4.8357,
                    timezone = "Europe/Paris"
                ),
                metadata = RadarMetadata(
                    host = "https://tilecache.rainviewer.com",
                    past = frames,
                    generatedEpochSeconds = frames.last().timeEpochSeconds
                ),
                frames = frames,
                selectedFrameIndex = frames.lastIndex,
                displayImage = null,
                baseTiles = emptyList()
            )
        )
    }
}

@MeteoScreenPreview
@Composable
private fun RadarNetworkErrorPreview() {
    MeteoPreviewSurface {
        RadarContent(state = RadarUiState.Error(RadarErrorReason.NETWORK))
    }
}
