package com.meteocompare.app.ui.citydetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.components.WeatherIconDecorative
import com.meteocompare.app.ui.components.semanticTint
import com.meteocompare.app.ui.theme.confidenceColor
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// ============================================================================
//  Public screen entry
// ============================================================================

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityDetailScreen(
    cityId: String,
    onBack: () -> Unit,
    onConfidenceClick: (isoDate: String) -> Unit = {},
    viewModel: CityDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Context capturé hors de LaunchedEffect pour résoudre les strings depuis
    // une coroutine (où stringResource n'est pas accessible — c'est un @Composable).
    val context = LocalContext.current

    // Collecte les événements one-shot de refresh — succès ou erreur.
    // LaunchedEffect avec viewModel comme key : si la VM change (changement
    // de cityId via nav), on relance la collecte. flowWithLifecycle évite de
    // collecter quand l'écran est en background — pas indispensable ici car
    // les events sont rares, mais c'est l'habitude.
    LaunchedEffect(viewModel) {
        viewModel.refreshFeedback.collect { feedback ->
            when (feedback) {
                RefreshFeedback.Success -> snackbarHostState.showSnackbar(
                    message = context.getString(R.string.refresh_success),
                    duration = SnackbarDuration.Short
                )
                is RefreshFeedback.Error -> snackbarHostState.showSnackbar(
                    message = context.getString(R.string.refresh_error, feedback.message),
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    CityDetailContent(
        state = state,
        isRefreshing = isRefreshing,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onConfidenceClick = onConfidenceClick
    )
}

// ============================================================================
//  Stateless content
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CityDetailContent(
    state: CityDetailUiState,
    isRefreshing: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onConfidenceClick: (isoDate: String) -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    val title = (state as? CityDetailUiState.Loaded)?.forecast?.city?.name
                        ?: stringResource(R.string.title_detail_fallback)
                    Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    // Pendant le refresh : on désactive le bouton (évite double-tap
                    // qui spammerait le réseau) ET on remplace l'icône par un
                    // spinner. C'est le feedback visuel immédiat "ton tap a été pris".
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "detail-state",
            contentKey = {
                when (it) {
                    CityDetailUiState.Loading -> "loading"
                    is CityDetailUiState.Loaded -> "loaded"
                    is CityDetailUiState.Error -> "error"
                }
            }
        ) { s ->
            when (s) {
                CityDetailUiState.Loading -> LoadingView(padding)
                is CityDetailUiState.Error -> ErrorView(
                    message = s.message,
                    onRetry = onRefresh,
                    padding = padding
                )
                is CityDetailUiState.Loaded -> PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LoadedView(
                        forecast = s.forecast,
                        weekly = s.weeklyConfidence,
                        hourlyBands = s.hourlyBands,
                        currentTemp = s.currentTemp,
                        currentCondition = s.currentCondition,
                        dailyConditions = s.dailyConditions,
                        normals = s.normals,
                        padding = padding,
                        onConfidenceClick = onConfidenceClick
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingView(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding).testTag(TAG_DETAIL_LOADING),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit, padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).testTag(TAG_DETAIL_ERROR),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun LoadedView(
    forecast: CityForecast,
    weekly: List<DayConfidence>,
    hourlyBands: List<com.meteocompare.app.domain.model.HourlyConfidenceBand>,
    currentTemp: Double?,
    currentCondition: WeatherCondition?,
    dailyConditions: List<com.meteocompare.app.domain.usecase.DayConditionsRow>,
    normals: Map<Int, com.meteocompare.app.domain.model.DayNormals>?,
    padding: PaddingValues,
    onConfidenceClick: (isoDate: String) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(TAG_DETAIL_LOADED),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item("today_summary") {
            weekly.firstOrNull()?.let { today ->
                TodaySummaryCard(
                    today = today,
                    modelCount = forecast.availableModels.size,
                    currentTemp = currentTemp,
                    currentCondition = currentCondition,
                    onConfidenceClick = { onConfidenceClick(today.date.toString()) }
                )
            }
        }

        // Chart "bande de confiance" — placé haut car c'est le différenciateur clé
        if (hourlyBands.size >= 2) {
            item("hourly_confidence") {
                SectionTitle(stringResource(R.string.section_confidence_band))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    HourlyConfidenceChart(
                        bands = hourlyBands,
                        timezone = forecast.city.timezone
                    )
                }
            }
        }

        // Tableau matrice Jour × Modèle des conditions météo. Placé juste après
        // le résumé du jour parce que c'est l'info la plus immédiate après "il
        // fait combien" : "qu'est-ce que chaque modèle prédit comme temps ?".
        // On ne rend pas le bloc si aucune donnée — typiquement un cache
        // pré-feature dont la réponse JSON ne contient pas weather_code.
        if (dailyConditions.isNotEmpty()) {
            item("weather_by_model") {
                SectionTitle(stringResource(R.string.section_weather_by_model))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    WeatherByModelTable(
                        rows = dailyConditions,
                        modelOrder = forecast.availableModels,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                WeatherLegend()
            }
        }

        item("chart") {
            SectionTitle(stringResource(R.string.section_temperatures))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                // Les normales se chargent en background — quand elles arrivent,
                // les pointillés apparaissent automatiquement via recomposition.
                TemperatureComparisonChart(forecast = forecast, normals = normals)
            }
        }

        // Tableau fusionné max/min — remplace les deux tableaux séparés.
        // Coloration relative aux normales climatiques (rouge si > normale + 2°,
        // bleu si < normale - 2°). Si normals == null encore, affichage neutre.
        // Structure identique aux autres tableaux : Card pour la table, légende
        // en dessous (pas dans la Card).
        item("temp_table") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                MinMaxForecastTable(
                    forecast = forecast,
                    normals = normals,
                    modifier = Modifier.padding(8.dp)
                )
            }
            MinMaxForecastLegend(normalsAvailable = normals != null)
        }

        item("precip_table") {
            ForecastSection(
                title = stringResource(R.string.section_precipitation),
                forecast = forecast,
                extractor = { daily, idx -> daily.precipitationSum.getOrNull(idx) },
                formatter = { mm ->
                    if (mm < 0.05) "0" else "${"%.1f".format(mm)} mm"
                },
                valueStyler = ::precipitationStyle,
                legend = { PrecipitationLegend() }
            )
        }

        item("wind_table") {
            ForecastSection(
                title = stringResource(R.string.section_wind),
                forecast = forecast,
                extractor = { daily, idx -> daily.windSpeedMax.getOrNull(idx) },
                formatter = { "${it.roundToInt()} km/h" },
                valueStyler = ::windStyle,
                legend = { WindLegend() }
            )
        }

        if (forecast.errors.isNotEmpty()) {
            item("errors") { PartialErrorsSection(forecast.errors) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { heading() }
    )
}

@Composable
private fun ForecastSection(
    title: String,
    forecast: CityForecast,
    extractor: (DailyForecast, Int) -> Double?,
    formatter: (Double) -> String,
    valueStyler: ((Double) -> ValueStyle?)? = null,
    legend: @Composable (() -> Unit)? = null
) {
    Column {
        SectionTitle(title)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            ForecastTable(
                forecast = forecast,
                valueExtractor = extractor,
                valueFormatter = formatter,
                valueStyler = valueStyler,
                modifier = Modifier.padding(8.dp)
            )
        }
        legend?.invoke()
    }
}

@Composable
internal fun TodaySummaryCard(
    today: DayConfidence,
    modelCount: Int,
    currentTemp: Double?,
    currentCondition: WeatherCondition? = null,
    onConfidenceClick: () -> Unit = {}
) {
    // Description unifiée pour TalkBack qui résume toutes les valeurs.
    // On préfixe par "Maintenant X°" si dispo — c'est l'info la plus utile
    // au premier abord pour quelqu'un qui ouvre l'app.
    val context = LocalContext.current
    val baseDescription = com.meteocompare.app.ui.accessibility.A11yFormatter
        .todaySummaryDescription(context, today, modelCount)
    val a11yDescription = if (currentTemp != null) {
        context.getString(R.string.a11y_now_temp, currentTemp.roundToInt()) + ". $baseDescription"
    } else baseDescription

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = a11yDescription
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.today_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    // Formatter ré-créé via remember(locale) — sinon le top-level
                    // val resterait sur la locale initiale du process pour toute
                    // sa vie. Pattern "EEEE d MMMM" en FR donne "lundi 1 janvier",
                    // en EN donne "Monday 1 January".
                    val dateLocale = LocalConfiguration.current.locales[0]
                    val longDateFmt = remember(dateLocale) {
                        DateTimeFormatter.ofPattern("EEEE d MMMM", dateLocale)
                    }
                    Text(
                        text = today.date.format(longDateFmt)
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                today.overallPercent?.let { ConfidenceBadge(it, onClick = onConfidenceClick) }
            }

            // Température "maintenant" — bloc principal en grand. Placé entre
            // le titre et les détails parce que c'est l'info de premier plan
            // que les utilisateurs cherchent en ouvrant l'app. Si pas dispo,
            // on saute simplement ce bloc (le layout reste cohérent).
            if (currentTemp != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${currentTemp.roundToInt()}°",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    // Icône temps à droite de la grosse température. Taille 40dp
                    // pour rivaliser visuellement avec displayMedium sans la
                    // dominer. Tinte sémantique pour que la couleur véhicule
                    // l'info même avant que l'utilisateur identifie l'icône.
                    // Padding-bottom 8dp pour aligner sur la baseline du chiffre.
                    if (currentCondition != null) {
                        Spacer(Modifier.width(12.dp))
                        WeatherIconDecorative(
                            condition = currentCondition,
                            size = 40.dp,
                            tint = currentCondition.semanticTint(),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.now_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            // Pluriels gérés via 2 string resources distinctes — la grammaire FR/EN
            // ne suit pas la même règle (FR: 1=singulier, 2+=pluriel ; EN: 1=singulier,
            // 0+2+=pluriel). On simplifie en "1 = singulier, sinon pluriel".
            Text(
                text = if (modelCount > 1)
                    stringResource(R.string.models_analysed_many, modelCount)
                else
                    stringResource(R.string.models_analysed_one, modelCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))

            VariableRow(stringResource(R.string.var_temp_max), today.tempMax, "°")
            today.tempMin?.let {
                Spacer(Modifier.height(4.dp))
                VariableRow(stringResource(R.string.var_temp_min), it, "°")
            }
            Spacer(Modifier.height(4.dp))
            PrecipRow(today.precipitation)
            today.windMax?.let {
                Spacer(Modifier.height(4.dp))
                VariableRow(stringResource(R.string.var_wind_max), it, " km/h")
            }
        }
    }
}

@Composable
private fun VariableRow(label: String, score: ConfidenceScore?, unit: String) {
    if (score == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        val text = if (score.spread <= 1.0) {
            "${score.meanValue.roundToInt()}$unit"
        } else {
            "${score.minValue.roundToInt()}-${score.maxValue.roundToInt()}$unit"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(8.dp))
            ConfidencePill(score.percent)
        }
    }
}

@Composable
private fun PrecipRow(precip: PrecipitationConfidence?) {
    if (precip == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.var_precipitation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        val text = when (precip) {
            is PrecipitationConfidence.NoRain -> stringResource(R.string.precip_dry)
            is PrecipitationConfidence.Rain ->
                "${precip.minMm.roundToInt()}-${precip.maxMm.roundToInt()} mm"
            is PrecipitationConfidence.Divided ->
                stringResource(R.string.precip_divided, precip.modelsForRain, precip.modelCount)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(8.dp))
            ConfidencePill(precip.percent)
        }
    }
}

@Composable
private fun ConfidencePill(percent: Int) {
    val color = confidenceColor(percent)
    Surface(
        color = color.copy(alpha = 0.2f),
        modifier = Modifier.clip(MaterialTheme.shapes.extraSmall)
    ) {
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ConfidenceBadge(percent: Int, onClick: () -> Unit = {}) {
    // confidenceColor() renvoie une couleur calibrée pour le thème :
    //   - clair : foncée → bon contraste avec le texte `surface` (clair)
    //   - sombre : pastel claire → bon contraste avec le texte `surface` (foncé)
    // En gardant le texte sur `surface`, on a un duo couleur/texte qui
    // s'inverse correctement entre les deux thèmes.
    val color = confidenceColor(percent)
    // Modifier.clickable plutôt que Surface(onClick=) : la surcharge onClick
    // de Surface est marquée @ExperimentalMaterial3Api dans certaines
    // versions du BOM, et on évite de propager l'opt-in pour si peu. On
    // garde le ripple natif via clickable(role=Button) qui le configure
    // automatiquement. La chevron Arrow signale visuellement le tap.
    val a11yLabel = stringResource(R.string.a11y_open_confidence_explanation, percent)
    Surface(
        color = color,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = a11yLabel }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.confidence_badge_percent, percent),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.surface
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null, // décoratif — la sémantique est sur Surface
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun PartialErrorsSection(errors: Map<WeatherModel, String>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.models_unavailable),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(4.dp))
        errors.forEach { (model, message) ->
            Text(
                text = "• ${model.displayName} : $message",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Constants ──────────────────────────────────────────────────────────────

internal const val TAG_DETAIL_LOADING = "detail_loading"
internal const val TAG_DETAIL_ERROR = "detail_error"
internal const val TAG_DETAIL_LOADED = "detail_loaded"

// ============================================================================
//  Légendes des tableaux précipitations et vent
// ============================================================================
//
//  Pourquoi 4 chips et non 5 (correspondant aux 5 paliers du styler) :
//  le premier palier "neutre" (≈ 0 dans le tableau) n'a pas de couleur dédiée
//  — c'est juste l'absence de styling, donc rien à expliquer en légende.
//  Les 4 chips restants couvrent les 4 paliers visibles.
//
//  Layout en FlowRow : compact sur 1 ligne sur la plupart des téléphones,
//  bascule sur 2 lignes sur les écrans très étroits sans perte d'info.

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrecipitationLegend() {
    LegendChipsRow(
        chips = listOf(
            Color(0xFF4FC3F7) to stringResource(R.string.precip_legend_light),
            Color(0xFF1E88E5) to stringResource(R.string.precip_legend_moderate),
            Color(0xFF1565C0) to stringResource(R.string.precip_legend_strong),
            Color(0xFF0D47A1) to stringResource(R.string.precip_legend_very_strong)
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WindLegend() {
    LegendChipsRow(
        chips = listOf(
            Color(0xFFFFB74D) to stringResource(R.string.wind_legend_light),
            Color(0xFFFB8C00) to stringResource(R.string.wind_legend_moderate),
            Color(0xFFE64A19) to stringResource(R.string.wind_legend_strong),
            Color(0xFFC62828) to stringResource(R.string.wind_legend_storm)
        )
    )
}

/** Rangée de chips colorées (dot + label), utilisée par les deux légendes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegendChipsRow(chips: List<Pair<Color, String>>) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        chips.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(10.dp)
                        .background(color, shape = androidx.compose.foundation.shape.CircleShape)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================================================
//  Stylers d'intensité — couleur + graisse modulées selon la valeur
// ============================================================================
//
//  Pourquoi des bins discrets et pas une interpolation continue :
//
//  Une interpolation HSL color de "neutral → dark blue" sur [0, 30mm] donne
//  une couleur visuellement légèrement différente à chaque pas, ce qui rend
//  les cellules adjacentes "pas-tout-à-fait-pareil" — pénible à lire, donne
//  une fausse impression de granularité. Des bins de 4-5 paliers calés sur
//  des seuils meteorologiques réels (drizzle, pluie modérée, forte, etc.)
//  rendent les sauts de couleur LISIBLES comme du signal — l'œil identifie
//  immédiatement les jours "remarquables" par rapport au reste.
//
//  Les seuils correspondent grossièrement aux catégories de Météo-France et
//  à l'échelle de Beaufort respectivement, mais simplifiées pour 5 paliers.

/**
 * Style de la cellule en fonction des précipitations en mm/jour.
 *
 *   - < 0.05 mm  : null (neutre)
 *   - 0.05–1    : bleu clair, Normal
 *   - 1–5       : bleu, Medium
 *   - 5–15      : bleu foncé, SemiBold
 *   - > 15      : bleu très foncé, Bold
 *
 *  Au-delà de 15 mm/jour on est dans la pluie forte (avertissement orange en
 *  général). Le maximum visuel est calé là pour que les fortes pluies hivernales
 *  ou orages d'été ressortent clairement.
 */
private fun precipitationStyle(mm: Double): ValueStyle? = when {
    mm < 0.05 -> null
    mm < 1.0  -> ValueStyle(color = Color(0xFF4FC3F7), fontWeight = FontWeight.Normal)
    mm < 5.0  -> ValueStyle(color = Color(0xFF1E88E5), fontWeight = FontWeight.Medium)
    mm < 15.0 -> ValueStyle(color = Color(0xFF1565C0), fontWeight = FontWeight.SemiBold)
    else      -> ValueStyle(color = Color(0xFF0D47A1), fontWeight = FontWeight.Bold)
}

/**
 * Style de la cellule en fonction du vent max en km/h.
 *
 *   - < 20 km/h : null (calme, neutre)
 *   - 20–40     : orange clair, Normal           (brise)
 *   - 40–60     : orange, Medium                 (vent modéré)
 *   - 60–80     : orange foncé, SemiBold         (vent fort, vigilance jaune)
 *   - > 80      : rouge, Bold                    (tempête, vigilance orange/rouge)
 *
 *  Progression orange→rouge plutôt que bleu/vert : cohérent avec les codes de
 *  vigilance Météo-France et l'intuition générale "rouge = attention".
 *  Distinct des températures (bleu/rouge) ET des précipitations (bleu) pour
 *  éviter toute confusion visuelle entre tableaux adjacents.
 */
private fun windStyle(kmh: Double): ValueStyle? = when {
    kmh < 20.0 -> null
    kmh < 40.0 -> ValueStyle(color = Color(0xFFFFB74D), fontWeight = FontWeight.Normal)
    kmh < 60.0 -> ValueStyle(color = Color(0xFFFB8C00), fontWeight = FontWeight.Medium)
    kmh < 80.0 -> ValueStyle(color = Color(0xFFE64A19), fontWeight = FontWeight.SemiBold)
    else       -> ValueStyle(color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
}
