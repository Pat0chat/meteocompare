package com.meteocompare.app.ui.citylist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.ui.components.ShimmerBox
import com.meteocompare.app.ui.theme.confidenceColor
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import java.time.LocalDate
import kotlin.math.roundToInt

// ============================================================================
//  Public screen entry — Hilt + state collection
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityListScreen(
    onCityClick: (cityId: String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: CityListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val addState by viewModel.addCityState.collectAsStateWithLifecycle()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }

    CityListContent(
        uiState = uiState,
        onCityClick = onCityClick,
        onAddClick = { showAddSheet = true },
        onSettingsClick = onSettingsClick,
        onRemoveCity = viewModel::onRemoveCity,
        onRetry = viewModel::onRetry,
        onRefresh = viewModel::onRefreshAll
    )

    if (showAddSheet) {
        AddCitySheet(
            state = addState,
            onQueryChanged = viewModel::onSearchQueryChanged,
            onCitySelected = { city ->
                viewModel.onAddCity(city)
                showAddSheet = false
            },
            onDismiss = {
                showAddSheet = false
                viewModel.onSearchQueryChanged("")
            }
        )
    }
}

// ============================================================================
//  Stateless content — internal so tests can drive it without Hilt
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CityListContent(
    uiState: CityListUiState,
    onCityClick: (cityId: String) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRemoveCity: (cityId: String) -> Unit,
    onRetry: (City) -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.testTag(TAG_SETTINGS_BUTTON)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                modifier = Modifier.testTag(TAG_ADD_FAB)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_city))
            }
        }
    ) { padding ->
        Crossfade(
            targetState = uiState.isEmpty,
            animationSpec = tween(250),
            modifier = Modifier.padding(padding),
            label = "list-empty-state"
        ) { empty ->
            if (empty) {
                EmptyState(onAddClick = onAddClick)
            } else {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CityList(
                        items = uiState.items,
                        onCityClick = onCityClick,
                        onRemove = onRemoveCity,
                        onRetry = onRetry
                    )
                }
            }
        }
    }
}

@Composable
internal fun CityList(
    items: List<CityCardState>,
    onCityClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRetry: (City) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_CITY_LIST),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 96.dp, // espace pour le FAB
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.city.id }) { state ->
            CityCard(
                state = state,
                onClick = { onCityClick(state.city.id) },
                onRemove = { onRemove(state.city.id) },
                onRetry = { onRetry(state.city) },
                // animateItem() permet aux ajouts/suppressions d'animer
                // proprement à l'intérieur de la LazyColumn.
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(300),
                    fadeOutSpec = tween(200)
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CityCard(
    state: CityCardState,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Description sémantique consolidée pour TalkBack : on remplace l'annonce
    // fragmentée (chaque texte/icône à part) par un résumé fluide qui inclut
    // la ville, les valeurs et le niveau de confiance.
    val context = LocalContext.current
    val a11yDescription = com.meteocompare.app.ui.accessibility.A11yFormatter
        .cityCardDescription(context, state)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("$TAG_CITY_CARD${state.city.id}")
            .semantics(mergeDescendants = true) {
                contentDescription = a11yDescription
                role = Role.Button
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.city.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    val subtitle = state.city.admin1 ?: state.city.country
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                CityCardMenu(onRemove = onRemove)
            }

            Spacer(Modifier.height(12.dp))

            // AnimatedContent entre les états du forecast.
            // La cible utilise une clé type-stable (le simpleName de la classe),
            // pas l'objet lui-même, pour que le Loaded(today=A) →
            // Loaded(today=B) ne soit pas vu comme un changement d'état.
            AnimatedContent(
                targetState = state.forecast,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "forecast-state",
                contentKey = {
                    when (it) {
                        ForecastState.Loading -> "loading"
                        is ForecastState.Loaded -> "loaded"
                        is ForecastState.Error -> "error"
                    }
                }
            ) { forecast ->
                when (forecast) {
                    ForecastState.Loading -> CityCardLoading()
                    is ForecastState.Loaded -> CityCardLoaded(forecast.today, forecast.currentTemp)
                    is ForecastState.Error -> CityCardError(forecast.message, onRetry)
                }
            }
        }
    }
}

@Composable
private fun CityCardLoading() {
    // Skeleton : 3 boxes shimmer alignées comme les futures valeurs réelles.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(modifier = Modifier.size(width = 72.dp, height = 22.dp))
        ShimmerBox(modifier = Modifier.size(width = 56.dp, height = 22.dp))
        ShimmerBox(modifier = Modifier.size(width = 48.dp, height = 24.dp), cornerRadius = 8.dp)
    }
}

@Composable
private fun CityCardError(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.action_retry))
        }
    }
}

