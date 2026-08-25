package com.meteocompare.app.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.WaterDrop
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
            illustration = { ModelAggregationIllustration() }
        ),
        HelpSection(
            number = 2,
            title = stringResource(R.string.help_section_2_title),
            body = stringResource(R.string.help_section_2_body),
            icon = Icons.Outlined.SettingsSuggest,
            illustration = { EngineModesIllustration() },
            footer = {
                ChipGroup(
                    listOf(
                        stringResource(R.string.metric_temperature),
                        stringResource(R.string.help_variable_rain),
                        stringResource(R.string.metric_wind),
                        stringResource(R.string.metric_home_gusts),
                        stringResource(R.string.help_variable_humidity),
                        stringResource(R.string.help_variable_cloud_cover)
                    )
                )
            }
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
        ),
        HelpSection(
            number = 5,
            title = stringResource(R.string.help_section_5_title),
            body = stringResource(R.string.help_section_5_body),
            icon = Icons.Outlined.WaterDrop,
            illustration = { RadarProjectionIllustration() },
            footer = {
                Text(
                    text = stringResource(R.string.help_radar_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        ),
        HelpSection(
            number = 6,
            title = stringResource(R.string.help_section_6_title),
            body = stringResource(R.string.help_section_6_body),
            icon = Icons.Outlined.FavoriteBorder,
            illustration = { DecisionSupportIllustration() }
        )
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HelpHeroCard() }
        items(sections) { section -> HelpSectionCard(section) }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            ) {
                Text(
                    text = stringResource(R.string.help_footer_note),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.help_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.help_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ChipGroup(
                listOf(
                    stringResource(R.string.help_section_1_title),
                    stringResource(R.string.help_section_2_title),
                    stringResource(R.string.help_section_3_title)
                )
            )
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = section.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ModelAggregationIllustration() {
    ChipGroup(listOf("AROME", "ICON", "ECMWF", "GFS", "UKMO"))
    Text(
        text = "↓",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "MeteoCompare",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun EngineModesIllustration() {
    TwoColumnFeatureGrid(
        items = listOf(
            stringResource(R.string.help_engine_multi_title) to stringResource(R.string.help_engine_multi_body),
            stringResource(R.string.help_engine_calibration_title) to stringResource(R.string.help_engine_calibration_body),
            stringResource(R.string.help_engine_scenarios_title) to stringResource(R.string.help_engine_scenarios_body),
            stringResource(R.string.help_engine_adaptive_title) to stringResource(R.string.help_engine_adaptive_body)
        )
    )
}

@Composable
private fun HierarchicalConsensusIllustration() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HighlightPill(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.help_hierarchical_dry),
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
        HighlightPill(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.help_hierarchical_precip),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f),
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipGroup(listOf("☀", "🌤", "☁", "🌫"))
            HelpMiniText(stringResource(R.string.help_hierarchical_dry_body))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipGroup(listOf("🌧", "🌨", "🧊", "⛈"))
            HelpMiniText(stringResource(R.string.help_hierarchical_precip_body))
        }
    }
}

@Composable
private fun ConvergenceIllustration() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatusPanel(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.help_convergence_strong_title),
            accent = MaterialTheme.colorScheme.secondary,
            dots = listOf(true, true, true, true, true),
            body = stringResource(R.string.help_convergence_strong_body)
        )
        StatusPanel(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.help_convergence_uncertain_title),
            accent = MaterialTheme.colorScheme.error,
            dots = listOf(true, true, false, true, false),
            body = stringResource(R.string.help_convergence_uncertain_body)
        )
    }
}

@Composable
private fun RadarProjectionIllustration() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            stringResource(R.string.help_radar_step_now),
            "+15",
            "+30",
            "+45",
            "+60"
        ).forEach { label ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "☔")
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun DecisionSupportIllustration() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MiniDecisionCard(
            modifier = Modifier.weight(1f),
            emoji = "🔎",
            title = stringResource(R.string.help_decision_compare_title)
        )
        MiniDecisionCard(
            modifier = Modifier.weight(1f),
            emoji = "⚙",
            title = stringResource(R.string.help_decision_synthesize_title)
        )
        MiniDecisionCard(
            modifier = Modifier.weight(1f),
            emoji = "🎯",
            title = stringResource(R.string.help_decision_understand_title)
        )
    }
}

@Composable
private fun MiniDecisionCard(modifier: Modifier = Modifier, emoji: String, title: String) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = emoji, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatusPanel(
    modifier: Modifier = Modifier,
    title: String,
    accent: Color,
    dots: List<Boolean>,
    body: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.10f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dots.forEach { active ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (active) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    )
                }
            }
            HelpMiniText(body)
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
    modifier: Modifier = Modifier,
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = containerColor
    ) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TwoColumnFeatureGrid(items: List<Pair<String, String>>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = 2,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { (title, body) ->
            Surface(
                modifier = Modifier.fillMaxWidth(0.48f),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
