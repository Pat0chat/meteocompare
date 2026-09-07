package com.meteocompare.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.ui.preview.MeteoComponentPreview
import com.meteocompare.app.ui.preview.MeteoPreviewSurface

@MeteoComponentPreview
@Composable
private fun AppToastVariantsPreview() {
    MeteoPreviewSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppToastCard(
                message = stringResource(R.string.toast_city_added, "Paris"),
                type = AppToastType.SUCCESS
            )
            AppToastCard(
                message = stringResource(R.string.refresh_error, "Pas de connexion Internet"),
                type = AppToastType.ERROR,
                onDismiss = {}
            )
            AppToastCard(
                message = stringResource(R.string.toast_refresh_all_partial),
                type = AppToastType.WARNING
            )
            AppToastCard(
                message = stringResource(R.string.settings_bias_refresh_queued),
                type = AppToastType.INFO,
                actionLabel = stringResource(R.string.action_retry),
                onAction = {}
            )
        }
    }
}
