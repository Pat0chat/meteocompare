package com.meteocompare.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.WeatherCondition

private val previewConditions = listOf(
    WeatherCondition.CLEAR,
    WeatherCondition.MAINLY_CLEAR,
    WeatherCondition.PARTLY_CLOUDY,
    WeatherCondition.OVERCAST,
    WeatherCondition.FOG,
    WeatherCondition.DRIZZLE,
    WeatherCondition.RAIN,
    WeatherCondition.RAIN_SHOWERS,
    WeatherCondition.FREEZING_RAIN,
    WeatherCondition.SNOW,
    WeatherCondition.SNOW_SHOWERS,
    WeatherCondition.THUNDERSTORM,
    WeatherCondition.UNKNOWN
)

@Composable
fun WeatherIconGallery(
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(
            items = previewConditions,
            key = { _, condition -> condition.name }
        ) { index, condition ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AnimatedWeatherIcon(
                        condition = condition,
                        size = 60.dp,
                        animated = animated,
                        animationSeed = condition.name.hashCode() + index
                    )
                    Column {
                        Text(
                            text = condition.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = condition.previewLabel(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun WeatherCondition.previewLabel(): String = when (this) {
    WeatherCondition.CLEAR -> "Ciel dégagé"
    WeatherCondition.MAINLY_CLEAR -> "Globalement dégagé"
    WeatherCondition.PARTLY_CLOUDY -> "Partiellement nuageux"
    WeatherCondition.OVERCAST -> "Couvert"
    WeatherCondition.FOG -> "Brouillard"
    WeatherCondition.DRIZZLE -> "Bruine"
    WeatherCondition.RAIN -> "Pluie"
    WeatherCondition.RAIN_SHOWERS -> "Averses"
    WeatherCondition.FREEZING_RAIN -> "Pluie verglaçante"
    WeatherCondition.SNOW -> "Neige"
    WeatherCondition.SNOW_SHOWERS -> "Averses de neige"
    WeatherCondition.THUNDERSTORM -> "Orage"
    WeatherCondition.UNKNOWN -> "Condition inconnue"
}

@Preview(
    name = "Weather icons - light",
    showBackground = true,
    widthDp = 390,
    heightDp = 900
)
@Composable
private fun WeatherIconGalleryLightPreview() {
    MaterialTheme {
        WeatherIconGallery(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            animated = false
        )
    }
}

@Preview(
    name = "Weather icons - dark",
    showBackground = true,
    backgroundColor = 0xFF10151D,
    widthDp = 390,
    heightDp = 900
)
@Composable
private fun WeatherIconGalleryDarkPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        WeatherIconGallery(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            animated = false
        )
    }
}
