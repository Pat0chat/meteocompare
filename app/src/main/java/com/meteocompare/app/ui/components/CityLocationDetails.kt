package com.meteocompare.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.City

/** Pays, région, subdivisions et code postal sur des lignes séparées. */
@Composable
fun CityLocationDetails(city: City, modifier: Modifier = Modifier) {
    val details = city.locationDetails
    if (details.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        details.forEach { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Variante Home : exactement deux lignes au plus, accolée au nom de ville.
 * Les précisions supplémentaires restent disponibles pour TalkBack et dans
 * la page Détails, même lorsque la seconde ligne est raccourcie à l'écran.
 */
@Composable
fun CityHomeLocationDetails(city: City, modifier: Modifier = Modifier) {
    val lines = city.homeLocationLines
    if (lines.isEmpty()) return
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = city.locationDetails.joinToString(", ")
        },
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
