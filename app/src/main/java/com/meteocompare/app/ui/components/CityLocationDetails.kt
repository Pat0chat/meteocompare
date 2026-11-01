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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
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

/** Deux lignes maximum pour distinguer les localités homonymes : pays puis subdivisions.
 * La description accessible conserve toutes les précisions, y compris celles ellipsées.
 */
@Composable
private fun CityTwoLineLocationDetails(
    city: City,
    modifier: Modifier = Modifier,
    style: TextStyle
) {
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
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Accueil : police discrète, deux lignes et largeur allouée par la carte. */
@Composable
fun CityHomeLocationDetails(city: City, modifier: Modifier = Modifier) {
    CityTwoLineLocationDetails(
        city = city,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp)
    )
}

/** Recherche : pays et subdivisions sur deux lignes, avec détail accessible complet. */
@Composable
fun CitySearchLocationDetails(city: City, modifier: Modifier = Modifier) {
    CityTwoLineLocationDetails(
        city = city,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall
    )
}
