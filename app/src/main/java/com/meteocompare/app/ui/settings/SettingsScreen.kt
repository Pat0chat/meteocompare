package com.meteocompare.app.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.Coverage
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.components.ModernStateChip
import com.meteocompare.app.ui.components.OpenMeteoAttribution
import com.meteocompare.app.ui.components.ModernSlidingSelector
import com.meteocompare.app.ui.theme.color
import kotlinx.coroutines.launch

// ═════════════════════════════════════════════════════════════════════════════
//  Tri des modèles
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Mode de tri/regroupement de la liste des modèles.
 *
 *   - [ZONE]     : groupé par Coverage (France → Europe → Monde). C'est le
 *                  défaut : la plupart des utilisateurs veulent scanner les
 *                  modèles pertinents pour LEUR zone rapidement.
 *   - [FAMILLE]  : groupé par ModelFamily (institution productrice) — utile
 *                  pour activer/désactiver tous les modèles d'une institution
 *                  en un coup d'œil, ou comparer les diversité méthodologiques.
 *   - [FINESSE]  : trié par résolution native (finesse) du plus fin au plus
 *                  grossier, sans regroupement. Utile pour privilégier les
 *                  modèles haute-résolution pour une localisation qu'ils
 *                  couvrent bien.
 *
 * Le tri est purement visuel — la liste des modèles activés reste identique,
 * seul l'ordre d'affichage et les headers change.
 */
enum class ModelSortMode {
    ZONE, FAMILLE, FINESSE;