@Composable
private fun CityCardLoaded(today: DayConfidence, currentTemp: Double?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TemperatureSummary(currentTemp = currentTemp, tempMax = today.tempMax)
        PrecipitationSummary(precip = today.precipitation)
        ConfidenceBadge(percent = today.overallPercent)
    }
}

@Composable
private fun TemperatureSummary(currentTemp: Double?, tempMax: ConfidenceScore?) {
    // Affichage : grosse temp actuelle + petite "↑ max" en dessous.
    // Si pas de current dispo (cas dégénéré), on retombe sur l'ancien affichage
    // de la max seule pour rester utile.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Thermostat,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        if (currentTemp != null) {
            Column {
                Text(
                    text = "${currentTemp.roundToInt()}°",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (tempMax != null) {
                    // Format intervalle si les modèles divergent significativement,
                    // sinon valeur unique. Le seuil 1°C correspond à du bruit
                    // d'arrondi — au-delà, c'est de la vraie incertitude qu'on
                    // veut surfacer (c'est le différenciateur de l'app).
                    val maxText = if (tempMax.spread <= 1.0) {
                        "${tempMax.meanValue.roundToInt()}°"
                    } else {
                        "${tempMax.minValue.roundToInt()}-${tempMax.maxValue.roundToInt()}°"
                    }
                    Text(
                        text = "↑ $maxText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (tempMax != null) {
            val text = if (tempMax.spread <= 1.0) {
                "${tempMax.meanValue.roundToInt()}°"
            } else {
                "${tempMax.minValue.roundToInt()}-${tempMax.maxValue.roundToInt()}°"
            }
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        } else {
            Text("—", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PrecipitationSummary(precip: PrecipitationConfidence?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.WaterDrop,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        val text = when (precip) {
            null -> "—"
            is PrecipitationConfidence.NoRain -> stringResource(R.string.precip_dry)
            is PrecipitationConfidence.Rain ->
                "${precip.minMm.roundToInt()}-${precip.maxMm.roundToInt()} mm"
            is PrecipitationConfidence.Divided ->
                "${precip.modelsForRain}/${precip.modelCount} ⚠"
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ConfidenceBadge(percent: Int?) {
    if (percent == null) return
    val color = confidenceColor(percent)
    Surface(
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.clip(MaterialTheme.shapes.small)
    ) {
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CityCardMenu(onRemove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_options))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_remove_from_favorites)) },
                onClick = {
                    expanded = false
                    onRemove()
                }
            )
        }
    }
}

@Composable
internal fun EmptyState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_EMPTY_STATE)
            // Padding horizontal porté par le Box (et hérité par la Column
            // centrée), pour que le sous-titre — qui est long — ne vienne
            // pas coller au bord de l'écran sur les petits téléphones. Le
            // padding extérieur du Scaffold ne s'occupe pas des bords
            // latéraux, donc il faut bien le mettre ici.
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.LocationCity,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.empty_favorites_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.empty_favorites_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // textAlign=Center pour la lisibilité d'une description
                // centrée sous un titre — un texte aligné à gauche dans
                // une Column centrée donne un look brouillon.
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_add_city))
            }
        }
    }
}

// ─── Test tags exposés pour les tests d'instrumentation ─────────────────────
internal const val TAG_CITY_LIST = "city_list"
internal const val TAG_CITY_CARD = "city_card_"
internal const val TAG_EMPTY_STATE = "empty_state"
internal const val TAG_ADD_FAB = "add_fab"
internal const val TAG_SETTINGS_BUTTON = "settings_button"

// ─── Previews ────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun CityCardLoadedPreview() {
    MeteoCompareTheme {
        val sample = CityCardState(
            city = City(
                id = "1", name = "Paris", admin1 = "Île-de-France",
                country = "France", latitude = 48.85, longitude = 2.35
            ),
            forecast = ForecastState.Loaded(
                today = DayConfidence(
                    date = LocalDate.now(),
                    tempMax = ConfidenceScore(85, 21.0, 24.0, 22.5, 0.8, 5),
                    tempMin = ConfidenceScore(78, 14.0, 17.0, 15.5, 1.0, 5),
                    precipitation = PrecipitationConfidence.NoRain(100, 5, 0.0),
                    windMax = ConfidenceScore(72, 12.0, 18.0, 15.0, 2.5, 5)
                ),
                currentTemp = 19.0
            )
        )
        Surface { CityCard(state = sample, onClick = {}, onRemove = {}, onRetry = {}) }
    }
}

@Preview(showBackground = true)
@Composable
private fun CityCardLoadingPreview() {
    MeteoCompareTheme {
        val sample = CityCardState(
            city = City(id = "1", name = "Paris", country = "France",
                latitude = 48.85, longitude = 2.35),
            forecast = ForecastState.Loading
        )
        Surface { CityCard(state = sample, onClick = {}, onRemove = {}, onRetry = {}) }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    MeteoCompareTheme {
        Surface { EmptyState(onAddClick = {}) }
    }
}
