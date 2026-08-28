package com.meteocompare.app.ui.help

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbCloudy
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.components.WeatherIconDecorative
import com.meteocompare.app.ui.components.semanticTint

private data class HelpSection(
    val step: Int,
    val title: String,
    val body: String,
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
                title = {
                    Text(
                        text = stringResource(R.string.help_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
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
            step = 1,
            title = stringResource(R.string.help_section_1_title),
            body = stringResource(R.string.help_section_1_body),
            illustration = { ModelSourcesIllustration() }
        ),
        HelpSection(
            step = 2,
            title = stringResource(R.string.help_section_2_title),
            body = stringResource(R.string.help_section_2_body),
            illustration = { EngineModesIllustration() }
        ),
        HelpSection(
            step = 3,
            title = stringResource(R.string.help_section_3_title),
            body = stringResource(R.string.help_section_3_body),
            illustration = { HierarchicalConsensusIllustration() },
            footer = {
                Text(
                    text = stringResource(R.string.help_hierarchical_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }
        ),
        HelpSection(
            step = 4,
            title = stringResource(R.string.help_section_4_title),
            body = stringResource(R.string.help_section_4_body),
            illustration = { ConvergenceIllustration() }
        )
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { HelpHero() }

        sections.forEach { section ->
            item(key = "help_${section.step}") {
                TimelineSection(section = section)
            }
        }

        item { TakeawaySurface() }
    }
}

@Composable
private fun HelpHero() {
    val shape = RoundedCornerShape(32.dp)
    val heroBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f),
            MaterialTheme.colorScheme.surfaceContainerLowest,
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.18f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(heroBrush)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = shape
            )
            .padding(22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.help_subtitle),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
private fun TimelineSection(section: HelpSection) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepBadge(step = section.step)

                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
            }

            Text(
                text = section.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )

            section.illustration()
            section.footer?.invoke()
        }
    }
}

@Composable
private fun StepBadge(step: Int) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = step.toString().padStart(2, '0'),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private val HelpFeatureBoxHeight = 240.dp

@Composable
private fun ModelSourcesIllustration(
    compact: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 26.dp else 22.dp),
        color = containerColor,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (compact) 16.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModelBadgeRow(listOf("AROME", "ICON", "ECMWF"))
            ModelBadgeRow(listOf("GFS", "UKMO", "ARPEGE"))

            ConsensusConnector(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 54.dp else 44.dp)
            )

            Surface(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OfficialMeteoCompareLogo(size = 60.dp)
                    Text(
                        text = "MeteoCompare",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelBadgeRow(models: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        models.forEach { model ->
            ModelBadge(text = model, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ModelBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.WbCloudy,
                contentDescription = null,
                tint = modelAccent(text),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OfficialMeteoCompareLogo(
    size: Dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.ic_splash_logo),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
    )
}

@Composable
private fun modelAccent(model: String): Color = when (model) {
    "GFS" -> MaterialTheme.colorScheme.secondary
    "ECMWF" -> MaterialTheme.colorScheme.primary
    "ICON" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
}

@Composable
private fun ConsensusConnector(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height)
        val starts = listOf(0.08f, 0.24f, 0.40f, 0.60f, 0.76f, 0.92f)

        starts.forEachIndexed { index, xFraction ->
            val start = Offset(size.width * xFraction, 0f)
            val path = Path().apply {
                moveTo(start.x, start.y)
                cubicTo(
                    start.x,
                    size.height * 0.52f,
                    center.x + (index - 2.5f) * 10f,
                    size.height * 0.62f,
                    center.x,
                    center.y
                )
            }
            drawPath(path = path, color = color, style = Stroke(width = 2.dp.toPx()))
        }

        drawCircle(
            color = color,
            radius = 3.dp.toPx(),
            center = center
        )
    }
}

private data class EngineItem(
    val title: String,
    val body: String,
    val icon: ImageVector,
    val container: Color,
    val accent: Color
)

@Composable
private fun EngineModesIllustration() {
    val items = listOf(
        EngineItem(
            title = stringResource(R.string.help_engine_multi_title),
            body = stringResource(R.string.help_engine_multi_body),
            icon = Icons.Outlined.Layers,
            container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
            accent = MaterialTheme.colorScheme.primary
        ),
        EngineItem(
            title = stringResource(R.string.help_engine_calibration_title),
            body = stringResource(R.string.help_engine_calibration_body),
            icon = Icons.Outlined.SettingsSuggest,
            container = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.52f),
            accent = MaterialTheme.colorScheme.tertiary
        ),
        EngineItem(
            title = stringResource(R.string.help_engine_scenarios_title),
            body = stringResource(R.string.help_engine_scenarios_body),
            icon = Icons.Outlined.TrackChanges,
            container = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.56f),
            accent = MaterialTheme.colorScheme.secondary
        ),
        EngineItem(
            title = stringResource(R.string.help_engine_adaptive_title),
            body = stringResource(R.string.help_engine_adaptive_body),
            icon = Icons.Outlined.Air,
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            accent = MaterialTheme.colorScheme.primary
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { item ->
                    EngineTile(item = item, modifier = Modifier.weight(1f))
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.help_engines_compute_variables),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                VariableIconRow()
            }
        }
    }
}

