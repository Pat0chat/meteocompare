package com.meteocompare.app.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.ui.theme.MeteoCompareTheme

@Preview(
    name = "Light",
    group = "MeteoCompare",
    showBackground = true,
    widthDp = 390,
    heightDp = 844
)
@Preview(
    name = "Dark",
    group = "MeteoCompare",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
internal annotation class MeteoScreenPreview

@Preview(
    name = "Light",
    group = "MeteoCompare components",
    showBackground = true,
    widthDp = 390
)
@Preview(
    name = "Dark",
    group = "MeteoCompare components",
    showBackground = true,
    widthDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
internal annotation class MeteoComponentPreview

@Composable
internal fun MeteoPreviewSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    MeteoCompareTheme(
        themePreference = ThemePreference.SYSTEM,
        dynamicColor = false
    ) {
        Surface(modifier = modifier.fillMaxSize()) {
            content()
        }
    }
}
