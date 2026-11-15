package com.meteocompare.app.ui.help

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteocompare.app.ui.preview.MeteoPreviewSurface
import com.meteocompare.app.ui.preview.MeteoScreenPreview
import com.meteocompare.app.ui.preview.MeteoComponentPreview

@MeteoScreenPreview
@Composable
private fun HowItWorksScreenPreview() {
    MeteoPreviewSurface {
        HowItWorksScreen(onBack = {})
    }
}

@MeteoComponentPreview
@Composable
private fun HelpSunshinePreview() {
    MeteoPreviewSurface {
        Column(modifier = Modifier.padding(16.dp)) { SunshineMethodIllustration() }
    }
}

@MeteoComponentPreview
@Composable
private fun HelpStatusesPreview() {
    MeteoPreviewSurface {
        Column(modifier = Modifier.padding(16.dp)) { ConditionStatusesIllustration() }
    }
}

@MeteoComponentPreview
@Composable
private fun HelpConditionsPreview() {
    MeteoPreviewSurface {
        Column(modifier = Modifier.padding(16.dp)) { HierarchicalConsensusIllustration() }
    }
}