@Composable
private fun EngineTile(item: EngineItem, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(HelpFeatureBoxHeight),
        shape = RoundedCornerShape(20.dp),
        color = item.container,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = item.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun HierarchicalConsensusIllustration() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            )
        ) {
            Text(
                text = stringResource(R.string.help_section_3_title),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )
        }

        ConsensusBranchConnector(
            modifier = Modifier.fillMaxWidth().height(30.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ConsensusScenario(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.help_hierarchical_dry),
                percentage = 72,
                accent = MaterialTheme.colorScheme.secondary,
                container = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.46f),
                conditions = listOf(
                    WeatherCondition.CLEAR,
                    WeatherCondition.PARTLY_CLOUDY,
                    WeatherCondition.OVERCAST,
                    WeatherCondition.FOG
                ),
                body = stringResource(R.string.help_hierarchical_dry_body)
            )

            ConsensusScenario(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.help_hierarchical_precip),
                percentage = 28,
                accent = MaterialTheme.colorScheme.primary,
                container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                conditions = listOf(
                    WeatherCondition.DRIZZLE,
                    WeatherCondition.RAIN,
                    WeatherCondition.SNOW,
                    WeatherCondition.THUNDERSTORM
                ),
                body = stringResource(R.string.help_hierarchical_precip_body)
            )
        }
    }
}

@Composable
private fun ConsensusBranchConnector(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
    Canvas(modifier = modifier) {
        val top = Offset(size.width / 2f, 0f)
        val left = Offset(size.width * 0.25f, size.height)
        val right = Offset(size.width * 0.75f, size.height)
        val midY = size.height * 0.48f

        drawLine(color, top, Offset(top.x, midY), strokeWidth = 2.dp.toPx())
        drawLine(color, Offset(left.x, midY), Offset(right.x, midY), strokeWidth = 2.dp.toPx())
        drawLine(color, Offset(left.x, midY), left, strokeWidth = 2.dp.toPx())
        drawLine(color, Offset(right.x, midY), right, strokeWidth = 2.dp.toPx())
        drawCircle(color = color, radius = 2.5.dp.toPx(), center = left)
        drawCircle(color = color, radius = 2.5.dp.toPx(), center = right)
    }
}

@Composable
private fun ConsensusScenario(
    title: String,
    percentage: Int,
    accent: Color,
    container: Color,
    conditions: List<WeatherCondition>,
    body: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(HelpFeatureBoxHeight),
        shape = RoundedCornerShape(20.dp),
        color = container,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "$percentage %",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            WeatherConditionRow(conditions)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ConvergenceIllustration() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ConfidencePanel(
            title = stringResource(R.string.help_convergence_strong_title),
            body = stringResource(R.string.help_convergence_strong_body),
            percentage = 86,
            accent = MaterialTheme.colorScheme.secondary,
            container = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.40f),
            positive = true
        )
        ConfidencePanel(
            title = stringResource(R.string.help_convergence_uncertain_title),
            body = stringResource(R.string.help_convergence_uncertain_body),
            percentage = 43,
            accent = MaterialTheme.colorScheme.error,
            container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.34f),
            positive = false
        )
    }
}

@Composable
private fun ConfidencePanel(
    title: String,
    body: String,
    percentage: Int,
    accent: Color,
    container: Color,
    positive: Boolean
) {
    var targetProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(percentage) {
        targetProgress = (percentage / 100f).coerceIn(0f, 1f)
    }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 650),
        label = "helpConfidence"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = container,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (positive) {
                            Icon(
                                imageVector = Icons.Outlined.TrackChanges,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(21.dp)
                            )
                        } else {
                            Text(
                                text = "?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        }
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$percentage %",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }

            ProgressTrack(progress = animatedProgress, accent = accent)

            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun ProgressTrack(progress: Float, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(9.dp)
                .clip(CircleShape)
                .background(accent)
        )
    }
}

@Composable
private fun VariableIconRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VariableChip(Icons.Outlined.Thermostat, Modifier.weight(1f))
        VariableChip(Icons.Outlined.WaterDrop, Modifier.weight(1f))
        VariableChip(Icons.Outlined.Air, Modifier.weight(1f))
        VariableChip(Icons.Outlined.WbCloudy, Modifier.weight(1f))
    }
}

@Composable
private fun VariableChip(icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun WeatherConditionRow(conditions: List<WeatherCondition>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        conditions.forEach { condition ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                WeatherIconDecorative(
                    condition = condition,
                    size = 22.dp,
                    tint = condition.semanticTint()
                )
            }
        }
    }
}

@Composable
private fun TakeawaySurface() {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.32f)
                    )
                )
            )
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    OfficialMeteoCompareLogo(size = 60.dp)
                }
            }

            Text(
                text = stringResource(R.string.help_footer_note),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )
        }
    }
}
