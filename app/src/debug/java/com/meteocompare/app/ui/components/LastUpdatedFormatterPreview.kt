package com.meteocompare.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteocompare.app.ui.preview.MeteoComponentPreview
import com.meteocompare.app.ui.preview.MeteoPreviewSurface
import com.meteocompare.app.ui.preview.PreviewFixtures

@MeteoComponentPreview
@Composable
private fun LastUpdatedFormatterPreview() {
    MeteoPreviewSurface {
        Column(Modifier.padding(16.dp)) {
            Text("Dernière mise à jour")
            Text(rememberFormattedLastUpdated(PreviewFixtures.now.minusSeconds(17 * 60L)))
        }
    }
}
