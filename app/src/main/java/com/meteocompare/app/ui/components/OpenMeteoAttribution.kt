package com.meteocompare.app.ui.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.meteocompare.app.R
import com.meteocompare.app.core.network.OPEN_METEO_WEBSITE_URL

/**
 * Attribution visible et actionnable à placer à proximité des données météo.
 * Le texte précise que MeteoCompare transforme/agrège les données et le tap
 * ouvre directement la source Open-Meteo.
 */
@Composable
fun OpenMeteoAttribution(
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.open_meteo_attribution),
    style: TextStyle = MaterialTheme.typography.labelSmall
) {
    val context = LocalContext.current
    val openDescription = stringResource(R.string.open_meteo_attribution_open)

    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = modifier
            .clickable(onClickLabel = openDescription) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, OPEN_METEO_WEBSITE_URL.toUri()))
                }
            }
            .padding(vertical = 4.dp)
    )
}