    companion object {
        val Saver: Saver<ModelSortMode, String> = Saver(
            save = { it.name },
            restore = { runCatching { valueOf(it) }.getOrDefault(ZONE) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val enabled by viewModel.enabledModels.collectAsStateWithLifecycle()
    val theme by viewModel.themePreference.collectAsStateWithLifecycle()
    val language by viewModel.languagePreference.collectAsStateWithLifecycle()
    val refreshInterval by viewModel.refreshInterval.collectAsStateWithLifecycle()
    val forecastEngine by viewModel.forecastEngine.collectAsStateWithLifecycle()
    var showDonationDialog by rememberSaveable { mutableStateOf(false) }
    var biasRefreshRequested by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(TAG_SETTINGS_BACK)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        SettingsContent(
            enabledModels = enabled,
            onToggle = viewModel::onModelToggled,
            theme = theme,
            onThemeSelected = viewModel::onThemeSelected,
            language = language,
            onLanguageSelected = { preference ->
                scope.launch {
                    // La préférence canonique est écrite avant recreate().
                    // attachBaseContext() relit alors immédiatement la nouvelle
                    // valeur, sans copie concurrente dans AppCompat/DataStore.
                    viewModel.onLanguageSelected(preference)
                    (context as? android.app.Activity)?.recreate()
                }
            },
            refreshInterval = refreshInterval,
            onRefreshIntervalSelected = viewModel::onRefreshIntervalSelected,
            forecastEngine = forecastEngine,
            onForecastEngineSelected = viewModel::onForecastEngineSelected,
            biasRefreshRequested = biasRefreshRequested,
            onBiasRefreshClick = {
                viewModel.onBiasRefreshRequested()
                biasRefreshRequested = true
            },
            onDonateClick = { showDonationDialog = true },
            padding = padding
        )
    }

    if (showDonationDialog) {
        DonationDialog(onDismiss = { showDonationDialog = false })
    }
}

@Composable
internal fun SettingsContent(
    enabledModels: Set<WeatherModel>,
    onToggle: (WeatherModel, Boolean) -> Unit,
    theme: ThemePreference,
    onThemeSelected: (ThemePreference) -> Unit,
    language: LanguagePreference,
    onLanguageSelected: (LanguagePreference) -> Unit,
    refreshInterval: RefreshInterval,
    onRefreshIntervalSelected: (RefreshInterval) -> Unit,
    forecastEngine: ForecastEngine = ForecastEngine.DEFAULT,
    onForecastEngineSelected: (ForecastEngine) -> Unit = {},
    biasRefreshRequested: Boolean,
    onBiasRefreshClick: () -> Unit,
    onDonateClick: () -> Unit,
    padding: PaddingValues
) {
    // État du tri des modèles — survit à la rotation et au dark-mode toggle.
    // Défaut ZONE parce que 90% des utilisateurs raisonnent d'abord "modèles
    // pour ma région" (l'app est franco-centrée à l'origine, la zone est
    // souvent le filtre le plus actionnable).
    var sortMode by rememberSaveable(stateSaver = ModelSortMode.Saver) {
        mutableStateOf(ModelSortMode.ZONE)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(TAG_SETTINGS_ROOT),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 16.dp
        )
    ) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_appearance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                ThemeSelector(selected = theme, onSelect = onThemeSelected)
            }
        }
        item { HorizontalDivider() }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                LanguageSelector(selected = language, onSelect = onLanguageSelected)
            }
        }
        item { HorizontalDivider() }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_refresh_interval_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_refresh_interval_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                RefreshIntervalSelector(
                    selected = refreshInterval,
                    onSelect = onRefreshIntervalSelected
                )
            }
        }
        item { HorizontalDivider() }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_forecast_engine_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_forecast_engine_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                ForecastEngineSelector(
                    selected = forecastEngine,
                    onSelect = onForecastEngineSelected
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            modifier = Modifier.size(30.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Σ",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(
                                    R.string.engine_comparison_selected,
                                    forecastEngineLabel(forecastEngine)
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = forecastEngineDescription(forecastEngine),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
        item { HorizontalDivider() }

        // ─── Section "Modèles météo" ────────────────────────────────────
        // Header + description + sélecteur de tri. Le sélecteur est intégré
        // ici pour rester proche du contexte "cette liste concerne les
        // modèles" — le déplacer plus haut/bas romprait le fil narratif.
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text(
                    text = stringResource(R.string.settings_models_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_models_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                ModelSortSelector(selected = sortMode, onSelect = { sortMode = it })
            }
        }

        // Rendu de la liste avec headers de section selon le tri. Une seule
        // méthode qui écrit dans le LazyListScope — évite de dupliquer la
        // logique de LazyColumn.items par mode.
        renderModelList(
            enabledModels = enabledModels,
            sortMode = sortMode,
            onToggle = onToggle
        )

        item {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_models_min_warning),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Section "À propos"
        item {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_about_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.settings_about_version,
                        com.meteocompare.app.BuildConfig.VERSION_NAME
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_about_data_source),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OpenMeteoAttribution(
                    text = stringResource(R.string.open_meteo_link_label),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_about_marine),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_about_models_credit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_privacy_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_privacy_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(20.dp))

                OutlinedButton(
                    onClick = onDonateClick,
                    modifier = Modifier.fillMaxWidth().testTag(TAG_SETTINGS_DONATE)
                ) {
                    Icon(
                        Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_support_dev))
                }
            }
        }

        // Action de maintenance volontairement placée tout en bas : elle ne
        // fait pas partie des réglages courants et doit rester exceptionnelle.
        item {
            HorizontalDivider()
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_bias_refresh_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_bias_refresh_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onBiasRefreshClick,
                    enabled = !biasRefreshRequested,
                    modifier = Modifier.fillMaxWidth().testTag(TAG_SETTINGS_BIAS_REFRESH)
                ) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_bias_refresh_action))
                }
                if (biasRefreshRequested) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_bias_refresh_queued),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Rend la liste des modèles dans le LazyListScope courant avec des headers
 * de section selon le [sortMode]. Extraite en extension pour rester dans le
 * même LazyColumn que le reste des sections settings (sans avoir à imbriquer
 * un LazyColumn dans un LazyColumn, ce qui serait un antipattern).
 *
 * ─── Note sur le tri ────────────────────────────────────────────────────
 * Pour ZONE et FAMILLE, on trie par (clé de groupe, resolutionKm, ordinal) —
 * dans chaque groupe on privilégie la finesse, et l'ordinal sert de
 * tie-breaker stable (ex. AROME_FRANCE_HD et AROME_FRANCE ont la même résol
 * effective 1.5-2.5, on veut un ordre reproductible).
 *
 * Pour FINESSE, tri global par (resolutionKm ASC, ordinal) — le modèle le
 * plus fin en tête. Pas de headers (mode plat).
 */
