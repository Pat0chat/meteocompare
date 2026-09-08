package com.meteocompare.app.ui.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.meteocompare.app.R
import com.meteocompare.app.core.network.OPEN_METEO_LICENSE_URL

/**
 * Attribution visible et actionnable à placer à proximité des données météo.
 *
 * Le libellé indique explicitement la source, la licence CC BY 4.0 et le fait
 * que MeteoCompare agrège/transforme les données. Le composant reste volontairement
 * discret : une petite surface tonale, cohérente avec Material 3, plutôt qu'un
 * lien souligné isolé qui attire davantage l'œil que les données météo.
 */
@Composable
fun OpenMeteoAttribution(
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.open_meteo_attribution),
    style: TextStyle = MaterialTheme.typography.labelSmall,
    url: String = OPEN_METEO_LICENSE_URL
) {
    val context = LocalContext.current
    val openDescription = stringResource(R.string.open_meteo_attribution_open)
    val showToast = rememberAppToastDispatcher()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        contentColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClickLabel = openDescription) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    }.onFailure {
                        showToast(AppToastEvent.error(R.string.toast_open_link_error))
                    }
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
