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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.Coverage
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.theme.color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val enabled by viewModel.enabledModels.collectAsStateWithLifecycle()
    val theme by viewModel.themePreference.collectAsStateWithLifecycle()
    val language by viewModel.languagePreference.collectAsStateWithLifecycle()
    var showDonationDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                // Source de vérité : SharedPreferences (sync, commit garanti).
                // On écrit ICI, sur le UI thread, AVANT le recreate(). Comme ça
                // attachBaseContext() lit la valeur fraîchement persistée et
                // applique la locale immédiatement.
                //
                // Pourquoi commit() et pas apply() :
                //   - apply() est async (écriture disque en background). Si on
                //     recreate() avant que l'écriture soit committée, la nouvelle
                //     Activity lit l'ANCIENNE valeur.
                //   - commit() bloque le UI thread jusqu'à ce que l'écriture
                //     soit faite — quelques millisecondes max pour un fichier
                //     trivial comme celui-ci. Acceptable pour ce cas d'usage.
                context.getSharedPreferences(
                    com.meteocompare.app.MainActivity.LOCALE_PREFS,
                    android.content.Context.MODE_PRIVATE
                ).edit()
                    .putString(
                        com.meteocompare.app.MainActivity.LOCALE_KEY,
                        preference.bcp47Tag
                    )
                    .commit()

                // Notification système Android 13+ : permet à l'utilisateur de
                // voir/changer la langue de l'app depuis Settings > Languages.
                // Mais ce n'est PLUS notre source de vérité (cf. attachBaseContext).
                val locales = preference.bcp47Tag?.let {
                    androidx.core.os.LocaleListCompat.forLanguageTags(it)
                } ?: androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)

                // DataStore async pour l'UI du toggle (état du SegmentedButton)
                viewModel.onLanguageSelected(preference)

                // Recreate — attachBaseContext sera ré-appelé avec un Context
                // neuf qui lira le SharedPreferences fraîchement écrit.
                (context as? android.app.Activity)?.recreate()
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
private fun SettingsContent(
    enabledModels: Set<WeatherModel>,
    onToggle: (WeatherModel, Boolean) -> Unit,
    theme: ThemePreference,
    onThemeSelected: (ThemePreference) -> Unit,
    language: LanguagePreference,
    onLanguageSelected: (LanguagePreference) -> Unit,
    onDonateClick: () -> Unit,
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 16.dp
        )
    ) {
        // Section "Apparence" en premier — c'est le réglage le plus universel,
        // ceux qui rentrent dans les settings le cherchent souvent en premier.
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

        // Section "Langue" — placée juste après apparence car même nature
        // (choix d'affichage personnel). Changer la langue déclenche une
        // recréation de l'Activity (par AppCompatDelegate), donc le user verra
        // l'écran clignoter brièvement — comportement standard Android.
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
            }
        }

        items(WeatherModel.entries, key = { it.name }) { model ->
            ModelRow(
                model = model,
                enabled = model in enabledModels,
                canDisable = enabledModels.size > 1 || model !in enabledModels,
                onToggle = { onToggle(model, it) }
            )
            HorizontalDivider()
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_models_min_warning),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Section "À propos" — attribution Open-Meteo + privacy en clair.
        // Open-Meteo demande explicitement une attribution visible dans
        // les apps qui utilisent leur API gratuite.
        item {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_about_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_about_data_source),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
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

                // Bouton "Soutenir" — déclenche le dialog des plateformes de don.
                // Placé en bas car secondaire : c'est offrir une option, pas
                // l'imposer. Style OutlinedButton pour ne pas concurrencer
                // visuellement la liste de modèles.
                OutlinedButton(
                    onClick = onDonateClick,
                    modifier = Modifier.fillMaxWidth()
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
    }
}

@Composable
private fun ModelRow(
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pastille de couleur du modèle (la même que sur le graph)
        Surface(
            color = model.color(),
            modifier = Modifier.size(14.dp).clip(CircleShape)
        ) {}
        Spacer(Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${model.resolutionKm} km · ${coverageLabel(model.coverage)} · ${model.maxForecastDays} j",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Checkbox(
            checked = enabled,
            onCheckedChange = if (clickable) onToggle else null
        )
    }
}

private fun coverageLabel(coverage: Coverage): String = when (coverage) {
    Coverage.FRANCE -> "France"
    Coverage.EUROPE -> "Europe"
    Coverage.GLOBAL -> "Monde"
}

/**
 * Sélecteur de thème en SingleChoiceSegmentedButtonRow Material 3.
 *
 * Sémantique : RadioGroup-équivalent avec rendu segmenté. Plus compact
 * qu'une liste de RadioButton (3 lignes), plus accessible que des chips,
 * et l'état "sélectionné" est très visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelector(
    selected: ThemePreference,
    onSelect: (ThemePreference) -> Unit
) {
    val options = listOf(
        ThemePreference.SYSTEM to stringResource(R.string.theme_system),
        ThemePreference.LIGHT to stringResource(R.string.theme_light),
        ThemePreference.DARK to stringResource(R.string.theme_dark)
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { idx, (pref, label) ->
            SegmentedButton(
                selected = selected == pref,
                onClick = { onSelect(pref) },
                shape = SegmentedButtonDefaults.itemShape(index = idx, count = options.size),
                icon = { /* pas d'icône — état rendu lisible par le fill */ }
            ) {
                Text(label)
            }
        }
    }
}

/**
 * Sélecteur de langue — même UX que [ThemeSelector] : SegmentedButton avec 3
 * options (Système / Français / English).
 *
 * Le choix d'une langue déclenche un `AppCompatDelegate.setApplicationLocales`
 * dans le ViewModel, ce qui recrée l'Activity. L'utilisateur verra un flash
 * pendant cette recréation — c'est le comportement standard Android pour les
 * per-app languages (Android 13+ ou via AppCompat sur versions antérieures).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelector(
    selected: LanguagePreference,
    onSelect: (LanguagePreference) -> Unit
) {
    val options = listOf(
        LanguagePreference.SYSTEM to stringResource(R.string.language_system),
        LanguagePreference.FRENCH to stringResource(R.string.language_french),
        LanguagePreference.ENGLISH to stringResource(R.string.language_english)
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { idx, (pref, label) ->
            SegmentedButton(
                selected = selected == pref,
                onClick = { onSelect(pref) },
                shape = SegmentedButtonDefaults.itemShape(index = idx, count = options.size),
                icon = { /* idem, pas d'icône check */ }
            ) {
                Text(label)
            }
        }
    }
}