private fun androidx.compose.foundation.lazy.LazyListScope.renderModelList(
    enabledModels: Set<WeatherModel>,
    sortMode: ModelSortMode,
    onToggle: (WeatherModel, Boolean) -> Unit
) {
    val allModels = WeatherModel.entries.toList()

    when (sortMode) {
        ModelSortMode.ZONE -> {
            // Groupé par Coverage — l'ordre déclaré de l'enum est déjà
            // "plus local vers plus étendu", ce qui est le bon ordre pour
            // afficher France → Europe → Monde.
            val grouped = allModels.groupBy { it.coverage }
                .toSortedMap(compareBy { it.ordinal })
            grouped.forEach { (coverage, models) ->
                item(key = "zone_${coverage.name}") {
                    ModelGroupHeader(text = coverageLabel(coverage))
                }
                val sorted = models.sortedWith(
                    compareBy({ it.resolutionKm }, { it.ordinal })
                )
                sorted.forEach { model ->
                    item(key = "model_${model.name}") {
                        CompactModelRow(
                            model = model,
                            enabled = model in enabledModels,
                            canDisable = enabledModels.size > 1 || model !in enabledModels,
                            onToggle = { onToggle(model, it) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        ModelSortMode.FAMILLE -> {
            val grouped = allModels.groupBy { it.family }
                .toSortedMap(compareBy { it.ordinal })
            grouped.forEach { (family, models) ->
                item(key = "family_${family.name}") {
                    ModelGroupHeader(text = family.displayName)
                }
                val sorted = models.sortedWith(
                    compareBy({ it.resolutionKm }, { it.ordinal })
                )
                sorted.forEach { model ->
                    item(key = "model_${model.name}") {
                        CompactModelRow(
                            model = model,
                            enabled = model in enabledModels,
                            canDisable = enabledModels.size > 1 || model !in enabledModels,
                            onToggle = { onToggle(model, it) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        ModelSortMode.FINESSE -> {
            val sorted = allModels.sortedWith(
                compareBy({ it.resolutionKm }, { it.ordinal })
            )
            sorted.forEach { model ->
                item(key = "model_${model.name}") {
                    CompactModelRow(
                        model = model,
                        enabled = model in enabledModels,
                        canDisable = enabledModels.size > 1 || model !in enabledModels,
                        onToggle = { onToggle(model, it) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Header de groupe dans la liste des modèles — texte léger, discret. Rendu
 * en labelMedium pour rester subordonné aux titres de section principaux
 * (settings_models_title en titleMedium/SemiBold), tout en restant
 * visuellement distinct des lignes de modèles.
 */
@Composable
private fun ModelGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * Ligne de modèle compactée par rapport à l'ancienne version.
 *
 * ─── Optimisations de densité ──────────────────────────────────────────
 * Ancien layout : 2 lignes de texte (displayName en bodyLarge/Medium + méta
 * en bodySmall), padding vertical 12dp → hauteur ~64dp.
 *
 * Nouveau : 1 seule ligne combinant nom (bodyMedium/Medium) + métadonnées
 * courtes (labelSmall) séparées par bullet, padding vertical 8dp → hauteur
 * ~40dp. Gain : ~35% de hauteur, on voit deux fois plus de modèles à l'écran
 * sur un téléphone standard. Utile maintenant que l'enum WeatherModel a
 * grossi à 19 modèles (débordement inévitable sinon).
 *
 * ─── Format des méta ───────────────────────────────────────────────────
 * "1.5 km · 48 h" ou "11 km · 4 j" — résolution + horizon natif. La zone n'est plus dupliquée sur
 * chaque ligne car elle est déjà portée par le header de groupe en mode ZONE.
 * En mode FAMILLE ou FINESSE, l'utilisateur peut inférer la zone depuis le
 * nom du modèle ("EU" dans "ICON-EU", etc.) — trade-off acceptable pour la
 * densité gagnée.
 */
@Composable
private fun CompactModelRow(
    model: WeatherModel,
    enabled: Boolean,
    canDisable: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val clickable = canDisable || !enabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (clickable) it.clickable { onToggle(!enabled) } else it }
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("$TAG_SETTINGS_MODEL${model.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pastille de couleur du modèle — 10dp (vs 14dp historique) pour
        // rester lisible sans dominer la ligne compacte.
        Surface(
            color = model.color(),
            modifier = Modifier.size(10.dp).clip(CircleShape)
        ) {}
        Spacer(Modifier.size(10.dp))

        // Nom + méta sur la même ligne avec baseline alignée. Le weight sur
        // le nom pousse les métadonnées et la checkbox contre le bord droit.
        Text(
            text = model.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(
                R.string.model_metadata,
                formatResolution(model.resolutionKm),
                formatForecastHorizon(model.forecastHorizonHours)
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )

        Checkbox(
            checked = enabled,
            onCheckedChange = if (clickable) onToggle else null,
            // Réduit la taille interne du Checkbox — le default M3 a une
            // hitbox de 48dp qui devient énorme sur cette ligne compacte.
            // Pas de override officiel : on laisse tel quel pour préserver
            // l'accessibilité tactile (48dp est le min recommandé WCAG).
        )
    }
}

/**
 * Format compact de la résolution : "1.5 km" pour < 10 km (précision utile
 * en mésoéchelle), "13 km" pour ≥ 10 km (le .0 devient parasite).
 */
private fun formatResolution(km: Double): String =
    if (km < 10.0) "%.1f km".format(km) else "${km.toInt()} km"

/** Horizon natif affiché sans le confondre avec le `forecast_days` entier de l'API. */
@Composable
private fun formatForecastHorizon(hours: Int): String = when {
    hours % 24 == 0 -> stringResource(R.string.model_horizon_days, hours / 24)
    hours % 12 == 0 -> stringResource(R.string.model_horizon_days_decimal, hours / 24.0)
    else -> stringResource(R.string.model_horizon_hours, hours)
}

@Composable
private fun coverageLabel(coverage: Coverage): String = stringResource(
    when (coverage) {
        Coverage.FRANCE -> R.string.model_coverage_france
        Coverage.EUROPE -> R.string.model_coverage_europe
        Coverage.UNITED_STATES -> R.string.model_coverage_united_states
        Coverage.GLOBAL -> R.string.model_coverage_global
    }
)

/** Sélecteur de mode de tri des modèles. */
@Composable
private fun ModelSortSelector(
    selected: ModelSortMode,
    onSelect: (ModelSortMode) -> Unit
) {
    val options = listOf(ModelSortMode.ZONE, ModelSortMode.FAMILLE, ModelSortMode.FINESSE)
    ModernSlidingSelector(
        options = options,
        selected = selected,
        onSelected = onSelect,
        label = { mode ->
            stringResource(
                when (mode) {
                    ModelSortMode.ZONE -> R.string.model_sort_zone
                    ModelSortMode.FAMILLE -> R.string.model_sort_family
                    ModelSortMode.FINESSE -> R.string.model_sort_finesse
                }
            )
        },
        accent = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        itemModifier = { mode -> Modifier.testTag("$TAG_SETTINGS_SORT${mode.name}") }
    )
}

@Composable
private fun ThemeSelector(
    selected: ThemePreference,
    onSelect: (ThemePreference) -> Unit
) {
    val options = listOf(ThemePreference.SYSTEM, ThemePreference.LIGHT, ThemePreference.DARK)
    ModernSlidingSelector(
        options = options,
        selected = selected,
        onSelected = onSelect,
        label = { pref ->
            stringResource(
                when (pref) {
                    ThemePreference.SYSTEM -> R.string.theme_system
                    ThemePreference.LIGHT -> R.string.theme_light
                    ThemePreference.DARK -> R.string.theme_dark
                }
            )
        },
        accent = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        itemModifier = { pref -> Modifier.testTag("$TAG_SETTINGS_THEME${pref.name}") }
    )
}

@Composable
private fun LanguageSelector(
    selected: LanguagePreference,
    onSelect: (LanguagePreference) -> Unit
) {
    val options = listOf(
        LanguagePreference.SYSTEM,
        LanguagePreference.FRENCH,
        LanguagePreference.ENGLISH,
        LanguagePreference.SPANISH,
        LanguagePreference.GERMAN,
        LanguagePreference.ITALIAN
    )
    ModernSlidingSelector(
        options = options,
        selected = selected,
        onSelected = onSelect,
        label = { pref ->
            stringResource(
                when (pref) {
                    LanguagePreference.SYSTEM -> R.string.language_system_short
                    LanguagePreference.FRENCH -> R.string.language_french_short
                    LanguagePreference.ENGLISH -> R.string.language_english_short
                    LanguagePreference.SPANISH -> R.string.language_spanish_short
                    LanguagePreference.GERMAN -> R.string.language_german_short
                    LanguagePreference.ITALIAN -> R.string.language_italian_short
                }
            )
        },
        accent = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        itemModifier = { pref -> Modifier.testTag("$TAG_SETTINGS_LANGUAGE${pref.name}") },
        accessibilityLabel = { pref ->
            stringResource(
                when (pref) {
                    LanguagePreference.SYSTEM -> R.string.language_system
                    LanguagePreference.FRENCH -> R.string.language_french
                    LanguagePreference.ENGLISH -> R.string.language_english
                    LanguagePreference.SPANISH -> R.string.language_spanish
                    LanguagePreference.GERMAN -> R.string.language_german
                    LanguagePreference.ITALIAN -> R.string.language_italian
                }
            )
        }
    )
}

@Composable
private fun ForecastEngineSelector(
    selected: ForecastEngine,
    onSelect: (ForecastEngine) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        ForecastEngine.entries.forEach { engine ->
            ModernStateChip(
                selected = selected == engine,
                onClick = { onSelect(engine) },
                label = forecastEngineLabel(engine),
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("$TAG_SETTINGS_ENGINE${engine.name}")
            )
        }
    }
}

@Composable
private fun forecastEngineLabel(engine: ForecastEngine): String = stringResource(
    when (engine) {
        ForecastEngine.MULTI_CONSENSUS -> R.string.forecast_engine_multi_consensus
        ForecastEngine.CALIBRATION -> R.string.forecast_engine_calibration
        ForecastEngine.SCENARIOS -> R.string.forecast_engine_scenarios
        ForecastEngine.ADAPTIVE -> R.string.forecast_engine_adaptive
    }
)

@Composable
private fun forecastEngineDescription(engine: ForecastEngine): String = stringResource(
    when (engine) {
        ForecastEngine.MULTI_CONSENSUS -> R.string.forecast_engine_multi_consensus_desc
        ForecastEngine.CALIBRATION -> R.string.forecast_engine_calibration_desc
        ForecastEngine.SCENARIOS -> R.string.forecast_engine_scenarios_desc
        ForecastEngine.ADAPTIVE -> R.string.forecast_engine_adaptive_desc
    }
)

@Composable
private fun RefreshIntervalSelector(
    selected: RefreshInterval,
    onSelect: (RefreshInterval) -> Unit
) {
    val options = listOf(
        RefreshInterval.MINUTES_15 to stringResource(R.string.refresh_interval_15_min),
        RefreshInterval.MINUTES_30 to stringResource(R.string.refresh_interval_30_min),
        RefreshInterval.HOUR_1 to stringResource(R.string.refresh_interval_1_hour),
        RefreshInterval.HOURS_3 to stringResource(R.string.refresh_interval_3_hours),
        RefreshInterval.HOURS_6 to stringResource(R.string.refresh_interval_6_hours),
        RefreshInterval.MANUAL to stringResource(R.string.refresh_interval_manual)
    )

    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (interval, label) ->
            ModernStateChip(
                selected = selected == interval,
                onClick = { onSelect(interval) },
                label = label,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("$TAG_SETTINGS_REFRESH${interval.name}")
            )
        }
    }
}

internal const val TAG_SETTINGS_ROOT = "settings_root"
internal const val TAG_SETTINGS_BACK = "settings_back"
internal const val TAG_SETTINGS_DONATE = "settings_donate"
internal const val TAG_SETTINGS_BIAS_REFRESH = "settings_bias_refresh"
internal const val TAG_SETTINGS_MODEL = "settings_model_"
internal const val TAG_SETTINGS_SORT = "settings_sort_"
internal const val TAG_SETTINGS_THEME = "settings_theme_"
internal const val TAG_SETTINGS_LANGUAGE = "settings_language_"
internal const val TAG_SETTINGS_REFRESH = "settings_refresh_"
internal const val TAG_SETTINGS_ENGINE = "settings_engine_"
