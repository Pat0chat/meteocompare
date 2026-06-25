package com.meteocompare.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

/**
 * Dialog des plateformes de don.
 *
 * Politique :
 *   - Lien web vers le navigateur seulement, jamais de Play Billing
 *     (interdit par Google Play pour les apps non-charity).
 *   - Aucun privilège accordé aux donateurs — pas de "pro version".
 *   - Les URLs contiennent `USERNAME` placeholder — à remplacer par
 *     les vrais handles avant publication.
 *
 * Les liens sont ouverts via Intent.ACTION_VIEW, ce qui permet à
 * l'utilisateur de choisir son navigateur (cohérent avec ses
 * préférences système).
 */
@Composable
fun DonationDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        // FLAG_ACTIVITY_NEW_TASK n'est pas strictement nécessaire ici
        // (LocalContext est l'activity), mais le rend explicite pour
        // les linters statiques.
        context.startActivity(intent)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Soutenir le développement") },
        text = {
            Column {
                Text(
                    text = "MeteoCompare est gratuit, open-source et sans publicité. Si vous souhaitez soutenir le développement :",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))

                DonationPlatformRow(
                    name = "Liberapay",
                    description = "Contributions hebdomadaires, friendly FOSS",
                    onClick = { openUrl("https://liberapay.com/USERNAME") }
                )
                HorizontalDivider()
                DonationPlatformRow(
                    name = "GitHub Sponsors",
                    description = "Contributions mensuelles via GitHub",
                    onClick = { openUrl("https://github.com/sponsors/USERNAME") }
                )
                HorizontalDivider()
                DonationPlatformRow(
                    name = "Ko-fi",
                    description = "Don ponctuel \"offre-moi un café\"",
                    onClick = { openUrl("https://ko-fi.com/USERNAME") }
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Aucun privilège n'est accordé aux donateurs. Le code source et l'app restent identiques pour tout le monde.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}

@Composable
private fun DonationPlatformRow(
    name: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
