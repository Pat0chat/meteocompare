package com.meteocompare.app.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.domain.model.Coverage
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.theme.color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val enabled by viewModel.enabledModels.collectAsStateWithLifecycle()
    var showDonationDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        SettingsContent(
            enabledModels = enabled,
            onToggle = viewModel::onModelToggled,
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
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Modèles météo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Choisissez les modèles à comparer. Plus de modèles = plus de précision sur la confiance, mais plus de requêtes.",
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
                text = "Au moins un modèle doit rester activé.",
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
                    text = "À propos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Données météo fournies par Open-Meteo (open-meteo.com).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Modèles : AROME et ARPEGE par Météo-France, ICON par DWD, GFS par NOAA, ECMWF par le Centre Européen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Confidentialité",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Cette application ne collecte aucune donnée personnelle. Aucun analytics, aucun tracking, aucune publicité. Vos favoris et préférences restent sur votre appareil.",
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
                    Text("Soutenir le développement")
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
