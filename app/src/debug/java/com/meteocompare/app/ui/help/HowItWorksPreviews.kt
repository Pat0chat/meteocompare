package com.meteocompare.app.ui.help

import androidx.compose.runtime.Composable
import com.meteocompare.app.ui.preview.MeteoPreviewSurface
import com.meteocompare.app.ui.preview.MeteoScreenPreview

@MeteoScreenPreview
@Composable
private fun HowItWorksScreenPreview() {
    MeteoPreviewSurface {
        HowItWorksScreen(onBack = {})
    }
}
