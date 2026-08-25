package com.meteocompare.app.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.components.WeatherIconDecorative
import com.meteocompare.app.ui.components.semanticTint
import com.meteocompare.app.R

private data class HelpSection(
    val number: Int,
    val title: String,
    val body: String,
    val icon: ImageVector,
    val illustration: @Composable () -> Unit,
    val footer: (@Composable () -> Unit)? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowItWorksScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { padding ->
        HowItWorksContent(modifier = Modifier.padding(padding))
    }
}

@Composable
private fun HowItWorksContent(modifier: Modifier = Modifier) {
    val sections = listOf(
        HelpSection(
            number = 1,
            title = stringResource(R.string.help_section_1_title),
            body = stringResource(R.string.help_section_1_body),
            icon = Icons.Outlined.Layers,
            illustration = { ModelSourcesIllustration() }
        ),
        HelpSection(
            number = 2,
            title = stringResource(R.string.help_section_2_title),
            body = stringResource(R.string.help_section_2_body),
            icon = Icons.Outlined.SettingsSuggest,
            illustration = { EngineModesIllustration() }
        ),
        HelpSection(
            number = 3,
            title = stringResource(R.string.help_section_3_title),
            body = stringResource(R.string.help_section_3_body),
            icon = Icons.Outlined.TrackChanges,
            illustration = { HierarchicalConsensusIllustration() },
            footer = {
                Text(
                    text = stringResource(R.string.help_hierarchical_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        ),
        HelpSection(
            number = 4,
            title = stringResource(R.string.help_section_4_title),
            body = stringResource(R.string.help_section_4_body),
            icon = Icons.Outlined.TrackChanges,
            illustration = { ConvergenceIllustration() }
        )
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HelpHeroCard() }
        items(sections) { section -> HelpSectionCard(section) }
    }
}

@Composable
private fun HelpHeroCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.help_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.help_footer_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpSectionCard(section: HelpSection) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = section.number.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = section.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IllustrationSurface { section.illustration() }
            section.footer?.invoke()
        }
    }
}

@Composable
private fun IllustrationSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) { content() }
    }
}

@Composable
private fun ModelSourcesIllustration() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("AROME", "ICON", "ECMWF").forEach {
                ModelBadge(it, Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("GFS", "UKMO", "ARPEGE").forEach {
                ModelBadge(it, Modifier.weight(1f))
            }
        }
        Text(
            text = "↓",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "MeteoCompare",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.help_decision_compare_body),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ModelBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.WbCloudy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = text,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EngineModesIllustration() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TwoByTwoCards(
            listOf(
                EngineItem(stringResource(R.string.help_engine_multi_title), stringResource(R.string.help_engine_multi_body), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer),
                EngineItem(stringResource(R.string.help_engine_calibration_title), stringResource(R.string.help_engine_calibration_body), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer),
                EngineItem(stringResource(R.string.help_engine_scenarios_title), stringResource(R.string.help_engine_scenarios_body), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer),
                EngineItem(stringResource(R.string.help_engine_adaptive_title), stringResource(R.string.help_engine_adaptive_body), MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f), MaterialTheme.colorScheme.onErrorContainer)
            )
        )
        Text(
            text = "↓ " + stringResource(R.string.help_engines_compute_variables),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        VariableIconRow()
    }
}

private data class EngineItem(val title: String, val body: String, val bg: Color, val fg: Color)

@Composable
private fun TwoByTwoCards(items: List<EngineItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (i in items.indices step 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                items.subList(i, minOf(i + 2, items.size)).forEach { item ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        color = item.bg
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().height(142.dp).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = item.fg)
                            Text(item.body, style = MaterialTheme.typography.bodySmall, color = item.fg)
                        }
                    }
                }
                if (items.subList(i, minOf(i + 2, items.size)).size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HierarchicalConsensusIllustration() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HighlightPill(
            text = "Consensus météo",
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "↓",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HighlightPill(
                    text = stringResource(R.string.help_hierarchical_dry),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                )
                WeatherConditionRow(listOf(
                    WeatherCondition.CLEAR,
                    WeatherCondition.PARTLY_CLOUDY,
                    WeatherCondition.OVERCAST,
                    WeatherCondition.FOG
                ))
                HelpMiniText(stringResource(R.string.help_hierarchical_dry_body))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HighlightPill(
                    text = stringResource(R.string.help_hierarchical_precip),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                )
                WeatherConditionRow(listOf(
                    WeatherCondition.DRIZZLE,
                    WeatherCondition.RAIN,
                    WeatherCondition.SNOW,
                    WeatherCondition.THUNDERSTORM
                ))
                HelpMiniText(stringResource(R.string.help_hierarchical_precip_body))
            }
        }
    }
}

@Composable
private fun ConvergenceIllustration() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusPanel(
            title = stringResource(R.string.help_convergence_strong_title),
            accent = MaterialTheme.colorScheme.secondary,
            dots = listOf(true, true, true, true, true),
            body = stringResource(R.string.help_convergence_strong_body)
        )
        StatusPanel(
            title = stringResource(R.string.help_convergence_uncertain_title),
            accent = MaterialTheme.colorScheme.error,
            dots = listOf(true, false, true, false, true),
            body = stringResource(R.string.help_convergence_uncertain_body)
        )
    }
}

@Composable
private fun StatusPanel(title: String, accent: Color, dots: List<Boolean>, body: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = 0.10f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dots.forEach { active ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (active) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        )
                    }
                }
            }
            VisualRangeBar(accent = accent, activeCount = dots.count { it }, totalCount = dots.size)
            HelpMiniText(body)
        }
    }
}

@Composable
private fun VisualRangeBar(accent: Color, activeCount: Int, totalCount: Int) {
    val fill = (activeCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .height(10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent)
            )
        }
    }
}

@Composable
private fun VariableIconRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VariableChip(Icons.Outlined.Thermostat, stringResource(R.string.metric_temperature), Modifier.weight(1f))
        VariableChip(Icons.Outlined.WaterDrop, stringResource(R.string.help_variable_rain), Modifier.weight(1f))
        VariableChip(Icons.Outlined.Air, stringResource(R.string.metric_wind), Modifier.weight(1f))
        VariableChip(Icons.Outlined.WbCloudy, stringResource(R.string.help_variable_cloud_cover), Modifier.weight(1f))
    }
}

@Composable
private fun VariableChip(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun WeatherConditionRow(conditions: List<WeatherCondition>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        conditions.forEach { condition ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    WeatherIconDecorative(
                        condition = condition,
                        size = 24.dp,
                        tint = condition.semanticTint()
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpMiniText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun HighlightPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = containerColor) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(items: List<String>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = item,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
